package fr.horscadre.data.repository

import fr.horscadre.data.local.HorsCadreDatabase
import fr.horscadre.data.local.dao.AnecdoteWithCategory
import fr.horscadre.data.local.dao.SourceDetails
import fr.horscadre.data.local.entities.CollectionEntity
import fr.horscadre.data.local.entities.DeliveryEntity
import fr.horscadre.data.preferences.UserPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class CatalogRepository(private val db: HorsCadreDatabase, private val preferences: UserPreferences) {
    val collection: Flow<List<AnecdoteWithCategory>> = db.catalogDao().observeCollection()
    suspend fun dailyAnecdote(today: LocalDate = LocalDate.now()): AnecdoteWithCategory? {
        val dao = db.catalogDao()
        val all = dao.validatedAnecdotes()
        val delivered = dao.successfullyDeliveredIds().toSet()
        val eligible = all.filterNot { it.id in delivered }.ifEmpty { all }
        val id = preferences.dailySelection(today, all.map { it.id }, eligible.map { it.id }) ?: return null
        return dao.getValidatedAnecdote(id)
    }
    suspend fun hasSuccessfulDeliveryOn(id: String, date: LocalDate): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return db.catalogDao().hasSuccessfulDeliveryBetween(id, start, end)
    }
    fun observeAnecdote(id: String) = db.catalogDao().observeAnecdote(id)
    suspend fun sources(id: String): List<SourceDetails> = db.catalogDao().sourcesFor(id)
    suspend fun isCollected(id: String) = db.catalogDao().isCollected(id)
    suspend fun recordDelivery(id: String, succeeded: Boolean, error: String? = null) {
        db.catalogDao().putDelivery(DeliveryEntity(anecdoteId = id, attemptedAt = System.currentTimeMillis(), succeeded = succeeded, error = error))
    }
    /** Records a successful FLAG_LOCK application and adds the card without replacing its first discovery date. */
    suspend fun recordSuccessfulLockScreenApplication(id: String, appliedAt: Long = System.currentTimeMillis()) {
        db.withTransaction {
            db.catalogDao().putDelivery(DeliveryEntity(anecdoteId = id, attemptedAt = appliedAt, succeeded = true))
            db.catalogDao().putCollection(CollectionEntity(id, appliedAt))
        }
    }
}
