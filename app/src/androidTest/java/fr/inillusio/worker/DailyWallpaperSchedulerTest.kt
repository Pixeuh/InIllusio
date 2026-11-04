package fr.inillusio.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Delayed probe never applies a wallpaper or changes the collection. */
class SchedulerMigrationProbe(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork() = Result.success()
}

@RunWith(AndroidJUnit4::class)
class DailyWallpaperSchedulerTest {
    @Test fun migrationCancelsOldNameAndKeepsOneStableNewPeriodicJob() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkManager.getInstance(context)
        val suffix = UUID.randomUUID()
        val oldName = "${DailyWallpaperScheduler.LEGACY_UNIQUE_WORK_NAME}-test-$suffix"
        val newName = "${DailyWallpaperScheduler.UNIQUE_WORK_NAME}-test-$suffix"
        val old = PeriodicWorkRequestBuilder<SchedulerMigrationProbe>(1, TimeUnit.DAYS).setInitialDelay(1, TimeUnit.DAYS).build()
        val current = PeriodicWorkRequestBuilder<SchedulerMigrationProbe>(1, TimeUnit.DAYS).setInitialDelay(1, TimeUnit.DAYS).build()
        try {
            manager.enqueueUniquePeriodicWork(oldName, ExistingPeriodicWorkPolicy.KEEP, old).await()
            manager.enqueueUniquePeriodicWork(newName, ExistingPeriodicWorkPolicy.KEEP, current).await()
            DailyWallpaperScheduler.updateSchedule(manager, true, oldName, newName)
            DailyWallpaperScheduler.updateSchedule(manager, true, oldName, newName)
            assertTrue(manager.getWorkInfosForUniqueWorkFlow(oldName).first().all { it.state.isFinished })
            val active = manager.getWorkInfosForUniqueWorkFlow(newName).first().filterNot { it.state.isFinished }
            assertEquals(listOf(current.id), active.map { it.id })
            DailyWallpaperScheduler.updateSchedule(manager, false, oldName, newName)
            assertTrue(manager.getWorkInfosForUniqueWorkFlow(newName).first().all { it.state.isFinished })
        } finally { DailyWallpaperScheduler.updateSchedule(manager, false, oldName, newName) }
    }
}
