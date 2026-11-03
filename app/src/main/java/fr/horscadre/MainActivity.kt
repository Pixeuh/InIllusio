package fr.horscadre

import android.os.Bundle
import android.animation.ValueAnimator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
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
import fr.horscadre.ui.explanationExcerpt
import fr.horscadre.ui.illustration.CategoryIllustration
import fr.horscadre.ui.illustration.DisciplinePalettes
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
    val dailyLoaded by model.dailyLoaded.collectAsStateWithLifecycle()
    val collection by model.collection.collectAsStateWithLifecycle(initialValue = emptyList())
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
                        composable("home") { HomeScreen(daily, dailyLoaded, { daily?.let { selected = it; model.showSources(it.id); navController.navigate("explanation") } }, { navController.navigate("collection") }, { navController.navigate("settings") }) }
                        composable("explanation") { ExplanationScreen(selected, sources, { navController.popBackStack() }) }
                        composable("collection") { CollectionScreen(collection, { navController.popBackStack() }, { fact -> selected = fact; navController.navigate("card") }, debugCanRestore, model::debugUnlockAll, { model.loadDeveloperPreview(); navController.navigate("developer-preview") }, { model.debugRegenerateAndApplyWallpaper(dark) }, model::debugRunDailyRenewal, model::debugRestoreCollection) }
                        composable("card") { CardScreen(collection, selected?.id, { navController.popBackStack() }, { fact -> selected = fact; model.showSources(fact.id); navController.navigate("explanation") }) }
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

@Composable private fun HomeScreen(fact: AnecdoteWithCategory?, dailyLoaded: Boolean, onExplain: () -> Unit, onCollection: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TopLine(onSettings = onSettings)
        Spacer(Modifier.height(8.dp))
        if (fact == null && !dailyLoaded) { CircularProgressIndicator(); Text("Préparation de la carte du jour…", Modifier.padding(20.dp)) }
        else if (fact == null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aucune anecdote validée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Le catalogue ne contient pas encore d’anecdote disponible pour la diffusion.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            TextButton(onClick = onSettings, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Settings, null); Text(" Paramètres") }
            TextButton(onClick = onCollection, modifier = Modifier.align(Alignment.End)) { Text("Ma collection") }
        }
        else {
            FlipCard(
                fact = fact,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onOpenExplanation = onExplain
            )
            TextButton(onClick = onCollection, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) { Text("Ma collection") }
        }
    }
}

@Composable private fun EditorialCard(fact: AnecdoteWithCategory, modifier: Modifier = Modifier, reference: String? = null, showSourceReference: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = remember(context, fact.categoryId) { DisciplinePalettes.forCategory(context, fact.categoryId) }
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp)).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(fact.categoryName.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = Color(palette.secondary), fontWeight = FontWeight.Bold)
            }
            Text("HORS\nCADRE", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, letterSpacing = 1.sp)
        }
        CategoryIllustration(fact.categoryId, Modifier.fillMaxWidth().weight(.9f))
        Column {
            Text(fact.hook, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(palette.secondary), thickness = 2.dp)
            if (showSourceReference) {
                Text("UNE OBSERVATION, DES SOURCES", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 12.dp))
                reference?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
        }
    }
}

@Composable private fun FlipCard(
    fact: AnecdoteWithCategory,
    modifier: Modifier = Modifier,
    resetKey: Any? = fact.id,
    onOpenExplanation: () -> Unit
) {
    var flipped by remember(fact.id, resetKey) { mutableStateOf(false) }
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = if (animationsEnabled) tween(durationMillis = 520, easing = FastOutSlowInEasing) else snap(),
        label = "card-flip"
    )
    val cameraDistance = 16f * LocalDensity.current.density
    Box(modifier) {
        if (rotation <= 90f) {
            EditorialCard(
                fact = fact,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = rotation
                        this.cameraDistance = cameraDistance
                    }
                    .clickable(onClickLabel = "Retourner la carte") { flipped = true }
                    .semantics { contentDescription = "Recto de la carte, ${fact.categoryName}. Appuyer pour retourner." }
            )
        } else {
            FlipCardBack(
                fact = fact,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = rotation - 180f
                        this.cameraDistance = cameraDistance
                    }
                    .clickable(onClickLabel = "Afficher le recto") { flipped = false }
                    .semantics { contentDescription = "Verso de la carte, ${fact.categoryName}. Appuyer pour afficher le recto." },
                onOpenExplanation = onOpenExplanation
            )
        }
    }
}

@Composable private fun FlipCardBack(fact: AnecdoteWithCategory, modifier: Modifier = Modifier, onOpenExplanation: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = remember(context, fact.categoryId) { DisciplinePalettes.forCategory(context, fact.categoryId) }
    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("${fact.categoryName.uppercase()}  ·  AU VERSO", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = Color(palette.secondary), fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(top = 14.dp), color = Color(palette.secondary), thickness = 2.dp)
        }
        Text(
            text = explanationExcerpt(fact.explanation),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 25.sp,
            maxLines = 9,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        OutlinedButton(onClick = onOpenExplanation, modifier = Modifier.fillMaxWidth()) { Text("Lire l’explication complète") }
    }
}

