package fr.horscadre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.horscadre.data.local.dao.AnecdoteWithCategory
import fr.horscadre.BuildConfig
import fr.horscadre.debug.DeveloperTools
import fr.horscadre.data.preferences.AppTheme
import fr.horscadre.CatalogImportState
import fr.horscadre.ui.AppViewModel
import fr.horscadre.ui.illustration.CategoryIllustration
import fr.horscadre.ui.theme.HorsCadreTheme
import fr.horscadre.wallpaper.WallpaperArtwork

class MainActivity : ComponentActivity() {
    private val model by viewModels<AppViewModel> { object : ViewModelProvider.AndroidViewModelFactory(application) { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(application) as T } }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { HorsCadreRoot(model) } }
    override fun onResume() { super.onResume(); model.onAppForeground() }
}

@Composable private fun HorsCadreRoot(model: AppViewModel) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val catalogImportState by model.catalogImportState.collectAsStateWithLifecycle()
    val daily by model.daily.collectAsStateWithLifecycle()
    val collection by model.collection.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedIds by model.savedIds.collectAsStateWithLifecycle()
    val deliveredIds by model.deliveredIds.collectAsStateWithLifecycle()
    val sources by model.sources.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val reference by model.reference.collectAsStateWithLifecycle()
    val wallpaperPreview by model.wallpaperPreview.collectAsStateWithLifecycle()
    val developerPreview by model.developerPreview.collectAsStateWithLifecycle()
    val debugCanRestore by model.debugCanRestore.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<AnecdoteWithCategory?>(null) }
    val navController = rememberNavController()
    var acceptedConsent by remember { mutableStateOf(false) }
    val dark = when (prefs.theme) { AppTheme.DARK -> true; AppTheme.LIGHT -> false; AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme() }
    HorsCadreTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when {
                    catalogImportState is CatalogImportState.Loading -> CatalogLoadingScreen()
                    catalogImportState is CatalogImportState.Failed -> CatalogErrorScreen(
                        (catalogImportState as CatalogImportState.Failed).message,
                        model::retryCatalogImport
                    )
                    !prefs.onboardingComplete -> OnboardingScreen(acceptedConsent, { acceptedConsent = it }) {
                        model.setRefreshEnabled(acceptedConsent); model.completeOnboarding()
                    }
                    else -> NavHost(navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                        composable("home") { HomeScreen(daily, daily?.let { it.id in savedIds } == true, daily?.let { it.id in deliveredIds } == true, { daily?.let { model.save(it.id) } }, { daily?.let { selected = it; model.showSources(it.id); navController.navigate("explanation") } }, { navController.navigate("collection") }, { navController.navigate("settings") }) }
                        composable("explanation") { ExplanationScreen(selected, sources, { navController.popBackStack() }, { selected?.let { model.save(it.id) } }, selected?.let { it.id in savedIds } == true, selected?.let { it.id in deliveredIds } == true) }
                        composable("collection") { CollectionScreen(collection, { navController.popBackStack() }, { selected = it; model.showSources(it.id); navController.navigate("card") }, debugCanRestore, model::debugUnlockAll, { model.loadDeveloperPreview(); navController.navigate("developer-preview") }, { model.debugRegenerateAndApplyWallpaper(dark) }, model::debugRunDailyRenewal, model::debugRestoreCollection) }
                        composable("card") { CardScreen(selected, sources, { navController.popBackStack() }, { selected?.let { model.save(it.id) } }, selected?.let { it.id in savedIds } == true) }
                        if (BuildConfig.DEBUG) composable("developer-preview") { DeveloperTools.PreviewScreen(developerPreview) { navController.popBackStack() } }
                        composable("settings") { SettingsScreen(prefs.theme, dark, prefs.wallpaperRefreshEnabled, prefs.wallpaperTextOffset, daily, reference, wallpaperPreview, { model.setTheme(it) }, { model.setRefreshEnabled(it) }, { model.scheduleWallpaperTextOffsetSave(it) }, { model.persistWallpaperTextOffset(it) }, { model.resetWallpaperTextOffset() }, { fact, source, darkTheme, offset -> model.prepareWallpaperPreview(fact, source, darkTheme, offset) }, { fact, source, darkTheme, offset -> model.applyWallpaper(fact, source, darkTheme, offset) }, { navController.popBackStack() }) }
                    }
                }
                message?.let { text -> LaunchedEffect(text) { kotlinx.coroutines.delay(3500); model.clearMessage() }; Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(text) } }
            }
        }
    }
}

@Composable private fun CatalogLoadingScreen() {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text("Préparation du catalogue…", Modifier.padding(top = 18.dp), textAlign = TextAlign.Center)
    }
}

