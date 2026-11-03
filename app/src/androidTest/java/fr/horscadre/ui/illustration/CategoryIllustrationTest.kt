package fr.horscadre.ui.illustration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryIllustrationTest {
    @Test fun everyCategoryHasItsOwnNonEmptyVectorArtwork() {
        assertEquals(8, CategoryIllustrationRenderer.categoryIds.size)
        val palettes = listOf(
            CategoryIllustrationRenderer.Palette(Color.rgb(255, 99, 71), Color.rgb(0, 191, 174), Color.rgb(247, 243, 232), Color.rgb(36, 51, 59)),
            CategoryIllustrationRenderer.Palette(Color.rgb(255, 99, 71), Color.rgb(0, 191, 174), Color.rgb(11, 20, 27), Color.rgb(236, 232, 223))
        )
        val themedBitmaps = palettes.map { palette ->
            CategoryIllustrationRenderer.categoryIds.map { id ->
                Bitmap.createBitmap(600, 420, Bitmap.Config.ARGB_8888).also { bitmap ->
                    CategoryIllustrationRenderer.draw(id, Canvas(bitmap), RectF(0f, 0f, 600f, 420f), palette)
                    assertFalse("Artwork for $id is empty", bitmap.allPixelsTransparent())
                }
            }
        }
        themedBitmaps.forEach { bitmaps ->
            bitmaps.forEachIndexed { index, bitmap ->
                bitmaps.drop(index + 1).forEach { other -> assertFalse("Two category illustrations are identical", bitmap.sameAs(other)) }
            }
        }
        themedBitmaps[0].zip(themedBitmaps[1]).forEach { (dark, light) -> assertFalse("Theme palettes should adapt the illustration", dark.sameAs(light)) }
    }

    private fun Bitmap.allPixelsTransparent(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.all { Color.alpha(it) == 0 }
    }
}
