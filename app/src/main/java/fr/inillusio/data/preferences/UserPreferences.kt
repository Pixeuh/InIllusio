package fr.inillusio.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import fr.inillusio.domain.DailySelectionPolicy
import java.time.LocalDate

private val Context.preferences by preferencesDataStore(name = "inillusio_preferences")
enum class AppTheme { SYSTEM, LIGHT, DARK }
const val MIN_WALLPAPER_TEXT_OFFSET = -0.15f
const val MAX_WALLPAPER_TEXT_OFFSET = 0.15f
const val MIN_WALLPAPER_SOURCE_OFFSET = -0.15f
const val MAX_WALLPAPER_SOURCE_OFFSET = 0.15f
data class PreferencesState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val wallpaperRefreshEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val wallpaperTextOffset: Float = 0f,
    val wallpaperSourceOffset: Float = 0f
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val wallpaper = booleanPreferencesKey("wallpaper_refresh_enabled")
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val wallpaperTextOffset = floatPreferencesKey("wallpaper_text_vertical_offset")
        val wallpaperSourceOffset = floatPreferencesKey("wallpaper_source_vertical_offset")
        val legacyCollectionDisplayMode = stringPreferencesKey("collection_display_mode")
        val dailyDate = stringPreferencesKey("daily_date")
        val dailyId = stringPreferencesKey("daily_id")
    }
    val state = context.preferences.data
        .onStart {
            context.preferences.edit {
                it.remove(Keys.legacyCollectionDisplayMode)
                // The previous shared offset moved both copy and sources together.
                // Seed the new source preference from it once to preserve that layout.
                if (Keys.wallpaperSourceOffset !in it) {
                    it[Keys.wallpaperSourceOffset] = it[Keys.wallpaperTextOffset] ?: 0f
                }
            }
        }
        .map { p -> PreferencesState(
        theme = runCatching { AppTheme.valueOf(p[Keys.theme] ?: "SYSTEM") }.getOrDefault(AppTheme.SYSTEM),
        wallpaperRefreshEnabled = p[Keys.wallpaper] ?: false,
        onboardingComplete = p[Keys.onboarding] ?: false,
        wallpaperTextOffset = (p[Keys.wallpaperTextOffset] ?: 0f).coerceIn(MIN_WALLPAPER_TEXT_OFFSET, MAX_WALLPAPER_TEXT_OFFSET),
        wallpaperSourceOffset = (p[Keys.wallpaperSourceOffset] ?: 0f).coerceIn(MIN_WALLPAPER_SOURCE_OFFSET, MAX_WALLPAPER_SOURCE_OFFSET)
    ) }
    suspend fun setTheme(theme: AppTheme) { context.preferences.edit { it[Keys.theme] = theme.name } }
    suspend fun setWallpaperRefresh(enabled: Boolean) { context.preferences.edit { it[Keys.wallpaper] = enabled } }
    suspend fun setWallpaperTextOffset(offset: Float) {
        context.preferences.edit { it[Keys.wallpaperTextOffset] = offset.coerceIn(MIN_WALLPAPER_TEXT_OFFSET, MAX_WALLPAPER_TEXT_OFFSET) }
    }
    suspend fun setWallpaperSourceOffset(offset: Float) {
        context.preferences.edit { it[Keys.wallpaperSourceOffset] = offset.coerceIn(MIN_WALLPAPER_SOURCE_OFFSET, MAX_WALLPAPER_SOURCE_OFFSET) }
    }
    suspend fun setWallpaperOffsets(textOffset: Float, sourceOffset: Float) {
        context.preferences.edit {
            it[Keys.wallpaperTextOffset] = textOffset.coerceIn(MIN_WALLPAPER_TEXT_OFFSET, MAX_WALLPAPER_TEXT_OFFSET)
            it[Keys.wallpaperSourceOffset] = sourceOffset.coerceIn(MIN_WALLPAPER_SOURCE_OFFSET, MAX_WALLPAPER_SOURCE_OFFSET)
        }
    }
    suspend fun resetWallpaperTextOffset() { context.preferences.edit { it[Keys.wallpaperTextOffset] = 0f } }
    suspend fun resetWallpaperSourceOffset() { context.preferences.edit { it[Keys.wallpaperSourceOffset] = 0f } }
    suspend fun resetWallpaperOffsets() {
        context.preferences.edit {
            it[Keys.wallpaperTextOffset] = 0f
            it[Keys.wallpaperSourceOffset] = 0f
        }
    }
    suspend fun completeOnboarding() { context.preferences.edit { it[Keys.onboarding] = true } }
    suspend fun dailySelection(today: LocalDate, catalogIds: List<String>, candidateIds: List<String>): String? {
        if (catalogIds.isEmpty() || candidateIds.isEmpty()) return null
        var selection: String? = null
        context.preferences.edit { prefs ->
            selection = DailySelectionPolicy.select(
                today.toString(), prefs[Keys.dailyDate], prefs[Keys.dailyId], catalogIds, candidateIds
            )
            if (prefs[Keys.dailyDate] != today.toString() || prefs[Keys.dailyId] !in catalogIds) {
                selection?.let {
                    prefs[Keys.dailyDate] = today.toString()
                    prefs[Keys.dailyId] = it
                }
            }
        }
        return selection
    }
}
