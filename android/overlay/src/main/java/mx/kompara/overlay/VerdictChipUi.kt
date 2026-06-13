package mx.kompara.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.ui.components.brandColor
import mx.kompara.ui.components.labelRes
import mx.kompara.ui.components.onBrandColor

/**
 * Callbacks the host (the [OverlayController]) wires the chip to. Drag deltas move the *window*
 * (the controller updates the WindowManager LayoutParams), not a Compose offset, because the chip
 * lives in its own overlay window.
 */
data class VerdictChipCallbacks(
    /** Accumulated drag delta since the last call, in px. */
    val onDrag: (dxPx: Float, dyPx: Float) -> Unit = { _, _ -> },
    /** Drag finished → snap to the nearer edge and persist. */
    val onDragEnd: () -> Unit = {},
    /** The driver edited a floor in the quick sheet; the full updated threshold persists. */
    val onThresholdChange: (threshold: PlatformThreshold) -> Unit = {},
)

/**
 * The floating verdict chip the driver sees over Uber/DiDi.
 *
 * Collapsed: a traffic-light pill with the driver's preferred rate big (the one-second read —
 * net $/km or net $/hr per [VerdictChipState.preferred], B-079) and the other rate right under it
 * as context. The net total ("ganancia neta") lives in the expanded detail: the host app already
 * shows the fare, so repeating a peso total up front spends glance budget without adding signal.
 * Tap toggles the expanded detail rows (net profit, $/min, gross $/km, and a missing-data hint).
 * Long-press opens the inline quick-threshold sheet (green + red floor sliders for the preferred
 * metric, persisting through [VerdictChipCallbacks.onThresholdChange]).
 *
 * Glanceability (1-second cognition target): the hero figure is heavy and oversized; colour does
 * most of the talking so the meaning lands peripherally.
 */
@Composable
fun VerdictChipUi(
    state: VerdictChipState,
    threshold: PlatformThreshold,
    callbacks: VerdictChipCallbacks = VerdictChipCallbacks(),
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }

    val chipDescription = androidx.compose.ui.res.stringResource(R.string.overlay_chip_content_description)

    Column(
        modifier = modifier
            .widthIn(min = 132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(state.level.brandColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        callbacks.onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = { callbacks.onDragEnd() },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { expanded = !expanded },
                    onLongPress = { sheetOpen = true },
                )
            }
            .semantics { contentDescription = chipDescription }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        CollapsedContent(state)

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            ExpandedDetail(state)
        }

        if (state.hasMissingData) {
            Spacer(Modifier.height(4.dp))
            MissingHint(state.missingHintKind)
        }
    }

    if (sheetOpen) {
        QuickThresholdSheet(
            threshold = threshold,
            preferred = state.preferred,
            onThresholdChange = callbacks.onThresholdChange,
            onClose = { sheetOpen = false },
        )
    }
}

