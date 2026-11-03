package fr.inillusio.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.inillusio.data.importer.CatalogImporter
import fr.inillusio.data.local.InIllusioDatabase
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import fr.inillusio.data.local.dao.BoosterProgress
import fr.inillusio.data.local.entities.CollectionEntity
import fr.inillusio.data.local.entities.DeliveryEntity
import fr.inillusio.data.preferences.UserPreferences
import fr.inillusio.data.repository.CatalogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BoosterRepositoryTest {
    private lateinit var database: InIllusioDatabase
    private lateinit var repository: CatalogRepository
    private lateinit var dao: fr.inillusio.data.local.dao.CatalogDao

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, InIllusioDatabase::class.java).allowMainThreadQueries().build()
        dao = database.catalogDao()
        repository = CatalogRepository(database, UserPreferences(context))
    }

    @After fun tearDown() = database.close()

    @Test fun normalBoosterAddsFiveDistinctValidatedCardsToCollectionOnly() = runBlocking {
        val importer = importer()
        importer.importJson(catalog((1..7).map { fact("fiction-$it") }))

        val result = repository.redeemBooster("reward-normal", 100L)

        assertEquals(5, result.cards.size)
        assertEquals(5, result.cards.map { it.id }.toSet().size)
        assertEquals(5, dao.observeCollection().first().size)
        assertEquals(0, dao.successfullyDeliveredIds().size)
        assertEquals(BoosterProgress(5, 7), repository.boosterProgress.first())
    }

    @Test fun nearlyCompleteCatalogAwardsOnlyRemainingCardsAndKeepsOldDiscoveryDates() = runBlocking {
        importer().importJson(catalog((1..5).map { fact("fiction-$it") }))
        dao.putCollection(CollectionEntity("fiction-1", 42L))
        dao.putCollection(CollectionEntity("fiction-2", 43L))
        dao.putCollection(CollectionEntity("fiction-3", 44L))

        val result = repository.redeemBooster("reward-last-two", 200L)

        assertEquals(2, result.cards.size)
        assertEquals(42L, dao.collectionEntry("fiction-1")?.savedAt)
        assertEquals(BoosterProgress(5, 5), repository.boosterProgress.first())
    }

    @Test fun fullCollectionHasNoCandidatesAndRepeatedRewardEventIsIdempotent() = runBlocking {
        importer().importJson(catalog(listOf(fact("fiction-1"), fact("fiction-2"))))
        dao.putCollection(CollectionEntity("fiction-1", 9L))
        dao.putCollection(CollectionEntity("fiction-2", 10L))

        val empty = repository.redeemBooster("reward-empty", 300L)
        val duplicate = repository.redeemBooster("reward-empty", 301L)

        assertTrue(empty.cards.isEmpty())
        assertTrue(duplicate.duplicateRedemption)
        assertTrue(duplicate.cards.isEmpty())
        assertEquals(2, dao.observeCollection().first().size)
    }

    @Test fun pendingAndArchivedFactsCannotBeAwardedButCollectedArchivedCardsRemainVisible() = runBlocking {
        val catalogImporter = importer()
        catalogImporter.importJson(catalog(listOf(fact("fiction-kept"), fact("fiction-pending", status = "a_valider"), fact("fiction-archived"))))
        dao.putCollection(CollectionEntity("fiction-archived", 20L))
        catalogImporter.importJson(catalog(listOf(fact("fiction-kept"), fact("fiction-pending", status = "a_valider"))))

        val result = repository.redeemBooster("reward-eligible-only", 400L)

        assertEquals(listOf("fiction-kept"), result.cards.map { it.id })
        assertEquals(setOf("fiction-archived", "fiction-kept"), dao.observeCollection().first().map { it.id }.toSet())
        assertNull(dao.getValidatedAnecdote("fiction-pending"))
    }

    @Test fun boosterDoesNotReplaceAlreadySelectedAnecdoteDuringLocalDay() = runBlocking {
        importer().importJson(catalog((1..8).map { fact("fiction-$it") }))
        val date = LocalDate.of(2026, 11, 3)
        val selected = requireNotNull(repository.dailyAnecdote(date))

        repository.redeemBooster("reward-same-day", 500L)

        assertEquals(selected.id, repository.dailyAnecdote(date)?.id)
    }

    @Test fun concurrentBoostersCannotAwardTheSameAnecdoteTwice() = runBlocking {
        importer().importJson(catalog((1..7).map { fact("fiction-$it") }))

        val results = coroutineScope {
            listOf("reward-a", "reward-b").map { id -> async { repository.redeemBooster(id, 600L) } }.awaitAll()
        }

        val awarded = results.flatMap { it.cards.map(AnecdoteWithCategory::id) }
        assertTrue(awarded.isNotEmpty())
        assertEquals(awarded.size, awarded.toSet().size)
        assertEquals(awarded.size, dao.observeCollection().first().size)
    }

    @Test fun nextDaySelectionPrefersTheOnlyUncollectedValidatedFact() = runBlocking {
        importer().importJson(catalog((1..8).map { fact("fiction-$it") }))
        val firstDay = LocalDate.of(2026, 11, 3)
        repository.dailyAnecdote(firstDay)
        (1..7).forEach { dao.putCollection(CollectionEntity("fiction-$it", 700L + it)) }

        val nextDay = repository.dailyAnecdote(firstDay.plusDays(1))

        assertEquals("fiction-8", nextDay?.id)
    }

    @Test fun clearingCollectionPreservesCatalogDeliveryAndRedemptionsAndAllowsNewBoosterCards() = runBlocking {
        importer().importJson(catalog((1..7).map { fact("fiction-$it") }))
        val firstReward = repository.redeemBooster("reward-before-reset", 800L)
        dao.putDelivery(DeliveryEntity(anecdoteId = "fiction-1", attemptedAt = 801L, succeeded = true))
        val catalogIdsBeforeReset = dao.allAnecdotes().map { it.id }.toSet()

        database.withTransaction { dao.clearCollection() }

        assertEquals(5, firstReward.cards.size)
        assertTrue(dao.observeCollection().first().isEmpty())
        assertEquals(BoosterProgress(0, 7), repository.boosterProgress.first())
        assertEquals(catalogIdsBeforeReset, dao.allAnecdotes().map { it.id }.toSet())
        assertTrue(dao.hasSuccessfulDelivery("fiction-1"))
        assertEquals(7, dao.uncollectedValidatedAnecdotes().size)
        assertTrue(repository.redeemBooster("reward-before-reset", 802L).duplicateRedemption)
        val nextReward = repository.redeemBooster("reward-after-reset", 803L)
        assertEquals(5, nextReward.cards.size)
        assertEquals(5, dao.observeCollection().first().size)
    }

    @Test fun clearingAnAlreadyEmptyCollectionIsIdempotent() = runBlocking {
        importer().importJson(catalog(listOf(fact("fiction-empty"))))

        database.withTransaction { dao.clearCollection() }
        database.withTransaction { dao.clearCollection() }

        assertTrue(dao.observeCollection().first().isEmpty())
        assertEquals(BoosterProgress(0, 1), repository.boosterProgress.first())
    }

    private fun importer() = CatalogImporter(ApplicationProvider.getApplicationContext(), database)
    private fun catalog(facts: List<JSONObject>): String = JSONObject()
        .put("categories", JSONArray().put(JSONObject().put("id", "sociologie").put("nom", "Sociologie")))
        .put("faits", JSONArray().apply { facts.forEach(::put) })
        .toString()
    private fun fact(id: String, status: String = "validee") = JSONObject()
        .put("id", id).put("categorie", "sociologie").put("titre", "Fait fictif")
        .put("accroche", "Accroche de test").put("explication", "Explication fictive")
        .put("statut_editorial", status)
        .put("source", JSONObject().put("type", "article").put("auteurs", "Auteur fictif").put("titre", "Revue fictive").put("annee", 2026))
}
