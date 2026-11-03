package fr.inillusio.ui.illustration

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/** Shared vector-style artwork used by Compose cards and the lock-screen bitmap. */
object CategoryIllustrationRenderer {
    const val SOCIOLOGY = "sociologie"
    const val ANTHROPOLOGY = "anthropologie"
    const val SOCIAL_PSYCHOLOGY = "psychologie_sociale"
    const val HISTORY = "histoire"
    const val LINGUISTICS = "linguistique"
    const val ECONOMICS = "economie"
    const val PHILOSOPHY = "philosophie"
    const val HUMAN_GEOGRAPHY = "geographie_humaine"

    val categoryIds = setOf(SOCIOLOGY, ANTHROPOLOGY, SOCIAL_PSYCHOLOGY, HISTORY, LINGUISTICS, ECONOMICS, PHILOSOPHY, HUMAN_GEOGRAPHY)

    data class Palette(val primary: Int, val secondary: Int, val ink: Int, val neutral: Int)

    fun draw(categoryId: String, canvas: Canvas, bounds: RectF, palette: Palette, preserveAspectRatio: Boolean = true) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val save = canvas.save()
        canvas.clipRect(bounds)
        if (preserveAspectRatio) {
            val scale = min(bounds.width() / 1000f, bounds.height() / 700f)
            canvas.translate(bounds.left + (bounds.width() - 1000f * scale) / 2f, bounds.top + (bounds.height() - 700f * scale) / 2f)
            canvas.scale(scale, scale)
        } else {
            // The poster composition is taller than the card artboard. Scale both axes
            // to its measured target rectangle so the actual ink uses the full hero area.
            canvas.translate(bounds.left, bounds.top)
            canvas.scale(bounds.width() / 1000f, bounds.height() / 700f)
        }
        val art = Art(canvas, palette)
        art.commonTexture()
        val categoryScale = when (categoryId) {
            SOCIOLOGY -> 1.08f
            ANTHROPOLOGY -> 1.12f
            SOCIAL_PSYCHOLOGY -> 1.08f
            HISTORY -> 1.03f
            LINGUISTICS -> 1.04f
            ECONOMICS -> 1.06f
            PHILOSOPHY -> 1.25f
            HUMAN_GEOGRAPHY -> 1.20f
            else -> 1.08f
        }
        // Normalize the visual weight of categories whose central motif is
        // naturally compact. The uniform scale preserves geometry proportions.
        canvas.translate(500f, 350f)
        canvas.scale(categoryScale, categoryScale)
        canvas.translate(-500f, -350f)
        when (categoryId) {
            SOCIOLOGY -> art.sociology()
            ANTHROPOLOGY -> art.anthropology()
            SOCIAL_PSYCHOLOGY -> art.socialPsychology()
            HISTORY -> art.history()
            LINGUISTICS -> art.linguistics()
            ECONOMICS -> art.economics()
            PHILOSOPHY -> art.philosophy()
            HUMAN_GEOGRAPHY -> art.geography()
            else -> art.sociology()
        }
        canvas.restoreToCount(save)
    }

    private class Art(private val canvas: Canvas, private val colors: Palette) {
        private fun paint(color: Int, width: Float = 0f, style: Paint.Style = Paint.Style.FILL): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = width
            this.style = style
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int = colors.ink, width: Float = 4f) {
            canvas.drawLine(x1, y1, x2, y2, paint(color, width, Paint.Style.STROKE))
        }
        private fun path(color: Int, width: Float = 0f, style: Paint.Style = Paint.Style.FILL, block: Path.() -> Unit) {
            val value = Path().apply(block)
            canvas.drawPath(value, paint(color, width, style))
        }
        private fun dot(x: Float, y: Float, radius: Float, color: Int) { canvas.drawCircle(x, y, radius, paint(color)) }
        private fun halftone(cx: Float, cy: Float, radius: Float, color: Int, step: Float = 20f) {
            val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
            val save = canvas.save(); canvas.clipPath(clip)
            var y = cy - radius
            while (y <= cy + radius) {
                var x = cx - radius
                while (x <= cx + radius) {
                    val distance = kotlin.math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
                    if (distance <= radius) canvas.drawCircle(x, y, (1.6f * (1f - distance / (radius * 1.5f))).coerceAtLeast(.6f), paint(color))
                    x += step
                }
                y += step
            }
            canvas.restoreToCount(save)
        }
        private fun person(cx: Float, cy: Float, scale: Float, fill: Int, face: Boolean = false) {
            canvas.drawCircle(cx, cy, 27f * scale, paint(fill))
            val body = Path().apply {
                moveTo(cx - 52f * scale, cy + 103f * scale)
                cubicTo(cx - 48f * scale, cy + 45f * scale, cx - 28f * scale, cy + 35f * scale, cx, cy + 35f * scale)
                cubicTo(cx + 28f * scale, cy + 35f * scale, cx + 48f * scale, cy + 45f * scale, cx + 52f * scale, cy + 103f * scale)
                close()
            }
            canvas.drawPath(body, paint(fill))
            if (face) {
                line(cx - 10f * scale, cy, cx - 2f * scale, cy + 2f * scale, colors.ink, 2f * scale)
                line(cx + 7f * scale, cy + 8f * scale, cx + 15f * scale, cy + 7f * scale, colors.ink, 2f * scale)
            }
        }
        fun commonTexture() {
            canvas.drawArc(RectF(735f, -120f, 1130f, 275f), 30f, 265f, false, paint(colors.secondary, 2.5f, Paint.Style.STROKE))
            canvas.drawArc(RectF(770f, -85f, 1095f, 240f), 30f, 265f, false, paint(colors.primary, 2f, Paint.Style.STROKE))
            for (row in 0..4) for (column in 0..5) {
                dot(65f + column * 17f, 580f + row * 17f, 2.1f, if ((row + column) % 2 == 0) colors.primary else colors.secondary)
            }
            line(54f, 90f, 54f, 480f, colors.neutral, 1.6f)
            line(54f, 480f, 945f, 480f, colors.neutral, 1.6f)
        }
        fun sociology() {
            val nodes = listOf(220f to 250f, 380f to 150f, 560f to 245f, 755f to 155f, 790f to 340f, 555f to 390f, 330f to 385f)
            val edges = listOf(0 to 1, 0 to 2, 0 to 6, 1 to 2, 1 to 3, 2 to 3, 2 to 4, 2 to 5, 4 to 5, 5 to 6, 0 to 5)
            edges.forEachIndexed { index, (a, b) -> line(nodes[a].first, nodes[a].second + 25f, nodes[b].first, nodes[b].second + 25f, if (index % 3 == 0) colors.primary else colors.secondary, 3f) }
            nodes.forEachIndexed { index, (x, y) -> person(x, y, if (index == 2) 1.15f else .82f, if (index == 2 || index == 5) colors.primary else colors.ink, true) }
            halftone(220f, 250f, 48f, colors.secondary, 12f)
            nodes.forEach { (x, y) -> dot(x, y + 25f, 5f, colors.neutral) }
        }
        fun anthropology() {
            // A field notebook, a vessel and an abstract human portrait form a non-specific cultural collage.
            canvas.drawRoundRect(RectF(105f, 150f, 355f, 470f), 15f, 15f, paint(colors.neutral))
            for (i in 0..8) line(135f, 195f + i * 25f, 320f, 195f + i * 25f, if (i % 2 == 0) colors.secondary else colors.ink, 2f)
            canvas.drawRoundRect(RectF(150f, 260f, 308f, 273f), 6f, 6f, paint(colors.primary))
            path(colors.primary) { moveTo(490f, 215f); lineTo(690f, 215f); lineTo(666f, 425f); cubicTo(620f, 465f, 555f, 465f, 510f, 425f); close() }
            canvas.drawOval(RectF(500f, 198f, 680f, 246f), paint(colors.secondary))
            canvas.drawOval(RectF(537f, 320f, 644f, 352f), paint(colors.neutral))
            halftone(590f, 340f, 53f, colors.ink, 11f)
            canvas.drawCircle(806f, 310f, 110f, paint(colors.neutral))
            path(colors.ink) { moveTo(806f, 210f); cubicTo(738f, 224f, 726f, 296f, 765f, 336f); lineTo(760f, 432f); lineTo(862f, 432f); lineTo(845f, 324f); cubicTo(883f, 280f, 860f, 223f, 806f, 210f); close() }
            canvas.drawCircle(790f, 284f, 7f, paint(colors.primary)); line(805f, 315f, 825f, 309f, colors.secondary, 4f)
            line(706f, 450f, 900f, 450f, colors.primary, 5f)
        }
        fun socialPsychology() {
            val centers = listOf(205f, 345f, 485f, 625f, 765f)
            centers.forEachIndexed { index, x -> person(x, if (index == 2) 205f else 235f, if (index == 2) 1.24f else .82f, if (index == 2) colors.primary else colors.ink, true) }
            centers.forEachIndexed { index, x ->
                if (index != 2) {
                    val direction = if (index < 2) 1f else -1f
                    line(x, 385f, 485f + direction * 55f, 390f, colors.secondary, 3f)
                    canvas.drawCircle(x, 385f, 5f, paint(colors.primary))
                }
            }
            halftone(485f, 205f, 53f, colors.secondary, 12f)
            canvas.drawCircle(485f, 205f, 8f, paint(colors.ink))
            path(colors.primary, 4f, Paint.Style.STROKE) { moveTo(175f, 450f); cubicTo(340f, 505f, 650f, 505f, 820f, 450f) }
            line(820f, 450f, 785f, 446f, colors.primary, 4f); line(820f, 450f, 802f, 477f, colors.primary, 4f)
        }
        fun history() {
            // An abstract classical portico layered over a measured timeline.
            path(colors.neutral) { moveTo(150f, 220f); lineTo(485f, 105f); lineTo(820f, 220f); close() }
            path(colors.ink) { moveTo(180f, 222f); lineTo(485f, 118f); lineTo(790f, 222f); close() }
            canvas.drawRect(174f, 225f, 796f, 252f, paint(colors.primary))
            listOf(230f, 375f, 520f, 665f, 750f).forEachIndexed { index, x ->
                canvas.drawRoundRect(RectF(x - 38f, 250f, x + 38f, 427f), 12f, 12f, paint(if (index == 2) colors.neutral else colors.ink))
                line(x - 17f, 270f, x - 17f, 405f, colors.secondary, 3f)
                line(x + 17f, 270f, x + 17f, 405f, colors.primary, 3f)
            }
            canvas.drawRect(145f, 425f, 825f, 450f, paint(colors.secondary))
            line(130f, 545f, 870f, 545f, colors.ink, 4f)
            val ticks = listOf(190f, 355f, 520f, 685f, 850f)
            ticks.forEachIndexed { index, x ->
                line(x, 530f, x, 562f, colors.ink, 3f)
                dot(x, 545f, if (index == 2) 13f else 9f, if (index == 2) colors.primary else colors.secondary)
            }
            halftone(850f, 545f, 48f, colors.primary, 12f)
        }
        fun linguistics() {
            val type = Typeface.create("serif", Typeface.BOLD)
            val glyphs = listOf("A" to 210f, "α" to 365f, "ع" to 520f, "漢" to 680f, "अ" to 830f)
            glyphs.forEachIndexed { index, (glyph, x) ->
                val size = if (index == 2) 150f else 130f
                canvas.drawText(glyph, x, 350f + if (index % 2 == 0) 0f else 35f, paint(if (index % 2 == 0) colors.ink else colors.primary).apply { textSize = size; this.typeface = type })
                line(x - 45f, 395f, x + 45f, 395f, colors.secondary, 3f)
            }
            path(colors.secondary, 4f, Paint.Style.STROKE) { moveTo(120f, 450f); cubicTo(230f, 390f, 310f, 530f, 420f, 455f); cubicTo(535f, 380f, 620f, 530f, 740f, 450f); cubicTo(800f, 410f, 845f, 430f, 890f, 455f) }
            halftone(515f, 230f, 66f, colors.primary, 13f)
            canvas.drawText("“", 130f, 220f, paint(colors.secondary).apply { textSize = 120f; this.typeface = Typeface.SERIF })
            canvas.drawText("”", 830f, 500f, paint(colors.primary).apply { textSize = 120f; this.typeface = Typeface.SERIF })
        }
        fun economics() {
            // Interlocking exchange loops connect coins, a ledger and a changing market curve.
            path(colors.secondary, 7f, Paint.Style.STROKE) { moveTo(145f, 255f); cubicTo(245f, 130f, 370f, 130f, 450f, 250f); cubicTo(525f, 365f, 650f, 360f, 765f, 230f) }
            path(colors.primary, 7f, Paint.Style.STROKE) { moveTo(770f, 285f); cubicTo(660f, 430f, 515f, 450f, 430f, 315f); cubicTo(345f, 180f, 235f, 210f, 150f, 365f) }
            listOf(180f to 255f, 455f to 270f, 750f to 252f).forEachIndexed { index, (x, y) ->
                canvas.drawCircle(x, y, 62f, paint(if (index == 1) colors.primary else colors.neutral))
                canvas.drawCircle(x, y, 49f, paint(if (index == 1) colors.secondary else colors.ink, 3f, Paint.Style.STROKE))
                canvas.drawText("↔", x - 30f, y + 15f, paint(colors.ink).apply { textSize = 48f; typeface = Typeface.DEFAULT_BOLD })
            }
            line(145f, 500f, 850f, 500f, colors.ink, 3f)
            line(145f, 500f, 145f, 400f, colors.ink, 3f)
            val curve = Path().apply { moveTo(180f, 470f); cubicTo(300f, 450f, 320f, 520f, 435f, 465f); cubicTo(550f, 405f, 630f, 475f, 800f, 410f) }
            canvas.drawPath(curve, paint(colors.primary, 7f, Paint.Style.STROKE))
            listOf(260f to 462f, 435f to 465f, 620f to 450f, 800f to 410f).forEach { dot(it.first, it.second, 8f, colors.secondary) }
            halftone(830f, 410f, 52f, colors.secondary, 12f)
        }
        fun philosophy() {
            // Contemporary engraved philosopher profile, built from silhouette, contour and hatch marks.
            canvas.drawCircle(520f, 300f, 202f, paint(colors.neutral))
            canvas.drawCircle(520f, 300f, 188f, paint(colors.secondary, 3f, Paint.Style.STROKE))
            path(colors.ink) {
                moveTo(560f, 145f); cubicTo(430f, 142f, 366f, 245f, 407f, 341f)
                lineTo(380f, 378f); lineTo(430f, 391f); cubicTo(440f, 454f, 486f, 473f, 545f, 449f)
                lineTo(568f, 510f); lineTo(707f, 510f); cubicTo(685f, 441f, 657f, 390f, 633f, 361f)
                lineTo(670f, 341f); lineTo(634f, 326f); lineTo(633f, 266f)
                cubicTo(630f, 196f, 596f, 157f, 560f, 145f); close()
            }
            path(colors.primary) { moveTo(438f, 433f); cubicTo(466f, 460f, 522f, 465f, 559f, 438f); lineTo(570f, 510f); lineTo(455f, 510f); close() }
            line(408f, 378f, 451f, 380f, colors.secondary, 4f)
            line(540f, 300f, 573f, 307f, colors.secondary, 4f)
            line(585f, 348f, 609f, 343f, colors.primary, 3f)
            for (i in 0..7) line(464f + i * 12f, 180f, 425f + i * 12f, 248f, colors.neutral, 2f)
            halftone(700f, 165f, 72f, colors.primary, 12f)
            canvas.drawCircle(700f, 165f, 7f, paint(colors.ink))
            line(330f, 535f, 710f, 535f, colors.primary, 4f)
        }
        fun geography() {
            val globe = Path().apply { addCircle(520f, 320f, 220f, Path.Direction.CW) }
            val save = canvas.save(); canvas.clipPath(globe)
            canvas.drawCircle(520f, 320f, 220f, paint(colors.neutral))
            // Abstract land masses deliberately avoid implying a political boundary.
            path(colors.ink) { moveTo(370f, 180f); lineTo(430f, 160f); lineTo(462f, 205f); lineTo(447f, 248f); lineTo(485f, 276f); lineTo(450f, 315f); lineTo(423f, 375f); lineTo(390f, 355f); lineTo(368f, 293f); lineTo(335f, 260f); close() }
            path(colors.primary) { moveTo(565f, 188f); lineTo(620f, 205f); lineTo(655f, 254f); lineTo(625f, 280f); lineTo(635f, 325f); lineTo(600f, 348f); lineTo(579f, 315f); lineTo(548f, 290f); lineTo(558f, 245f); close() }
            path(colors.secondary) { moveTo(465f, 355f); lineTo(523f, 338f); lineTo(555f, 380f); lineTo(535f, 430f); lineTo(501f, 470f); lineTo(478f, 420f); close() }
            for (offset in listOf(-130f, -65f, 0f, 65f, 130f)) {
                val width = kotlin.math.sqrt((220f * 220f - offset * offset).coerceAtLeast(0f))
                canvas.drawOval(RectF(520f - width, 320f + offset * .35f - 23f, 520f + width, 320f + offset * .35f + 23f), paint(colors.secondary, 2f, Paint.Style.STROKE))
                canvas.drawOval(RectF(520f + offset * .38f - 45f, 100f, 520f + offset * .38f + 45f, 540f), paint(colors.ink, 1.8f, Paint.Style.STROKE))
            }
            path(colors.primary, 5f, Paint.Style.STROKE) { moveTo(310f, 355f); cubicTo(410f, 185f, 570f, 175f, 736f, 292f) }
            listOf(310f to 355f, 493f to 210f, 736f to 292f).forEach { dot(it.first, it.second, 9f, colors.primary) }
            canvas.restoreToCount(save)
            canvas.drawCircle(520f, 320f, 220f, paint(colors.ink, 4f, Paint.Style.STROKE))
            path(colors.primary, 4f, Paint.Style.STROKE) { moveTo(270f, 520f); cubicTo(410f, 610f, 655f, 600f, 790f, 505f) }
            halftone(790f, 505f, 43f, colors.secondary, 11f)
        }
    }
}
