package fr.inillusio.ui.illustration

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
    @Test fun archaeologyLeavesTheLowerOutsideAreaTransparent() {
        val primary = Color.rgb(229, 138, 60)
        val secondary = Color.rgb(101, 68, 51)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.ARCHAEOLOGY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(primary, secondary, Color.DKGRAY, Color.WHITE)
        )

        for (y in 525 until 690 step 11) {
            listOf(250, 750, 850).forEach { x ->
                assertEquals("Archaeology's lower outside area remains transparent at $x,$y", 0, Color.alpha(bitmap.getPixel(x, y)))
            }
        }
        for (row in 0 until 5) for (column in 0 until 6) {
            val point = (65 + column * 17) to (580 + row * 17)
            val expected = if ((row + column) % 2 == 0) primary else secondary
            assertEquals("Archaeology keeps the shared 6x5 dot motif at row $row column $column", expected, bitmap.getPixel(point.first, point.second))
        }
        assertEquals("Archaeology has no extra sixth dot row", 0, Color.alpha(bitmap.getPixel(65, 665)))
        val formerArrow = pointAfterCategoryScale(797f, 223f, 1.05f)
        assertEquals("Archaeology's removed tool/arrow leaves no replacement mark", 0, Color.alpha(bitmap.getPixel(formerArrow.first, formerArrow.second)))
        bitmap.recycle()
    }

    @Test fun demographyKeepsEveryHeadVisibleInFrontOfBodiesAndContours() {
        val primary = Color.rgb(37, 131, 186)
        val secondary = Color.rgb(121, 183, 122)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.DEMOGRAPHY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(primary, secondary, Color.DKGRAY, Color.WHITE)
        )
        val rows = listOf(
            Triple(437.5f, 135f, 0.92f),
            Triple(312.5f, 290f, 0.82f),
            Triple(187.5f, 445f, 0.72f)
        )
        rows.forEachIndexed { row, (left, y, scale) ->
            val count = 2 + row * 2
            assertTrue("Every demography body fits horizontally", left - 52f * scale >= 0f && left + (count - 1) * 125f + 52f * scale <= 1000f)
            assertTrue("Every demography body fits vertically", y - 27f * scale >= 0f && y + 103f * scale <= 700f)
            assertTrue("Adjacent demography figures have separate body silhouettes", 125f > 104f * scale)
            if (row > 0) {
                val previous = rows[row - 1]
                assertTrue("Adjacent demography rows have vertical clearance", y - previous.second > 103f * previous.third + 27f * scale)
            }
            repeat(count) { i ->
                val x = left + i * 125f
                val expected = if ((i + row) % 2 == 0) primary else secondary
                val screenX = (500f + (x - 500f) * 1.05f).toInt()
                val screenY = (350f + (y - 350f) * 1.05f).toInt()
                val sampleOffset = (15f * scale * 1.05f).toInt()
                assertEquals("Head top is visible for demography figure row $row index $i", expected, bitmap.getPixel(screenX, screenY - sampleOffset))
                assertEquals("Head bottom is visible for demography figure row $row index $i", expected, bitmap.getPixel(screenX, screenY + sampleOffset))
            }
        }
        bitmap.recycle()
    }

    @Test fun ethologyClearlySeparatesHumanAndMouseHeadFacingEachOther() {
        val brown = Color.rgb(121, 81, 62)
        val green = Color.rgb(118, 164, 108)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.ETHOLOGY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(brown, green, Color.DKGRAY, Color.WHITE)
        )
        val humanSample = pointAfterCategoryScale(260f, 250f, 1.04f)
        val mouseSample = pointAfterCategoryScale(750f, 290f, 1.04f)
        assertEquals("Human silhouette occupies the left profile", brown, bitmap.getPixel(humanSample.first, humanSample.second))
        assertEquals("Mouse head occupies the right profile", green, bitmap.getPixel(mouseSample.first, mouseSample.second))
        val belowMouseHead = pointAfterCategoryScale(750f, 420f, 1.04f)
        assertEquals("Mouse has no neck or bust below its head", 0, Color.alpha(bitmap.getPixel(belowMouseHead.first, belowMouseHead.second)))
        bitmap.recycle()
    }

    @Test fun socialPsychologyCentralFigureHasNoPointOrFacialStrokes() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val colors = DisciplinePalettes.forCategory(context, CategoryIllustrationRenderer.SOCIAL_PSYCHOLOGY)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.SOCIAL_PSYCHOLOGY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(colors.primary, colors.secondary, Color.DKGRAY, Color.WHITE)
        )
        val headCenter = pointAfterCategoryScale(485f, 205f, 1.08f)
        assertEquals("Central social psychology head has no overlaid point", colors.primary, bitmap.getPixel(headCenter.first, headCenter.second))
        val screenCenter = pointAfterCategoryScale(485f, 205f, 1.08f)
        for (y in screenCenter.second - 35..screenCenter.second + 35) for (x in screenCenter.first - 35..screenCenter.first + 35) {
            val dx = x - screenCenter.first
            val dy = y - screenCenter.second
            if (dx * dx + dy * dy <= 34 * 34) {
                assertFalse("Pink central figure has no dark facial strokes", bitmap.getPixel(x, y) == Color.DKGRAY)
            }
        }
        bitmap.recycle()
    }

    @Test fun sociologyPortraitsNoLongerHaveTheTwoFacialStrokes() {
        val primary = Color.rgb(255, 99, 71)
        val secondary = Color.rgb(0, 169, 149)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.SOCIOLOGY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(primary, secondary, Color.DKGRAY, Color.WHITE)
        )
        val nodes = listOf(220f to 250f, 380f to 150f, 560f to 245f, 755f to 155f, 790f to 340f, 555f to 390f, 330f to 385f)
        nodes.forEachIndexed { index, (x, y) ->
            val size = if (index == 2) 1.15f else .82f
            val fill = if (index == 2 || index == 5) primary else Color.DKGRAY
            listOf(x - 6f * size to y + size, x + 11f * size to y + 7.5f * size).forEach { (sampleX, sampleY) ->
                val point = pointAfterCategoryScale(sampleX, sampleY, 1.08f)
                assertEquals("Sociology face remains a clean silhouette", fill, bitmap.getPixel(point.first, point.second))
            }
        }
        val whitePoint = pointAfterCategoryScale(560f, 280.5f, 1.08f)
        assertEquals("Central sociology marker is centered in the neck", Color.WHITE, bitmap.getPixel(whitePoint.first, whitePoint.second))
        bitmap.recycle()
    }

    @Test fun sociologyMiddleConnectionsMeetTheCentralNeckMarker() {
        val primary = Color.rgb(255, 99, 71)
        val secondary = Color.rgb(0, 169, 149)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.SOCIOLOGY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(primary, secondary, Color.DKGRAY, Color.WHITE)
        )

        val incomingGreen = pointAfterCategoryScale(553f, 280.39f, 1.08f)
        val outgoingRed = pointAfterCategoryScale(566f, 282.71f, 1.08f)
        val marker = pointAfterCategoryScale(560f, 280.5f, 1.08f)
        val headCenter = pointAfterCategoryScale(560f, 245f, 1.08f)
        val redEdgeOnTorso = pointAfterCategoryScale(576.3f, 288.5f, 1.08f)
        assertEquals("Green edge reaches the central neck", secondary, bitmap.getPixel(incomingGreen.first, incomingGreen.second))
        assertEquals("Red edge leaves the same central neck", primary, bitmap.getPixel(outgoingRed.first, outgoingRed.second))
        val outlinedRed = bitmap.getPixel(redEdgeOnTorso.first, redEdgeOnTorso.second)
        assertTrue("Red edge remains distinct on the orange torso", Color.red(outlinedRed) < Color.red(primary))
        assertEquals("The white marker overlays the edge junction", Color.WHITE, bitmap.getPixel(marker.first, marker.second))
        assertEquals("No network line is drawn across the orange head", primary, bitmap.getPixel(headCenter.first, headCenter.second))

        bitmap.recycle()
    }
    @Test fun sociologyLowerConnectionsReachTheCentralNeckMarkerWithoutCrossingTheFigure() {
        val primary = Color.rgb(255, 99, 71)
        val secondary = Color.rgb(0, 169, 149)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.SOCIOLOGY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(primary, secondary, Color.DKGRAY, Color.WHITE)
        )

        val leftLine = pointAfterCategoryScale(545f, 414.78f, 1.08f)
        val rightLine = pointAfterCategoryScale(565f, 412.87f, 1.08f)
        val marker = pointAfterCategoryScale(555f, 415f, 1.08f)
        val headCenter = pointAfterCategoryScale(555f, 390f, 1.08f)
        assertEquals("Existing lower-left edge remains continuous to the neck gap", primary, bitmap.getPixel(leftLine.first, leftLine.second))
        assertEquals("Existing right edge remains continuous to the neck gap", secondary, bitmap.getPixel(rightLine.first, rightLine.second))
        assertEquals("Central white marker overlays the edge junction", Color.WHITE, bitmap.getPixel(marker.first, marker.second))
        assertEquals("No network edge is drawn over the central portrait", primary, bitmap.getPixel(headCenter.first, headCenter.second))
        bitmap.recycle()
    }
    @Test fun cognitivePsychologyCentralFaceHasNoPupilOrSmileMark() {
        val primary = Color.rgb(196, 63, 120)
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.COGNITIVE_PSYCHOLOGY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(primary, Color.rgb(39, 88, 68), Color.DKGRAY, Color.WHITE)
        )
        val eyeCenter = pointAfterCategoryScale(500f, 336f, 1.08f)
        val formerSmileCenter = pointAfterCategoryScale(500f, 573.5f, 1.08f)
        assertEquals("Central perception mark has no pupil dot", primary, bitmap.getPixel(eyeCenter.first, eyeCenter.second))
        assertEquals("Central profile has no smile line", 0, Color.alpha(bitmap.getPixel(formerSmileCenter.first, formerSmileCenter.second)))
        bitmap.recycle()
    }

    @Test fun philosophyHasNoMainPointAtItsFormerPosition() {
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        CategoryIllustrationRenderer.draw(
            CategoryIllustrationRenderer.PHILOSOPHY,
            Canvas(bitmap),
            RectF(0f, 0f, 1000f, 700f),
            CategoryIllustrationRenderer.Palette(Color.rgb(88, 61, 101), Color.rgb(216, 190, 145), Color.DKGRAY, Color.WHITE)
        )
        val formerPoint = pointAfterCategoryScale(700f, 165f, 1.25f)
        assertEquals("Philosophy's removed point leaves no mark", 0, Color.alpha(bitmap.getPixel(formerPoint.first, formerPoint.second)))
        bitmap.recycle()
    }

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
        assertEquals(14, CategoryIllustrationRenderer.categoryIds.size)
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

    private fun pointAfterCategoryScale(x: Float, y: Float, scale: Float): Pair<Int, Int> =
        (500f + (x - 500f) * scale).toInt() to (350f + (y - 350f) * scale).toInt()

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
