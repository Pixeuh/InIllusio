package fr.inillusio.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object DailyWallpaperScheduler {
    const val UNIQUE_WORK_NAME = "inillusio_daily_lock_wallpaper"
    // Compatibility only: WorkManager cannot rename a unique work name through its public API.
    internal const val LEGACY_UNIQUE_WORK_NAME = "hors-cadre-daily-lock-wallpaper"
    private val schedulingMutex = Mutex()

    suspend fun setEnabled(context: Context, enabled: Boolean) = updateSchedule(
        WorkManager.getInstance(context.applicationContext), enabled, LEGACY_UNIQUE_WORK_NAME, UNIQUE_WORK_NAME
    )

    // Names are injected only to let instrumentation exercise this exact operation on isolated jobs.
    internal suspend fun updateSchedule(
        workManager: WorkManager, enabled: Boolean, legacyName: String, uniqueName: String
    ): Unit = schedulingMutex.withLock {
        withContext(Dispatchers.IO) {
            // Wait for cancellation before enqueueing the new name. KEEP preserves the new work
            // ID on repeated starts; the updater also prevents a second delivery for the same date.
            workManager.cancelUniqueWork(legacyName).await()
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(1, TimeUnit.DAYS).build()
                workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.KEEP, request).await()
            } else {
                workManager.cancelUniqueWork(uniqueName).await()
            }
        }
    }
}
