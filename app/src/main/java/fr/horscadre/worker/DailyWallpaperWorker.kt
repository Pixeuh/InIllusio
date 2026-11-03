package fr.horscadre.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.horscadre.HorsCadreApplication
import kotlinx.coroutines.CancellationException

class DailyWallpaperWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        when (DailyWallpaperUpdater(applicationContext as HorsCadreApplication).update()) {
            DailyUpdateResult.FAILED -> Result.retry()
            DailyUpdateResult.APPLIED,
            DailyUpdateResult.ALREADY_APPLIED,
            DailyUpdateResult.DISABLED,
            DailyUpdateResult.NO_ANECDOTE -> Result.success()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
}
