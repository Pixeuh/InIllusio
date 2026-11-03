package fr.inillusio.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.inillusio.data.importer.CatalogImporter
import fr.inillusio.data.local.InIllusioDatabase
import fr.inillusio.data.local.entities.CollectionEntity
import fr.inillusio.data.preferences.UserPreferences
import fr.inillusio.data.repository.CatalogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogImporterTest {
    private lateinit var database: InIllusioDatabase
    private lateinit var importer: CatalogImporter

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, InIllusioDatabase::class.java).allowMainThreadQueries().build()
        importer = CatalogImporter(context, database)
    }

    @After fun tearDown() { database.close() }

    @Test fun importContainsThirtyFiveFactsFourteenCategoriesAndTenSociologyFacts() = runBlocking {
        importer.importBundledCatalog()
        assertEquals(35, database.catalogDao().anecdoteCount())
        assertEquals(14, database.catalogDao().categoryCount())
        assertEquals(10, database.catalogDao().allAnecdotes().count { it.categoryId == "sociologie" })
        assertEquals("archeologie", database.catalogDao().getAnecdote("histoire-02")?.categoryId)
        assertTrue(database.catalogDao().allAnecdotes().any { it.id == "ethologie-01" })
        assertNull(database.catalogDao().getValidatedAnecdote("ethologie-01"))
        val importedTypes = database.catalogDao().allAnecdotes()
            .flatMap { database.catalogDao().sourcesFor(it.id) }
            .map { it.type }
            .toSet()
        assertEquals(setOf("livre", "article", "rapport", "autre"), importedTypes)
        assertEquals(validatedIds(JSONObject(readCatalog())).sorted(), database.catalogDao().validatedAnecdotes().map { it.id }.sorted())
    }

    @Test fun onlyValidatedFactsCanBeDistributedAndRevalidationKeepsCollectedCards() = runBlocking {
        val dao = database.catalogDao()
        importer.importBundledCatalog()
        val catalog = JSONObject(readCatalog())
        val facts = catalog.getJSONArray("faits")
        facts.getJSONObject(0).put("statut_editorial", "validee")
        importer.importJson(catalog.toString())

        assertEquals(validatedIds(catalog).sorted(), dao.validatedAnecdotes().map { it.id }.sorted())
        dao.putCollection(CollectionEntity("sociologie-01", 10L))

        facts.getJSONObject(0).put("statut_editorial", "a_valider")
        importer.importJson(catalog.toString())

        assertEquals(validatedIds(catalog).sorted(), dao.validatedAnecdotes().map { it.id }.sorted())
        assertNotNull(dao.getAnecdote("sociologie-01"))
        assertEquals("sociologie-01", dao.observeCollection().first().single().id)
    }

    @Test fun successfulLockScreenApplicationCollectsIdempotentlyAndFailuresDoNot() = runBlocking {
        importer.importBundledCatalog()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = CatalogRepository(database, UserPreferences(context))
        val dao = database.catalogDao()

        repository.recordDelivery("sociologie-01", succeeded = false, error = "test failure")
        assertNull(dao.collectionEntry("sociologie-01"))

        repository.recordSuccessfulLockScreenApplication("sociologie-02", appliedAt = 100L)
        assertEquals(100L, dao.collectionEntry("sociologie-02")?.savedAt)
        repository.recordSuccessfulLockScreenApplication("sociologie-02", appliedAt = 200L)

        assertEquals(100L, dao.collectionEntry("sociologie-02")?.savedAt)
        assertNull(dao.collectionEntry("sociologie-01"))
        assertEquals(setOf("sociologie-02"), dao.successfullyDeliveredIds().toSet())
    }

    @Test fun missingIndividualStatusDefaultsToPendingAndInvalidStatusIsRejectedAtomically() = runBlocking {
        val dao = database.catalogDao()
        val catalog = JSONObject(readCatalog())
        val facts = catalog.getJSONArray("faits")
        facts.getJSONObject(0).remove("statut_editorial")
        importer.importJson(catalog.toString())
        assertEquals(validatedIds(catalog).sorted(), dao.validatedAnecdotes().map { it.id }.sorted())

        val before = dao.anecdoteCount()
        facts.getJSONObject(0).put("statut_editorial", "publie")
        runCatching { importer.importJson(catalog.toString()) }
            .onSuccess { throw AssertionError("Unknown editorial status should be rejected") }
        assertEquals(before, dao.anecdoteCount())
        assertEquals(validatedIds(catalog).sorted(), dao.validatedAnecdotes().map { it.id }.sorted())
    }

    @Test fun obsoleteSourceTypeIsRejectedWithoutChangingCollectionOrCatalog() = runBlocking {
        importer.importBundledCatalog()
        val dao = database.catalogDao()
        dao.putCollection(CollectionEntity("sociologie-01", 1L))
        val countBefore = dao.anecdoteCount()
        val root = JSONObject(readCatalog())
        root.getJSONArray("faits").getJSONObject(0).getJSONObject("source").put("type", "lien")

        runCatching { importer.importJson(root.toString()) }
            .onSuccess { throw AssertionError("Obsolete source type should fail validation before the Room transaction") }

        assertEquals(countBefore, dao.anecdoteCount())
        assertNotNull(dao.collectionEntry("sociologie-01"))
        assertEquals(setOf("livre", "article", "rapport", "autre"), dao.allAnecdotes()
            .flatMap { dao.sourcesFor(it.id) }.map { it.type }.toSet())
    }

    @Test fun repeatedImportDoesNotDuplicateRowsOrDeleteCollection() = runBlocking {
        importer.importBundledCatalog()
        database.catalogDao().putCollection(CollectionEntity("sociologie-01", 1L))
        importer.importBundledCatalog()
        assertEquals(35, database.catalogDao().anecdoteCount())
        assertEquals(14, database.catalogDao().categoryCount())
        assertEquals(10, database.catalogDao().allAnecdotes().count { it.categoryId == "sociologie" })
        assertNotNull(database.catalogDao().collectionEntry("sociologie-01"))
        val sourceAuthors = JSONObject(readCatalog()).getJSONArray("faits").let { facts ->
            (0 until facts.length()).mapNotNull { facts.getJSONObject(it).optJSONObject("source")?.optString("auteurs")?.takeIf(String::isNotBlank) }
        }
        assertEquals(sourceAuthors.toSet().size, database.catalogDao().authorCount())
        assertEquals(sourceAuthors.size, database.catalogDao().sourceAuthorCount())
    }

    @Test fun reclassifyingRomanThermopoliumKeepsItsCollectedEntry() = runBlocking {
        val dao = database.catalogDao()
        val original = JSONObject(readCatalog())
        val roman = original.getJSONArray("faits").let { facts ->
            (0 until facts.length()).map { facts.getJSONObject(it) }.single { it.getString("id") == "histoire-02" }
        }
        roman.put("categorie", "histoire")
        importer.importJson(original.toString())
        dao.putCollection(CollectionEntity("histoire-02", 42L))

        roman.put("categorie", "archeologie")
        importer.importJson(original.toString())

        assertEquals("archeologie", dao.getAnecdote("histoire-02")?.categoryId)
        assertEquals(42L, dao.collectionEntry("histoire-02")?.savedAt)
        assertEquals("Les Romains avaient aussi des snacks à emporter", dao.getAnecdote("histoire-02")?.title)
    }

    @Test fun sourceWithoutUrlKeepsBibliographyAndClearsLegacyUrlColumn() = runBlocking {
        val root = JSONObject(readCatalog())
        val fact = root.getJSONArray("faits").getJSONObject(0)
        val originalSource = fact.getJSONObject("source")
        originalSource.put("url", "https://example.invalid/legacy")
        importer.importJson(root.toString())

        val storedUrl = database.openHelper.readableDatabase.query(
            "SELECT url FROM sources WHERE id = ?",
            arrayOf("${fact.getString("id")}-source-1")
        ).use { cursor ->
            check(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getString(0)
        }
        assertNull(storedUrl)
        val displayedSource = database.catalogDao().sourcesFor(fact.getString("id")).single()
        assertEquals(originalSource.getString("auteurs"), displayedSource.author)
        assertEquals(originalSource.getString("titre"), displayedSource.title)
        assertEquals(originalSource.getInt("annee"), displayedSource.year)
    }

    @Test fun removedFactsAreArchivedAndCollectedCardsRemainVisible() = runBlocking {
        importer.importBundledCatalog()
        val dao = database.catalogDao()
        dao.putCollection(CollectionEntity("sociologie-01", 1L))

        val root = JSONObject(readCatalog())
        val original = root.getJSONArray("faits")
        val retained = JSONArray()
        for (index in 0 until original.length()) {
            val fact = original.getJSONObject(index)
            if (fact.getString("id") != "sociologie-01" && fact.getString("id") != "histoire-01") {
                if (fact.getString("id") == "anthropologie-01") fact.put("source", JSONObject.NULL)
                retained.put(fact)
            }
        }
        root.put("faits", retained)
        importer.importJson(root.toString())

        assertEquals(33, dao.allAnecdotes().size)
        assertNotNull(dao.getAnecdote("sociologie-01"))
        assertEquals("sociologie-01", dao.observeCollection().first().single().id)
        assertEquals(0, dao.sourcesFor("anthropologie-01").size)
        assertEquals(0, dao.sourcesFor("histoire-01").size)
    }

    @Test fun malformedCatalogueDoesNotPartiallyChangeDatabase() = runBlocking {
        importer.importBundledCatalog()
        val dao = database.catalogDao()
        val countsBefore = listOf(dao.anecdoteCount(), dao.categoryCount(), dao.sourceCount(), dao.authorCount())
        runCatching { importer.importJson("{\"categories\":[") }
            .onSuccess { throw AssertionError("Malformed JSON should fail before database writes") }
        assertEquals(countsBefore, listOf(dao.anecdoteCount(), dao.categoryCount(), dao.sourceCount(), dao.authorCount()))
        assertEquals(35, dao.allAnecdotes().size)
    }

    private fun readCatalog(): String = ApplicationProvider.getApplicationContext<android.content.Context>()
        .assets.open("faits_sciences_humaines_v1_16.json").bufferedReader().use { it.readText() }

    private fun validatedIds(catalog: JSONObject): List<String> {
        val facts = catalog.getJSONArray("faits")
        return (0 until facts.length()).mapNotNull { index ->
            facts.getJSONObject(index).takeIf { it.optString("statut_editorial", "a_valider") == "validee" }
                ?.getString("id")
        }
    }
}
