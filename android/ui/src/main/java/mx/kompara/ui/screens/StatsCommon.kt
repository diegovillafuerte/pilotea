package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.kompara.data.model.Platform
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.ui.components.MetricCard
import mx.kompara.ui.components.PercentileBadge
import mx.kompara.ui.components.PercentilePanel
import mx.kompara.ui.stats.MetricCardValues
import mx.kompara.ui.stats.MetricPercentiles
import mx.kompara.ui.stats.PercentilesUiState
import mx.kompara.ui.stats.PeriodStats
import mx.kompara.ui.stats.platformChipLabel

/**
 * The five MetricCards for a [PeriodStats], shared by Inicio, day-detail and week-summary so the
 * stats surfaces read identically (B-040 req 3: "week summary reusing MetricCards"). When
 * [percentiles] carries standings (B-046) each card also shows its "Top X%" badge + 20-person bar,
 * or the premium-locked stand-in when [PercentilesUiState.locked].
 */
@Composable
fun MetricCardsBlock(
    stats: PeriodStats,
    modifier: Modifier = Modifier,
    percentiles: PercentilesUiState = PercentilesUiState.EMPTY,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCardValues.of(stats).forEachIndexed { index, card ->
            PercentileMetricCard(
                label = stringResource(card.labelRes),
                value = card.value,
                percentile = MetricPercentiles.forCard(index, percentiles.byMetric),
                locked = percentiles.locked,
            )
        }
    }
}

/**
 * A [MetricCard] with the B-046 percentile overlay: a "Top X%" badge in the card's badge slot and a
 * 20-person [PercentilePanel] beneath it. When [locked] is true the badge/bar render the premium
 * stand-in instead of the real standing. When there's no [percentile] (acceptance card, no benchmark
 * cached, or no value) the card renders bare — just label + value.
 *
 * The bar/badge appear only for a metric that actually has a benchmark; the locked stand-in is shown
 * only when there *would* be a standing (i.e. we have a [percentile]) so a free driver sees the
 * teaser exactly where the value lives, not on cards that never benchmark.
 */
@Composable
fun PercentileMetricCard(
    label: String,
    value: String,
    percentile: PercentileResult?,
    locked: Boolean,
    modifier: Modifier = Modifier,
) {
    val show = percentile != null
    Column(modifier = modifier.fillMaxWidth()) {
        MetricCard(
            label = label,
            value = value,
            badge = if (show && !locked) {
                {
                    PercentileBadge(
                        topPercent = percentile!!.topPercent,
                        contentDescription = stringResource(
                            mx.kompara.ui.R.string.percentile_badge_description,
                            percentile.displayPercentile,
                        ),
                    )
                }
            } else {
                null
            },
        )
        if (show) {
            Spacer(Modifier.height(6.dp))
            val descRes = if (percentile!!.isNationalFallback) {
                mx.kompara.ui.R.string.percentile_bar_description_national
            } else {
                mx.kompara.ui.R.string.percentile_bar_description
            }
            PercentilePanel(
                displayPercentile = percentile.displayPercentile,
                barContentDescription = stringResource(descRes, percentile.displayPercentile),
                isSynthetic = percentile.isSynthetic,
                locked = locked,
            )
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
