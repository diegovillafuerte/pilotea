package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.kompara.data.model.Platform
import mx.kompara.ui.components.MetricCard
import mx.kompara.ui.stats.MetricCardValues
import mx.kompara.ui.stats.PeriodStats
import mx.kompara.ui.stats.platformChipLabel

/**
 * The five MetricCards for a [PeriodStats], shared by Inicio, day-detail and week-summary so the
 * stats surfaces read identically (B-040 req 3: "week summary reusing MetricCards").
 */
@Composable
fun MetricCardsBlock(stats: PeriodStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCardValues.of(stats).forEach { card ->
            MetricCard(label = stringResource(card.labelRes), value = card.value)
        }
    }
}

/** A compact platform-chip row reused across stats screens; null entry = "Todas". */
@Composable
fun StatsPlatformChips(
    chips: List<Platform?>,
    selected: Platform?,
    onSelect: (Platform?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            FilterChip(
                selected = chip == selected,
                onClick = { onSelect(chip) },
                label = { Text(stringResource(platformChipLabel(chip))) },
            )
        }
    }
}

/** A simple labelled key/value row used by the period summary strip. */
@Composable
fun SummaryRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