@Composable private fun OnboardingScreen(consent: Boolean, onConsent: (Boolean) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("HORS CADRE", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(30.dp)); Text("Une idée\nà garder en tête.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp)); Text("Chaque jour, une anecdote des sciences humaines. Lis son explication et retrouve dans ta collection les cartes appliquées à ton écran verrouillé.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(18.dp)); Text("Le fond d’écran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Avec ton accord, Hors Cadre renouvelle chaque jour l’affiche du fond d’écran verrouillé. L’application ne touche jamais au fond d’accueil. Android peut retarder l’exécution pour économiser la batterie.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = onConsent)
            Text("J’accepte le renouvellement quotidien du fond d’écran verrouillé.", style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onContinue, enabled = consent, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continuer") }
    }
}

@Composable private fun ExplanationScreen(fact: AnecdoteWithCategory?, sources: List<fr.horscadre.data.local.dao.SourceDetails>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopLine("Retour", onBack)
        if (fact == null) return@Column
        Text(fact.categoryName.uppercase(), Modifier.padding(horizontal = 26.dp), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
        Text(fact.title, Modifier.padding(horizontal = 26.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(fact.explanation, Modifier.padding(horizontal = 26.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
        Text("SOURCES", Modifier.padding(start = 26.dp, top = 28.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
        sources.forEach { SourceLine(it) }
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
    val gridState = rememberLazyGridState()
    Column(Modifier.fillMaxSize()) {
        TopLine("Accueil", onBack)
        DeveloperTools.CollectionTitle { showDebugMenu = true }
        DeveloperTools.MenuDialog(showDebugMenu, debugCanRestore, { showDebugMenu = false }, { showDebugMenu = false; onDebugUnlockAll() }, { showDebugMenu = false; onDebugPreviewAll() }, { showDebugMenu = false; onDebugRegenerateWallpaper() }, { showDebugMenu = false; onDebugRunDailyRenewal() }, { showDebugMenu = false; onDebugRestore() })
        Text("Ma collection", Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("${cards.size} carte${if (cards.size == 1) "" else "s"}", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
        if (cards.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Les cartes que tu choisis garder apparaîtront ici.", textAlign = TextAlign.Center) }
        else LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { it.id }) { fact ->
                Column(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(fact) }.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val palette = remember(context, fact.categoryId) { DisciplinePalettes.forCategory(context, fact.categoryId) }
                    Text(fact.categoryName.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = Color(palette.secondary))
                    CategoryIllustration(fact.categoryId, Modifier.fillMaxWidth().height(94.dp))
                    Text(fact.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3)
                }
            }
        }
    }
}

@Composable private fun CardScreen(cards: List<AnecdoteWithCategory>, selectedCardId: String?, onBack: () -> Unit, onOpenExplanation: (AnecdoteWithCategory) -> Unit) {
    val cardIds = remember(cards) { cards.map { it.id } }
    var currentCardId by rememberSaveable { mutableStateOf(selectedCardId) }
    val selectedIndex = cardIds.indexOf(currentCardId).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { cards.size })
    LaunchedEffect(pagerState.settledPage, cardIds) {
        cards.getOrNull(pagerState.settledPage)?.let { currentCardId = it.id }
    }
    LaunchedEffect(cardIds, currentCardId) {
        if (cards.isNotEmpty()) {
            val target = cardIds.indexOf(currentCardId).takeIf { it >= 0 }
                ?: pagerState.currentPage.coerceIn(cards.indices)
            if (target != pagerState.currentPage) pagerState.scrollToPage(target)
            currentCardId = cards[target].id
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopLine("Retour", onBack)
        if (cards.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Cette carte n’est plus dans la collection.", textAlign = TextAlign.Center)
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val maxCardHeight = minOf(maxHeight * 0.86f, 620.dp)
                val maxCardWidth = (maxWidth - 44.dp).coerceAtLeast(0.dp)
                val cardWidth = minOf(maxCardWidth, maxCardHeight * 0.68f)
                val cardHeight = cardWidth / 0.68f
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { cards[it].id }) { page ->
                    cards.getOrNull(page)?.let { fact ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            FlipCard(
                                fact = fact,
                                modifier = Modifier.size(cardWidth, cardHeight),
                                resetKey = pagerState.currentPage,
                                onOpenExplanation = { currentCardId = fact.id; onOpenExplanation(fact) }
                            )
                        }
                    }
                }
            }
            Text(
                "${(pagerState.settledPage + 1).coerceIn(1, cards.size)} / ${cards.size}",
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
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
