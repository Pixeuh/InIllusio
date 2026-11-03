package fr.horscadre.ui.illustration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryIllustrationTest {
    @Test fun everyCategoryKeepsVisibleArtworkInsideTheResponsiveArtboardInBothThemes() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val artboards = listOf(360 to 252, 600 to 420)

        CategoryIllustrationRenderer.categoryIds.forEach { categoryId ->
            val colors = DisciplinePalettes.forCategory(context, categoryId)
            listOf(
                CategoryIllustrationRenderer.Palette(colors.primary, colors.secondary, Color.rgb(247, 243, 232), Color.rgb(36, 51, 59)),
                CategoryIllustrationRenderer.Palette(colors.primary, colors.secondary, Color.rgb(11, 20, 27), Color.rgb(236, 232, 223))
            ).forEach { palette ->
                artboards.forEach { (width, height) ->
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    CategoryIllustrationRenderer.draw(categoryId, Canvas(bitmap), RectF(0f, 0f, width.toFloat(), height.toFloat()), palette)
                    val visible = bitmap.visibleBounds()
                    assertTrue("$categoryId has visible ink with ${width}x$height artboard", visible.width() > width * .60f)
                    assertTrue("$categoryId has visible ink across the hero height with ${width}x$height artboard", visible.height() > height * .35f)
                    assertTrue("$categoryId stays clipped to its artboard with ${width}x$height artboard", visible.left >= 0 && visible.top >= 0 && visible.right <= width && visible.bottom <= height)
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun everyCategoryHasItsOwnNonEmptyVectorArtwork() {
        assertEquals(8, CategoryIllustrationRenderer.categoryIds.size)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val themedBitmaps = CategoryIllustrationRenderer.categoryIds.map { id ->
            val colors = DisciplinePalettes.forCategory(context, id)
            listOf(
                CategoryIllustrationRenderer.Palette(colors.primary, colors.secondary, Color.rgb(247, 243, 232), Color.rgb(36, 51, 59)),
                CategoryIllustrationRenderer.Palette(colors.primary, colors.secondary, Color.rgb(11, 20, 27), Color.rgb(236, 232, 223))
            ).map { palette ->
                Bitmap.createBitmap(600, 420, Bitmap.Config.ARGB_8888).also { bitmap ->
                    CategoryIllustrationRenderer.draw(id, Canvas(bitmap), RectF(0f, 0f, 600f, 420f), palette)
                    assertFalse("Artwork for $id is empty", bitmap.allPixelsTransparent())
                }
            }
        }
        themedBitmaps.forEachIndexed { index, bitmaps ->
            bitmaps.forEachIndexed { themeIndex, bitmap ->
                assertTrue("${CategoryIllustrationRenderer.categoryIds.elementAt(index)} contains primary palette color", bitmap.countColor(DisciplinePalettes.forCategory(context, CategoryIllustrationRenderer.categoryIds.elementAt(index)).primary) > 0)
                assertTrue("${CategoryIllustrationRenderer.categoryIds.elementAt(index)} contains secondary palette color", bitmap.countColor(DisciplinePalettes.forCategory(context, CategoryIllustrationRenderer.categoryIds.elementAt(index)).secondary) > 0)
                if (themeIndex == 1) assertFalse("Theme palettes should adapt the illustration", bitmaps[0].sameAs(bitmap))
            }
        }
        CategoryIllustrationRenderer.categoryIds.forEachIndexed { index, _ ->
            CategoryIllustrationRenderer.categoryIds.drop(index + 1).forEach { other ->
                val otherIndex = CategoryIllustrationRenderer.categoryIds.indexOf(other)
                assertFalse("Two category illustrations are identical", themedBitmaps[index][0].sameAs(themedBitmaps[otherIndex][0]))
            }
        }
    }

    private fun Bitmap.allPixelsTransparent(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.all { Color.alpha(it) == 0 }
    }

    private fun Bitmap.countColor(color: Int): Int {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.count { it == color }
    }

    private fun Bitmap.visibleBounds(): android.graphics.Rect {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            if (Color.alpha(pixel) > 0) {
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        return android.graphics.Rect(left, top, right + 1, bottom + 1)
    }
}