@Composable
private fun CollapsedContent(state: VerdictChipState) {
    val onColor = state.level.onBrandColor
    Text(
        text = androidx.compose.ui.res.stringResource(state.level.labelRes),
        color = onColor,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
    Text(
        text = state.heroRate,
        color = onColor,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
    )
    Text(
        text = state.secondaryRate,
        color = onColor,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
}

@Composable
private fun ExpandedDetail(state: VerdictChipState) {
    val onColor = state.level.onBrandColor
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ExplainLine(state.explainKind, color = onColor)
        DetailRow(label = androidx.compose.ui.res.stringResource(R.string.overlay_net_profit_label), value = state.netProfit, color = onColor)
        DetailRow(label = androidx.compose.ui.res.stringResource(R.string.overlay_per_min_label), value = state.netPerMin, color = onColor)
        DetailRow(label = androidx.compose.ui.res.stringResource(R.string.overlay_gross_per_km_label), value = state.grossPerKm, color = onColor)
    }
}

/** The one-line "¿por qué?" — which floor passed/failed — at the top of the expanded detail. */
@Composable
private fun ExplainLine(kind: VerdictChipState.ExplainKind, color: androidx.compose.ui.graphics.Color) {
    val res = when (kind) {
        VerdictChipState.ExplainKind.NONE -> return
        VerdictChipState.ExplainKind.BOTH_STRONG -> R.string.overlay_explain_both_strong
        VerdictChipState.ExplainKind.KM_WEAK -> R.string.overlay_explain_km_weak
        VerdictChipState.ExplainKind.HOUR_WEAK -> R.string.overlay_explain_hour_weak
        VerdictChipState.ExplainKind.BOTH_WEAK -> R.string.overlay_explain_both_weak
    }
    Text(
        text = androidx.compose.ui.res.stringResource(res),
        color = color,
        fontSize = 11.sp,
        modifier = Modifier.widthIn(max = 180.dp).padding(bottom = 2.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = color, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(text = value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun MissingHint(kind: VerdictChipState.MissingHintKind) {
    val res = when (kind) {
        VerdictChipState.MissingHintKind.DISTANCE -> R.string.overlay_missing_distance
        VerdictChipState.MissingHintKind.FARE -> R.string.overlay_missing_fare
        VerdictChipState.MissingHintKind.GENERIC -> R.string.overlay_missing_generic
        VerdictChipState.MissingHintKind.NONE -> return
    }
    Text(
        text = androidx.compose.ui.res.stringResource(res),
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = 10.sp,
    )
}

/**
 * The long-press quick-settings card: green + red floor sliders for the metric that decides the
 * semáforo (B-079) — $/km when the driver prefers IPK, $/hr for IPH; the other metric's floors are
 * never touched from here. Lives inline in the overlay window (no separate Activity) so the driver
 * never leaves the host app. Persists immediately via [onThresholdChange]; [ThresholdSheet] keeps
 * the red floor at or below the green one.
 */
@Composable
private fun QuickThresholdSheet(
    threshold: PlatformThreshold,
    preferred: PreferredMetric,
    onThresholdChange: (PlatformThreshold) -> Unit,
    onClose: () -> Unit,
) {
    var current by remember { mutableStateOf(ThresholdSheet.snapped(threshold, preferred)) }
    val titleRes = when (preferred) {
        PreferredMetric.IPK -> R.string.overlay_threshold_title
        PreferredMetric.IPH -> R.string.overlay_threshold_title_hour
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.widthIn(min = 220.dp).padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(titleRes),
                fontWeight = FontWeight.Bold,
            )
            when (preferred) {
                PreferredMetric.IPK -> {
                    FloorSlider(
                        label = androidx.compose.ui.res.stringResource(R.string.overlay_threshold_green_label),
                        valueText = perKmValueText(current.minPerKmMxn),
                        value = current.minPerKmMxn,
                        range = ThresholdSheet.MIN_PER_KM..ThresholdSheet.MAX_PER_KM,
                        steps = ThresholdSheet.stepCount,
                        onValueChange = { current = ThresholdSheet.withGreenPerKm(current, it) },
                        onValueChangeFinished = { onThresholdChange(current) },
                    )
                    FloorSlider(
                        label = androidx.compose.ui.res.stringResource(R.string.overlay_threshold_red_label),
                        valueText = perKmValueText(current.redPerKmMxn),
                        value = current.redPerKmMxn,
                        range = ThresholdSheet.MIN_PER_KM..ThresholdSheet.MAX_PER_KM,
                        steps = ThresholdSheet.stepCount,
                        onValueChange = { current = ThresholdSheet.withRedPerKm(current, it) },
                        onValueChangeFinished = { onThresholdChange(current) },
                    )
                }
                PreferredMetric.IPH -> {
                    FloorSlider(
                        label = androidx.compose.ui.res.stringResource(R.string.overlay_threshold_green_label),
                        valueText = perHourValueText(current.minPerHourMxn),
                        value = current.minPerHourMxn,
                        range = ThresholdSheet.MIN_PER_HOUR..ThresholdSheet.MAX_PER_HOUR,
                        steps = ThresholdSheet.hourStepCount,
                        onValueChange = { current = ThresholdSheet.withGreenPerHour(current, it) },
                        onValueChangeFinished = { onThresholdChange(current) },
                    )
                    FloorSlider(
                        label = androidx.compose.ui.res.stringResource(R.string.overlay_threshold_red_label),
                        valueText = perHourValueText(current.redPerHourMxn),
                        value = current.redPerHourMxn,
                        range = ThresholdSheet.MIN_PER_HOUR..ThresholdSheet.MAX_PER_HOUR,
                        steps = ThresholdSheet.hourStepCount,
                        onValueChange = { current = ThresholdSheet.withRedPerHour(current, it) },
                        onValueChangeFinished = { onThresholdChange(current) },
                    )
                }
            }
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.overlay_threshold_hint),
                fontSize = 11.sp,
            )
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Text(text = androidx.compose.ui.res.stringResource(R.string.overlay_threshold_close))
            }
        }
    }
}

@Composable
private fun perKmValueText(value: Double): String =
    androidx.compose.ui.res.stringResource(
        R.string.overlay_threshold_value,
        String.format(java.util.Locale.forLanguageTag("es-MX"), "%.1f", value),
    )

@Composable
private fun perHourValueText(value: Double): String =
    androidx.compose.ui.res.stringResource(
        R.string.overlay_threshold_value_hour,
        String.format(java.util.Locale.forLanguageTag("es-MX"), "%.0f", value),
    )

@Composable
private fun FloorSlider(
    label: String,
    valueText: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    steps: Int,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 12.sp)
        Text(
            text = valueText,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
        )
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toDouble()) },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        steps = steps,
    )
}
