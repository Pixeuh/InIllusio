package fr.inillusio.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "anecdotes", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])], indices = [Index("categoryId")])
data class AnecdoteEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val title: String,
    val hook: String,
    val explanation: String,
    val editorialStatus: String,
    val illustrationId: String? = null,
    val archived: Boolean = false
)

@Entity(tableName = "authors")
data class AuthorEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val year: Int?,
    val url: String?,
    val bibliographicDetails: String?
)

@Entity(tableName = "source_authors", primaryKeys = ["sourceId", "authorId"], foreignKeys = [
    ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AuthorEntity::class, parentColumns = ["id"], childColumns = ["authorId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("authorId")])
data class SourceAuthorEntity(val sourceId: String, val authorId: Long)

@Entity(tableName = "anecdote_sources", primaryKeys = ["anecdoteId", "sourceId"], foreignKeys = [
    ForeignKey(entity = AnecdoteEntity::class, parentColumns = ["id"], childColumns = ["anecdoteId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("sourceId")])
data class AnecdoteSourceEntity(val anecdoteId: String, val sourceId: String)

@Entity(tableName = "illustrations")
data class IllustrationEntity(@PrimaryKey val id: String, val assetPath: String, val description: String?)

@Entity(tableName = "delivery_history", indices = [Index("anecdoteId"), Index("attemptedAt")])
data class DeliveryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val anecdoteId: String, val attemptedAt: Long, val succeeded: Boolean, val error: String? = null)

@Entity(tableName = "collection", foreignKeys = [ForeignKey(entity = AnecdoteEntity::class, parentColumns = ["id"], childColumns = ["anecdoteId"], onDelete = ForeignKey.NO_ACTION)], indices = [Index("anecdoteId", unique = true)])
data class CollectionEntity(@PrimaryKey val anecdoteId: String, val savedAt: Long)

/** Idempotency key for a single rewarded-ad callback. */
@Entity(tableName = "booster_redemptions")
data class BoosterRedemptionEntity(@PrimaryKey val redemptionId: String, val rewardedAt: Long)

@Entity(tableName = "personal_cards", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])], indices = [Index("categoryId")])
data class PersonalCardEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val hook: String,
    val explanation: String,
    val source: String?,
    val createdAt: Long,
    val updatedAt: Long
)
