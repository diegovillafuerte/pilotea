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
    /** The driver edited the $/km floor in the quick sheet. */
    val onThresholdChange: (perKm: Double) -> Unit = {},
)

/**
 * The floating verdict chip the driver sees over Uber/DiDi.
 *
 * Collapsed: a traffic-light pill with the net $/km big (the one-second read) and the net profit
 * underneath. Tap toggles an expanded detail row ($/min, gross-vs-net $/km, and a missing-data
 * hint). Long-press opens the inline quick-threshold sheet (a single $/km floor slider that
 * persists through [VerdictChipCallbacks.onThresholdChange]).
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
            onPerKmChange = callbacks.onThresholdChange,
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
        text = state.netPerKm,
        color = onColor,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = state.netProfit,
            color = onColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.overlay_net_profit_label),
            color = onColor,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ExpandedDetail(state: VerdictChipState) {
    val onColor = state.level.onBrandColor
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DetailRow(label = androidx.compose.ui.res.stringResource(R.string.overlay_per_min_label), value = state.netPerMin, color = onColor)
        DetailRow(label = androidx.compose.ui.res.stringResource(R.string.overlay_gross_per_km_label), value = state.grossPerKm, color = onColor)
    }
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
 * The long-press quick-settings card: a single $/km floor slider. Lives inline in the overlay
 * window (no separate Activity) so the driver never leaves the host app. Persists immediately via
 * [onPerKmChange].
 */
@Composable
private fun QuickThresholdSheet(
    threshold: PlatformThreshold,
    onPerKmChange: (Double) -> Unit,
    onClose: () -> Unit,
) {
    var value by remember { mutableStateOf(ThresholdSheet.clampPerKm(threshold.minPerKmMxn)) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.widthIn(min = 220.dp).padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.overlay_threshold_title),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = androidx.compose.ui.res.stringResource(
                    R.string.overlay_threshold_value,
                    String.format(java.util.Locale.forLanguageTag("es-MX"), "%.1f", value),
                ),
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
            )
            Slider(
                value = value.toFloat(),
                onValueChange = { value = ThresholdSheet.clampPerKm(it.toDouble()) },
                onValueChangeFinished = { onPerKmChange(value) },
                valueRange = ThresholdSheet.MIN_PER_KM.toFloat()..ThresholdSheet.MAX_PER_KM.toFloat(),
                steps = ThresholdSheet.stepCount,
            )
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
