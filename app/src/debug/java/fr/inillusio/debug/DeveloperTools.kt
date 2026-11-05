package fr.inillusio.debug

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import fr.inillusio.BuildConfig
import fr.inillusio.InIllusioApplication
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import fr.inillusio.data.local.entities.CollectionEntity
import fr.inillusio.ui.illustration.CategoryIllustration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val android.content.Context.developerPreferences by preferencesDataStore(name = "inillusio_developer")

object DeveloperTools {
    private val snapshotKey = stringPreferencesKey("collection_snapshot_v1")

    fun hasSnapshot(application: InIllusioApplication): Flow<Boolean> {
        require(BuildConfig.DEBUG) { "Les outils développeur ne sont disponibles qu'en debug." }
        return application.developerPreferences.data.map { it[snapshotKey] != null }
    }

    suspend fun allAnecdotes(application: InIllusioApplication): List<AnecdoteWithCategory> {
        require(BuildConfig.DEBUG) { "Les outils développeur ne sont disponibles qu'en debug." }
        application.catalogImport.join()
        return application.database.catalogDao().allAnecdotes()
    }

    suspend fun unlockAll(application: InIllusioApplication): Int {
        require(BuildConfig.DEBUG) { "Les outils développeur ne sont disponibles qu'en debug." }
        application.catalogImport.join()
        val dao = application.database.catalogDao()
        val current = dao.allCollectionEntries()
        application.developerPreferences.edit { preferences ->
            if (preferences[snapshotKey] == null) preferences[snapshotKey] = encode(current)
        }
        val facts = dao.allAnecdotes()
        val timestamp = System.currentTimeMillis()
        dao.putCollection(facts.map { CollectionEntity(it.id, timestamp) })
        return facts.size
    }

    suspend fun restoreCollection(application: InIllusioApplication): Int {
        require(BuildConfig.DEBUG) { "Les outils développeur ne sont disponibles qu'en debug." }
        val preferences = application.developerPreferences.data.map { it[snapshotKey] }.first()
            ?: error("Aucune sauvegarde de collection développeur n'est disponible.")
        val saved = decode(preferences)
        val dao = application.database.catalogDao()
        application.database.withTransaction {
            dao.clearCollection()
            dao.putCollection(saved)
        }
        application.developerPreferences.edit { it.remove(snapshotKey) }
        return saved.size
    }

    suspend fun clearCollection(application: InIllusioApplication) {
        require(BuildConfig.DEBUG) { "Les outils développeur ne sont disponibles qu'en debug." }
        application.clearCollectionForDeveloper()
    }

    @Composable
    fun CollectionTitle(onOpenMenu: () -> Unit) {
        val taps = remember { RapidTapSequence() }
        val open by rememberUpdatedState(onOpenMenu)
        Text(
            "SCIENCES HUMAINES",
            Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { if (taps.registerTap(SystemClock.uptimeMillis())) open() })
            }.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }

    @Composable
    fun MenuDialog(
        open: Boolean,
        canRestore: Boolean,
        onDismiss: () -> Unit,
        onUnlockAll: () -> Unit,
        onPreviewAll: () -> Unit,
        onRegenerateWallpaper: () -> Unit,
        onRunDailyRenewal: () -> Unit,
        onRestore: () -> Unit,
        onClearCollection: () -> Unit
    ) {
        if (!open) return
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Menu DEBUG") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Outils de prévisualisation et de test. La collection initiale est sauvegardée avant le premier déblocage massif.")
                    Button(onClick = onUnlockAll, modifier = Modifier.fillMaxWidth()) { Text("Débloquer toutes les cartes") }
                    OutlinedButton(onClick = onPreviewAll, modifier = Modifier.fillMaxWidth()) { Text("Prévisualiser toutes les cartes") }
                    OutlinedButton(onClick = onRegenerateWallpaper, modifier = Modifier.fillMaxWidth()) { Text("Régénérer et appliquer le fond d'écran") }
                    OutlinedButton(onClick = onRunDailyRenewal, modifier = Modifier.fillMaxWidth()) { Text("Tester le renouvellement maintenant") }
                    OutlinedButton(onClick = onRestore, enabled = canRestore, modifier = Modifier.fillMaxWidth()) { Text("Restaurer ma collection initiale") }
                    OutlinedButton(onClick = onClearCollection, modifier = Modifier.fillMaxWidth()) { Text("Vider la collection") }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
        )
    }

    @Composable
    fun ClearCollectionDialog(
        open: Boolean,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    ) {
        if (!open) return
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Vider la collection ?") },
            text = { Text("Toutes les cartes collectionnées seront retirées de votre collection. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text("Vider la collection") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        )
    }

    @Composable
    fun PreviewScreen(cards: List<AnecdoteWithCategory>, onBack: () -> Unit) {
        var selected by remember { mutableStateOf<AnecdoteWithCategory?>(null) }
        var flipped by remember(selected?.id) { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = if (selected == null) onBack else { { selected = null } }) { Text("← Retour") }
                Column(Modifier.weight(1f)) {
                    Text("PRÉVISUALISATION DÉVELOPPEUR", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("${cards.size} cartes · aucune donnée modifiée", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (selected == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cards, key = { it.id }) { fact ->
                        Column(
                            Modifier.fillMaxWidth().height(190.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                                .clickable { selected = fact }.padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(fact.categoryName.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                            CategoryIllustration(fact.categoryId, Modifier.fillMaxWidth().height(100.dp))
                            Text(fact.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3)
                        }
                    }
                }
            } else {
                val rotation by animateFloatAsState(if (flipped) 180f else 0f, label = "developer-card-flip")
                val front = rotation <= 90f
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CARTE AGRANDIE · ${if (front) "RECTO" else "VERSO"}", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().weight(1f).graphicsLayer { rotationY = rotation; cameraDistance = 16 * density }
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp)).clickable { flipped = !flipped }.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (front) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                Text(selected!!.categoryName.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                                CategoryIllustration(selected!!.categoryId, Modifier.fillMaxWidth().weight(1f).padding(12.dp))
                                Text(selected!!.hook, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start)
                            }
                        } else {
                            Column(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp)).padding(8.dp)) {
                                Text(selected!!.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(16.dp))
                                Text(selected!!.explanation, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text("Aperçu sans modification de la collection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { flipped = !flipped }) { Text(if (flipped) "Retourner au recto" else "Retourner la carte") }
                }
            }
        }
    }

    private fun encode(entries: List<CollectionEntity>): String = JSONArray().apply {
        entries.forEach { put(org.json.JSONObject().put("id", it.anecdoteId).put("savedAt", it.savedAt)) }
    }.toString()

    private fun decode(value: String): List<CollectionEntity> {
        val json = JSONArray(value)
        return List(json.length()) { index ->
            val entry = json.getJSONObject(index)
            CollectionEntity(entry.getString("id"), entry.getLong("savedAt"))
        }
    }
}
