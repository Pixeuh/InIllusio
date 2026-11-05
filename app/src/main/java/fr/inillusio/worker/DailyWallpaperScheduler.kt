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
    private val schedulingMutex = Mutex()

    suspend fun setEnabled(context: Context, enabled: Boolean) = schedulingMutex.withLock {
        withContext(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(1, TimeUnit.DAYS).build()
                workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request).await()
            } else {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME).await()
            }
        }
    }
}
