package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.R
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.KomparaSlider
import mx.kompara.ui.components.VerdictBadge
import mx.kompara.ui.stats.ThresholdEditor
import mx.kompara.ui.stats.ThresholdsViewModel
import mx.kompara.ui.theme.KomparaType
import java.util.Locale

/**
 * Ajustes → "Tu semáforo" (B-070 / B-079): the threshold editor. A segmented control picks the
 * metric that *decides* the light (net $/km or net $/hr — the other is context only, per B-079), and
 * a single tonal card edits that metric's green + red net floors with live sliders. A verdict-badge
 * legend names the three lights. One semáforo shared by every platform (B-076); changes persist
 * immediately (same store the overlay quick-sheet and the engine read), so there is no save button.
 */
@Composable
fun ThresholdsScreen(
    modifier: Modifier = Modifier,
    viewModel: ThresholdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isKm = state.preferredMetric == PreferredMetric.IPK

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.thresholds_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.thresholds_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.thresholds_metric_title),
            style = KomparaType.metricLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        MetricSegmentedControl(
            selectedKm = isKm,
            onSelect = viewModel::setPreferredMetric,
        )

        Spacer(Modifier.height(16.dp))
        KomparaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (isKm) {
                    FloorSliderRow(
                        label = stringResource(R.string.thresholds_green_label),
                        valueText = stringResource(R.string.thresholds_value_km, money1(state.threshold.minPerKmMxn)),
                        value = state.threshold.minPerKmMxn,
                        range = ThresholdEditor.MIN_PER_KM..ThresholdEditor.MAX_PER_KM,
                        steps = ThresholdEditor.kmStepCount,
                        onCommit = viewModel::setGreenPerKm,
                    )
                    FloorSliderRow(
                        label = stringResource(R.string.thresholds_red_label),
                        valueText = stringResource(R.string.thresholds_value_km, money1(state.threshold.redPerKmMxn)),
                        value = state.threshold.redPerKmMxn,
                        range = ThresholdEditor.MIN_PER_KM..ThresholdEditor.MAX_PER_KM,
                        steps = ThresholdEditor.kmStepCount,
                        onCommit = viewModel::setRedPerKm,
                    )
                } else {
                    FloorSliderRow(
                        label = stringResource(R.string.thresholds_green_label),
                        valueText = stringResource(R.string.thresholds_value_hour, money0(state.threshold.minPerHourMxn)),
                        value = state.threshold.minPerHourMxn,
                        range = ThresholdEditor.MIN_PER_HOUR..ThresholdEditor.MAX_PER_HOUR,
                        steps = ThresholdEditor.hourStepCount,
                        onCommit = viewModel::setGreenPerHour,
                    )
                    FloorSliderRow(
                        label = stringResource(R.string.thresholds_red_label),
                        valueText = stringResource(R.string.thresholds_value_hour, money0(state.threshold.redPerHourMxn)),
                        value = state.threshold.redPerHourMxn,
                        range = ThresholdEditor.MIN_PER_HOUR..ThresholdEditor.MAX_PER_HOUR,
                        steps = ThresholdEditor.hourStepCount,
                        onCommit = viewModel::setRedPerHour,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            VerdictBadge(level = VerdictLevel.GREEN)
            VerdictBadge(level = VerdictLevel.YELLOW)
            VerdictBadge(level = VerdictLevel.RED)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.thresholds_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = viewModel::resetToCityDefault,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.thresholds_reset, state.cityDisplayName))
        }
    }
}

/** Pill segmented control: container fills, the selected segment is a brand-emerald tab. */
@Composable
private fun MetricSegmentedControl(
    selectedKm: Boolean,
    onSelect: (PreferredMetric) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SegmentButton(
            text = stringResource(R.string.thresholds_metric_ipk),
            selected = selectedKm,
            onClick = { onSelect(PreferredMetric.IPK) },
        )
        SegmentButton(
            text = stringResource(R.string.thresholds_metric_iph),
            selected = !selectedKm,
            onClick = { onSelect(PreferredMetric.IPH) },
        )
    }
}

@Composable
private fun RowScope.SegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One floor: label + current value + slider. The slider holds a local value while dragging and
 * persists through [onCommit] on release; [value] re-seeds the local state when persistence (or a
 * metric switch) emits a new figure.
 */
@Composable
private fun FloorSliderRow(
    label: String,
    valueText: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    steps: Int,
    onCommit: (Double) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = valueText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
    KomparaSlider(
        value = local.toFloat(),
        onValueChange = { local = it.toDouble() },
        onValueChangeFinished = { onCommit(local) },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        steps = steps,
    )
}

private fun money1(value: Double): String =
    String.format(Locale.forLanguageTag("es-MX"), "%.1f", value)

private fun money0(value: Double): String =
    String.format(Locale.forLanguageTag("es-MX"), "%.0f", value)
