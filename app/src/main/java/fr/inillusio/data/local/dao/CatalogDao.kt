package fr.inillusio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Upsert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import fr.inillusio.data.local.entities.*
import kotlinx.coroutines.flow.Flow

data class AnecdoteWithCategory(val id: String, val categoryId: String, val categoryName: String, val title: String, val hook: String, val explanation: String)
data class SourceDetails(val type: String, val title: String, val year: Int?, val author: String?)
data class BoosterProgress(val discovered: Int, val total: Int)

@Dao
interface CatalogDao {
    @Query("SELECT a.id, a.categoryId, c.name AS categoryName, a.title, a.hook, a.explanation FROM anecdotes a JOIN categories c ON c.id = a.categoryId WHERE a.id = :id")
    suspend fun getAnecdote(id: String): AnecdoteWithCategory?
    @Query("SELECT a.id, a.categoryId, c.name AS categoryName, a.title, a.hook, a.explanation FROM anecdotes a JOIN categories c ON c.id = a.categoryId WHERE a.id = :id")
    fun observeAnecdote(id: String): Flow<AnecdoteWithCategory?>
    @Query("SELECT a.id, a.categoryId, c.name AS categoryName, a.title, a.hook, a.explanation FROM anecdotes a JOIN categories c ON c.id = a.categoryId WHERE a.archived = 0 ORDER BY a.id")
    suspend fun allAnecdotes(): List<AnecdoteWithCategory>
    @Query("SELECT a.id, a.categoryId, c.name AS categoryName, a.title, a.hook, a.explanation FROM anecdotes a JOIN categories c ON c.id = a.categoryId WHERE a.archived = 0 AND a.editorialStatus = 'validee' ORDER BY a.id")
    suspend fun validatedAnecdotes(): List<AnecdoteWithCategory>
    @Query("SELECT a.id, a.categoryId, c.name AS categoryName, a.title, a.hook, a.explanation FROM anecdotes a JOIN categories c ON c.id = a.categoryId WHERE a.archived = 0 AND a.editorialStatus = 'validee' AND NOT EXISTS (SELECT 1 FROM collection x WHERE x.anecdoteId = a.id) ORDER BY RANDOM()")
    suspend fun uncollectedValidatedAnecdotes(): List<AnecdoteWithCategory>
    @Query("SELECT a.id, a.categoryId, c.name AS categoryName, a.title, a.hook, a.explanation FROM anecdotes a JOIN categories c ON c.id = a.categoryId WHERE a.id = :id AND a.archived = 0 AND a.editorialStatus = 'validee'")
    suspend fun getValidatedAnecdote(id: String): AnecdoteWithCategory?
    @Query("SELECT a.id, a.categoryId, c.name AS categoryName, a.title, a.hook, a.explanation FROM collection x JOIN anecdotes a ON a.id = x.anecdoteId JOIN categories c ON c.id = a.categoryId ORDER BY x.savedAt DESC")
    fun observeCollection(): Flow<List<AnecdoteWithCategory>>
    @Query("SELECT (SELECT COUNT(*) FROM collection x JOIN anecdotes a ON a.id = x.anecdoteId WHERE a.archived = 0 AND a.editorialStatus = 'validee') AS discovered, (SELECT COUNT(*) FROM anecdotes WHERE archived = 0 AND editorialStatus = 'validee') AS total")
    fun observeBoosterProgress(): Flow<BoosterProgress>
    @Query("SELECT * FROM collection ORDER BY savedAt") suspend fun allCollectionEntries(): List<CollectionEntity>
    @Query("DELETE FROM collection") suspend fun clearCollection()
    @Query("SELECT EXISTS(SELECT 1 FROM delivery_history WHERE anecdoteId = :id AND succeeded = 1)")
    suspend fun hasSuccessfulDelivery(id: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM delivery_history WHERE anecdoteId = :id AND succeeded = 1 AND attemptedAt >= :startInclusive AND attemptedAt < :endExclusive)")
    suspend fun hasSuccessfulDeliveryBetween(id: String, startInclusive: Long, endExclusive: Long): Boolean
    @Query("SELECT DISTINCT anecdoteId FROM delivery_history WHERE succeeded = 1") suspend fun successfullyDeliveredIds(): List<String>
    @Query("SELECT COUNT(*) FROM anecdotes") suspend fun anecdoteCount(): Int
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Query("SELECT COUNT(*) FROM sources") suspend fun sourceCount(): Int
    @Query("SELECT COUNT(*) FROM authors") suspend fun authorCount(): Int
    @Query("SELECT COUNT(*) FROM source_authors") suspend fun sourceAuthorCount(): Int
    @Query("SELECT EXISTS(SELECT 1 FROM collection WHERE anecdoteId = :id)")
    suspend fun isCollected(id: String): Boolean
    @Upsert suspend fun putCategory(value: CategoryEntity)
    @Upsert suspend fun putAnecdote(value: AnecdoteEntity)
    @Upsert suspend fun putSource(value: SourceEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putAuthor(value: AuthorEntity): Long
    @Query("SELECT id FROM authors WHERE name = :name LIMIT 1") suspend fun authorId(name: String): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSourceAuthor(value: SourceAuthorEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAnecdoteSource(value: AnecdoteSourceEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDelivery(value: DeliveryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putCollection(value: CollectionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putBoosterRedemption(value: BoosterRedemptionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putCollection(values: List<CollectionEntity>)
    @Query("DELETE FROM source_authors WHERE sourceId = :sourceId") suspend fun clearSourceAuthors(sourceId: String)
    @Query("DELETE FROM anecdote_sources WHERE anecdoteId = :anecdoteId") suspend fun clearAnecdoteSources(anecdoteId: String)
    @Query("UPDATE anecdotes SET archived = 1") suspend fun archiveAllAnecdotes()
    @Query("INSERT OR IGNORE INTO source_authors(sourceId, authorId) SELECT relation.sourceId, (SELECT MIN(canonical.id) FROM authors duplicate JOIN authors canonical ON canonical.name = duplicate.name WHERE duplicate.id = relation.authorId) FROM source_authors relation") suspend fun mergeDuplicateAuthorLinks()
    @Query("DELETE FROM source_authors WHERE authorId NOT IN (SELECT MIN(id) FROM authors GROUP BY name)") suspend fun deleteDuplicateAuthorLinks()
    @Query("DELETE FROM authors WHERE id NOT IN (SELECT MIN(id) FROM authors GROUP BY name)") suspend fun deleteDuplicateAuthors()
    @Query("DELETE FROM anecdote_sources WHERE anecdoteId IN (SELECT id FROM anecdotes WHERE archived = 1 AND id NOT IN (SELECT anecdoteId FROM collection))") suspend fun clearArchivedUncollectedSources()
    @Query("DELETE FROM sources WHERE id NOT IN (SELECT sourceId FROM anecdote_sources)") suspend fun deleteUnreferencedSources()
    @Query("DELETE FROM authors WHERE id NOT IN (SELECT authorId FROM source_authors)") suspend fun deleteUnreferencedAuthors()
    @Query("SELECT s.type, s.title, s.year, (SELECT GROUP_CONCAT(au.name, ', ') FROM source_authors sa JOIN authors au ON au.id = sa.authorId WHERE sa.sourceId = s.id) AS author FROM sources s JOIN anecdote_sources ax ON ax.sourceId = s.id WHERE ax.anecdoteId = :id")
    suspend fun sourcesFor(id: String): List<SourceDetails>
    @Transaction @Query("SELECT * FROM collection WHERE anecdoteId = :id") suspend fun collectionEntry(id: String): CollectionEntity?
}
