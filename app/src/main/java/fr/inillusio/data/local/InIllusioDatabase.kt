package fr.inillusio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.inillusio.data.local.dao.CatalogDao
import fr.inillusio.data.local.entities.*

@Database(entities = [CategoryEntity::class, AnecdoteEntity::class, AuthorEntity::class, SourceEntity::class, SourceAuthorEntity::class, AnecdoteSourceEntity::class, IllustrationEntity::class, DeliveryEntity::class, CollectionEntity::class, BoosterRedemptionEntity::class], version = 3, exportSchema = true)
abstract class InIllusioDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anecdotes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TEMP TABLE merged_source_authors (sourceId TEXT NOT NULL, authorId INTEGER NOT NULL, PRIMARY KEY(sourceId, authorId))")
                db.execSQL("""
                    INSERT OR IGNORE INTO merged_source_authors(sourceId, authorId)
                    SELECT relation.sourceId,
                           (SELECT MIN(canonical.id) FROM authors AS duplicate
                            JOIN authors AS canonical ON canonical.name = duplicate.name
                            WHERE duplicate.id = relation.authorId)
                    FROM source_authors AS relation
                """.trimIndent())
                db.execSQL("DELETE FROM source_authors")
                db.execSQL("INSERT OR IGNORE INTO source_authors(sourceId, authorId) SELECT sourceId, authorId FROM merged_source_authors")
                db.execSQL("DROP TABLE merged_source_authors")
                db.execSQL("DELETE FROM authors WHERE id NOT IN (SELECT MIN(id) FROM authors GROUP BY name)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS booster_redemptions (redemptionId TEXT NOT NULL, rewardedAt INTEGER NOT NULL, PRIMARY KEY(redemptionId))")
            }
        }
    }
}