@Composable private fun CatalogErrorScreen(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Le catalogue n’a pas pu être chargé", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(message, Modifier.padding(top = 12.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry, Modifier.padding(top = 20.dp)) { Text("Réessayer") }
    }
}

@Composable private fun TopLine(actionLabel: String? = null, onBack: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text(actionLabel ?: "Retour") }
        else Text("HORS CADRE", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Paramètres") }
    }
}

@Composable private fun HomeScreen(fact: AnecdoteWithCategory?, saved: Boolean, delivered: Boolean, onSave: () -> Unit, onExplain: () -> Unit, onCollection: () -> Unit, onSettings: () -> Unit) {
    val heartScale by animateFloatAsState(if (saved) 1.12f else 1f, label = "heart-scale")
    val heartColor by animateColorAsState(if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, label = "heart-color")
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TopLine(onSettings = onSettings)
        Spacer(Modifier.height(8.dp))
        if (fact == null) { CircularProgressIndicator(); Text("Préparation de la carte du jour…", Modifier.padding(20.dp)) }
        else {
            EditorialCard(fact, Modifier.weight(1f).fillMaxWidth(), showSourceReference = false)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSave, enabled = saved || delivered, modifier = Modifier.size(54.dp)) {
                    Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Enregistrer", tint = heartColor, modifier = Modifier.size(30.dp).scale(heartScale))
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = onExplain, modifier = Modifier.weight(1f).height(52.dp)) { Text("Découvrir l’explication  →") }
            }
            Text("Pour collectionner, applique d’abord la carte au fond verrouillé dans les paramètres.", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = onCollection, modifier = Modifier.padding(top = 4.dp)) { Text("Ma collection") }
        }
    }
}

@Composable private fun EditorialCard(fact: AnecdoteWithCategory, modifier: Modifier = Modifier, reference: String? = null, showSourceReference: Boolean = true) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp)).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(fact.categoryName.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Text("HORS\nCADRE", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, letterSpacing = 1.sp)
        }
        CategoryIllustration(fact.categoryId, Modifier.fillMaxWidth().weight(.9f))
        Column {
            Text(fact.hook, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary, thickness = 2.dp)
            if (showSourceReference) {
                Text("UNE OBSERVATION, DES SOURCES", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 12.dp))
                reference?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
        }
    }
}

