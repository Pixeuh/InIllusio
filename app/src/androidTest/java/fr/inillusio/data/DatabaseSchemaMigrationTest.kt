package fr.inillusio.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.inillusio.data.local.InIllusioDatabase
import fr.inillusio.data.local.entities.BoosterRedemptionEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "database-schema-${UUID.randomUUID()}").apply { check(mkdirs()) }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun room(file: File) = Room.databaseBuilder(context, InIllusioDatabase::class.java, file.path)
        .addMigrations(InIllusioDatabase.MIGRATION_1_2, InIllusioDatabase.MIGRATION_2_3)
        .build()

    @Test
    fun supportedRoomSchemasUpgradeWithoutLosingCollectionEntries() = runBlocking {
        for (version in listOf(1, 2)) {
            val file = File(directory, "schema-$version.db")
            val schema = InstrumentationRegistry.getInstrumentation().context.assets
                .open("fr.inillusio.data.local.InIllusioDatabase/$version.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }

            SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val tableName = entity.getString("tableName")
                    database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    val indices = entity.optJSONArray("indices")
                    for (j in 0 until (indices?.length() ?: 0)) {
                        val index = checkNotNull(indices).getJSONObject(j)
                        database.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) database.execSQL(setup.getString(i))

                database.execSQL("INSERT INTO categories VALUES ('fiction', 'Test')")
                val archiveColumn = if (version >= 2) ",archived" else ""
                val archiveValue = if (version >= 2) ",0" else ""
                database.execSQL(
                    "INSERT INTO anecdotes(id,categoryId,title,hook,explanation,editorialStatus,illustrationId$archiveColumn) " +
                        "VALUES ('stable-id','fiction','Titre','Accroche','Texte','validee',NULL$archiveValue)"
                )
                database.execSQL("INSERT INTO collection VALUES ('stable-id',123)")
                database.version = version
            }

            val migrated = room(file)
            try {
                assertEquals(123L, migrated.catalogDao().collectionEntry("stable-id")?.savedAt)
                assertEquals("stable-id", migrated.catalogDao().validatedAnecdotes().single().id)
                assertTrue(
                    migrated.catalogDao().putBoosterRedemption(BoosterRedemptionEntity("schema-$version-key", 100L)) != -1L
                )
            } finally {
                migrated.close()
            }
        }
    }
}
