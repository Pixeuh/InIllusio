package fr.horscadre.ui.illustration

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.material3.MaterialTheme
import android.graphics.RectF

@Composable
fun CategoryIllustration(categoryId: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palette = CategoryIllustrationRenderer.Palette(
        coral = scheme.primary.toArgb(),
        turquoise = scheme.secondary.toArgb(),
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
