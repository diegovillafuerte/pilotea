package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.ui.R
import mx.kompara.ui.stats.ThresholdEditor
import mx.kompara.ui.stats.ThresholdsViewModel
import java.util.Locale

/**
 * Ajustes → "Tu semáforo" (B-070): the full threshold editor — the metric that decides the light
 * (B-079: IPK or IPH, the other stays as context) plus green + red floors for both net $/km and
 * net $/hr, with a reset to the city-seeded median. One semáforo shared by every platform (B-076).
 * Changes persist immediately (same store the overlay quick sheet and the engine read), so there
 * is no save button.
 */
@Composable
fun ThresholdsScreen(
    modifier: Modifier = Modifier,
    viewModel: ThresholdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.thresholds_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.thresholds_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.thresholds_metric_title))
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.thresholds_metric_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.preferredMetric == PreferredMetric.IPK,
                onClick = { viewModel.setPreferredMetric(PreferredMetric.IPK) },
                label = { Text(stringResource(R.string.thresholds_metric_ipk)) },
            )
            FilterChip(
                selected = state.preferredMetric == PreferredMetric.IPH,
                onClick = { viewModel.setPreferredMetric(PreferredMetric.IPH) },
                label = { Text(stringResource(R.string.thresholds_metric_iph)) },
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.thresholds_section_km))
        FloorSliderRow(
            label = stringResource(R.string.thresholds_green_label),
            valueText = stringResource(R.string.thresholds_value_km, money2(state.threshold.minPerKmMxn)),
            value = state.threshold.minPerKmMxn,
            range = ThresholdEditor.MIN_PER_KM..ThresholdEditor.MAX_PER_KM,
            steps = ThresholdEditor.kmStepCount,
            onCommit = viewModel::setGreenPerKm,
        )
        FloorSliderRow(
            label = stringResource(R.string.thresholds_red_label),
            valueText = stringResource(R.string.thresholds_value_km, money2(state.threshold.redPerKmMxn)),
            value = state.threshold.redPerKmMxn,
            range = ThresholdEditor.MIN_PER_KM..ThresholdEditor.MAX_PER_KM,
            steps = ThresholdEditor.kmStepCount,
            onCommit = viewModel::setRedPerKm,
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.thresholds_section_hour))
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

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = viewModel::resetToCityDefault) {
            Text(stringResource(R.string.thresholds_reset, state.cityDisplayName))
        }
        Text(
            text = stringResource(R.string.thresholds_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/**
 * One floor: label + current value + slider. The slider holds a local value while dragging and
 * persists through [onCommit] on release; [value] re-seeds the local state when persistence (or a
 * platform switch) emits a new figure.
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
        Text(text = valueText, fontWeight = FontWeight.Black)
    }
    Slider(
        value = local.toFloat(),
        onValueChange = { local = it.toDouble() },
        onValueChangeFinished = { onCommit(local) },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        steps = steps,
    )
}

private fun money2(value: Double): String =
    String.format(Locale.forLanguageTag("es-MX"), "%.2f", value)

private fun money0(value: Double): String =
    String.format(Locale.forLanguageTag("es-MX"), "%.0f", value)
