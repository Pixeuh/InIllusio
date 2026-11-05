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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end editorial import checks using isolated, fictional anecdotes only. */
@RunWith(AndroidJUnit4::class)
class CatalogEditorialFlowTest {
    private lateinit var database: InIllusioDatabase
    private lateinit var importer: CatalogImporter

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, InIllusioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = CatalogImporter(context, database)
    }

    @After fun tearDown() = database.close()

    @Test fun newFactIsPendingUntilItsIndividualStatusIsValidated() = runBlocking {
        importer.importJson(catalog(fact("fiction-01")))
        assertNull(database.catalogDao().getValidatedAnecdote("fiction-01"))

        importer.importJson(catalog(fact("fiction-01", status = "validee")))
        assertNotNull(database.catalogDao().getValidatedAnecdote("fiction-01"))
    }

    @Test fun editedTextAndBibliographyReplaceTheImportedValues() = runBlocking {
        importer.importJson(catalog(fact("fiction-01", status = "validee")))
        val updated = fact(
            "fiction-01",
            status = "validee",
            title = "Une observation fictive révisée",
            hook = "Une accroche révisée avec des caractères accentués.",
            explanation = "Une explication révisée.",
            author = "Camille Étoile",
            sourceTitle = "Revue imaginaire, numéro 2",
            year = 2026
        )
        importer.importJson(catalog(updated))

        val dao = database.catalogDao()
        val stored = dao.getValidatedAnecdote("fiction-01")
        assertEquals("Une observation fictive révisée", stored?.title)
        assertEquals("Une accroche révisée avec des caractères accentués.", stored?.hook)
        assertEquals("Une explication révisée.", stored?.explanation)
        val source = dao.sourcesFor("fiction-01").single()
        assertEquals("Camille Étoile", source.author)
        assertEquals("Revue imaginaire, numéro 2", source.title)
        assertEquals(2026, source.year)
    }

    @Test fun devalidatedCollectedFactStopsBeingEligibleButStaysInCollection() = runBlocking {
        importer.importJson(catalog(fact("fiction-01", status = "validee")))
        val dao = database.catalogDao()
        dao.putCollection(CollectionEntity("fiction-01", 123L))

        importer.importJson(catalog(fact("fiction-01", status = "a_valider")))

        assertNull(dao.getValidatedAnecdote("fiction-01"))
        val collectedCard = dao.observeCollection().first().single()
        assertEquals("fiction-01", collectedCard.anecdote.id)
        assertEquals(123L, collectedCard.savedAt)
    }

    @Test fun removedFactIsArchivedAndOnlyCollectedCopyRemainsConsultable() = runBlocking {
        importer.importJson(catalog(fact("fiction-01"), fact("fiction-02")))
        val dao = database.catalogDao()
        dao.putCollection(CollectionEntity("fiction-01", 456L))

        importer.importJson(catalog(fact("fiction-03")))

        assertEquals(listOf("fiction-03"), dao.allAnecdotes().map { it.id })
        assertNull(dao.collectionEntry("fiction-02"))
        assertEquals("fiction-01", dao.observeCollection().first().single().anecdote.id)
        assertEquals("Camille Étoile", dao.sourcesFor("fiction-01").single().author)
        assertEquals(emptyList<Any>(), dao.sourcesFor("fiction-02"))
    }

    @Test fun repeatedFictionalImportDoesNotDuplicateCatalogOrAuthors() = runBlocking {
        val json = catalog(fact("fiction-01"), fact("fiction-02", author = "Camille Étoile"))
        importer.importJson(json)
        importer.importJson(json)

        val dao = database.catalogDao()
        assertEquals(2, dao.anecdoteCount())
        assertEquals(2, dao.sourceCount())
        assertEquals(1, dao.authorCount())
    }

    private fun catalog(vararg facts: JSONObject): String = JSONObject()
        .put("categories", JSONArray().put(JSONObject().put("id", "sociologie").put("nom", "Sociologie")))
        .put("faits", JSONArray().apply { facts.forEach(::put) })
        .toString()

    private fun fact(
        id: String,
        status: String = "a_valider",
        title: String = "Une observation fictive",
        hook: String = "Une accroche de test.",
        explanation: String = "Une explication fictive.",
        author: String = "Camille Étoile",
        sourceTitle: String = "Revue imaginaire, numéro 1",
        year: Int = 2025
    ) = JSONObject()
        .put("id", id)
        .put("categorie", "sociologie")
        .put("titre", title)
        .put("accroche", hook)
        .put("explication", explanation)
        .put("statut_editorial", status)
        .put("source", JSONObject()
            .put("type", "article")
            .put("auteurs", author)
            .put("titre", sourceTitle)
            .put("annee", year))
}
