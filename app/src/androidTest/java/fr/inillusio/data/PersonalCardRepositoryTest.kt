package fr.inillusio.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.inillusio.data.local.InIllusioDatabase
import fr.inillusio.data.local.entities.CategoryEntity
import fr.inillusio.data.local.entities.AnecdoteEntity
import fr.inillusio.data.local.entities.CollectionEntity
import fr.inillusio.data.preferences.UserPreferences
import fr.inillusio.data.repository.CatalogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalCardRepositoryTest {
    private lateinit var database: InIllusioDatabase
    private lateinit var repository: CatalogRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InIllusioDatabase::class.java).allowMainThreadQueries().build()
        repository = CatalogRepository(database, UserPreferences(context))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun personalCardsPersistJoinCollectionAndCanBeEditedAndDeleted() = runBlocking {
        database.catalogDao().putCategory(CategoryEntity("sociologie", "Sociologie"))
        database.catalogDao().putAnecdote(
            AnecdoteEntity("official-id", "sociologie", "Officielle", "Accroche officielle", "Explication", "validee")
        )
        database.catalogDao().putCollection(CollectionEntity("official-id", 50L))

        val id = repository.savePersonalCard(
            existingId = null,
            categoryId = "sociologie",
            hook = "Une accroche personnelle",
            explanation = "Une explication personnelle.",
            source = "Référence libre",
            now = 100L
        )
        assertTrue(id.startsWith("personal-"))
        val created = database.catalogDao().getPersonalCardRecord(id)
        assertNotNull(created)
        assertEquals(100L, created?.createdAt)
        assertEquals(100L, created?.updatedAt)

        val collected = repository.collection.first()
        val personal = collected.single { it.anecdote.id == id }
        assertEquals(2, collected.size)
        assertTrue(personal.anecdote.isPersonal)
        assertEquals("Une accroche personnelle", personal.anecdote.hook)
        assertEquals("Référence libre", repository.sources(id).single().title)

        repository.savePersonalCard(id, "sociologie", "Accroche modifiée", "Explication modifiée.", null, now = 200L)
        val updated = database.catalogDao().getPersonalCardRecord(id)
        assertEquals(100L, updated?.createdAt)
        assertEquals(200L, updated?.updatedAt)
        assertEquals("Accroche modifiée", repository.collection.first().single { it.anecdote.id == id }.anecdote.hook)
        assertTrue(repository.sources(id).isEmpty())

        assertTrue(repository.deletePersonalCard(id))
        assertFalse(repository.deletePersonalCard(id))
        val afterDelete = repository.collection.first()
        assertEquals(listOf("official-id"), afterDelete.map { it.anecdote.id })
    }
}
