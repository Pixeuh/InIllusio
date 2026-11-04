package fr.inillusio.ui.illustration

import android.graphics.Canvas
import android.graphics.Color
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
    const val ETHOLOGY = "ethologie"
    const val ARCHAEOLOGY = "archeologie"
    const val POLITICAL_SCIENCE = "science_politique"
    const val COGNITIVE_PSYCHOLOGY = "psychologie_cognitive"
    const val DEMOGRAPHY = "demographie"
    const val ART_HISTORY = "histoire_art"

    val categoryIds = setOf(SOCIOLOGY, ANTHROPOLOGY, SOCIAL_PSYCHOLOGY, HISTORY, LINGUISTICS, ECONOMICS, PHILOSOPHY, HUMAN_GEOGRAPHY, ETHOLOGY, ARCHAEOLOGY, POLITICAL_SCIENCE, COGNITIVE_PSYCHOLOGY, DEMOGRAPHY, ART_HISTORY)

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
        art.commonTexture(
            includeBottomRule = categoryId != ARCHAEOLOGY,
            bottomDotRows = if (categoryId == ARCHAEOLOGY) 0 else 5
        )
        val categoryScale = when (categoryId) {
            SOCIOLOGY -> 1.08f
            ANTHROPOLOGY -> 1.12f
            SOCIAL_PSYCHOLOGY -> 1.08f
            HISTORY -> 1.03f
            LINGUISTICS -> 1.04f
            ECONOMICS -> 1.06f
            PHILOSOPHY -> 1.25f
            HUMAN_GEOGRAPHY -> 1.20f
            ETHOLOGY -> 1.04f
            ARCHAEOLOGY -> 1.05f
            POLITICAL_SCIENCE -> 1.04f
            COGNITIVE_PSYCHOLOGY -> 1.08f
            DEMOGRAPHY -> 1.05f
            ART_HISTORY -> 1.08f
            else -> 1.08f
        }
        // Normalize the visual weight of categories whose central motif is
        // naturally compact. The uniform scale preserves geometry proportions.
        val artworkSave = canvas.save()
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
            ETHOLOGY -> art.ethology()
            ARCHAEOLOGY -> art.archaeology()
            POLITICAL_SCIENCE -> art.politicalScience()
            COGNITIVE_PSYCHOLOGY -> art.cognitivePsychology()
            DEMOGRAPHY -> art.demography()
            ART_HISTORY -> art.artHistory()
            else -> art.sociology()
        }
        canvas.restoreToCount(artworkSave)
        if (categoryId == ARCHAEOLOGY) art.bottomDotMotif(5)
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
        fun commonTexture(includeBottomRule: Boolean = true, bottomDotRows: Int = 5) {
            canvas.drawArc(RectF(735f, -120f, 1130f, 275f), 30f, 265f, false, paint(colors.secondary, 2.5f, Paint.Style.STROKE))
            canvas.drawArc(RectF(770f, -85f, 1095f, 240f), 30f, 265f, false, paint(colors.primary, 2f, Paint.Style.STROKE))
            if (bottomDotRows > 0) bottomDotMotif(bottomDotRows)
            line(54f, 90f, 54f, 480f, colors.neutral, 1.6f)
            if (includeBottomRule) line(54f, 480f, 945f, 480f, colors.neutral, 1.6f)
        }
        fun bottomDotMotif(rows: Int) {
            for (row in 0 until rows) for (column in 0..5) {
                dot(65f + column * 17f, 580f + row * 17f, 2.1f, if ((row + column) % 2 == 0) colors.primary else colors.secondary)
            }
        }
        fun sociology() {
            val nodes = listOf(220f to 250f, 380f to 150f, 560f to 245f, 755f to 155f, 790f to 340f, 555f to 390f, 330f to 385f)
            val edges = listOf(0 to 1, 0 to 2, 0 to 6, 1 to 2, 1 to 3, 2 to 3, 2 to 4, 2 to 5, 4 to 5, 5 to 6, 0 to 5)
            val centralConnections = edges.indices.filter { edges[it] == (0 to 2) || edges[it] == (2 to 4) }.toSet()
            val lowerConnections = edges.indices.filter { edges[it] == (4 to 5) || edges[it] == (5 to 6) }.toSet()
            val redrawnConnections = centralConnections + lowerConnections
            edges.forEachIndexed { index, (a, b) ->
                if (index !in redrawnConnections) {
                    line(nodes[a].first, nodes[a].second + 25f, nodes[b].first, nodes[b].second + 25f, if (index % 3 == 0) colors.primary else colors.secondary, 3f)
                }
            }
            nodes.forEachIndexed { index, (x, y) -> person(x, y, if (index == 2) 1.15f else .82f, if (index == 2 || index == 5) colors.primary else colors.ink) }
            halftone(220f, 250f, 48f, colors.secondary, 12f)
            // Draw the two existing middle edges last so they visibly meet the central neck marker.
            // Clip only the neighboring figures; their short overlap onto node 2 is intentional.
            val centralSave = canvas.save()
            listOf(0, 4).forEach { index ->
                val (cx, cy) = nodes[index]
                val scale = .82f
                val head = Path().apply { addCircle(cx, cy, 27f * scale, Path.Direction.CW) }
                val body = Path().apply {
                    moveTo(cx - 52f * scale, cy + 103f * scale)
                    cubicTo(cx - 48f * scale, cy + 45f * scale, cx - 28f * scale, cy + 35f * scale, cx, cy + 35f * scale)
                    cubicTo(cx + 28f * scale, cy + 35f * scale, cx + 48f * scale, cy + 45f * scale, cx + 52f * scale, cy + 103f * scale)
                    close()
                }
                canvas.clipOutPath(head)
                canvas.clipOutPath(body)
            }
            centralConnections.forEach { index ->
                val (a, b) = edges[index]
                val endAY = nodes[a].second + if (a == 2) 35.5f else 25f
                val endBY = nodes[b].second + if (b == 2) 35.5f else 25f
                val lineColor = if (index % 3 == 0) colors.primary else colors.secondary
                if (edges[index] == (2 to 4)) {
                    // A subtle darker-red under-stroke keeps the red edge distinct on the orange torso.
                    val (cx, cy) = nodes[2]
                    val silhouette = Path().apply {
                        addCircle(cx, cy, 27f * 1.15f, Path.Direction.CW)
                        moveTo(cx - 52f * 1.15f, cy + 103f * 1.15f)
                        cubicTo(cx - 48f * 1.15f, cy + 45f * 1.15f, cx - 28f * 1.15f, cy + 35f * 1.15f, cx, cy + 35f * 1.15f)
                        cubicTo(cx + 28f * 1.15f, cy + 35f * 1.15f, cx + 48f * 1.15f, cy + 45f * 1.15f, cx + 52f * 1.15f, cy + 103f * 1.15f)
                        close()
                    }
                    val silhouetteSave = canvas.save()
                    canvas.clipPath(silhouette)
                    val darkerRed = Color.rgb(
                        (Color.red(lineColor) * .68f).toInt(),
                        (Color.green(lineColor) * .68f).toInt(),
                        (Color.blue(lineColor) * .68f).toInt()
                    )
                    line(nodes[a].first, endAY, nodes[b].first, endBY, darkerRed, 5f)
                    canvas.restoreToCount(silhouetteSave)
                }
                line(nodes[a].first, endAY, nodes[b].first, endBY, lineColor, 3f)
            }
            canvas.restoreToCount(centralSave)

            // Keep the lower connections masked by all three lower portraits as before.
            val lowerSave = canvas.save()
            listOf(4, 5, 6).forEach { index ->
                val (cx, cy) = nodes[index]
                val scale = .82f
                val head = Path().apply { addCircle(cx, cy, 27f * scale, Path.Direction.CW) }
                val body = Path().apply {
                    moveTo(cx - 52f * scale, cy + 103f * scale)
                    cubicTo(cx - 48f * scale, cy + 45f * scale, cx - 28f * scale, cy + 35f * scale, cx, cy + 35f * scale)
                    cubicTo(cx + 28f * scale, cy + 35f * scale, cx + 48f * scale, cy + 45f * scale, cx + 52f * scale, cy + 103f * scale)
                    close()
                }
                canvas.clipOutPath(head)
                canvas.clipOutPath(body)
            }
            lowerConnections.forEach { index ->
                val (a, b) = edges[index]
                line(nodes[a].first, nodes[a].second + 25f, nodes[b].first, nodes[b].second + 25f, if (index % 3 == 0) colors.primary else colors.secondary, 3f)
            }
            canvas.restoreToCount(lowerSave)
            nodes.forEachIndexed { index, (x, y) ->
                val pointY = y + if (index == 2) 35.5f else 25f
                dot(x, pointY, 5f, colors.neutral)
            }
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
            centers.forEachIndexed { index, x -> person(x, if (index == 2) 205f else 235f, if (index == 2) 1.24f else .82f, if (index == 2) colors.primary else colors.ink, face = index != 2) }
            centers.forEachIndexed { index, x ->
                if (index != 2) {
                    val direction = if (index < 2) 1f else -1f
                    line(x, 385f, 485f + direction * 55f, 390f, colors.secondary, 3f)
                    canvas.drawCircle(x, 385f, 5f, paint(colors.primary))
                }
            }

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
            val glyphs = listOf("A" to 210f, "Î±" to 365f, "Ø¹" to 520f, "æ¼¢" to 680f, "à¤…" to 830f)
            glyphs.forEachIndexed { index, (glyph, x) ->
                val size = if (index == 2) 150f else 130f
                canvas.drawText(glyph, x, 350f + if (index % 2 == 0) 0f else 35f, paint(if (index % 2 == 0) colors.ink else colors.primary).apply { textSize = size; this.typeface = type })
                line(x - 45f, 395f, x + 45f, 395f, colors.secondary, 3f)
            }
            path(colors.secondary, 4f, Paint.Style.STROKE) { moveTo(120f, 450f); cubicTo(230f, 390f, 310f, 530f, 420f, 455f); cubicTo(535f, 380f, 620f, 530f, 740f, 450f); cubicTo(800f, 410f, 845f, 430f, 890f, 455f) }
            halftone(515f, 230f, 66f, colors.primary, 13f)
            canvas.drawText("â€œ", 130f, 220f, paint(colors.secondary).apply { textSize = 120f; this.typeface = Typeface.SERIF })
            canvas.drawText("â€", 830f, 500f, paint(colors.primary).apply { textSize = 120f; this.typeface = Typeface.SERIF })
        }
        fun economics() {
            // Interlocking exchange loops connect coins, a ledger and a changing market curve.
            path(colors.secondary, 7f, Paint.Style.STROKE) { moveTo(145f, 255f); cubicTo(245f, 130f, 370f, 130f, 450f, 250f); cubicTo(525f, 365f, 650f, 360f, 765f, 230f) }
            path(colors.primary, 7f, Paint.Style.STROKE) { moveTo(770f, 285f); cubicTo(660f, 430f, 515f, 450f, 430f, 315f); cubicTo(345f, 180f, 235f, 210f, 150f, 365f) }
            listOf(180f to 255f, 455f to 270f, 750f to 252f).forEachIndexed { index, (x, y) ->
                canvas.drawCircle(x, y, 62f, paint(if (index == 1) colors.primary else colors.neutral))
                canvas.drawCircle(x, y, 49f, paint(if (index == 1) colors.secondary else colors.ink, 3f, Paint.Style.STROKE))
                canvas.drawText("â†”", x - 30f, y + 15f, paint(colors.ink).apply { textSize = 48f; typeface = Typeface.DEFAULT_BOLD })
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

        fun ethology() {
            // A restrained human profile faces a mouse represented by its head alone.
            path(colors.primary) {
                moveTo(200f, 188f); cubicTo(235f, 157f, 302f, 150f, 342f, 179f)
                cubicTo(373f, 201f, 381f, 239f, 372f, 270f)
                lineTo(390f, 296f); cubicTo(395f, 303f, 388f, 310f, 375f, 313f)
                lineTo(368f, 329f); cubicTo(362f, 353f, 342f, 370f, 317f, 378f)
                lineTo(318f, 418f); cubicTo(365f, 426f, 415f, 449f, 433f, 497f)
                lineTo(160f, 497f); cubicTo(169f, 449f, 201f, 420f, 247f, 408f)
                lineTo(247f, 374f); cubicTo(210f, 359f, 184f, 327f, 177f, 286f)
                cubicTo(168f, 244f, 174f, 210f, 200f, 188f); close()
            }
            dot(345f, 263f, 4.5f, colors.secondary)

            canvas.drawOval(RectF(724f, 178f, 780f, 250f), paint(colors.primary))
            canvas.drawOval(RectF(779f, 183f, 829f, 250f), paint(colors.primary))
            canvas.drawOval(RectF(735f, 190f, 770f, 239f), paint(colors.secondary))
            canvas.drawOval(RectF(789f, 194f, 820f, 240f), paint(colors.secondary))
            path(colors.secondary) {
                moveTo(777f, 218f); cubicTo(814f, 226f, 833f, 257f, 824f, 293f)
                cubicTo(817f, 327f, 791f, 350f, 757f, 356f)
                cubicTo(731f, 361f, 711f, 349f, 696f, 335f)
                cubicTo(680f, 322f, 664f, 315f, 646f, 310f)
                lineTo(608f, 302f); cubicTo(596f, 300f, 589f, 295f, 594f, 288f)
                cubicTo(609f, 278f, 630f, 274f, 653f, 267f)
                lineTo(685f, 255f); cubicTo(701f, 229f, 737f, 213f, 777f, 218f); close()
            }
            dot(678f, 274f, 4.5f, colors.primary)
            dot(594f, 294f, 6f, colors.primary)
            line(606f, 291f, 550f, 274f, colors.primary, 2.5f)
            line(605f, 297f, 545f, 297f, colors.primary, 2.5f)
            line(606f, 303f, 552f, 321f, colors.primary, 2.5f)
            line(210f, 520f, 800f, 520f, colors.secondary, 4f)
        }

        fun archaeology() {
            // Two shallow soil bands leave the lower artboard open and transparent.
            path(colors.secondary) { moveTo(90f, 378f); cubicTo(230f, 350f, 340f, 405f, 485f, 376f); cubicTo(650f, 344f, 750f, 397f, 920f, 367f); lineTo(920f, 411f); cubicTo(750f, 441f, 650f, 388f, 485f, 420f); cubicTo(340f, 449f, 230f, 394f, 90f, 422f); close() }
            path(colors.primary) { moveTo(90f, 445f); cubicTo(250f, 420f, 345f, 467f, 500f, 438f); cubicTo(665f, 410f, 790f, 463f, 920f, 432f); lineTo(920f, 476f); cubicTo(790f, 507f, 665f, 454f, 500f, 482f); cubicTo(345f, 511f, 250f, 464f, 90f, 489f); close() }
            // Amphora body and neck.
            path(colors.neutral) { moveTo(440f, 192f); lineTo(560f, 192f); lineTo(552f, 252f); cubicTo(552f, 287f, 630f, 320f, 625f, 385f); cubicTo(619f, 463f, 570f, 500f, 500f, 500f); cubicTo(430f, 500f, 381f, 463f, 375f, 385f); cubicTo(370f, 320f, 448f, 287f, 448f, 252f); close() }
            path(colors.primary) { moveTo(458f, 218f); lineTo(542f, 218f); lineTo(538f, 255f); cubicTo(540f, 293f, 598f, 322f, 596f, 381f); cubicTo(592f, 435f, 557f, 466f, 500f, 468f); cubicTo(443f, 466f, 408f, 435f, 404f, 381f); cubicTo(402f, 322f, 460f, 293f, 462f, 255f); close() }
            canvas.drawRoundRect(RectF(425f, 162f, 575f, 206f), 12f, 12f, paint(colors.secondary))
            canvas.drawOval(RectF(428f, 190f, 572f, 218f), paint(colors.ink))
            path(colors.secondary, 18f, Paint.Style.STROKE) { moveTo(443f, 273f); cubicTo(360f, 255f, 365f, 344f, 395f, 348f) }
            path(colors.secondary, 18f, Paint.Style.STROKE) { moveTo(557f, 273f); cubicTo(640f, 255f, 635f, 344f, 605f, 348f) }
            path(colors.ink, 3f, Paint.Style.STROKE) { moveTo(450f, 350f); cubicTo(480f, 370f, 520f, 370f, 550f, 350f) }
            // Keep the upper soil contour over the vessel while leaving the base visible.
            path(colors.secondary) { moveTo(90f, 378f); cubicTo(230f, 350f, 340f, 405f, 485f, 376f); cubicTo(650f, 344f, 750f, 397f, 920f, 367f); lineTo(920f, 402f); cubicTo(750f, 432f, 650f, 379f, 485f, 411f); cubicTo(340f, 440f, 230f, 385f, 90f, 413f); close() }
            path(colors.primary) { moveTo(220f, 434f); lineTo(290f, 425f); lineTo(318f, 451f); lineTo(266f, 475f); close() }
            path(colors.neutral) { moveTo(715f, 416f); lineTo(770f, 407f); lineTo(797f, 430f); lineTo(744f, 450f); close() }
        }

        fun politicalScience() {
            // Ballot box foreground and a semicircle of assembly seats in the back.
            val seatRows = listOf(230f to listOf(510f, 610f, 710f), 315f to listOf(420f, 520f, 620f, 720f, 820f), 400f to listOf(345f, 445f, 545f, 645f, 745f, 845f))
            seatRows.forEachIndexed { row, (y, xs) -> xs.forEachIndexed { i, x ->
                val color = if ((i + row) % 2 == 0) colors.primary else colors.secondary
                canvas.drawRoundRect(RectF(x - 23f, y, x + 23f, y + 42f), 7f, 7f, paint(color))
                line(x - 32f, y + 44f, x + 32f, y + 44f, colors.ink, 4f)
                line(x - 17f, y + 44f, x - 20f, y + 61f, colors.ink, 3f)
                line(x + 17f, y + 44f, x + 20f, y + 61f, colors.ink, 3f)
            } }
            path(colors.secondary, 4f, Paint.Style.STROKE) { moveTo(330f, 445f); cubicTo(420f, 365f, 690f, 360f, 840f, 450f) }
            path(colors.primary) { moveTo(245f, 340f); lineTo(505f, 340f); lineTo(478f, 545f); lineTo(272f, 545f); close() }
            canvas.drawRect(270f, 365f, 480f, 548f, paint(colors.neutral))
            line(310f, 355f, 440f, 355f, colors.ink, 8f)
            path(colors.ink) { moveTo(390f, 270f); lineTo(474f, 314f); lineTo(438f, 333f); lineTo(355f, 289f); close() }
            path(colors.neutral) { moveTo(364f, 276f); lineTo(466f, 329f); lineTo(451f, 357f); lineTo(349f, 304f); close() }
            path(colors.secondary) { moveTo(290f, 548f); lineTo(462f, 548f); lineTo(500f, 586f); lineTo(252f, 586f); close() }
            line(292f, 392f, 458f, 392f, colors.secondary, 3f)
            line(292f, 530f, 458f, 530f, colors.ink, 3f)
            for (i in 0..3) dot(323f + i * 38f, 465f, 4f, colors.primary)
        }

        fun cognitivePsychology() {
            // Two inward-facing profiles frame an eye and small perception cues.
            path(colors.primary) {
                moveTo(345f, 150f); cubicTo(230f, 165f, 180f, 265f, 210f, 360f)
                lineTo(180f, 389f); lineTo(229f, 403f); cubicTo(245f, 454f, 280f, 477f, 334f, 466f)
                lineTo(345f, 540f); lineTo(466f, 540f); lineTo(444f, 454f); cubicTo(463f, 395f, 467f, 281f, 426f, 208f)
                cubicTo(404f, 169f, 376f, 151f, 345f, 150f); close()
            }
            path(colors.secondary) {
                moveTo(655f, 150f); cubicTo(770f, 165f, 820f, 265f, 790f, 360f)
                lineTo(820f, 389f); lineTo(771f, 403f); cubicTo(755f, 454f, 720f, 477f, 666f, 466f)
                lineTo(655f, 540f); lineTo(534f, 540f); lineTo(556f, 454f); cubicTo(537f, 395f, 533f, 281f, 574f, 208f)
                cubicTo(596f, 169f, 624f, 151f, 655f, 150f); close()
            }
            line(356f, 296f, 382f, 300f, colors.neutral, 5f)
            line(618f, 296f, 644f, 300f, colors.ink, 5f)
            path(colors.neutral) { moveTo(407f, 336f); cubicTo(452f, 287f, 548f, 287f, 593f, 336f); cubicTo(548f, 384f, 452f, 384f, 407f, 336f); close() }
            canvas.drawOval(RectF(463f, 305f, 537f, 367f), paint(colors.primary))
            // Leave the central perception motif unmarked for a neutral expression.
            path(colors.secondary, 4f, Paint.Style.STROKE) { moveTo(270f, 240f); cubicTo(340f, 190f, 410f, 205f, 450f, 250f) }
            path(colors.primary, 4f, Paint.Style.STROKE) { moveTo(550f, 250f); cubicTo(600f, 204f, 670f, 190f, 730f, 240f) }
            listOf(215f to 260f, 785f to 260f, 276f to 495f, 724f to 495f).forEachIndexed { i, (x, y) ->
                canvas.drawCircle(x, y, if (i < 2) 19f else 13f, paint(if (i % 2 == 0) colors.secondary else colors.primary))
            }
        }

        fun demography() {
            // Abstract generations arranged as an age pyramid, without chart labels or figures.
            val rows = listOf(
                Triple(437.5f, 135f, 0.92f),
                Triple(312.5f, 290f, 0.82f),
                Triple(187.5f, 445f, 0.72f)
            )
            rows.forEachIndexed { row, (left, y, scale) ->
                val count = 2 + row * 2
                for (i in 0 until count) {
                    val x = left + i * 125f
                    val color = if ((i + row) % 2 == 0) colors.primary else colors.secondary
                    person(x, y, scale, color)

                }
            }
            path(colors.secondary, 3f, Paint.Style.STROKE) { moveTo(140f, 520f); cubicTo(275f, 595f, 710f, 595f, 860f, 520f) }
            dot(140f, 520f, 7f, colors.primary); dot(860f, 520f, 7f, colors.secondary)
            path(colors.primary, 3f, Paint.Style.STROKE) { moveTo(235f, 180f); cubicTo(330f, 115f, 670f, 115f, 765f, 180f) }
            // Repaint the heads last so later figures and pyramid contours cannot cover them.
            rows.forEachIndexed { row, (left, y, scale) ->
                val count = 2 + row * 2
                for (i in 0 until count) {
                    val x = left + i * 125f
                    val color = if ((i + row) % 2 == 0) colors.primary else colors.secondary
                    canvas.drawCircle(x, y, 27f * scale, paint(color))

                }
            }
        }

        fun artHistory() {
            // A split-color classical bust stands before a restrained gallery frame.
            canvas.drawRoundRect(RectF(245f, 105f, 755f, 550f), 8f, 8f, paint(colors.secondary, 5f, Paint.Style.STROKE))
            canvas.drawRoundRect(RectF(268f, 128f, 732f, 527f), 5f, 5f, paint(colors.primary, 2f, Paint.Style.STROKE))
            path(colors.neutral) {
                moveTo(500f, 165f); cubicTo(417f, 165f, 378f, 229f, 390f, 310f)
                lineTo(368f, 338f); lineTo(406f, 350f); cubicTo(420f, 406f, 453f, 428f, 500f, 429f)
                lineTo(500f, 495f); lineTo(365f, 495f); cubicTo(369f, 446f, 412f, 420f, 449f, 405f)
                lineTo(439f, 374f); cubicTo(410f, 350f, 397f, 320f, 399f, 280f)
                cubicTo(400f, 210f, 438f, 166f, 500f, 165f); close()
            }
            path(colors.primary) {
                moveTo(500f, 165f); cubicTo(583f, 165f, 622f, 229f, 610f, 310f)
                lineTo(632f, 338f); lineTo(594f, 350f); cubicTo(580f, 406f, 547f, 428f, 500f, 429f)
                lineTo(500f, 495f); lineTo(635f, 495f); cubicTo(631f, 446f, 588f, 420f, 551f, 405f)
                lineTo(561f, 374f); cubicTo(590f, 350f, 603f, 320f, 601f, 280f)
                cubicTo(600f, 210f, 562f, 166f, 500f, 165f); close()
            }
            path(colors.secondary) { moveTo(443f, 210f); cubicTo(470f, 178f, 520f, 172f, 558f, 195f); lineTo(539f, 228f); lineTo(458f, 235f); close() }
            line(446f, 286f, 472f, 290f, colors.ink, 4f); line(528f, 290f, 554f, 286f, colors.neutral, 4f)
            line(476f, 344f, 500f, 350f, colors.secondary, 3f); line(500f, 350f, 524f, 344f, colors.secondary, 3f)
            path(colors.secondary) { moveTo(380f, 495f); lineTo(620f, 495f); lineTo(670f, 535f); lineTo(330f, 535f); close() }
            canvas.drawRect(468f, 430f, 532f, 496f, paint(colors.neutral))
            path(colors.primary) { moveTo(350f, 210f); lineTo(372f, 220f); lineTo(362f, 248f); lineTo(340f, 238f); close() }
            path(colors.secondary) { moveTo(650f, 195f); lineTo(673f, 205f); lineTo(663f, 233f); lineTo(640f, 223f); close() }
            line(307f, 564f, 693f, 564f, colors.primary, 4f)
            dot(307f, 564f, 6f, colors.secondary); dot(693f, 564f, 6f, colors.secondary)
        }
    }
}
