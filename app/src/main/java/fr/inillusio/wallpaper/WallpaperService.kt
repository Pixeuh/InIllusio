package fr.inillusio.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Display
import fr.inillusio.data.local.dao.AnecdoteWithCategory
import fr.inillusio.data.preferences.MAX_WALLPAPER_TEXT_OFFSET
import fr.inillusio.data.preferences.MAX_WALLPAPER_SOURCE_OFFSET
import fr.inillusio.data.preferences.MIN_WALLPAPER_TEXT_OFFSET
import fr.inillusio.data.preferences.MIN_WALLPAPER_SOURCE_OFFSET
import fr.inillusio.ui.illustration.CategoryIllustrationRenderer
import fr.inillusio.ui.illustration.DisciplinePalettes
import kotlin.math.max
import kotlin.math.min

data class WallpaperArtwork(
    val anecdoteId: String,
    val bitmap: Bitmap,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val textOffsetRange: ClosedFloatingPointRange<Float>,
    val sourceOffsetRange: ClosedFloatingPointRange<Float>
)

private data class RenderedComposition(
    val bitmap: Bitmap,
    val textOffsetRange: ClosedFloatingPointRange<Float>,
    val sourceOffsetRange: ClosedFloatingPointRange<Float>
)

class WallpaperService(context: Context) {
    private val appContext = context.applicationContext
    private val manager = WallpaperManager.getInstance(appContext)

