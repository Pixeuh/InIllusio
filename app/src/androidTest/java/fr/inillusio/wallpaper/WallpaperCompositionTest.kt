package fr.inillusio.wallpaper

import android.graphics.Bitmap
import android.graphics.Color
import android.app.WallpaperManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import fr.inillusio.ui.illustration.CategoryIllustrationRenderer
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
        val displays = listOf(1080 to 2400, 1080 to 2340, 1440 to 3200, 1080 to 1920)
        displays.forEach { (width, height) ->
            val header = service.headerLayout(width, height, "Psychologie sociale")
            val bounds = service.illustrationBounds(width, height, 1f, header.bottom + height * .037f)
            assertTrue("Header keeps an adaptive top safety margin at ${width}x$height", header.top / height.toFloat() >= .08f)
            assertEquals("Native artwork proportions at ${width}x$height", 1000f / 700f, bounds.width() / bounds.height(), .001f)
            assertTrue("Artwork stays inside the screen width at ${width}x$height", bounds.left >= 0f && bounds.right <= width)
            assertEquals("Artwork remains horizontally centered at ${width}x$height", width / 2f, bounds.centerX(), .5f)
            assertTrue("Full illustration remains substantial at ${width}x$height", bounds.width() / width.toFloat() > .99f)
            assertTrue("Illustration starts below the measured header and separator at ${width}x$height", bounds.top > header.bottom)
            assertTrue("Artwork height adapts to the display ratio at ${width}x$height", bounds.height() / height.toFloat() in .30f.. .41f)
        }
    }

    @Test
    fun headerAndIllustrationHaveARealGapAcrossScreenRatios() {
        val service = WallpaperService(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
        val categories = listOf(
            "Sociologie", "Anthropologie", "Psychologie sociale", "Histoire",
            "Linguistique", "Économie", "Philosophie", "Géographie"
        )
        listOf(1080 to 2400, 1080 to 2340, 1440 to 3200, 1080 to 1920).forEach { (width, height) ->
            val background = Color.rgb(11, 20, 27)
            categories.forEach { category ->
                val fact = AnecdoteWithCategory("header-test", "psychologie_sociale", category, "Test", "Accroche courte.", "")
                val header = service.headerLayout(width, height, category)
                val bounds = service.illustrationBounds(width, height, 1f, header.bottom + height * .037f)
                assertTrue("Header remains below the screen's safety area ($category, ${width}x$height)", header.top >= height * .08f)
                assertTrue("Header columns must not collide ($category, ${width}x$height)", header.categoryEnd + width * .025f <= header.brandLeft)
                assertTrue("Measured header must end before the artwork ($category, ${width}x$height)", bounds.top > header.bottom)

                val bitmap = service.render(fact, width, height, darkTheme = true)
                val headerTop = (height * .015f).toInt()
                val headerBottom = (header.bottom + height * .01f).toInt()
                val headerPixels = IntArray(width * (headerBottom - headerTop))
                bitmap.getPixels(headerPixels, 0, width, 0, headerTop, width, headerBottom - headerTop)
                assertTrue("Discipline and brand header should be drawn ($category, ${width}x$height)", headerPixels.any { it != background })

                val gapY = (header.bottom + height * .02f).toInt()
                val gapPixels = IntArray(width)
                bitmap.getPixels(gapPixels, 0, width, 0, gapY, width, 1)
                assertTrue("A clear band must separate header from artwork ($category, ${width}x$height)", gapPixels.all { it == background })
                assertTrue("The artwork remains fully within the bitmap ($category, ${width}x$height)", bounds.top >= 0 && bounds.bottom < height)
                bitmap.recycle()
            }
        }
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
        assertTrue("The anecdote should move with its slider", !defaultLower.contentEquals(shiftedLower))
        val background = Color.rgb(11, 20, 27)
        assertTrue("Text and decoration must remain inside the bitmap", (0 until shifted.width).all { shifted.getPixel(it, 0) == background && shifted.getPixel(it, shifted.height - 1) == background })
    }

    @Test
    fun textAndSourceOffsetsMoveOnlyTheirOwnBlocksAndKeepSafeRanges() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val service = WallpaperService(context)
        val fact = AnecdoteWithCategory("independent-offset-test", "histoire", "Histoire", "Test", "Accroche courte de contrôle.", "")
        val citation = "Auteur · Publication scientifique · 2024"
        val base = service.render(fact, 1080, 2400, citation, darkTheme = true)
        val textMoved = service.render(fact, 1080, 2400, citation, darkTheme = true, textVerticalOffset = .06f)
        val sourceMoved = service.render(fact, 1080, 2400, citation, darkTheme = true, sourceVerticalOffset = -.06f)

        assertRowsEqual(base, textMoved, 0f, .52f, "Text adjustment must leave the illustration unchanged")
        assertRowsEqual(base, textMoved, .86f, .99f, "Text adjustment must leave the source block unchanged")
        assertRowsEqual(base, sourceMoved, 0f, .69f, "Source adjustment must leave the illustration and anecdote unchanged")
        assertRowsDiffer(base, sourceMoved, .72f, .98f, "Source label, divider and reference should move together")

        val longFact = fact.copy(hook = "Une anecdote assez longue pour occuper plusieurs lignes et tester les limites de déplacement. ".repeat(3))
        val longReference = "Auteur collectif · Publication scientifique avec une référence bibliographique longue · 2024. ".repeat(2)
        val artwork = service.renderForCurrentDisplay(longFact, longReference, darkTheme = false, textVerticalOffset = .15f, sourceVerticalOffset = -.15f)
        assertTrue("The valid text range should be ordered", artwork.textOffsetRange.start <= artwork.textOffsetRange.endInclusive)
        assertTrue("The valid source range should be ordered", artwork.sourceOffsetRange.start <= artwork.sourceOffsetRange.endInclusive)
        assertTrue("Text positions should remain inside the supported preference range", artwork.textOffsetRange.start >= -.15f && artwork.textOffsetRange.endInclusive <= .15f)
        assertTrue("Source positions should remain inside the supported preference range", artwork.sourceOffsetRange.start >= -.15f && artwork.sourceOffsetRange.endInclusive <= .15f)
    }

    @Test
    fun illustrationPixelsBeginNearTheTopAndFillTheUpperArtworkArea() {
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
        assertTrue("Visible illustration pixels should reach the lower part of its allocated area", populatedRows.last().toFloat() / bitmap.height > .28f)
        assertTrue("Actual artwork should remain large after preserving its native proportions", (populatedRows.last() - populatedRows.first()).toFloat() / bitmap.height > .24f)
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

    private fun assertRowsEqual(first: Bitmap, second: Bitmap, topRatio: Float, bottomRatio: Float, message: String) {
        val top = (first.height * topRatio).toInt()
        val bottom = (first.height * bottomRatio).toInt()
        val firstPixels = IntArray(first.width * (bottom - top))
        val secondPixels = IntArray(first.width * (bottom - top))
        first.getPixels(firstPixels, 0, first.width, 0, top, first.width, bottom - top)
        second.getPixels(secondPixels, 0, second.width, 0, top, second.width, bottom - top)
        assertTrue(message, firstPixels.contentEquals(secondPixels))
    }

    private fun assertRowsDiffer(first: Bitmap, second: Bitmap, topRatio: Float, bottomRatio: Float, message: String) {
        val top = (first.height * topRatio).toInt()
        val bottom = (first.height * bottomRatio).toInt()
        val firstPixels = IntArray(first.width * (bottom - top))
        val secondPixels = IntArray(first.width * (bottom - top))
        first.getPixels(firstPixels, 0, first.width, 0, top, first.width, bottom - top)
        second.getPixels(secondPixels, 0, second.width, 0, top, second.width, bottom - top)
        assertTrue(message, !firstPixels.contentEquals(secondPixels))
    }
}
