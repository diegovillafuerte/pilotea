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
fun PeriodBars(bars: List<BarValue>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, showBestLabel: Boolean = true, showNegativeLabels: Boolean = false,
    fixedBarWidth: androidx.compose.ui.unit.Dp? = null, dashedUnknown: Boolean = false) {
    val c = KomparaTheme.colors
    val scale = BarScale.of(bars.map { it.value })
    val best = bars.indices.filter { bars[it].value != null }.maxByOrNull { bars[it].value!! }
    Column(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .semantics(mergeDescendants = true) {}) {
        best?.takeIf { showBestLabel }?.let { index ->
            val label = "${bars[index].label} ${bars[index].display}"
            val density = LocalDensity.current
            val labelWidth = rememberTextMeasurer().measure(label, KomparaType.small).size.width.toFloat()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = with(density) { maxWidth.toPx() }
                val gap = with(density) { 10.dp.toPx() }
                val barWidth = ((width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(0f)
                val center = if (fixedBarWidth != null) width / bars.size * (index + .5f) else index * (barWidth + gap) + barWidth / 2
                val x = (center - labelWidth / 2).coerceIn(0f, (width - labelWidth).coerceAtLeast(0f))
                Text(label, Modifier.offset(x = with(density) { x.toDp() }), style = KomparaType.small, color = c.inkSecondary)
            }
        }
        val description = bars.joinToString("; ") { "${it.label}: ${it.display}" }
        Canvas(Modifier.fillMaxWidth().height(72.dp).clearAndSetSemantics { contentDescription = description }) {
            val zero = size.height * (1f - scale.fraction(0.0))
            val gap = 10.dp.toPx()
            val slot = size.width / bars.size.coerceAtLeast(1)
            val width = fixedBarWidth?.toPx()?.coerceAtMost(slot) ?: ((size.width - gap * (bars.size - 1)) / bars.size.coerceAtLeast(1)).coerceAtLeast(0f)
            fun left(index: Int) = if (fixedBarWidth != null) slot * index + (slot - width) / 2 else index * (width + gap)
            bars.forEachIndexed { index, bar ->
                bar.value?.takeIf { it.isFinite() }?.let { value ->
                    val y = size.height * (1 - scale.fraction(value))
                    drawRect(if (index == best) c.ink else c.rule, Offset(left(index), minOf(y, zero)),
                        Size(width, kotlin.math.abs(zero - y)))
                    if (showNegativeLabels && value < 0) {
                        val left = left(index)
                        drawLine(c.inkSecondary, Offset(left, zero), Offset(left, y), 1.5.dp.toPx())
                        drawLine(c.inkSecondary, Offset(left, y), Offset(left + width, y), 1.5.dp.toPx())
                        drawLine(c.inkSecondary, Offset(left + width, y), Offset(left + width, zero), 1.5.dp.toPx())
                    }
                }
            }
            if (dashedUnknown) bars.forEachIndexed { index, bar ->
                val baseline = zero.coerceIn(1.dp.toPx(), size.height - 1.dp.toPx())
                drawLine(c.inkTertiary, Offset(slot * index, baseline), Offset(slot * (index + 1), baseline), 1.dp.toPx(),
                    pathEffect = if (bar.value == null) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null)
            } else drawLine(if (showNegativeLabels) c.inkTertiary else c.ink, Offset(0f, zero.coerceIn(1.dp.toPx(), size.height - 1.dp.toPx())),
                Offset(size.width, zero.coerceIn(1.dp.toPx(), size.height - 1.dp.toPx())), (if (showNegativeLabels) 1.dp else 2.dp).toPx())
        }
        if (showNegativeLabels && bars.any { (it.value ?: 0.0) < 0 }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                bars.forEach { bar -> Text(if ((bar.value ?: 0.0) < 0) bar.display else "", style = KomparaType.small, color = c.inkSecondary) }
            }
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