    fun renderForCurrentDisplay(
        fact: AnecdoteWithCategory,
        reference: String? = null,
        darkTheme: Boolean = true,
        textVerticalOffset: Float = 0f,
        sourceVerticalOffset: Float = 0f
    ): WallpaperArtwork {
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = requireNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY)) {
            "Écran principal introuvable."
        }
        val mode = display.mode
        val rotated = display.rotation == android.view.Surface.ROTATION_90 ||
            display.rotation == android.view.Surface.ROTATION_270
        val width = if (rotated) mode.physicalHeight else mode.physicalWidth
        val height = if (rotated) mode.physicalWidth else mode.physicalHeight
        require(width > 0 && height > 0) { "La résolution réelle de l’écran est indisponible." }

        val composition = renderComposition(fact, width, height, reference, darkTheme, textVerticalOffset, sourceVerticalOffset)
        return WallpaperArtwork(
            anecdoteId = fact.id,
            bitmap = composition.bitmap,
            screenWidthPx = width,
            screenHeightPx = height,
            textOffsetRange = composition.textOffsetRange,
            sourceOffsetRange = composition.sourceOffsetRange
        )
    }

    fun render(
        fact: AnecdoteWithCategory,
        width: Int,
        height: Int,
        reference: String? = null,
        darkTheme: Boolean = true,
        textVerticalOffset: Float = 0f,
        sourceVerticalOffset: Float = 0f
    ): Bitmap = renderComposition(fact, width, height, reference, darkTheme, textVerticalOffset, sourceVerticalOffset).bitmap

    private fun renderComposition(
        fact: AnecdoteWithCategory,
        width: Int,
        height: Int,
        reference: String?,
        darkTheme: Boolean,
        textVerticalOffset: Float,
        sourceVerticalOffset: Float
    ): RenderedComposition {
        require(width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = if (darkTheme) ColorPalette.ink else ColorPalette.paper
        val textColor = if (darkTheme) ColorPalette.paper else ColorPalette.ink
        val neutral = if (darkTheme) ColorPalette.darkNeutral else ColorPalette.lightNeutral
        val teal = ColorPalette.teal
        val labelColor = if (darkTheme) teal else ColorPalette.darkTeal
        val disciplinePalette = DisciplinePalettes.forCategory(appContext, fact.categoryId)
        canvas.drawColor(background)

        val scale = min(width / 420f, height / 900f)
        val margin = width * .09f
        val textWidth = width - margin * 2
        val artworkTop = height * .035f
        val referenceText = reference?.takeIf { it.isNotBlank() } ?: "Référence dans l’application"
        val referenceLayout = fitTextLayout(
            text = referenceText,
            maxWidth = textWidth,
            maxHeight = height * .12f,
            preferredTextSize = 13f * scale,
            minimumTextSize = 9f * scale,
            color = if (darkTheme) android.graphics.Color.argb(225, 247, 243, 232) else android.graphics.Color.rgb(39, 49, 54),
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        )

        val categoryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 11.5f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val hookPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val sourceLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = 8.5f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val referenceTop = height * .965f - referenceLayout.height
        val dividerY = referenceTop - height * .022f
        val sourceLabelBaseline = dividerY - height * .015f - sourceLabelPaint.descent()
        val sourceLabelTop = sourceLabelBaseline + sourceLabelPaint.ascent()
        val hookBottom = sourceLabelTop - height * .035f

        // Fit the full native 1000:700 artboard to the screen width. The former
        // 1.35x width cap cropped important drawing elements on portrait phones.
        // Long copy can still reclaim space by reducing the artwork scale.
        var artworkScale = 1f
        var artworkWidth = artworkWidth(width, height, artworkScale)
        var artworkHeight = artworkHeight(artworkWidth)
        var categoryTop = artworkTop + artworkHeight + height * .022f
        var categoryBaseline = categoryTop - categoryPaint.ascent()
        var categoryRuleY = categoryBaseline + categoryPaint.descent() + height * .018f
        var hookTop = categoryRuleY + height * .045f
        var hookLayout = fitTextLayout(
            text = fact.hook,
            maxWidth = textWidth,
            maxHeight = (hookBottom - hookTop).coerceAtLeast(1f),
            preferredTextSize = 22f * scale,
            minimumTextSize = 13f * scale,
            color = textColor,
            typeface = hookPaint.typeface
        )

        // Reclaim vertical space for long anecdotes before reducing their type size.
        while ((hookLayout.height > hookBottom - hookTop || hookTop >= hookBottom) && artworkScale > .90f) {
            artworkScale = (artworkScale - .05f).coerceAtLeast(.90f)
            artworkWidth = artworkWidth(width, height, artworkScale)
            artworkHeight = artworkHeight(artworkWidth)
            categoryTop = artworkTop + artworkHeight + height * .022f
            categoryBaseline = categoryTop - categoryPaint.ascent()
            categoryRuleY = categoryBaseline + categoryPaint.descent() + height * .018f
            hookTop = categoryRuleY + height * .045f
            hookLayout = fitTextLayout(
                text = fact.hook,
                maxWidth = textWidth,
                maxHeight = (hookBottom - hookTop).coerceAtLeast(1f),
                preferredTextSize = 22f * scale,
                minimumTextSize = 13f * scale,
                color = textColor,
                typeface = hookPaint.typeface
            )
        }
        if (hookLayout.height > hookBottom - hookTop) {
            hookLayout = fitTextLayout(
                text = fact.hook,
                maxWidth = textWidth,
                maxHeight = (hookBottom - hookTop).coerceAtLeast(1f),
                preferredTextSize = 13f * scale,
                minimumTextSize = 8f * scale,
                color = textColor,
                typeface = hookPaint.typeface
            )
        }

        // Keep the headline and source block independently adjustable. Their
        // safe ranges use the measured layouts, the actual bitmap size and the
        // other block's requested position, so the gap cannot collapse.
        val requestedTextOffset = height * textVerticalOffset.coerceIn(
            MIN_WALLPAPER_TEXT_OFFSET, MAX_WALLPAPER_TEXT_OFFSET
        )
        val requestedSourceOffset = height * sourceVerticalOffset.coerceIn(
            MIN_WALLPAPER_SOURCE_OFFSET, MAX_WALLPAPER_SOURCE_OFFSET
        )
        val sourceBlockTop = sourceLabelTop
        val sourceBlockBottom = referenceTop + referenceLayout.height
        val hookBlockBottom = hookTop + hookLayout.height
        val blockGap = height * .035f
        val sourceScreenMin = height * .025f - sourceBlockTop
        val sourceScreenMax = height * .975f - sourceBlockBottom
        val sourceScreenOffset = requestedSourceOffset.coerceIn(sourceScreenMin, sourceScreenMax)
        val textScreenMin = categoryRuleY + height * .025f - hookTop
        val textScreenMax = height * .965f - hookBlockBottom

        fun textRangeFor(sourceOffset: Float): ClosedFloatingPointRange<Float> {
            val minPx = max(textScreenMin, -height * .15f)
            val maxPx = min(textScreenMax, sourceBlockTop + sourceOffset - blockGap - hookBlockBottom)
                .coerceAtMost(height * .15f)
            return (minPx / height).coerceAtMost(maxPx / height)..(maxPx / height)
        }

        fun sourceRangeFor(textOffset: Float): ClosedFloatingPointRange<Float> {
            val minPx = max(sourceScreenMin, hookBlockBottom + textOffset + blockGap - sourceBlockTop)
                .coerceAtLeast(-height * .15f)
            val maxPx = min(sourceScreenMax, height * .15f)
            return (minPx / height).coerceAtMost(maxPx / height)..(maxPx / height)
        }

        val initialTextRange = textRangeFor(sourceScreenOffset)
        val effectiveTextOffset = requestedTextOffset.coerceIn(
            initialTextRange.start * height,
            initialTextRange.endInclusive * height
        )
        val sourceRange = sourceRangeFor(effectiveTextOffset)
        val effectiveSourceOffset = sourceScreenOffset.coerceIn(
            sourceRange.start * height,
            sourceRange.endInclusive * height
        )
        val textRange = textRangeFor(effectiveSourceOffset)
        val finalTextOffset = requestedTextOffset.coerceIn(
            textRange.start * height,
            textRange.endInclusive * height
        )

        val illustrationBounds = illustrationBounds(width, height, artworkScale)
        CategoryIllustrationRenderer.draw(
            fact.categoryId,
            canvas,
            illustrationBounds,
            CategoryIllustrationRenderer.Palette(
                primary = disciplinePalette.primary,
                secondary = disciplinePalette.secondary,
                ink = textColor,
                neutral = neutral
            ),
            preserveAspectRatio = true
        )

        drawTrackedText(canvas, fact.categoryName.uppercase(), margin, categoryBaseline, categoryPaint, 1.8f * scale)
        canvas.drawLine(margin, categoryRuleY, margin + 38f * scale, categoryRuleY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = disciplinePalette.primary
            strokeWidth = 2.2f * scale
            strokeCap = Paint.Cap.ROUND
        })

        // A short editorial rule leads into the headline without constraining copy width.
        canvas.drawLine(margin - 11f * scale, hookTop + finalTextOffset, margin - 11f * scale, hookTop + finalTextOffset + min(hookLayout.height.toFloat(), height * .09f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = disciplinePalette.secondary
            strokeWidth = 2.4f * scale
            strokeCap = Paint.Cap.ROUND
        })
        drawTextLayout(canvas, hookLayout, margin, hookTop + finalTextOffset)

        drawTrackedText(canvas, "SOURCE SCIENTIFIQUE", margin, sourceLabelBaseline + effectiveSourceOffset, sourceLabelPaint, 1.25f * scale)
        canvas.drawLine(margin, dividerY + effectiveSourceOffset, width - margin, dividerY + effectiveSourceOffset, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkTheme) android.graphics.Color.argb(100, 247, 243, 232) else android.graphics.Color.argb(85, 11, 20, 27)
            strokeWidth = max(1f, scale)
        })
        drawTextLayout(canvas, referenceLayout, margin, referenceTop + effectiveSourceOffset)
        return RenderedComposition(bitmap, textRange, sourceRange)
    }

    fun applyToLockScreen(artwork: WallpaperArtwork) {
        require(artwork.bitmap.width == artwork.screenWidthPx)
        require(artwork.bitmap.height == artwork.screenHeightPx)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val fullImage = Rect(0, 0, artwork.screenWidthPx, artwork.screenHeightPx)
            val wallpaperId = manager.setBitmap(artwork.bitmap, fullImage, true, WallpaperManager.FLAG_LOCK)
            check(wallpaperId != 0) { "Android n’a pas confirmé l’application du fond verrouillé." }
        } else {
            throw UnsupportedOperationException("Le fond d’écran de verrouillage requiert Android 7.0 ou ultérieur.")
        }
    }

    private fun artworkWidth(width: Int, height: Int, scale: Float): Float =
        min(width * scale, min(width.toFloat(), height * .54f * 1000f / 700f))

    private fun artworkHeight(artworkWidth: Float): Float = artworkWidth * .7f

    internal fun illustrationBounds(width: Int, height: Int, scale: Float): RectF {
        val artWidth = artworkWidth(width, height, scale)
        val artHeight = artworkHeight(artWidth)
        val top = height * .035f
        val left = (width - artWidth) / 2f
        return RectF(left, top, left + artWidth, top + artHeight)
    }

    private fun fitTextLayout(
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        preferredTextSize: Float,
        minimumTextSize: Float,
        color: Int,
        typeface: Typeface
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.typeface = typeface
        }
        var textSize = preferredTextSize
        var layout = createTextLayout(text, paint, textSize, maxWidth)
        while (layout.height > maxHeight && textSize > minimumTextSize) {
            textSize = (textSize * .94f).coerceAtLeast(minimumTextSize)
            layout = createTextLayout(text, paint, textSize, maxWidth)
        }
        return layout
    }

    private fun drawTextLayout(canvas: Canvas, layout: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(x, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawTrackedText(canvas: Canvas, text: String, x: Float, baseline: Float, paint: TextPaint, spacing: Float) {
        var cursor = x
        text.forEach { character ->
            val glyph = character.toString()
            canvas.drawText(glyph, cursor, baseline, paint)
            cursor += paint.measureText(glyph) + spacing
        }
    }

    private fun createTextLayout(text: String, paint: TextPaint, textSize: Float, maxWidth: Float): StaticLayout {
        paint.textSize = textSize
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.14f)
            .build()
    }

    private object ColorPalette {
        val ink = android.graphics.Color.rgb(11, 20, 27)
        val paper = android.graphics.Color.rgb(255, 252, 246)
        val teal = android.graphics.Color.rgb(0, 191, 174)
        val darkTeal = android.graphics.Color.rgb(0, 112, 102)
        val darkNeutral = android.graphics.Color.rgb(36, 51, 59)
        val lightNeutral = android.graphics.Color.rgb(236, 232, 223)
    }
}
