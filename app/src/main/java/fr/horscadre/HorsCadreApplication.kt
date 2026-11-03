package fr.horscadre

import android.app.Application
import androidx.room.Room
import fr.horscadre.data.importer.CatalogImporter
import fr.horscadre.data.local.HorsCadreDatabase
import fr.horscadre.data.preferences.UserPreferences
import fr.horscadre.data.repository.CatalogRepository
import fr.horscadre.worker.DailyWallpaperScheduler
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

sealed interface CatalogImportState {
    data object Loading : CatalogImportState
    data object Ready : CatalogImportState
    data class Failed(val message: String) : CatalogImportState
}

class HorsCadreApplication : Application() {
    lateinit var database: HorsCadreDatabase; private set
    lateinit var preferences: UserPreferences; private set
    lateinit var repository: CatalogRepository; private set
    lateinit var catalogImport: Job; private set
    private val _catalogImportState = MutableStateFlow<CatalogImportState>(CatalogImportState.Loading)
    val catalogImportState: StateFlow<CatalogImportState> = _catalogImportState.asStateFlow()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, HorsCadreDatabase::class.java, "hors-cadre.db")
            .addMigrations(HorsCadreDatabase.MIGRATION_1_2)
            .build()
        preferences = UserPreferences(this)
        repository = CatalogRepository(database, preferences)
        retryCatalogImport()
        appScope.launch {
            preferences.state.map { it.wallpaperRefreshEnabled }.distinctUntilChanged().collect { enabled ->
                DailyWallpaperScheduler.setEnabled(this@HorsCadreApplication, enabled)
            }
        }
    }

    fun retryCatalogImport() {
        if (::catalogImport.isInitialized && catalogImport.isActive) return
        _catalogImportState.value = CatalogImportState.Loading
        catalogImport = appScope.launch {
            try {
                CatalogImporter(this@HorsCadreApplication, database).importBundledCatalog()
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
