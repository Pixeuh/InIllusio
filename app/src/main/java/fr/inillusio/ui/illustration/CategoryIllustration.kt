package fr.inillusio.ui.illustration

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import android.graphics.RectF

@Composable
fun CategoryIllustration(categoryId: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val colors = remember(context, categoryId) { DisciplinePalettes.forCategory(context, categoryId) }
    val palette = CategoryIllustrationRenderer.Palette(
        primary = colors.primary,
        secondary = colors.secondary,
        ink = scheme.onSurface.toArgb(),
        neutral = scheme.surfaceVariant.toArgb()
    )
    Canvas(modifier) {
        drawIntoCanvas { draw ->
            CategoryIllustrationRenderer.draw(
                categoryId,
                draw.nativeCanvas,
                RectF(0f, 0f, size.width, size.height),
                palette
            )
        }
    }
}
