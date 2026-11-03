package fr.inillusio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.inillusio.InIllusioApplication
import fr.inillusio.CatalogImportState
import fr.inillusio.BuildConfig
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import fr.inillusio.data.local.dao.SourceDetails
import fr.inillusio.data.preferences.AppTheme
import fr.inillusio.data.preferences.PreferencesState
import fr.inillusio.data.repository.CatalogRepository
import fr.inillusio.data.repository.BoosterAwardResult
import fr.inillusio.domain.shortCitation
import fr.inillusio.debug.DeveloperTools
import fr.inillusio.wallpaper.WallpaperArtwork
import fr.inillusio.wallpaper.WallpaperService
import fr.inillusio.worker.DailyWallpaperScheduler
import fr.inillusio.worker.DailyWallpaperUpdater
import fr.inillusio.worker.DailyUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import java.time.ZoneId

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InIllusioApplication
    private val repository: CatalogRepository = app.repository
    private val _daily = MutableStateFlow<AnecdoteWithCategory?>(null)
    val daily = _daily.asStateFlow()
    private val _dailyLoaded = MutableStateFlow(false)
    val dailyLoaded = _dailyLoaded.asStateFlow()
    val collection = repository.collection
    val boosterProgress = repository.boosterProgress
    val preferences = app.preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferencesState())
    val catalogImportState = app.catalogImportState
    private val _sources = MutableStateFlow<List<SourceDetails>>(emptyList())
    val sources = _sources.asStateFlow()
    private val _reference = MutableStateFlow<String?>(null)
    val reference = _reference.asStateFlow()
    private val _wallpaperPreview = MutableStateFlow<WallpaperArtwork?>(null)
    val wallpaperPreview = _wallpaperPreview.asStateFlow()
    private var wallpaperPreviewJob: Job? = null
    private var wallpaperOffsetSaveJob: Job? = null
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    val debugCanRestore = DeveloperTools.hasSnapshot(app).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _developerPreview = MutableStateFlow<List<AnecdoteWithCategory>>(emptyList())
    val developerPreview = _developerPreview.asStateFlow()
    private val foregroundCheckMutex = Mutex()
    init {
        viewModelScope.launch {
            app.catalogImport.join()
            if (app.catalogImportState.value == CatalogImportState.Ready) onAppForeground()
        }
        viewModelScope.launch {
            while (isActive) {
                val now = java.time.ZonedDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                kotlinx.coroutines.delay(java.time.Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L))
                onAppForeground()
            }
        }
    }
    fun refreshDaily() = viewModelScope.launch {
        if (app.catalogImportState.value != CatalogImportState.Ready) return@launch
        loadDaily()
    }
    fun retryCatalogImport() {
        app.retryCatalogImport()
        viewModelScope.launch {
            app.catalogImport.join()
            if (app.catalogImportState.value == CatalogImportState.Ready) onAppForeground()
        }
    }
    private suspend fun loadDaily() {
        val fact = repository.dailyAnecdote()
        _daily.value = fact
        _reference.value = fact?.let { current -> repository.sources(current.id).firstOrNull()?.shortCitation() }
        _dailyLoaded.value = true
    }
    fun onAppForeground() = viewModelScope.launch {
        foregroundCheckMutex.withLock {
            app.catalogImport.join()
            if (app.catalogImportState.value != CatalogImportState.Ready) return@withLock
            loadDaily()
            val settings = app.preferences.state.first()
            if (settings.wallpaperRefreshEnabled) {
                val result = DailyWallpaperUpdater(app).update()
                if (result == DailyUpdateResult.FAILED) _message.value = "Le renouvellement du fond verrouillé a échoué. Il sera réessayé plus tard."
            }
        }
    }
    fun showSources(id: String) = viewModelScope.launch { _sources.value = repository.sources(id) }
    suspend fun cardCitation(id: String): String? =
        repository.sources(id).firstOrNull()?.shortCitation()?.takeIf(String::isNotBlank)
    fun setTheme(theme: AppTheme) = viewModelScope.launch { app.preferences.setTheme(theme) }
    fun setRefreshEnabled(enabled: Boolean) = viewModelScope.launch {
        app.preferences.setWallpaperRefresh(enabled)
        DailyWallpaperScheduler.setEnabled(app, enabled)
        if (enabled) onAppForeground().join()
    }
    fun debugRunDailyRenewal() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            when (DailyWallpaperUpdater(app).update(forceApply = true)) {
                DailyUpdateResult.APPLIED -> _message.value = "Test du renouvellement appliqué au fond verrouillé et carte ajoutée à la collection."
                DailyUpdateResult.DISABLED -> _message.value = "Activez d’abord le renouvellement quotidien pour tester le Worker."
                DailyUpdateResult.ALREADY_APPLIED -> _message.value = "L’affiche du jour est déjà à jour."
                DailyUpdateResult.NO_ANECDOTE -> _message.value = "Aucune anecdote disponible pour le test."
                DailyUpdateResult.FAILED -> _message.value = "Échec du test du renouvellement du fond verrouillé."
            }
        }
    }
    fun completeOnboarding() = viewModelScope.launch { app.preferences.completeOnboarding() }
    fun prepareWallpaperPreview(fact: AnecdoteWithCategory, reference: String?, darkTheme: Boolean, textVerticalOffset: Float, sourceVerticalOffset: Float) {
        wallpaperPreviewJob?.cancel()
        wallpaperPreviewJob = viewModelScope.launch {
            kotlinx.coroutines.delay(35)
            val artwork = withContext(Dispatchers.Default) {
                WallpaperService(getApplication()).renderForCurrentDisplay(fact, reference, darkTheme, textVerticalOffset, sourceVerticalOffset)
            }
            if (isActive && _daily.value?.id == fact.id) _wallpaperPreview.value = artwork
        }
    }
    fun scheduleWallpaperOffsetSave(textOffset: Float, sourceOffset: Float) {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            app.preferences.setWallpaperOffsets(textOffset, sourceOffset)
        }
    }
    fun persistWallpaperOffsets(textOffset: Float, sourceOffset: Float) {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch { app.preferences.setWallpaperOffsets(textOffset, sourceOffset) }
    }
    fun resetWallpaperTextOffset(sourceOffset: Float) {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch { app.preferences.setWallpaperOffsets(0f, sourceOffset) }
    }
    fun resetWallpaperSourceOffset(textOffset: Float) {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch { app.preferences.setWallpaperOffsets(textOffset, 0f) }
    }
    fun resetWallpaperOffsets() {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch { app.preferences.resetWallpaperOffsets() }
    }
    fun applyWallpaper(fact: AnecdoteWithCategory, reference: String?, darkTheme: Boolean, textVerticalOffset: Float, sourceVerticalOffset: Float) = viewModelScope.launch {
        runCatching {
            val wallpaperService = WallpaperService(getApplication())
            val freshArtwork = withContext(Dispatchers.Default) {
                wallpaperService.renderForCurrentDisplay(fact, reference, darkTheme, textVerticalOffset, sourceVerticalOffset)
            }
            withContext(Dispatchers.IO) { wallpaperService.applyToLockScreen(freshArtwork) }
            repository.recordSuccessfulLockScreenApplication(fact.id)
        }.onSuccess { _message.value = "Affiche appliquée au fond verrouillé ; carte ajoutée à la collection." }
            .onFailure { error -> repository.recordDelivery(fact.id, false, error.message); _message.value = "Échec de l’application du fond d’écran : ${error.message ?: "erreur inconnue"}" }
    }
    fun clearMessage() { _message.value = null }

    fun redeemBoosterAfterReward(redemptionId: String, onComplete: (BoosterAwardResult) -> Unit) {
        app.redeemBoosterAfterReward(redemptionId, onComplete)
    }

    fun debugSimulateBoosterReward(onComplete: (BoosterAwardResult) -> Unit) {
        if (!BuildConfig.DEBUG) return
        app.redeemBoosterAfterReward("debug-${java.util.UUID.randomUUID()}") { result ->
            _message.value = if (result.cards.isEmpty()) "Aucune nouvelle carte disponible pour le test."
                else "Récompense de test enregistrée : ${result.cards.size} carte${if (result.cards.size == 1) "" else "s"}."
            onComplete(result)
        }
    }

    fun loadDeveloperPreview() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            runCatching { DeveloperTools.allAnecdotes(app) }
                .onSuccess { _developerPreview.value = it }
                .onFailure { _message.value = it.message ?: "Impossible de charger l’aperçu développeur." }
        }
    }

    fun debugUnlockAll() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            runCatching { DeveloperTools.unlockAll(app) }
                .onSuccess { _message.value = "$it cartes débloquées pour les tests." }
                .onFailure { _message.value = it.message ?: "Échec du déblocage développeur." }
        }
    }

    fun debugRestoreCollection() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            runCatching { DeveloperTools.restoreCollection(app) }
                .onSuccess { count -> _message.value = "Collection initiale restaurée ($count carte${if (count == 1) "" else "s"})." }
                .onFailure { _message.value = it.message ?: "Échec de la restauration développeur." }
        }
    }

    fun debugClearCollection() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            runCatching { DeveloperTools.clearCollection(app) }
                .onSuccess { _message.value = "Collection vidée." }
                .onFailure { error -> _message.value = error.message ?: "Échec de la réinitialisation de la collection." }
        }
    }

    fun debugRegenerateAndApplyWallpaper(darkTheme: Boolean) {
        if (!BuildConfig.DEBUG) return
        val fact = _daily.value ?: run {
            _message.value = "Aucune anecdote du jour n’est disponible pour générer le fond."
            return
        }
        wallpaperPreviewJob?.cancel()
        _wallpaperPreview.value = null
        viewModelScope.launch {
            runCatching {
                val wallpaperService = WallpaperService(app)
                val freshArtwork = withContext(Dispatchers.Default) {
                    val currentPreferences = app.preferences.state.first()
                    wallpaperService.renderForCurrentDisplay(fact, _reference.value, darkTheme, currentPreferences.wallpaperTextOffset, currentPreferences.wallpaperSourceOffset)
                }
                withContext(Dispatchers.IO) { wallpaperService.applyToLockScreen(freshArtwork) }
                repository.recordSuccessfulLockScreenApplication(fact.id)
                freshArtwork
            }.onSuccess { artwork ->
                _wallpaperPreview.value = artwork
                _message.value = "Nouveau fond verrouillé appliqué ; carte ajoutée à la collection."
            }.onFailure { error ->
                _message.value = "Échec de la régénération du fond verrouillé : ${error.message ?: "erreur inconnue"}"
            }
        }
    }
}
