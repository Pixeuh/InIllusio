package fr.inillusio

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import fr.inillusio.BuildConfig
import fr.inillusio.data.importer.CatalogImporter
import fr.inillusio.data.local.InIllusioDatabase
import fr.inillusio.data.preferences.UserPreferences
import fr.inillusio.data.repository.CatalogRepository
import fr.inillusio.data.repository.BoosterAwardResult
import fr.inillusio.worker.DailyWallpaperScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

sealed interface CatalogImportState {
    data object Loading : CatalogImportState
    data object Ready : CatalogImportState
    data class Failed(val message: String) : CatalogImportState
}

class InIllusioApplication : Application() {
    lateinit var database: InIllusioDatabase; private set
    lateinit var preferences: UserPreferences; private set
    lateinit var repository: CatalogRepository; private set
    lateinit var adsConsent: AdsConsentManager; private set
    lateinit var catalogImport: Job; private set
    private val _catalogImportState = MutableStateFlow<CatalogImportState>(CatalogImportState.Loading)
    val catalogImportState: StateFlow<CatalogImportState> = _catalogImportState.asStateFlow()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val boosterMutationMutex = Mutex()
    private val collectionResetGeneration = AtomicLong(0L)
    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, InIllusioDatabase::class.java, "hors-cadre.db")
            .addMigrations(InIllusioDatabase.MIGRATION_1_2)
            .addMigrations(InIllusioDatabase.MIGRATION_2_3)
            .build()
        preferences = UserPreferences(this)
        repository = CatalogRepository(database, preferences)
        adsConsent = AdsConsentManager(this)
        retryCatalogImport()
        appScope.launch {
            preferences.state.map { it.wallpaperRefreshEnabled }.distinctUntilChanged().collect { enabled ->
                DailyWallpaperScheduler.setEnabled(this@InIllusioApplication, enabled)
            }
        }
    }

    /** Runs on the application scope so leaving the reveal screen cannot cancel a confirmed reward. */
    fun redeemBoosterAfterReward(redemptionId: String, onComplete: (BoosterAwardResult) -> Unit) {
        val requestGeneration = collectionResetGeneration.get()
        appScope.launch {
            catalogImport.join()
            if (_catalogImportState.value != CatalogImportState.Ready) return@launch
            val result = boosterMutationMutex.withLock {
                if (requestGeneration != collectionResetGeneration.get()) return@withLock null
                repository.redeemBooster(redemptionId)
                    .takeIf { requestGeneration == collectionResetGeneration.get() }
            } ?: return@launch
            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                if (requestGeneration == collectionResetGeneration.get()) onComplete(result)
            }
        }
    }

    /** Debug-only reset, serialized with reward attribution and without touching delivery history. */
    internal suspend fun clearCollectionForDeveloper() {
        check(BuildConfig.DEBUG) { "La réinitialisation développeur n'est disponible qu'en debug." }
        collectionResetGeneration.incrementAndGet()
        catalogImport.join()
        boosterMutationMutex.withLock {
            database.withTransaction { database.catalogDao().clearCollection() }
        }
    }

    fun retryCatalogImport() {
        if (::catalogImport.isInitialized && catalogImport.isActive) return
        _catalogImportState.value = CatalogImportState.Loading
        catalogImport = appScope.launch {
            try {
                CatalogImporter(this@InIllusioApplication, database).importBundledCatalog()
                _catalogImportState.value = CatalogImportState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _catalogImportState.value = CatalogImportState.Failed(
                    error.message?.takeIf { it.isNotBlank() } ?: "Le catalogue embarqué est invalide ou inaccessible."
                )
            }
        }
    }
}
