package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/**
 * A chunky, rounded determinate progress bar for goal/percentile fills (e.g. "vas al 72 % de tu
 * meta del día"). Deliberately thicker than Material's hairline so it reads from a dashboard
 * mount. Use [trackColor]/[fillColor] to tint with a verdict colour when it represents one.
 *
 * @param progress fraction in 0f..1f; values outside are clamped.
 */
@Composable
fun KomparaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(fillColor),
        )
    }
}

@Preview(showBackground = true, name = "Progress — 72%")
@Composable
private fun KomparaProgressBarPreview() {
    KomparaTheme { KomparaProgressBar(progress = 0.72f) }
}

@Preview(showBackground = true, name = "Progress — full")
@Composable
private fun KomparaProgressBarFullPreview() {
    KomparaTheme { KomparaProgressBar(progress = 1.2f) }
}
