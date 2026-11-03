package fr.inillusio.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.inillusio.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesTest {
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
