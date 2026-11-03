package fr.inillusio.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0B141B)
private val Paper = Color(0xFFFFFCF6)
private val WarmWhite = Color(0xFFF7F3E8)
private val Coral = Color(0xFFFF6347)
private val Teal = Color(0xFF00BFAE)

private val LightColors = lightColorScheme(primary = Coral, onPrimary = Color.White, secondary = Teal, onSecondary = Ink, background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink, surfaceVariant = Color(0xFFECE8DF))
private val DarkColors = darkColorScheme(primary = Coral, onPrimary = Ink, secondary = Teal, onSecondary = Ink, background = Ink, onBackground = WarmWhite, surface = Color(0xFF14232C), onSurface = WarmWhite, surfaceVariant = Color(0xFF24333B))

@Composable fun InIllusioTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
}