@Composable private fun OnboardingScreen(consent: Boolean, onConsent: (Boolean) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("HORS CADRE", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(30.dp)); Text("Une idée\nà garder en tête.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp)); Text("Chaque jour, une anecdote des sciences humaines. Lis son explication, explore les sources et garde les cartes qui te plaisent.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(18.dp)); Text("Le fond d’écran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Avec ton accord, Hors Cadre renouvelle chaque jour l’affiche du fond d’écran verrouillé. L’application ne touche jamais au fond d’accueil. Android peut retarder l’exécution pour économiser la batterie.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = onConsent)
            Text("J’accepte le renouvellement quotidien du fond d’écran verrouillé.", style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onContinue, enabled = consent, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continuer") }
    }
}

@Composable private fun ExplanationScreen(fact: AnecdoteWithCategory?, sources: List<fr.horscadre.data.local.dao.SourceDetails>, onBack: () -> Unit, onSave: () -> Unit, saved: Boolean, delivered: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopLine("Retour", onBack)
        if (fact == null) return@Column
        Text(fact.categoryName.uppercase(), Modifier.padding(horizontal = 26.dp), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
        Text(fact.title, Modifier.padding(horizontal = 26.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(fact.explanation, Modifier.padding(horizontal = 26.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
        Text("SOURCES", Modifier.padding(start = 26.dp, top = 28.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        sources.forEach { SourceLine(it) }
        TextButton(onClick = onSave, enabled = saved || delivered) { Text(if (saved) "♥  Dans ma collection" else "♡  Ajouter à ma collection") }
    }
}

@Composable private fun SourceLine(source: fr.horscadre.data.local.dao.SourceDetails) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp)) {
        Text(source.author ?: source.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(source.title + (source.year?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun CollectionScreen(cards: List<AnecdoteWithCategory>, onBack: () -> Unit, onSelect: (AnecdoteWithCategory) -> Unit, debugCanRestore: Boolean, onDebugUnlockAll: () -> Unit, onDebugPreviewAll: () -> Unit, onDebugRegenerateWallpaper: () -> Unit, onDebugRunDailyRenewal: () -> Unit, onDebugRestore: () -> Unit) {
    var showDebugMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopLine("Accueil", onBack)
        DeveloperTools.CollectionTitle { showDebugMenu = true }
        DeveloperTools.MenuDialog(showDebugMenu, debugCanRestore, { showDebugMenu = false }, { showDebugMenu = false; onDebugUnlockAll() }, { showDebugMenu = false; onDebugPreviewAll() }, { showDebugMenu = false; onDebugRegenerateWallpaper() }, { showDebugMenu = false; onDebugRunDailyRenewal() }, { showDebugMenu = false; onDebugRestore() })
        Text("Ma collection", Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("${cards.size} carte${if (cards.size == 1) "" else "s"}", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
        if (cards.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("Les cartes que tu choisis garder apparaîtront ici.", textAlign = TextAlign.Center) }
        else LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { it.id }) { fact ->
                Column(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(fact) }.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(fact.categoryName.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                    CategoryIllustration(fact.categoryId, Modifier.fillMaxWidth().height(94.dp))
                    Text(fact.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3)
                }
            }
        }
    }
}

@Composable private fun CardScreen(fact: AnecdoteWithCategory?, sources: List<fr.horscadre.data.local.dao.SourceDetails>, onBack: () -> Unit, onSave: () -> Unit, saved: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopLine("Collection", onBack)
        fact?.let {
            EditorialCard(it, Modifier.fillMaxWidth().height(480.dp).padding(horizontal = 22.dp))
            Button(onClick = onSave, enabled = saved, modifier = Modifier.fillMaxWidth().padding(22.dp)) { Text("♥  Enregistrée") }
            Text(it.explanation, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge)
            sources.forEach { source -> SourceLine(source) }
        }
    }
}

@Composable private fun SettingsScreen(theme: AppTheme, darkTheme: Boolean, refresh: Boolean, savedTextOffset: Float, daily: AnecdoteWithCategory?, reference: String?, wallpaperPreview: WallpaperArtwork?, onTheme: (AppTheme) -> Unit, onRefresh: (Boolean) -> Unit, onScheduleOffsetSave: (Float) -> Unit, onPersistOffset: (Float) -> Unit, onResetOffset: () -> Unit, onPreparePreview: (AnecdoteWithCategory, String?, Boolean, Float) -> Unit, onApply: (AnecdoteWithCategory, String?, Boolean, Float) -> Unit, onBack: () -> Unit) {
    var textOffset by remember(daily?.id) { mutableFloatStateOf(savedTextOffset) }
    LaunchedEffect(savedTextOffset) { textOffset = savedTextOffset }
    LaunchedEffect(daily?.id, reference, darkTheme, textOffset) { daily?.let { onPreparePreview(it, reference, darkTheme, textOffset) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        TopLine("Accueil", onBack)
        Text("Paramètres", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("APPARENCE", Modifier.padding(start = 24.dp, top = 24.dp), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
        listOf(AppTheme.SYSTEM to "Système", AppTheme.LIGHT to "Clair", AppTheme.DARK to "Sombre").forEach { (choice, label) -> Row(Modifier.fillMaxWidth().clickable { onTheme(choice) }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(theme == choice, onClick = { onTheme(choice) }); Text(label) } }
        HorizontalDivider(Modifier.padding(24.dp))
        Text("FOND D’ÉCRAN VERROUILLÉ", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Renouvellement quotidien", style = MaterialTheme.typography.titleSmall); Text(if (refresh) "Actif · exécution différée possible selon Android" else "Inactif · aucun fond ne sera renouvelé", style = MaterialTheme.typography.bodySmall) }
            Switch(refresh, onRefresh)
        }
        daily?.let { fact ->
            HorizontalDivider(Modifier.padding(24.dp))
            Text("Disposition du fond d’écran", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Position verticale du texte", Modifier.padding(start = 24.dp, top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = textOffset,
                onValueChange = { value ->
                    textOffset = value
                    onScheduleOffsetSave(value)
                },
                valueRange = -0.15f..0.15f,
                onValueChangeFinished = { onPersistOffset(textOffset) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Plus haut", style = MaterialTheme.typography.labelSmall)
                Text("${(textOffset * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                Text("Plus bas", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    textOffset = 0f
                    onResetOffset()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            ) { Text("Réinitialiser la position") }
            Text("APERÇU DE L’AFFICHE · éléments système non simulés", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.1.sp)
            val matchingPreview = wallpaperPreview?.takeIf { it.anecdoteId == fact.id }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(420.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                if (matchingPreview != null) {
                    Image(
                        bitmap = matchingPreview.bitmap.asImageBitmap(),
                        contentDescription = "Aperçu du fond de verrouillage, ${matchingPreview.screenWidthPx} × ${matchingPreview.screenHeightPx} pixels",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(color = Color(0xFF00BFAE))
                }
            }
            Button(
                onClick = { onApply(fact, reference, darkTheme, textOffset) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                enabled = matchingPreview != null
            ) { Text("Appliquer le fond d’écran") }
        }
        Text("Hors Cadre · Version 0.1.0", Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp), style = MaterialTheme.typography.labelSmall)
    }
}
