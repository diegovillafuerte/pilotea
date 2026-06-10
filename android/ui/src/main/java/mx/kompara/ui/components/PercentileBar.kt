package mx.kompara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.BrandGreen
import mx.kompara.ui.theme.KomparaTheme

/**
 * The "20 people" percentile visualization (B-046) — the Compose port of the web app's
 * `PercentileBar`. Renders [TOTAL_ICONS] little person glyphs in a row; the ones up to the driver's
 * position are filled in the [highlightColor], the rest are dimmed, so a driver can see at a glance
 * "I'm ahead of ~N out of 20 drivers".
 *
 * The driver's position is `round(displayPercentile / 100 * 20)`, clamped to `1..20` so there's
 * always at least one highlighted icon (you're never literally last) and never more than the row.
 * Uses [displayPercentile] (already inverted for lower-is-better metrics) so "more filled = better"
 * holds for every metric.
 *
 * Accessibility: the whole row carries a single [contentDescription] (passed by the caller, localized)
 * — 20 individual icons would be noise to a screen reader, so they're drawn on one [Canvas] and the
 * meaning is spoken once.
 *
 * @param displayPercentile the driver's 1–99 display percentile (higher = better).
 * @param contentDescription a localized sentence describing the standing, e.g. "Estás por encima del 78% de los choferes".
 * @param highlightColor fill for the driver's filled-in icons; defaults to the brand green.
 */
@Composable
fun PercentileBar(
    displayPercentile: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = BrandGreen,
    dimColor: Color = highlightColor.copy(alpha = 0.18f),
) {
    val filled = filledIcons(displayPercentile)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(TOTAL_ICONS) { index ->
            val color = if (index < filled) highlightColor else dimColor
            Box(modifier = Modifier.size(width = 14.dp, height = 28.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(28.dp).padding(1.dp)) {
                    drawPerson(color)
                }
            }
        }
    }
}

/** Number of highlighted icons for a [displayPercentile]; clamped to `1..TOTAL_ICONS`. */
internal fun filledIcons(displayPercentile: Int): Int =
    Math.round(displayPercentile / 100.0 * TOTAL_ICONS).toInt().coerceIn(1, TOTAL_ICONS)

/** Draw a simple person glyph (head + shoulders) filling the canvas in [color]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPerson(color: Color) {
    val w = size.width
    val h = size.height
    val headRadius = w * 0.32f
    // Head
    drawCircle(
        color = color,
        radius = headRadius,
        center = Offset(w / 2f, headRadius + h * 0.04f),
    )
    // Shoulders / torso — a rounded "bust" beneath the head.
    val bodyTop = headRadius * 2f + h * 0.10f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, bodyTop),
        size = androidx.compose.ui.geometry.Size(width = w * 0.76f, height = h - bodyTop),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.30f, w * 0.30f),
    )
}

/** Total person icons in the bar — the web app's 20-person grid. */
const val TOTAL_ICONS: Int = 20

@Preview(showBackground = true, name = "PercentileBar — Top 22%")
@Composable
private fun PercentileBarHighPreview() {
    KomparaTheme {
        PercentileBar(
            displayPercentile = 78,
            contentDescription = "Estás por encima del 78% de los choferes",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "PercentileBar — bajo")
@Composable
private fun PercentileBarLowPreview() {
    KomparaTheme {
        PercentileBar(
            displayPercentile = 15,
            contentDescription = "Estás por encima del 15% de los choferes",
            modifier = Modifier.padding(16.dp),
        )
    }
}
