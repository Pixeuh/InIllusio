package fr.inillusio.data.repository

import fr.inillusio.data.local.InIllusioDatabase
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import fr.inillusio.data.local.dao.CollectionCard
import fr.inillusio.data.local.dao.SourceDetails
import fr.inillusio.data.local.dao.BoosterProgress
import fr.inillusio.data.local.entities.CollectionEntity
import fr.inillusio.data.local.entities.DeliveryEntity
import fr.inillusio.data.local.entities.BoosterRedemptionEntity
import fr.inillusio.data.preferences.UserPreferences
import fr.inillusio.domain.BoosterPolicy
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class BoosterAwardResult(val duplicateRedemption: Boolean, val cards: List<AnecdoteWithCategory>)

class CatalogRepository(private val db: InIllusioDatabase, private val preferences: UserPreferences) {
    val collection: Flow<List<CollectionCard>> = db.catalogDao().observeCollection()
    val boosterProgress: Flow<BoosterProgress> = db.catalogDao().observeBoosterProgress()
    suspend fun dailyAnecdote(today: LocalDate = LocalDate.now()): AnecdoteWithCategory? {
        val dao = db.catalogDao()
        val all = dao.validatedAnecdotes()
        // The saved daily ID remains valid for the whole local day. Unknown cards
        // are preferred only when a new day's candidate set is selected.
        val unknown = dao.uncollectedValidatedAnecdotes()
        val eligible = unknown.ifEmpty { all }
        val id = preferences.dailySelection(today, all.map { it.id }, eligible.map { it.id }) ?: return null
        return dao.getValidatedAnecdote(id)
    }

    /** A confirmed SDK reward is redeemed once and persisted atomically before any UI animation. */
    suspend fun redeemBooster(redemptionId: String, rewardedAt: Long = System.currentTimeMillis()): BoosterAwardResult =
        db.withTransaction {
            val dao = db.catalogDao()
            val inserted = dao.putBoosterRedemption(BoosterRedemptionEntity(redemptionId, rewardedAt))
            if (inserted == -1L) return@withTransaction BoosterAwardResult(duplicateRedemption = true, cards = emptyList())
            val awarded = dao.uncollectedValidatedAnecdotes().take(BoosterPolicy.CARDS_PER_REWARD)
            if (awarded.isNotEmpty()) {
                dao.putCollection(awarded.map { CollectionEntity(it.id, rewardedAt) })
            }
            BoosterAwardResult(duplicateRedemption = false, cards = awarded)
        }
    suspend fun hasSuccessfulDeliveryOn(id: String, date: LocalDate): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return db.catalogDao().hasSuccessfulDeliveryBetween(id, start, end)
    }
    fun observeAnecdote(id: String) = db.catalogDao().observeAnecdote(id)
    suspend fun sources(id: String): List<SourceDetails> = db.catalogDao().sourcesFor(id)
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
    /** Saves a daily discovery while a user-selected wallpaper is pinned; it is not a delivery. */
    suspend fun recordDailyAcquisition(id: String, acquiredAt: Long = System.currentTimeMillis()) {
        db.catalogDao().putCollection(CollectionEntity(id, acquiredAt))
    }
    suspend fun anecdote(id: String): AnecdoteWithCategory? = db.catalogDao().getAnecdote(id)
}
