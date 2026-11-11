package mx.kompara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** A shared zero origin also handles negatives. Uncomputable values have no bar. */
data class BarValue(val label: String, val value: Double?, val display: String)
data class BarScale(val minimum: Double, val maximum: Double) {
    val span: Double get() = (maximum - minimum).takeIf { it > 0 } ?: 1.0
    fun fraction(value: Double): Float = ((value - minimum) / span).toFloat()
    companion object {
        fun of(values: List<Double?>): BarScale {
            val known = values.filterNotNull().filter { it.isFinite() }
            return BarScale(minOf(0.0, known.minOrNull() ?: 0.0), maxOf(0.0, known.maxOrNull() ?: 0.0))
        }
    }
}

@Composable
fun CompareBars(bars: List<BarValue>, modifier: Modifier = Modifier) {
    val c = KomparaTheme.colors
    val scale = BarScale.of(bars.map { it.value })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        bars.forEachIndexed { index, bar ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bar.label, style = KomparaType.bodyStrong, color = if (index == 0) c.ink else c.inkSecondary)
                    Text(bar.display, style = KomparaType.compareLabel, color = if (index == 0) c.ink else c.inkSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics {}) {
                    val zero = size.width * scale.fraction(0.0)
                    bar.value?.takeIf { it.isFinite() }?.let {
                        val end = size.width * scale.fraction(it)
                        drawRect(if (index == 0) c.ink else c.inkTertiary,
                            Offset(minOf(zero, end), 0f), Size(kotlin.math.abs(end - zero), size.height))
                    }
                    if (scale.minimum < 0) drawLine(c.inkSecondary, Offset(zero, 0f), Offset(zero, size.height), 1.dp.toPx())
                }
            }
        }
    }
}
