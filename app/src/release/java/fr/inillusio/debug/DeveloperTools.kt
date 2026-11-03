package fr.inillusio.debug

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import fr.inillusio.InIllusioApplication
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Release variant intentionally contains no developer UI or collection mutation code. */
object DeveloperTools {
    fun hasSnapshot(application: InIllusioApplication): Flow<Boolean> = flowOf(false)
    suspend fun allAnecdotes(application: InIllusioApplication): List<AnecdoteWithCategory> = emptyList()
    suspend fun unlockAll(application: InIllusioApplication): Int = error("Outils développeur indisponibles.")
    suspend fun restoreCollection(application: InIllusioApplication): Int = error("Outils développeur indisponibles.")

    @Composable fun CollectionTitle(onOpenMenu: () -> Unit) {
        Text("SCIENCES HUMAINES", Modifier, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
    }

    @Composable fun MenuDialog(open: Boolean, canRestore: Boolean, onDismiss: () -> Unit, onUnlockAll: () -> Unit, onPreviewAll: () -> Unit, onRegenerateWallpaper: () -> Unit, onRunDailyRenewal: () -> Unit, onRestore: () -> Unit) = Unit
    @Composable fun PreviewScreen(cards: List<AnecdoteWithCategory>, onBack: () -> Unit) = Unit
}
