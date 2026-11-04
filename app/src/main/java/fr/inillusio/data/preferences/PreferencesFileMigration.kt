package fr.inillusio.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import java.io.File

/** Import a previous DataStore once, including unknown keys; retain its file as a recovery copy. */
internal class PreferencesFileMigration(private val legacyFile: File) : DataMigration<Preferences> {
    private val imported = booleanPreferencesKey("inillusio_storage_name_migrated")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[imported] != true && legacyFile.exists()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val readerJob = SupervisorJob()
        try {
            val legacyStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + readerJob),
                produceFile = { legacyFile }
            )
            val merged = legacyStore.data.first().toMutablePreferences()
            currentData.asMap().forEach { (key, value) ->
                // Preferences keeps each key's original value type; newer values take precedence.
                @Suppress("UNCHECKED_CAST")
                merged[key as Preferences.Key<Any>] = value
            }
            merged[imported] = true
            return merged
        } finally {
            withContext(NonCancellable) { readerJob.cancelAndJoin() }
        }
    }

    // DataStore writes the destination atomically before invoking cleanUp. Keep the original file;
    // the destination marker prevents importing a consumed developer snapshot again after restart.
    override suspend fun cleanUp() = Unit
}
