package fr.horscadre.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.horscadre.HorsCadreApplication
import fr.horscadre.CatalogImportState
import fr.horscadre.BuildConfig
import fr.horscadre.data.local.dao.AnecdoteWithCategory
import fr.horscadre.data.local.dao.SourceDetails
import fr.horscadre.data.preferences.AppTheme
import fr.horscadre.data.preferences.PreferencesState
import fr.horscadre.data.repository.CatalogRepository
import fr.horscadre.domain.shortCitation
import fr.horscadre.debug.DeveloperTools
import fr.horscadre.wallpaper.WallpaperArtwork
import fr.horscadre.wallpaper.WallpaperService
import fr.horscadre.worker.DailyWallpaperScheduler
import fr.horscadre.worker.DailyWallpaperUpdater
import fr.horscadre.worker.DailyUpdateResult
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
    private val app = application as HorsCadreApplication
    private val repository: CatalogRepository = app.repository
    private val _daily = MutableStateFlow<AnecdoteWithCategory?>(null)
    val daily = _daily.asStateFlow()
    val collection = repository.collection
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
    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedIds = _savedIds.asStateFlow()
    private val _deliveredIds = MutableStateFlow<Set<String>>(emptySet())
    val deliveredIds = _deliveredIds.asStateFlow()
    val debugCanRestore = DeveloperTools.hasSnapshot(app).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _developerPreview = MutableStateFlow<List<AnecdoteWithCategory>>(emptyList())
    val developerPreview = _developerPreview.asStateFlow()
    private val foregroundCheckMutex = Mutex()
    init {
        viewModelScope.launch {
            app.catalogImport.join()
            if (app.catalogImportState.value == CatalogImportState.Ready) onAppForeground()
        }
        viewModelScope.launch { repository.collection.collect { _savedIds.value = it.map(AnecdoteWithCategory::id).toSet() } }
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
        if (fact != null && repositoryWasDelivered(fact.id)) _deliveredIds.value = _deliveredIds.value + fact.id
    }
    fun onAppForeground() = viewModelScope.launch {
        foregroundCheckMutex.withLock {
            app.catalogImport.join()
            if (app.catalogImportState.value != CatalogImportState.Ready) return@withLock
            loadDaily()
            val settings = app.preferences.state.first()
            if (settings.wallpaperRefreshEnabled) {
                val result = DailyWallpaperUpdater(app).update()
                if (result == DailyUpdateResult.APPLIED) _daily.value?.let { _deliveredIds.value = _deliveredIds.value + it.id }
                if (result == DailyUpdateResult.FAILED) _message.value = "Le renouvellement du fond verrouillé a échoué. Il sera réessayé plus tard."
            }
        }
    }
    private suspend fun repositoryWasDelivered(id: String) = app.database.catalogDao().hasSuccessfulDelivery(id)
    fun showSources(id: String) = viewModelScope.launch { _sources.value = repository.sources(id) }
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
                DailyUpdateResult.APPLIED -> { _daily.value?.let { _deliveredIds.value = _deliveredIds.value + it.id }; _message.value = "Test du renouvellement appliqué au fond verrouillé." }
                DailyUpdateResult.DISABLED -> _message.value = "Activez d’abord le renouvellement quotidien pour tester le Worker."
                DailyUpdateResult.ALREADY_APPLIED -> _message.value = "L’affiche du jour est déjà à jour."
                DailyUpdateResult.NO_ANECDOTE -> _message.value = "Aucune anecdote disponible pour le test."
                DailyUpdateResult.FAILED -> _message.value = "Échec du test du renouvellement du fond verrouillé."
            }
        }
    }
    fun completeOnboarding() = viewModelScope.launch { app.preferences.completeOnboarding() }
    fun save(id: String) = viewModelScope.launch {
        runCatching { repository.save(id) }.onSuccess { _message.value = "Carte ajoutée à votre collection." }.onFailure { _message.value = it.message }
    }
    fun prepareWallpaperPreview(fact: AnecdoteWithCategory, reference: String?, darkTheme: Boolean, textVerticalOffset: Float) {
        wallpaperPreviewJob?.cancel()
        _wallpaperPreview.value = null
        wallpaperPreviewJob = viewModelScope.launch {
            val artwork = withContext(Dispatchers.Default) {
                WallpaperService(getApplication()).renderForCurrentDisplay(fact, reference, darkTheme, textVerticalOffset)
            }
            if (_daily.value?.id == fact.id) _wallpaperPreview.value = artwork
        }
    }
    fun scheduleWallpaperTextOffsetSave(offset: Float) {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            app.preferences.setWallpaperTextOffset(offset)
        }
    }
    fun persistWallpaperTextOffset(offset: Float) {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch { app.preferences.setWallpaperTextOffset(offset) }
    }
    fun resetWallpaperTextOffset() {
        wallpaperOffsetSaveJob?.cancel()
        wallpaperOffsetSaveJob = viewModelScope.launch { app.preferences.resetWallpaperTextOffset() }
    }
    fun applyWallpaper(fact: AnecdoteWithCategory, reference: String?, darkTheme: Boolean, textVerticalOffset: Float) = viewModelScope.launch {
        runCatching {
            val wallpaperService = WallpaperService(getApplication())
            val freshArtwork = withContext(Dispatchers.Default) {
                wallpaperService.renderForCurrentDisplay(fact, reference, darkTheme, textVerticalOffset)
            }
            withContext(Dispatchers.IO) { wallpaperService.applyToLockScreen(freshArtwork) }
            repository.recordDelivery(fact.id, true)
        }.onSuccess { _deliveredIds.value = _deliveredIds.value + fact.id; _message.value = "Affiche appliquée au fond d’écran verrouillé." }
            .onFailure { error -> repository.recordDelivery(fact.id, false, error.message); _message.value = "Échec de l’application du fond d’écran : ${error.message ?: "erreur inconnue"}" }
    }
    fun clearMessage() { _message.value = null }

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
                    wallpaperService.renderForCurrentDisplay(fact, _reference.value, darkTheme, currentPreferences.wallpaperTextOffset)
                }
                withContext(Dispatchers.IO) { wallpaperService.applyToLockScreen(freshArtwork) }
                freshArtwork
            }.onSuccess { artwork ->
                _wallpaperPreview.value = artwork
                _message.value = "Nouveau fond d’écran de verrouillage régénéré et appliqué."
            }.onFailure { error ->
                _message.value = "Échec de la régénération du fond verrouillé : ${error.message ?: "erreur inconnue"}"
            }
        }
    }
}
