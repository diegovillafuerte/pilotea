package mx.kompara.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
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
import mx.kompara.ui.theme.BrandGreen

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
 * as context. A slim brand strip (the Kompara "K" mark + wordmark) sits on top so a screenshot a
 * driver shares is self-branding — a deliberate word-of-mouth growth lever.
 *
 * Long-press opens the inline quick-threshold sheet (green + red floor sliders for the preferred
 * metric, persisting through [VerdictChipCallbacks.onThresholdChange]); drag moves the chip. A plain
 * tap does nothing — the old tap-to-expand detail was removed (it covered the host screen, fired by
 * accident mid-drag, and added no decision-relevant signal). It is parked for possible reuse in
 * docs/parked/verdict-chip-tap-to-expand.md.
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
    var sheetOpen by remember { mutableStateOf(false) }

    // The verdict is colour-only on screen now, so TalkBack gets it spoken here: the level word
    // (verde/amarillo/rojo) leads the chip's description so blind/low-vision drivers don't lose it.
    val chipDescription = androidx.compose.ui.res.stringResource(
        R.string.overlay_chip_content_description,
        androidx.compose.ui.res.stringResource(state.level.labelRes),
    )

    Column(
        modifier = modifier
            .widthIn(min = 132.dp)
            // Size to the chip's own content (the body's net rate), then let the brand strip + body
            // fillMaxWidth fill THAT — not the overlay window's max width. Without this the
            // fillMaxWidth children would stretch the floating pill into a screen-wide touch band.
            .width(IntrinsicSize.Max)
            .clip(RoundedCornerShape(16.dp))
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
                detectTapGestures(onLongPress = { sheetOpen = true })
            }
            .semantics { contentDescription = chipDescription },
    ) {
        BrandStrip()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(state.level.brandColor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            CollapsedContent(state)

            if (state.hasMissingData) {
                Spacer(Modifier.height(4.dp))
                MissingHint(state.missingHintKind)
            }
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
    // No verdict word: the chip's colour already carries good/regular/bad, so dropping the label
    // line keeps the chip thin and the net rate the one thing the driver reads (B-080). The
    // verdict is still spoken via the chip's contentDescription (set in VerdictChipUi).
    val onColor = state.level.onBrandColor
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

/**
 * The brand strip atop the chip: the Kompara "K" mark + wordmark on a brand-green band. Always
 * visible so a screenshot the driver shares carries the brand. Sits above the verdict-coloured body;
 * the chip's outer clip rounds both into one pill.
 */
@Composable
private fun BrandStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandGreen)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(mx.kompara.ui.R.drawable.ic_kompara_logomark),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.overlay_brand_name),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
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
