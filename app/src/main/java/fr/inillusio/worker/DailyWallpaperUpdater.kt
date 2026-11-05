package fr.inillusio.worker

import android.content.res.Configuration
import fr.inillusio.InIllusioApplication
import fr.inillusio.CatalogImportState
import fr.inillusio.data.preferences.AppTheme
import fr.inillusio.data.preferences.WallpaperDisplayMode
import fr.inillusio.data.repository.CatalogRepository
import fr.inillusio.domain.DailyRenewalPolicy
import fr.inillusio.domain.shortCitation
import fr.inillusio.wallpaper.WallpaperService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate

enum class DailyUpdateResult { APPLIED, ACQUIRED_ONLY, ALREADY_APPLIED, DISABLED, NO_ANECDOTE, FAILED }

/** One idempotent update path shared by WorkManager, foreground checks and DEBUG tools. */
class DailyWallpaperUpdater(private val app: InIllusioApplication) {
    private val repository: CatalogRepository = app.repository

    suspend fun update(
        date: LocalDate = LocalDate.now(),
        requireConsent: Boolean = true,
        forceApply: Boolean = false
    ): DailyUpdateResult =
        processMutex.withLock {
            try {
                updateLocked(date, requireConsent, forceApply)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DailyUpdateResult.FAILED
            }
        }

    /** Applies a card already in the collection and only then persists the manual display choice. */
    suspend fun applyManual(fact: fr.inillusio.data.local.dao.AnecdoteWithCategory, mode: WallpaperDisplayMode): DailyUpdateResult =
        processMutex.withLock {
            try {
                val settings = app.preferences.state.first()
                apply(fact, settings)
                if (!fact.isPersonal) repository.recordSuccessfulLockScreenApplication(fact.id)
                app.preferences.setManualWallpaper(fact.id, mode, LocalDate.now())
                app.preferences.setWallpaperRefresh(true)
                runCatching { DailyWallpaperScheduler.setEnabled(app, true) }
                DailyUpdateResult.APPLIED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!fact.isPersonal) runCatching { repository.recordDelivery(fact.id, false, error.message) }
                DailyUpdateResult.FAILED
            }
        }

    /** Explicit resume action: apply today's stable selection before clearing the pinned state. */
    suspend fun resumeAutomatic(date: LocalDate = LocalDate.now()): DailyUpdateResult = processMutex.withLock {
        try {
            val result = updateLocked(date, requireConsent = false, forceApply = true, ignorePinned = true)
            if (result == DailyUpdateResult.APPLIED) {
                app.preferences.clearManualWallpaper()
                app.preferences.setWallpaperRefresh(true)
                runCatching { DailyWallpaperScheduler.setEnabled(app, true) }
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DailyUpdateResult.FAILED
        }
    }

    private suspend fun updateLocked(date: LocalDate, requireConsent: Boolean, forceApply: Boolean, ignorePinned: Boolean = false): DailyUpdateResult {
            app.catalogImport.join()
            if (app.catalogImportState.value != CatalogImportState.Ready) return DailyUpdateResult.FAILED
            val settings = app.preferences.state.first()
            if (!ignorePinned && settings.wallpaperDisplayMode == WallpaperDisplayMode.PINNED) {
                val pinnedId = settings.selectedWallpaperId
                if (pinnedId != null && repository.anecdote(pinnedId) != null) {
                    val dailyFact = repository.dailyAnecdote(date) ?: return DailyUpdateResult.NO_ANECDOTE
                    repository.recordDailyAcquisition(dailyFact.id)
                    return DailyUpdateResult.ACQUIRED_ONLY
                }
                app.preferences.clearManualWallpaper()
            }
            if (requireConsent && !settings.wallpaperRefreshEnabled) return DailyUpdateResult.DISABLED
            if (!ignorePinned && !forceApply && settings.wallpaperDisplayMode == WallpaperDisplayMode.UNTIL_NEXT_RENEWAL &&
                settings.temporaryWallpaperDate?.let { !date.isAfter(it) } == true
            ) return DailyUpdateResult.ALREADY_APPLIED

            val fact = try {
                repository.dailyAnecdote(date)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return DailyUpdateResult.FAILED
            } ?: return DailyUpdateResult.NO_ANECDOTE

            val alreadyAppliedToday = repository.hasSuccessfulDeliveryOn(fact.id, date)
            if (!forceApply && !DailyRenewalPolicy.shouldApply(settings.wallpaperRefreshEnabled, alreadyAppliedToday)) {
                return if (alreadyAppliedToday) DailyUpdateResult.ALREADY_APPLIED else DailyUpdateResult.DISABLED
            }

            try {
                apply(fact, settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                runCatching { repository.recordDelivery(fact.id, false, error.message) }
                return DailyUpdateResult.FAILED
            }

            try {
                repository.recordSuccessfulLockScreenApplication(fact.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return DailyUpdateResult.FAILED
            }
            if (settings.wallpaperDisplayMode == WallpaperDisplayMode.UNTIL_NEXT_RENEWAL &&
                settings.temporaryWallpaperDate?.let { date.isAfter(it) } == true
            ) app.preferences.clearManualWallpaper()
            return DailyUpdateResult.APPLIED
    }

    private suspend fun apply(fact: fr.inillusio.data.local.dao.AnecdoteWithCategory, settings: fr.inillusio.data.preferences.PreferencesState) {
        val source = repository.sources(fact.id).firstOrNull()
        val reference = source?.shortCitation()
        val dark = when (settings.theme) {
            AppTheme.DARK -> true
            AppTheme.LIGHT -> false
            AppTheme.SYSTEM -> (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val wallpaper = WallpaperService(app)
        val artwork = withContext(Dispatchers.Default) {
            wallpaper.renderForCurrentDisplay(fact, reference, dark, settings.wallpaperTextOffset, settings.wallpaperSourceOffset)
        }
        withContext(Dispatchers.IO) { wallpaper.applyToLockScreen(artwork) }
    }

    companion object {
        // WorkManager and foreground calls execute in the app process by default.
        private val processMutex = Mutex()
    }
}
