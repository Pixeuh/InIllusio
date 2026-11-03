package fr.inillusio

import android.os.Bundle
import android.animation.ValueAnimator
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import fr.inillusio.BuildConfig
import fr.inillusio.debug.DeveloperTools
import fr.inillusio.data.preferences.AppTheme
import fr.inillusio.CatalogImportState
import fr.inillusio.ui.AppViewModel
import fr.inillusio.ui.illustration.CategoryIllustration
import fr.inillusio.ui.illustration.DisciplinePalettes
import fr.inillusio.ui.theme.InIllusioTheme
import fr.inillusio.wallpaper.WallpaperArtwork
import fr.inillusio.data.local.dao.BoosterProgress
import fr.inillusio.data.repository.BoosterAwardResult
import fr.inillusio.domain.BoosterPolicy
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val model by viewModels<AppViewModel> { object : ViewModelProvider.AndroidViewModelFactory(application) { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(application) as T } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.ADS_ENABLED) (application as InIllusioApplication).adsConsent.updateForLaunch(this)
        setContent { InIllusioRoot(model) }
    }
    override fun onResume() { super.onResume(); model.onAppForeground() }
}

@Composable private fun InIllusioRoot(model: AppViewModel) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val catalogImportState by model.catalogImportState.collectAsStateWithLifecycle()
    val daily by model.daily.collectAsStateWithLifecycle()
    val dailyLoaded by model.dailyLoaded.collectAsStateWithLifecycle()
    val collection by model.collection.collectAsStateWithLifecycle(initialValue = emptyList())
    val boosterProgress by model.boosterProgress.collectAsStateWithLifecycle(initialValue = BoosterProgress(0, 0))
    val activity = LocalContext.current as Activity
    val app = LocalApp(applicationContext = androidx.compose.ui.platform.LocalContext.current.applicationContext)
    val adsConsent by app.adsConsent.state.collectAsStateWithLifecycle()
    val sources by model.sources.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val reference by model.reference.collectAsStateWithLifecycle()
    val wallpaperPreview by model.wallpaperPreview.collectAsStateWithLifecycle()
    val developerPreview by model.developerPreview.collectAsStateWithLifecycle()
    val debugCanRestore by model.debugCanRestore.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<AnecdoteWithCategory?>(null) }
    var homeCardSize by remember { mutableStateOf(IntSize.Zero) }
    var pendingBoosterAward by remember { mutableStateOf<BoosterAwardResult?>(null) }
    val navController = rememberNavController()
    var acceptedConsent by remember { mutableStateOf(false) }
    val dark = when (prefs.theme) { AppTheme.DARK -> true; AppTheme.LIGHT -> false; AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme() }
    InIllusioTheme(dark) {
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
                        composable("home") { HomeScreen(daily, dailyLoaded, reference, { daily?.let { selected = it; model.showSources(it.id); navController.navigate("explanation") } }, { id -> model.cardCitation(id) }, { navController.navigate("collection") }, { navController.navigate("settings") }, { homeCardSize = it }) }
                        composable("explanation") { ExplanationScreen(selected, sources, { navController.popBackStack() }) }
                        composable("collection") {
                            CollectionScreen(
                                cards = collection,
                                boosterProgress = boosterProgress,
                                consent = adsConsent,
                                onBack = { navController.popBackStack() },
                                onSelect = { fact -> selected = fact; navController.navigate("card") },
                                onRedeemBooster = model::redeemBoosterAfterReward,
                                onDebugReward = model::debugSimulateBoosterReward,
                                onAwarded = { result ->
                                    pendingBoosterAward = result
                                    navController.navigate("booster") { launchSingleTop = true }
                                },
                                debugCanRestore = debugCanRestore,
                                onDebugUnlockAll = model::debugUnlockAll,
                                onDebugPreviewAll = { model.loadDeveloperPreview(); navController.navigate("developer-preview") },
                                onDebugRegenerateWallpaper = { model.debugRegenerateAndApplyWallpaper(dark) },
                                onDebugRunDailyRenewal = model::debugRunDailyRenewal,
                                onDebugRestore = model::debugRestoreCollection,
                                onDebugClearCollection = model::debugClearCollection
                            )
                        }
                        composable("booster") {
                            pendingBoosterAward?.let { award ->
                                BoosterScreen(
                                    award = award,
                                    collection = collection,
                                    homeCardSize = homeCardSize,
                                    onBack = { navController.popBackStack() },
                                    onLoadReference = { id -> model.cardCitation(id) },
                                    onOpenExplanation = { fact -> selected = fact; model.showSources(fact.id); navController.navigate("explanation") }
                                )
                            } ?: LaunchedEffect(Unit) { navController.popBackStack() }
                        }
                        composable("card") { CardScreen(collection, selected?.id, homeCardSize, { navController.popBackStack() }, { fact -> selected = fact; model.showSources(fact.id); navController.navigate("explanation") }, { id -> model.cardCitation(id) }) }
                        if (BuildConfig.DEBUG) composable("developer-preview") { DeveloperTools.PreviewScreen(developerPreview) { navController.popBackStack() } }
                        composable("settings") {
                            SettingsScreen(
                                prefs.theme, dark, prefs.wallpaperRefreshEnabled,
                                prefs.wallpaperTextOffset, prefs.wallpaperSourceOffset,
                                daily, reference, wallpaperPreview, adsConsent.privacyOptionsRequired,
                                { model.setTheme(it) }, { model.setRefreshEnabled(it) },
                                { text, sources -> model.scheduleWallpaperOffsetSave(text, sources) },
                                { text, sources -> model.persistWallpaperOffsets(text, sources) },
                                { sources -> model.resetWallpaperTextOffset(sources) },
                                { text -> model.resetWallpaperSourceOffset(text) },
                                { model.resetWallpaperOffsets() },
                                { fact, source, darkTheme, text, sources -> model.prepareWallpaperPreview(fact, source, darkTheme, text, sources) },
                                { fact, source, darkTheme, text, sources -> model.applyWallpaper(fact, source, darkTheme, text, sources) },
                                { app.adsConsent.showPrivacyOptions(activity) {} },
                                { navController.popBackStack() }
                            )
                        }
                    }
                }
                message?.let { text -> LaunchedEffect(text) { kotlinx.coroutines.delay(3500); model.clearMessage() }; Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(text) } }
            }
        }
    }
}

