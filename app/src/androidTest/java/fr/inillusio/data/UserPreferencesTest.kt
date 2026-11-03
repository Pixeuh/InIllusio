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
    fun wallpaperTextOffsetIsPersistedAndResettable() = runBlocking {
        val preferences = UserPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = preferences.state.first().wallpaperTextOffset
        try {
            preferences.setWallpaperTextOffset(.12f)
            assertEquals(.12f, preferences.state.first().wallpaperTextOffset, .001f)
            preferences.setWallpaperTextOffset(1f)
            assertEquals(.15f, preferences.state.first().wallpaperTextOffset, .001f)
            preferences.resetWallpaperTextOffset()
            assertEquals(0f, preferences.state.first().wallpaperTextOffset, .001f)
        } finally {
            preferences.setWallpaperTextOffset(original)
        }
    }
}
