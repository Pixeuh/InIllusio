package fr.inillusio.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.inillusio.data.importer.CatalogImporter
import fr.inillusio.data.local.DatabaseFileMigration
import fr.inillusio.data.local.InIllusioDatabase
import fr.inillusio.data.local.entities.BoosterRedemptionEntity
import fr.inillusio.data.local.entities.CollectionEntity
import fr.inillusio.data.local.entities.DeliveryEntity
import fr.inillusio.data.preferences.PreferencesFileMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var directory: File

    @Before fun setUp() {
        directory = File(context.cacheDir, "storage-migration-${UUID.randomUUID()}").apply { check(mkdirs()) }
    }

    @After fun tearDown() { directory.deleteRecursively() }

    private fun isolatedContext(folder: File = directory) = object : ContextWrapper(context) {
        override fun getDatabasePath(name: String) = File(folder, name)
    }

    private fun room(file: File) = Room.databaseBuilder(context, InIllusioDatabase::class.java, file.path)
        .addMigrations(InIllusioDatabase.MIGRATION_1_2, InIllusioDatabase.MIGRATION_2_3).build()

    private inline fun <T> InIllusioDatabase.useDatabase(block: (InIllusioDatabase) -> T): T =
        try { block(this) } finally { close() }

    @Test fun databaseCopyIncludesWalAndPreservesCollectionHistoryAndRewardKeys() = runBlocking {
        val storage = isolatedContext()
        val legacy = storage.getDatabasePath("hors-cadre.db")
        room(legacy).useDatabase { database ->
            CatalogImporter(context, database).importBundledCatalog()
            database.catalogDao().putCollection(CollectionEntity("sociologie-01", 100L))
            database.catalogDao().putDelivery(DeliveryEntity(anecdoteId = "sociologie-01", attemptedAt = 101L, succeeded = true))
            database.catalogDao().putBoosterRedemption(BoosterRedemptionEntity("reward-before-update", 102L))
        }
        SQLiteDatabase.openDatabase(legacy.path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use { writer ->
            writer.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
            writer.execSQL("UPDATE collection SET savedAt=1234 WHERE anecdoteId='sociologie-01'")
            assertTrue(File(legacy.path + "-wal").length() > 0)
            assertEquals("inillusio.db", DatabaseFileMigration.prepare(storage))
        }
        val destination = storage.getDatabasePath(DatabaseFileMigration.DATABASE_NAME)
        assertTrue(legacy.isFile)
        room(destination).useDatabase { migrated ->
            val dao = migrated.catalogDao()
            assertEquals(35, dao.anecdoteCount())
            assertEquals(1234L, dao.collectionEntry("sociologie-01")?.savedAt)
            assertTrue(dao.hasSuccessfulDelivery("sociologie-01"))
            assertEquals(-1L, dao.putBoosterRedemption(BoosterRedemptionEntity("reward-before-update", 200L)))
            dao.putCollection(CollectionEntity("sociologie-02", 200L))
        }
        // A second start must neither replace newer data nor delete the original.
        DatabaseFileMigration.prepare(storage)
        room(destination).useDatabase { migrated -> assertEquals(2, migrated.catalogDao().allCollectionEntries().size) }
        room(legacy).useDatabase { original -> assertEquals(1, original.catalogDao().allCollectionEntries().size) }
    }

    @Test fun renamedDatabasesFromBothSupportedOlderSchemasUpgradeWithoutLosingCollections() = runBlocking {
        for (version in listOf(1, 2)) {
            val folder = File(directory, "schema-$version").apply { mkdirs() }
            val storage = isolatedContext(folder)
            val legacy = storage.getDatabasePath("hors-cadre.db")
            val schema = InstrumentationRegistry.getInstrumentation().context.assets
                .open("fr.inillusio.data.local.InIllusioDatabase/$version.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(legacy, null).use { database ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices")
                    for (j in 0 until (indices?.length() ?: 0)) {
                        database.execSQL(checkNotNull(indices).getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) database.execSQL(setup.getString(i))
                database.execSQL("INSERT INTO categories VALUES ('fiction', 'Test')")
                val archiveColumn = if (version >= 2) ",archived" else ""
                val archiveValue = if (version >= 2) ",0" else ""
                database.execSQL("INSERT INTO anecdotes(id,categoryId,title,hook,explanation,editorialStatus,illustrationId$archiveColumn) VALUES ('stable-id','fiction','Titre','Accroche','Texte','validee',NULL$archiveValue)")
                database.execSQL("INSERT INTO collection VALUES ('stable-id',123)")
                database.version = version
            }
            DatabaseFileMigration.prepare(storage)
            room(storage.getDatabasePath(DatabaseFileMigration.DATABASE_NAME)).useDatabase { migrated ->
                assertEquals(123L, migrated.catalogDao().collectionEntry("stable-id")?.savedAt)
                assertEquals("stable-id", migrated.catalogDao().validatedAnecdotes().single().id)
                assertTrue(migrated.catalogDao().putBoosterRedemption(BoosterRedemptionEntity("new-key", 100L)) != -1L)
            }
            assertTrue(legacy.isFile)
        }
    }

    @Test fun corruptDatabaseCannotBeReplacedByAnEmptyDestination() {
        val storage = isolatedContext()
        val original = storage.getDatabasePath("hors-cadre.db").apply { writeText("not a database") }
        assertTrue(runCatching { DatabaseFileMigration.prepare(storage) }.isFailure)
        assertTrue(original.isFile)
        assertFalse(storage.getDatabasePath(DatabaseFileMigration.DATABASE_NAME).exists())
    }

    private suspend fun <T> preferences(
        file: File,
        legacy: File? = null,
        block: suspend (androidx.datastore.core.DataStore<Preferences>) -> T
    ): T {
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                migrations = legacy?.let { listOf(PreferencesFileMigration(it)) } ?: emptyList(),
                scope = CoroutineScope(Dispatchers.IO + job),
                produceFile = { file }
            )
            return block(store)
        } finally { job.cancelAndJoin() }
    }

    @Test fun bothPreferenceStoresPreserveAllValuesAndNeverReimportAConsumedSnapshot() = runBlocking {
        for ((oldName, newName) in listOf(
            "hors_cadre_preferences" to "inillusio_preferences",
            "hors_cadre_developer" to "inillusio_developer"
        )) {
            val legacy = File(directory, "$oldName.preferences_pb")
            val destination = File(directory, "$newName.preferences_pb")
            val theme = stringPreferencesKey("theme")
            val onboarding = booleanPreferencesKey("onboarding_complete")
            val refresh = booleanPreferencesKey("wallpaper_refresh_enabled")
            val text = floatPreferencesKey("wallpaper_text_vertical_offset")
            val sources = floatPreferencesKey("wallpaper_source_vertical_offset")
            val dailyDate = stringPreferencesKey("daily_date")
            val dailyId = stringPreferencesKey("daily_id")
            val snapshot = stringPreferencesKey("collection_snapshot_v1")
            preferences(legacy) { store -> store.edit {
                it[theme] = "DARK"; it[onboarding] = true; it[refresh] = true
                it[text] = .12f; it[sources] = -.08f
                it[dailyDate] = "2026-11-04"; it[dailyId] = "stable-id"
                it[snapshot] = "[[\"stable-id\",123]]"
            } }
            val originalBytes = legacy.readBytes()
            preferences(destination, legacy) { store ->
                val migrated = store.data.first()
                assertEquals("DARK", migrated[theme]); assertEquals(true, migrated[onboarding]); assertEquals(true, migrated[refresh])
                assertEquals(.12f, migrated[text]); assertEquals(-.08f, migrated[sources])
                assertEquals("2026-11-04", migrated[dailyDate]); assertEquals("stable-id", migrated[dailyId])
                assertNotNull(migrated[snapshot])
                store.edit { it[theme] = "LIGHT"; it.remove(snapshot) }
            }
            preferences(destination, legacy) { restarted ->
                assertEquals("LIGHT", restarted.data.first()[theme])
                assertNull(restarted.data.first()[snapshot])
            }
            assertArrayEquals(originalBytes, legacy.readBytes())
        }
    }

    @Test fun existingCanonicalPreferencesWinOverLegacyValues() = runBlocking {
        val legacy = File(directory, "legacy.preferences_pb")
        val destination = File(directory, "canonical.preferences_pb")
        val key = stringPreferencesKey("theme")
        preferences(legacy) { store -> store.edit { it[key] = "DARK" } }
        preferences(destination) { store -> store.edit { it[key] = "LIGHT" } }
        preferences(destination, legacy) { store -> assertEquals("LIGHT", store.data.first()[key]) }
    }

    @Test fun corruptLegacyPreferencesAreRetainedAndNeverReplacedByDefaults() = runBlocking {
        val legacy = File(directory, "legacy.preferences_pb").apply { writeBytes(byteArrayOf(-1, -1, -1)) }
        val destination = File(directory, "new.preferences_pb")
        val result = runCatching { preferences(destination, legacy) { it.data.first() } }
        assertTrue(result.isFailure)
        assertArrayEquals(byteArrayOf(-1, -1, -1), legacy.readBytes())
        assertFalse(destination.exists())
    }
}