@Composable private fun LocalApp(applicationContext: android.content.Context): InIllusioApplication =
    applicationContext.applicationContext as InIllusioApplication

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

@Composable private fun TopLine(actionLabel: String? = null, onBack: (() -> Unit)? = null, onSettings: (() -> Unit)? = null, trailingText: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text(actionLabel ?: "Retour") }
        else Column {
            Text("IN ILLUSIO", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("Parce que la connaissance libère.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Paramètres") }
        else if (trailingText != null) Text(trailingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomeScreen(fact: AnecdoteWithCategory?, dailyLoaded: Boolean, reference: String?, onExplain: () -> Unit, onLoadReference: suspend (String) -> String?, onCollection: () -> Unit, onSettings: () -> Unit, onCardMeasured: (IntSize) -> Unit) {
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
                modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged(onCardMeasured),
                reference = reference,
                onLoadReference = onLoadReference,
                onOpenExplanation = onExplain
            )
            TextButton(onClick = onCollection, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) { Text("Ma collection") }
        }
    }
}

@Composable private fun EditorialCard(fact: AnecdoteWithCategory, modifier: Modifier = Modifier, reference: String? = null, showSourceReference: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = remember(context, fact.categoryId) { DisciplinePalettes.forCategory(context, fact.categoryId) }
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
            .padding(24.dp)
    ) {
        val illustrationHeight = minOf(maxHeight * 0.38f, maxWidth * 0.70f)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(fact.categoryName.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = Color(palette.secondary), fontWeight = FontWeight.Bold)
                }
                Box(Modifier.height(32.dp), contentAlignment = Alignment.TopEnd) {
                    Text("IN ILLUSIO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, lineHeight = 9.sp), textAlign = TextAlign.End, letterSpacing = 0.5.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(50.dp))
            Box(
                Modifier.fillMaxWidth().height(illustrationHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                CategoryIllustration(
                    fact.categoryId,
                    Modifier.fillMaxHeight().aspectRatio(10f / 7f)
                )
            }
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        fact.hook,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 31.sp,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(26.dp))
                HorizontalDivider(color = Color(palette.secondary), thickness = 2.dp)
                if (showSourceReference) {
                    reference?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun FlipCard(
    fact: AnecdoteWithCategory,
    modifier: Modifier = Modifier,
    resetKey: Any? = fact.id,
    reference: String? = null,
    onLoadReference: suspend (String) -> String?,
    onOpenExplanation: () -> Unit
) {
    var citation by remember(fact.id) { mutableStateOf(reference) }
    LaunchedEffect(fact.id, reference) {
        if (!reference.isNullOrBlank()) citation = reference
        else if (citation.isNullOrBlank()) citation = onLoadReference(fact.id)
    }
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
                reference = citation,
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
            text = fact.explanation,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 25.sp,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis
        )
        OutlinedButton(onClick = onOpenExplanation, modifier = Modifier.fillMaxWidth()) { Text("Lire l’explication complète") }
    }
}

@Composable private fun OnboardingScreen(consent: Boolean, onConsent: (Boolean) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("IN ILLUSIO", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(30.dp)); Text("Une idée\nà garder en tête.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp)); Text("Chaque jour, une anecdote des sciences humaines. Lis son explication et retrouve dans ta collection les cartes appliquées à ton écran verrouillé.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(18.dp)); Text("Le fond d’écran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Avec ton accord, In Illusio renouvelle chaque jour l’affiche du fond d’écran verrouillé. L’application ne touche jamais au fond d’accueil. Android peut retarder l’exécution pour économiser la batterie.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = onConsent)
            Text("J’accepte le renouvellement quotidien du fond d’écran verrouillé.", style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onContinue, enabled = consent, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continuer") }
    }
}

@Composable private fun ExplanationScreen(fact: AnecdoteWithCategory?, sources: List<fr.inillusio.data.local.dao.SourceDetails>, onBack: () -> Unit) {
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

@Composable private fun SourceLine(source: fr.inillusio.data.local.dao.SourceDetails) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp)) {
        Text(source.author ?: source.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(source.title + (source.year?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun CollectionScreen(
    cards: List<AnecdoteWithCategory>,
    boosterProgress: BoosterProgress,
    consent: AdsConsentState,
    onBack: () -> Unit,
    onSelect: (AnecdoteWithCategory) -> Unit,
    onRedeemBooster: (String, (BoosterAwardResult) -> Unit) -> Unit,
    onDebugReward: ((BoosterAwardResult) -> Unit) -> Unit,
    onAwarded: (BoosterAwardResult) -> Unit,
    debugCanRestore: Boolean,
    onDebugUnlockAll: () -> Unit,
    onDebugPreviewAll: () -> Unit,
    onDebugRegenerateWallpaper: () -> Unit,
    onDebugRunDailyRenewal: () -> Unit,
    onDebugRestore: () -> Unit,
    onDebugClearCollection: () -> Unit
) {
    val activity = LocalContext.current as Activity
    val adController = remember(activity) { RewardedBoosterAd(activity) }
    val screenActive = remember { AtomicBoolean(true) }
    var isBusy by remember { mutableStateOf(false) }
    var boosterError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(adController) {
        screenActive.set(true)
        onDispose {
            screenActive.set(false)
            adController.cancelPendingLoad()
        }
    }
    val handleAward: (BoosterAwardResult) -> Unit = { result ->
        if (screenActive.get()) {
            isBusy = false
            when {
                result.duplicateRedemption -> boosterError = "Cette récompense a déjà été enregistrée."
                result.cards.isEmpty() -> boosterError = "Aucune nouvelle anecdote n’est disponible : ta collection est complète."
                else -> onAwarded(result)
            }
        }
    }
    val requestBooster = {
        if (!isBusy) {
            boosterError = null
            when {
                !BuildConfig.ADS_ENABLED -> boosterError = "Les publicités ne sont pas configurées pour cette version."
                !consent.canRequestAds && consent.updateFinished -> boosterError = "Aucune publicité ne peut être demandée selon les choix de confidentialité actuels."
                !consent.canRequestAds -> boosterError = "Vérification des choix de confidentialité…"
                else -> {
                    isBusy = true
                    val redemptionId = UUID.randomUUID().toString()
                    adController.loadAndShow(
                        BuildConfig.ADMOB_REWARDED_UNIT_ID,
                        onReward = { onRedeemBooster(redemptionId, handleAward) },
                        onFailure = { message ->
                            if (screenActive.get()) {
                                isBusy = false
                                boosterError = message
                            }
                        }
                    )
                }
            }
        }
    }
    var showDebugMenu by remember { mutableStateOf(false) }
    var showClearCollectionConfirmation by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    Column(Modifier.fillMaxSize()) {
        TopLine("Accueil", onBack)
        DeveloperTools.CollectionTitle { showDebugMenu = true }
        DeveloperTools.MenuDialog(
            showDebugMenu,
            debugCanRestore,
            { showDebugMenu = false },
            { showDebugMenu = false; onDebugUnlockAll() },
            { showDebugMenu = false; onDebugPreviewAll() },
            { showDebugMenu = false; onDebugRegenerateWallpaper() },
            { showDebugMenu = false; onDebugRunDailyRenewal() },
            { showDebugMenu = false; onDebugRestore() },
            { showDebugMenu = false; showClearCollectionConfirmation = true }
        )
        DeveloperTools.ClearCollectionDialog(
            showClearCollectionConfirmation,
            { showClearCollectionConfirmation = false },
            { showClearCollectionConfirmation = false; onDebugClearCollection() }
        )
        Text("Ma collection", Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("${cards.size} carte${if (cards.size == 1) "" else "s"}", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
        LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(Modifier.fillMaxWidth().padding(bottom = 14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Découvrir 5 nouvelles cartes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Regardez une publicité pour agrandir votre collection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val canOpenBooster = BoosterPolicy.canOfferRewardedAd(boosterProgress.total, boosterProgress.discovered)
                        Button(onClick = requestBooster, enabled = canOpenBooster && !isBusy, modifier = Modifier.fillMaxWidth()) {
                            if (isBusy) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Ouvrir un booster")
                                }
                            } else {
                                Text(if (canOpenBooster) "Ouvrir un booster" else "Collection complète")
                            }
                        }
                        if (BuildConfig.DEBUG) {
                            OutlinedButton(
                                onClick = {
                                    if (!isBusy) {
                                        boosterError = null
                                        isBusy = true
                                        onDebugReward(handleAward)
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("DEBUG · Simuler une récompense") }
                        }
                        boosterError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (cards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(200.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Les cartes que tu choisis garder apparaîtront ici.", textAlign = TextAlign.Center)
                    }
                }
            } else items(cards, key = { it.id }) { fact ->
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

@Composable private fun CardScreen(cards: List<AnecdoteWithCategory>, selectedCardId: String?, homeCardSize: IntSize, onBack: () -> Unit, onOpenExplanation: (AnecdoteWithCategory) -> Unit, onLoadReference: suspend (String) -> String?) {
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val measuredHomeSize = with(density) {
                    if (homeCardSize.width > 0 && homeCardSize.height > 0) {
                        DpSize(homeCardSize.width.toDp(), homeCardSize.height.toDp())
                    } else null
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { cards[it].id }) { page ->
                    cards.getOrNull(page)?.let { fact ->
                        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val requestedWidth = measuredHomeSize?.width ?: (maxWidth - 44.dp).coerceAtLeast(0.dp)
                            val requestedHeight = measuredHomeSize?.height ?: maxHeight
                            val fittedSize = fitCardSize(requestedWidth, requestedHeight, maxWidth, maxHeight)
                            FlipCard(
                                fact = fact,
                                modifier = Modifier.size(fittedSize.width, fittedSize.height),
                                resetKey = pagerState.currentPage,
                                onLoadReference = onLoadReference,
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

private fun fitCardSize(requestedWidth: Dp, requestedHeight: Dp, maxWidth: Dp, maxHeight: Dp): DpSize {
    val scale = if (requestedWidth > 0.dp && requestedHeight > 0.dp) {
        minOf(1f, maxWidth / requestedWidth, maxHeight / requestedHeight)
    } else 1f
    return DpSize(requestedWidth * scale, requestedHeight * scale)
}

private fun fitBoosterDisplaySize(referenceSize: DpSize?, maxWidth: Dp, availableHeight: Dp): DpSize {
    val referenceWidth = referenceSize?.width ?: (maxWidth - 44.dp).coerceAtLeast(0.dp)
    val referenceHeight = referenceSize?.height ?: availableHeight
    return fitCardSize(referenceWidth, referenceHeight, maxWidth, availableHeight)
}

@Composable private fun BoosterScreen(
    award: BoosterAwardResult,
    collection: List<AnecdoteWithCategory>,
    homeCardSize: IntSize,
    onBack: () -> Unit,
    onLoadReference: suspend (String) -> String?,
    onOpenExplanation: (AnecdoteWithCategory) -> Unit
) {
    var newCards by remember { mutableStateOf(award.cards) }
    var awardIdsEncoded by rememberSaveable { mutableStateOf(award.cards.joinToString("|") { it.id }) }
    val awardIds = remember(awardIdsEncoded) { awardIdsEncoded.split('|').filter(String::isNotBlank) }
    val restoredAwardCards = remember(awardIds, collection) { awardIds.mapNotNull { id -> collection.firstOrNull { it.id == id } } }
    val displayCards = if (restoredAwardCards.size == awardIds.size && awardIds.isNotEmpty()) restoredAwardCards else newCards
    var revealedIdsEncoded by rememberSaveable { mutableStateOf("") }
    val revealedIds = remember(revealedIdsEncoded) { revealedIdsEncoded.split('|').filter(String::isNotBlank).toSet() }
    var packOpened by rememberSaveable { mutableStateOf(false) }
    var packFullyOpened by rememberSaveable { mutableStateOf(false) }
    var swipeHintDismissed by rememberSaveable(awardIdsEncoded) { mutableStateOf(false) }
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val openingProgress by animateFloatAsState(
        targetValue = if (packOpened) 1f else 0f,
        animationSpec = if (animationsEnabled && !packFullyOpened) tween(620, easing = FastOutSlowInEasing) else snap(),
        label = "booster-pack-opening"
    )
    val pagerState = rememberPagerState(pageCount = { displayCards.size })

    LaunchedEffect(packOpened, openingProgress) {
        if (packOpened && openingProgress >= 0.999f) packFullyOpened = true
    }

    LaunchedEffect(award, pagerState) {
        newCards = award.cards
        awardIdsEncoded = award.cards.joinToString("|") { it.id }
        revealedIdsEncoded = ""
        packOpened = false
        packFullyOpened = false
        swipeHintDismissed = false
        pagerState.scrollToPage(0)
    }

    Column(Modifier.fillMaxSize()) {
        val pageCounter = if (packFullyOpened && displayCards.isNotEmpty()) "${pagerState.settledPage + 1} / ${displayCards.size}" else null
        TopLine("Ma collection", onBack, trailingText = pageCounter)
        if (displayCards.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current
                    val referenceSize = with(density) {
                        if (homeCardSize.width > 0 && homeCardSize.height > 0) {
                            DpSize(homeCardSize.width.toDp(), homeCardSize.height.toDp())
                        } else null
                    }
                    val previousCardAreaHeight = (maxHeight - 48.dp).coerceAtLeast(0.dp)
                    val fittedSize = fitBoosterDisplaySize(referenceSize, maxWidth, previousCardAreaHeight)
                    val preservedTopSpace = ((previousCardAreaHeight - fittedSize.height) / 2).coerceAtLeast(0.dp)
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(preservedTopSpace))
                        Box(Modifier.fillMaxWidth().height(fittedSize.height), contentAlignment = Alignment.Center) {
                            if (!packFullyOpened) {
                                BoosterPack(
                                    modifier = Modifier
                                        .size(fittedSize.width, fittedSize.height)
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction("Ouvrir le paquet en faisant glisser") {
                                                    if (!packOpened) {
                                                        packOpened = true
                                                        true
                                                    } else false
                                                }
                                            )
                                        },
                                    openingProgress = openingProgress,
                                    gestureEnabled = !packOpened,
                                    onOpen = { packOpened = true }
                                )
                            } else {
                                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { displayCards[it].id }) { page ->
                                    val fact = displayCards[page]
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        RewardRevealCard(
                                            fact = fact,
                                            revealed = fact.id in revealedIds,
                                            modifier = Modifier.size(fittedSize.width, fittedSize.height),
                                            onReveal = { revealedIdsEncoded = (revealedIds + fact.id).joinToString("|") },
                                            onLoadReference = onLoadReference,
                                            onOpenExplanation = { onOpenExplanation(fact) }
                                        )
                                    }
                                }
                                LaunchedEffect(pagerState.settledPage, awardIdsEncoded) {
                                    if (pagerState.settledPage > 0) swipeHintDismissed = true
                                }
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        AnimatedVisibility(
                                            visible = displayCards.size > 1 && pagerState.settledPage == 0 && !swipeHintDismissed,
                                            enter = if (animationsEnabled) fadeIn(tween(180)) else fadeIn(snap()),
                                            exit = if (animationsEnabled) fadeOut(tween(180)) else fadeOut(snap()),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            val hintTransition = rememberInfiniteTransition(label = "booster-swipe-hint")
                                            val hintAlpha by hintTransition.animateFloat(
                                                initialValue = 0.55f,
                                                targetValue = 1f,
                                                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                                                label = "booster-swipe-hint-alpha"
                                            )
                                            val hintTranslation by hintTransition.animateFloat(
                                                initialValue = 0f,
                                                targetValue = 6f,
                                                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                                                label = "booster-swipe-hint-position"
                                            )
                                            Box(
                                                Modifier
                                                    .graphicsLayer {
                                                        alpha = if (animationsEnabled) hintAlpha else 1f
                                                        translationX = if (animationsEnabled) hintTranslation.dp.toPx() else 0f
                                                    }
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), CircleShape)
                                                    .padding(8.dp)
                                                    .clearAndSetSemantics {},
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = BoosterTurquoise
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal const val BOOSTER_PACK_OPEN_THRESHOLD = 0.58f
internal fun boosterPackSwipeOpens(progress: Float): Boolean = progress.coerceIn(0f, 1f) >= BOOSTER_PACK_OPEN_THRESHOLD
private val BoosterIvory = Color(0xFFF7F3E8)
private val BoosterCoral = Color(0xFFFF6347)
private val BoosterTurquoise = Color(0xFF00A995)

private fun tornEdgeOffset(xFraction: Float, amplitudePx: Float): Float {
    val t = xFraction.coerceIn(0f, 1f)
    val envelope = sin(t * PI).toFloat()
    val waviness = sin(t * PI * 13.0).toFloat() * 0.58f + sin(t * PI * 27.0).toFloat() * 0.24f
    return envelope * waviness * amplitudePx
}

private fun Path.appendTornEdge(endX: Float, width: Float, baseY: Float, stepPx: Float, amplitudePx: Float) {
    if (endX <= 0f) return
    var x = 0f
    while (x < endX) {
        lineTo(x, baseY + tornEdgeOffset(x / width, amplitudePx))
        x += stepPx
    }
    lineTo(endX, baseY + tornEdgeOffset(endX / width, amplitudePx))
}

@Composable private fun BoosterPack(
    modifier: Modifier = Modifier,
    openingProgress: Float,
    gestureEnabled: Boolean,
    onOpen: () -> Unit
) {
    var tearProgress by remember { mutableFloatStateOf(0f) }
    val animationScope = rememberCoroutineScope()
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val packWidth = maxWidth
        val packHeight = maxHeight
        val emblemSize = minOf(packWidth * 0.22f, packHeight * 0.12f)
        val density = LocalDensity.current
        val tearShapeProgress = maxOf(tearProgress, openingProgress).coerceIn(0f, 1f)
        val tearBandHeight = with(density) { 42.dp.toPx() }
        val tearAmplitude = with(density) { 2.8.dp.toPx() }
        val tearStep = with(density) { 5.dp.toPx() }
        val cornerRadius = with(density) { 24.dp.toPx() }
        val pouchShape = remember(tearShapeProgress, tearBandHeight, tearAmplitude, tearStep) {
            GenericShape { size, _ ->
                val cutX = size.width * tearShapeProgress
                if (cutX > 0f) {
                    moveTo(0f, 0f)
                    lineTo(0f, tearBandHeight + tornEdgeOffset(0f, tearAmplitude))
                    appendTornEdge(cutX, size.width, tearBandHeight, tearStep, tearAmplitude)
                    lineTo(cutX, 0f)
                    lineTo(size.width - cornerRadius, 0f)
                    quadraticTo(size.width, 0f, size.width, cornerRadius)
                } else {
                    moveTo(cornerRadius, 0f)
                    lineTo(size.width - cornerRadius, 0f)
                    quadraticTo(size.width, 0f, size.width, cornerRadius)
                }
                lineTo(size.width, size.height - cornerRadius)
                quadraticTo(size.width, size.height, size.width - cornerRadius, size.height)
                lineTo(cornerRadius, size.height)
                quadraticTo(0f, size.height, 0f, size.height - cornerRadius)
                if (cutX == 0f) {
                    lineTo(0f, cornerRadius)
                    quadraticTo(0f, 0f, cornerRadius, 0f)
                }
                close()
            }
        }
        val detachedStripShape = remember(tearShapeProgress, tearBandHeight, tearAmplitude, tearStep) {
            GenericShape { size, _ ->
                val cutX = size.width * tearShapeProgress
                moveTo(0f, 0f)
                lineTo(cutX, 0f)
                lineTo(cutX, tearBandHeight + tornEdgeOffset(tearShapeProgress, tearAmplitude))
                var x = cutX
                while (x > 0f) {
                    lineTo(x, tearBandHeight + tornEdgeOffset(x / size.width, tearAmplitude))
                    x -= tearStep
                }
                lineTo(0f, tearBandHeight + tornEdgeOffset(0f, tearAmplitude))
                close()
            }
        }
        Box(Modifier.width(packWidth).height(packHeight), contentAlignment = Alignment.TopCenter) {
            // Cards peek out from behind the pouch as its front panel opens.
            listOf(-7f, 0f, 7f).forEachIndexed { index, rotation ->
                BoosterCardSleeve(
                    Modifier
                        .size(packWidth, packHeight)
                        .align(Alignment.TopCenter)
                        .offset(y = packHeight * (0.13f - openingProgress * 0.47f))
                        .rotate(rotation)
                        .graphicsLayer { alpha = (openingProgress * (0.65f + index * 0.1f)).coerceIn(0f, 1f) }
                )
            }

            // The turquoise inner lining is only visible where the front face has torn away.
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(BoosterTurquoise))

            Box(Modifier.fillMaxSize().clip(pouchShape).background(BoosterCoral).border(1.dp, Color(0xFFCF513E), pouchShape)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(BoosterTurquoise.copy(alpha = 0.25f), radius = size.width * 0.3f, center = Offset(size.width * 0.78f, size.height * 0.63f))
                    drawCircle(BoosterIvory.copy(alpha = 0.18f), radius = size.width * 0.43f, center = Offset(size.width * 0.12f, size.height * 0.84f))
                    drawLine(BoosterIvory.copy(alpha = 0.36f), Offset(size.width * 0.08f, size.height * 0.84f), Offset(size.width * 0.92f, size.height * 0.84f), strokeWidth = 1.dp.toPx())
                }
                Box(Modifier.fillMaxWidth().height(42.dp).align(Alignment.TopCenter).background(BoosterIvory)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height - 1.5.dp.toPx()
                        drawLine(BoosterCoral.copy(alpha = 0.7f), Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                        val grip = Path().apply {
                            moveTo(17.dp.toPx(), 13.dp.toPx())
                            lineTo(23.dp.toPx(), size.height / 2f)
                            lineTo(17.dp.toPx(), size.height - 13.dp.toPx())
                        }
                        drawPath(grip, BoosterTurquoise, style = Stroke(width = 2.dp.toPx()))
                    }
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 54.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("IN ILLUSIO", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.2.sp, color = BoosterIvory, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(emblemSize).clip(RoundedCornerShape(20.dp)).background(BoosterIvory), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.illusio_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("CARTES À DÉCOUVRIR", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = BoosterIvory, fontWeight = FontWeight.SemiBold)
                }
            }

            if (tearShapeProgress > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(detachedStripShape)
                        .graphicsLayer {
                            translationX = tearShapeProgress * 5.dp.toPx()
                            translationY = -tearProgress * 4.dp.toPx() - openingProgress * 34.dp.toPx()
                            rotationZ = tearProgress * 2f + openingProgress * 10f
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .shadow((1f + tearProgress * 2f + openingProgress * 2f).dp, detachedStripShape)
                        .clip(detachedStripShape)
                        .background(BoosterIvory)
                        .border(1.dp, BoosterCoral.copy(alpha = 0.72f), detachedStripShape)
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height - 1.5.dp.toPx()
                        drawLine(BoosterCoral.copy(alpha = 0.7f), Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                    }
                }
            }
            if (gestureEnabled) {
                // Keep the touch target fixed to the package's tear strip even as the torn piece lifts away.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .align(Alignment.TopCenter)
                        .pointerInput(gestureEnabled) {
                            var dragFraction = 0f
                            detectDragGestures(
                                onDragStart = { dragFraction = 0f; tearProgress = 0f },
                                onDragEnd = {
                                    if (boosterPackSwipeOpens(dragFraction)) {
                                        onOpen()
                                    } else {
                                        val start = dragFraction
                                        animationScope.launch {
                                            if (animationsEnabled) {
                                                androidx.compose.animation.core.animate(
                                                    initialValue = start,
                                                    targetValue = 0f,
                                                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                                                ) { value, _ -> tearProgress = value }
                                            } else tearProgress = 0f
                                        }
                                    }
                                },
                                onDragCancel = { tearProgress = 0f }
                            ) { change, dragAmount ->
                                change.consume()
                                dragFraction = (dragFraction + dragAmount.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                tearProgress = dragFraction
                            }
                        }
                )
            }
        }
    }
}

@Composable private fun RewardRevealCard(
    fact: AnecdoteWithCategory,
    revealed: Boolean,
    modifier: Modifier,
    onReveal: () -> Unit,
    onLoadReference: suspend (String) -> String?,
    onOpenExplanation: () -> Unit
) {
    val rotation by animateFloatAsState(if (revealed) 180f else 0f, animationSpec = tween(420, easing = FastOutSlowInEasing), label = "booster-reveal")
    val density = LocalDensity.current.density
    Box(modifier) {
        if (rotation <= 90f) {
            BoosterCardSleeve(
                Modifier.fillMaxSize().graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 16f * density
                    alpha = (1f - rotation / 90f).coerceIn(0f, 1f)
                }.clickable(onClickLabel = "Révéler cette carte", onClick = onReveal)
            )
        } else {
            FlipCard(
                fact = fact,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = ((rotation - 90f) / 90f).coerceIn(0f, 1f)
                },
                onLoadReference = onLoadReference,
                onOpenExplanation = onOpenExplanation
            )
        }
    }
}

@Composable private fun BoosterCardSleeve(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(BoosterIvory).border(1.dp, Color(0xFFCEC9BE), RoundedCornerShape(26.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            drawRoundRect(BoosterCoral.copy(alpha = 0.7f), topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawCircle(BoosterTurquoise.copy(alpha = 0.16f), radius = size.width * 0.19f, center = Offset(size.width * 0.82f, size.height * 0.24f))
            drawCircle(BoosterCoral.copy(alpha = 0.12f), radius = size.width * 0.15f, center = Offset(size.width * 0.18f, size.height * 0.76f))
        }
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("IN ILLUSIO", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = Color(0xFF24323A), fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight(0.72f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(BoosterTurquoise.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.illusio_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HorizontalDivider(color = BoosterCoral.copy(alpha = 0.62f), thickness = 1.dp)
                Text("TOUCHE POUR RÉVÉLER", Modifier.background(BoosterIvory).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp, color = Color(0xFF24323A), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun SettingsScreen(theme: AppTheme, darkTheme: Boolean, refresh: Boolean, savedTextOffset: Float, savedSourceOffset: Float, daily: AnecdoteWithCategory?, reference: String?, wallpaperPreview: WallpaperArtwork?, privacyOptionsRequired: Boolean, onTheme: (AppTheme) -> Unit, onRefresh: (Boolean) -> Unit, onScheduleOffsetSave: (Float, Float) -> Unit, onPersistOffsets: (Float, Float) -> Unit, onResetTextOffset: (Float) -> Unit, onResetSourceOffset: (Float) -> Unit, onResetOffsets: () -> Unit, onPreparePreview: (AnecdoteWithCategory, String?, Boolean, Float, Float) -> Unit, onApply: (AnecdoteWithCategory, String?, Boolean, Float, Float) -> Unit, onPrivacyOptions: () -> Unit, onBack: () -> Unit) {
    var textOffset by remember(daily?.id) { mutableFloatStateOf(savedTextOffset) }
    var sourceOffset by remember(daily?.id) { mutableFloatStateOf(savedSourceOffset) }
    LaunchedEffect(savedTextOffset, savedSourceOffset) {
        textOffset = savedTextOffset
        sourceOffset = savedSourceOffset
    }
    LaunchedEffect(daily?.id, reference, darkTheme, textOffset, sourceOffset) {
        daily?.let { onPreparePreview(it, reference, darkTheme, textOffset, sourceOffset) }
    }
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
            Text("Position du texte", Modifier.padding(start = 24.dp, top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            val unrestrictedRange = -0.15f..0.15f
            val textRange = wallpaperPreview?.textOffsetRange ?: unrestrictedRange
            val displayedTextOffset = textOffset.coerceIn(textRange.start, textRange.endInclusive)
            Slider(
                value = displayedTextOffset,
                onValueChange = { value ->
                    textOffset = value
                    onScheduleOffsetSave(value, sourceOffset)
                },
                valueRange = textRange,
                onValueChangeFinished = { onPersistOffsets(textOffset, sourceOffset) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Plus haut", style = MaterialTheme.typography.labelSmall)
                Text("${(displayedTextOffset * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                Text("Plus bas", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    textOffset = 0f
                    onResetTextOffset(sourceOffset)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            ) { Text("Réinitialiser la position du texte") }
            Text("Position des sources", Modifier.padding(start = 24.dp, top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            val sourceRange = wallpaperPreview?.sourceOffsetRange ?: unrestrictedRange
            val displayedSourceOffset = sourceOffset.coerceIn(sourceRange.start, sourceRange.endInclusive)
            Slider(
                value = displayedSourceOffset,
                onValueChange = { value ->
                    sourceOffset = value
                    onScheduleOffsetSave(textOffset, value)
                },
                valueRange = sourceRange,
                onValueChangeFinished = { onPersistOffsets(textOffset, sourceOffset) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Plus haut", style = MaterialTheme.typography.labelSmall)
                Text("${(displayedSourceOffset * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                Text("Plus bas", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    sourceOffset = 0f
                    onResetSourceOffset(textOffset)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            ) { Text("Réinitialiser la position des sources") }
            TextButton(
                onClick = {
                    textOffset = 0f
                    sourceOffset = 0f
                    onResetOffsets()
                },
                modifier = Modifier.align(Alignment.End).padding(horizontal = 24.dp)
            ) { Text("Réinitialiser la disposition") }
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
                onClick = { onApply(fact, reference, darkTheme, textOffset, sourceOffset) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                enabled = matchingPreview != null
            ) { Text("Appliquer le fond d’écran") }
        }
        Text("In Illusio · Version 0.1.0", Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp), style = MaterialTheme.typography.labelSmall)
        if (privacyOptionsRequired) {
            TextButton(onClick = onPrivacyOptions, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Options de confidentialité")
            }
        }
    }
}
