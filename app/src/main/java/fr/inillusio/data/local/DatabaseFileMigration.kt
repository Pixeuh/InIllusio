package fr.inillusio.data.local

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/** Rename storage before Room opens it. Keep the original database as a recovery copy. */
internal object DatabaseFileMigration {
    const val DATABASE_NAME = "inillusio.db"
    private const val LEGACY_DATABASE_NAME = "hors-cadre.db"
    private val preserveFileOnCorruption = DatabaseErrorHandler {
        throw SQLiteDatabaseCorruptException("Migration interrompue : base locale corrompue, fichier d'origine conservé.")
    }

    @Synchronized
    fun prepare(context: Context): String {
        val source = context.getDatabasePath(LEGACY_DATABASE_NAME)
        val destination = context.getDatabasePath(DATABASE_NAME)
        if (destination.exists() || !source.exists()) return DATABASE_NAME

        // Application startup is the only caller: no Room/Worker writer has opened either file yet.
        // Flush committed WAL records before copying; an unsuccessful checkpoint must not create
        // an empty replacement database. Closing the connection also releases its journal handles.
        SQLiteDatabase.openDatabase(
            source.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING, preserveFileOnCorruption
        ).use { database ->
            database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0 && cursor.getInt(1) == cursor.getInt(2)) {
                    "La base locale est occupée ; sa migration sera réessayée au prochain démarrage."
                }
            }
        }

        checkNotNull(destination.parentFile).mkdirs()
        val staging = File(destination.parentFile, "${destination.name}.migrating")
        source.inputStream().use { input ->
            FileOutputStream(staging).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        SQLiteDatabase.openDatabase(staging.path, null, SQLiteDatabase.OPEN_READONLY, preserveFileOnCorruption).use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "La copie de la base locale n'a pas pu être vérifiée. La base d'origine est conservée."
                }
            }
            check(database.version > 0) { "La copie ne contient pas de schéma Room versionné." }
        }
        // Same directory/filesystem: publish only a complete, verified copy. An interrupted staging
        // file is overwritten on the next attempt; a published destination is never overwritten.
        check(staging.renameTo(destination)) { "Impossible de finaliser la migration de la base locale." }
        return DATABASE_NAME
    }
}
