package fr.inillusio.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.inillusio.data.preferences.UserPreferences
import fr.inillusio.data.preferences.WallpaperDisplayMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UserPreferencesTest {
    @Test
    fun manualWallpaperModeAndSelectedCardArePersistedAndCleared() = runBlocking {
        val preferences = UserPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        val initial = preferences.state.first()
        try {
            preferences.setManualWallpaper("fiction-pinned", WallpaperDisplayMode.PINNED, LocalDate.of(2026, 11, 5))
            val pinned = preferences.state.first()
            assertEquals(WallpaperDisplayMode.PINNED, pinned.wallpaperDisplayMode)
            assertEquals("fiction-pinned", pinned.selectedWallpaperId)
            assertNull(pinned.temporaryWallpaperDate)

            preferences.setManualWallpaper("fiction-temporary", WallpaperDisplayMode.UNTIL_NEXT_RENEWAL, LocalDate.of(2026, 11, 5))
            val temporary = preferences.state.first()
            assertEquals(WallpaperDisplayMode.UNTIL_NEXT_RENEWAL, temporary.wallpaperDisplayMode)
            assertEquals("fiction-temporary", temporary.selectedWallpaperId)
            assertEquals(LocalDate.of(2026, 11, 5), temporary.temporaryWallpaperDate)

            preferences.clearManualWallpaper()
            val cleared = preferences.state.first()
            assertEquals(WallpaperDisplayMode.AUTOMATIC, cleared.wallpaperDisplayMode)
            assertNull(cleared.selectedWallpaperId)
            assertNull(cleared.temporaryWallpaperDate)
        } finally {
            if (initial.selectedWallpaperId != null && initial.wallpaperDisplayMode != WallpaperDisplayMode.AUTOMATIC) {
                preferences.setManualWallpaper(initial.selectedWallpaperId, initial.wallpaperDisplayMode, initial.temporaryWallpaperDate)
            } else preferences.clearManualWallpaper()
            preferences.setWallpaperRefresh(initial.wallpaperRefreshEnabled)
        }
    }

    @Test
    fun wallpaperTextAndSourceOffsetsArePersistedIndependentlyAndResettable() = runBlocking {
        val preferences = UserPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        val initial = preferences.state.first()
        try {
            preferences.setWallpaperOffsets(.12f, -.08f)
            assertEquals(.12f, preferences.state.first().wallpaperTextOffset, .001f)
            assertEquals(-.08f, preferences.state.first().wallpaperSourceOffset, .001f)
            preferences.setWallpaperTextOffset(1f)
            assertEquals(.15f, preferences.state.first().wallpaperTextOffset, .001f)
            assertEquals(-.08f, preferences.state.first().wallpaperSourceOffset, .001f)
            preferences.setWallpaperSourceOffset(-1f)
            assertEquals(-.15f, preferences.state.first().wallpaperSourceOffset, .001f)
            preferences.resetWallpaperTextOffset()
            assertEquals(0f, preferences.state.first().wallpaperTextOffset, .001f)
            assertEquals(-.15f, preferences.state.first().wallpaperSourceOffset, .001f)
            preferences.resetWallpaperSourceOffset()
            assertEquals(0f, preferences.state.first().wallpaperSourceOffset, .001f)
            preferences.setWallpaperOffsets(.1f, -.1f)
            preferences.resetWallpaperOffsets()
            assertEquals(0f, preferences.state.first().wallpaperTextOffset, .001f)
            assertEquals(0f, preferences.state.first().wallpaperSourceOffset, .001f)
        } finally {
            preferences.setWallpaperOffsets(initial.wallpaperTextOffset, initial.wallpaperSourceOffset)
        }
    }
}
