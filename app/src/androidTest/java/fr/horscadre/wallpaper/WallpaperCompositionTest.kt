package fr.horscadre.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import android.app.WallpaperManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.horscadre.data.local.dao.AnecdoteWithCategory
import fr.horscadre.ui.illustration.CategoryIllustrationRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperCompositionTest {
    @Test
    fun illustrationUsesItsNativeArtboardAspectRatio() {
        val service = WallpaperService(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        val bounds = service.illustrationBounds(1080, 2400, 1.35f)
        assertEquals(1000f / 700f, bounds.width() / bounds.height(), .001f)
        assertTrue("The aspect-correct artwork still spans a substantial part of the display", bounds.height() / 2400f > .4f)
        assertTrue("The illustration stays near its existing upper-screen anchor", bounds.top / 2400f < .04f)
    }

    @Test
    fun textOffsetMovesEditorialCopyButKeepsIllustrationFixedAndInsideBitmap() {
        val service = WallpaperService(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        val fact = AnecdoteWithCategory("offset-test", "histoire", "Histoire", "Test", "Anecdote qui permet de vérifier le déplacement vertical du texte.", "")
        val default = service.render(fact, 1080, 2400, "Auteur · Publication · 2024", darkTheme = true, textVerticalOffset = 0f)
        val shifted = service.render(fact, 1080, 2400, "Auteur · Publication · 2024", darkTheme = true, textVerticalOffset = .15f)
        val topRows = (default.height * .45f).toInt()
        val defaultPixels = IntArray(default.width * topRows)
        val shiftedPixels = IntArray(shifted.width * topRows)
        default.getPixels(defaultPixels, 0, default.width, 0, 0, default.width, topRows)
        shifted.getPixels(shiftedPixels, 0, shifted.width, 0, 0, shifted.width, topRows)
        assertTrue("The illustration should remain unchanged when moving text", defaultPixels.contentEquals(shiftedPixels))

        val lowerTop = (default.height * .5f).toInt()
        val lowerHeight = default.height - lowerTop
        val defaultLower = IntArray(default.width * lowerHeight)
        val shiftedLower = IntArray(shifted.width * lowerHeight)
        default.getPixels(defaultLower, 0, default.width, 0, lowerTop, default.width, lowerHeight)
        shifted.getPixels(shiftedLower, 0, shifted.width, 0, lowerTop, shifted.width, lowerHeight)
        assertTrue("The category, anecdote and reference should move with the slider", !defaultLower.contentEquals(shiftedLower))
        val background = Color.rgb(11, 20, 27)
        assertTrue("Text and decoration must remain inside the bitmap", (0 until shifted.width).all { shifted.getPixel(it, 0) == background && shifted.getPixel(it, shifted.height - 1) == background })
    }

    @Test
    fun illustrationPixelsBeginNearTenPercentAndSpanNearlyHalfTheDisplay() {
        val service = WallpaperService(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        val fact = AnecdoteWithCategory("layout-test", "histoire", "Histoire", "Test", "Accroche courte.", "")
        val bitmap = service.render(fact, 1080, 2400, darkTheme = true)
        val background = Color.rgb(11, 20, 27)
        val populatedRows = (0 until (bitmap.height * .59f).toInt()).filter { y ->
            val pixels = IntArray(bitmap.width)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            pixels.count { it != background } > 8
        }

        assertTrue("Visible illustration pixels should start close to the top of the poster", populatedRows.first().toFloat() / bitmap.height < .11f)
        assertTrue("Visible illustration pixels should reach the lower part of its allocated area", populatedRows.last().toFloat() / bitmap.height > .42f)
        assertTrue("Actual artwork should remain large after preserving its native proportions", (populatedRows.last() - populatedRows.first()).toFloat() / bitmap.height > .36f)
    }

    @Test
    fun regeneratingCreatesFreshPixelsAndUpdatesOnlyTheLockWallpaper() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WallpaperManager.getInstance(context)
        val lockIdBefore = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val homeIdBefore = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val fact = AnecdoteWithCategory("refresh-test", "histoire", "Histoire", "Test", "Affiche fraîche de vérification.", "")
        val service = WallpaperService(context)
        val preview = service.renderForCurrentDisplay(fact, "Référence de contrôle", darkTheme = false)
        val fresh = service.renderForCurrentDisplay(fact, "Référence de contrôle", darkTheme = false)
        assertNotSame(preview.bitmap, fresh.bitmap)
        listOf(.08f, .2f, .35f, .5f, .7f, .9f).forEach { yRatio ->
            val x = fresh.bitmap.width / 2
            val y = (fresh.bitmap.height * yRatio).toInt()
            assertEquals(preview.bitmap.getPixel(x, y), fresh.bitmap.getPixel(x, y))
        }

        service.applyToLockScreen(fresh)

        assertNotEquals(lockIdBefore, manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertEquals(homeIdBefore, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        assertEquals(fresh.screenWidthPx, fresh.bitmap.width)
        assertEquals(fresh.screenHeightPx, fresh.bitmap.height)

        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity()
        try {
            val descriptor = manager.getWallpaperFile(WallpaperManager.FLAG_LOCK)
            assertNotNull("The new lock wallpaper should be readable in the privileged test", descriptor)
            descriptor!!.use {
                val stored = android.graphics.BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
                assertNotNull("Android should expose the newly applied lock bitmap", stored)
                assertEquals(fresh.bitmap.width, stored.width)
                assertEquals(fresh.bitmap.height, stored.height)
                listOf(.08f, .2f, .35f, .5f, .7f, .9f).forEach { yRatio ->
                    val x = fresh.bitmap.width / 2
                    val y = (fresh.bitmap.height * yRatio).toInt()
                    assertEquals("Stored pixels should match the freshly generated bitmap", fresh.bitmap.getPixel(x, y), stored.getPixel(x, y))
                }
                stored.recycle()
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun shortAndLongPosterCopyFitsInBothThemesAtEmulatorResolution() {
        val service = WallpaperService(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        val samples = listOf(
            AnecdoteWithCategory("short-test", "histoire", "Histoire", "Texte court", "Un texte court de vérification.", ""),
            AnecdoteWithCategory(
                "long-test", "economie", "Économie", "Texte long",
                "Texte long de vérification, avec plusieurs lignes pour contrôler la lisibilité et l’espace disponible. ".repeat(3),
                ""
            )
        )
        val references = listOf("Auteur test · Publication test · 2024", "Auteur test · Publication scientifique de vérification · 2024. ".repeat(2))

        samples.forEachIndexed { index, fact ->
            listOf(true, false).forEach { darkTheme ->
                val bitmap = service.render(fact, 1080, 2400, references[index], darkTheme)
                val expectedBackground = if (darkTheme) Color.rgb(11, 20, 27) else Color.rgb(255, 252, 246)
                assertEquals(1080, bitmap.width)
                assertEquals(2400, bitmap.height)
                assertEquals(expectedBackground, bitmap.getPixel(0, 0))
                assertTrue("The category illustration should occupy the upper half", countNonBackground(bitmap, expectedBackground, .08f, .55f) > 20_000)
                assertTrue("The anecdote and reference should remain visible below the illustration", countNonBackground(bitmap, expectedBackground, .55f, .98f) > 1_000)
            }
        }
    }

    private fun countNonBackground(bitmap: Bitmap, background: Int, topRatio: Float, bottomRatio: Float): Int {
        val top = (bitmap.height * topRatio).toInt()
        val bottom = (bitmap.height * bottomRatio).toInt()
        val pixels = IntArray(bitmap.width * (bottom - top))
        bitmap.getPixels(pixels, 0, bitmap.width, 0, top, bitmap.width, bottom - top)
        return pixels.count { it != background }
    }
}
