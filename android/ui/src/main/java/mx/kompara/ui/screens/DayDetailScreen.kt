package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.CardTone
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.VerdictBadge
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.CompletenessHint
import mx.kompara.ui.stats.DayDetail
import mx.kompara.ui.stats.DayDetailUiState
import mx.kompara.ui.stats.DayDetailViewModel
import mx.kompara.ui.stats.HourBlock
import mx.kompara.ui.stats.OfferFunnel
import mx.kompara.ui.stats.ShiftTimelineItem
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/**
 * The day-detail screen (B-040 req 2): per-shift timeline, the offer funnel (seen/taken/declined with
 * verdicts), best-hour blocks, plus the five MetricCards for the day.
 */
@Composable
fun DayDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: DayDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        is DayDetailUiState.Loading -> Spacer(modifier.fillMaxSize())
        is DayDetailUiState.Loaded -> DayDetailContent(detail = s.detail, modifier = modifier)
    }
}

@Composable
private fun DayDetailContent(detail: DayDetail, modifier: Modifier = Modifier) {
    if (detail.period.isEmpty && detail.offers.seen == 0 && detail.shifts.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.DateRange,
            title = stringResource(R.string.day_empty_title),
            body = stringResource(R.string.day_empty_body),
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = Formatters.formatDayLabel(detail.dayIso),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        MetricCardsBlock(detail.period)

        if (detail.completeness != CompletenessHint.NONE) {
            val note = when (detail.completeness) {
                CompletenessHint.HOURS_INFERRED -> stringResource(R.string.completeness_hours_inferred)
                CompletenessHint.HOURS_MISSING -> stringResource(R.string.completeness_hours_missing)
                CompletenessHint.NONE -> ""
            }
            Text(
                text = "ⓘ $note",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (detail.shifts.isNotEmpty()) {
            Section(title = stringResource(R.string.day_shifts_title)) {
                detail.shifts.forEach { ShiftLine(it) }
            }
        }

        Section(title = stringResource(R.string.day_offers_title)) {
            OffersFunnelLine(detail.offers)
            // PRESERVED richer-than-mock state: per-offer detail rows. The mock shows only the
            // aggregate funnel line, but we keep each offer (clock · fare + its verdict badge),
            // given the same DayListRow card chrome for consistency.
            detail.offers.rows.forEach { row ->
                DayListRow(
                    leading = {
                        Text(
                            text = "${Formatters.formatClock(row.seenAt)} · ${Formatters.formatMxn(row.fareMxn)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    trailing = { row.verdict?.let { VerdictBadge(level = it) } },
                )
            }
        }

        if (detail.bestHours.isNotEmpty()) {
            Section(title = stringResource(R.string.day_best_hours_title)) {
                detail.bestHours.take(3).forEach { BestHourLine(it) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    // 8dp gap (was 4dp): label-to-card and card-to-card spacing matches the mock's .listrow
    // margin-top:8px now that content rows are tonal cards rather than bare text.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            // Section label: design Dia .lbl is the small uppercase metricLabel (13/500, muted),
            // not a titleMedium heading.
            text = title,
            style = KomparaType.metricLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * The Compose form of the mock's `.listrow`: a tonal [KomparaCard] (radius 12, no shadow,
 * surfaceContainer fill = mock `--surface-card`) wrapping a SpaceBetween row with 14×16 padding.
 * [leading] is the row's primary content; [trailing] is the optional right-aligned slot.
 */
@Composable
private fun DayListRow(
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    KomparaCard(tone = CardTone.DEFAULT, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            trailing?.invoke()
        }
    }
}

@Composable
private fun ShiftLine(item: ShiftTimelineItem) {
    val end = item.endedAt?.let { Formatters.formatClock(it) } ?: stringResource(R.string.day_shift_open)
    DayListRow(
        leading = {
            Text(
                text = stringResource(
                    R.string.day_shift_line,
                    Formatters.formatClock(item.startedAt),
                    end,
                    item.tripCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailing = {
            // Shift net: emerald (brand primary, NOT a verdict) + bold + right-aligned per the mock.
            Text(
                text = Formatters.formatMxn(item.netMxn),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun OffersFunnelLine(funnel: OfferFunnel) {
    DayListRow(
        leading = {
            Text(
                text = stringResource(R.string.day_offers_funnel, funnel.seen, funnel.taken, funnel.declined),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun BestHourLine(block: HourBlock) {
    // QUESTION: the mock's best-hours trailing element is a VerdictBadge (green/yellow), but
    // HourBlock has no verdict field. Inventing one would break the "verdict colours = verdicts
    // only" rule, so we keep the net money figure as the trailing element instead. If product
    // wants the verdict pill here, HourBlock needs a real verdict level.
    DayListRow(
        leading = {
            Text(
                text = stringResource(R.string.day_best_hour_line, Formatters.formatHourRange(block.hour), block.trips),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailing = {
            Text(text = Formatters.formatMxn(block.netMxn), style = MaterialTheme.typography.bodyMedium)
        },
    )
}

@Preview(showBackground = true, name = "Day detail — vacío")
@Composable
private fun DayDetailEmptyPreview() {
    KomparaTheme {
        DayDetailContent(
            detail = DayDetail(
                dayIso = "2026-06-10",
                period = mx.kompara.ui.stats.PeriodStats.EMPTY,
                shifts = emptyList(),
                offers = OfferFunnel(seen = 0, taken = 0, declined = 0, pending = 0, rows = emptyList()),
                bestHours = emptyList(),
                completeness = CompletenessHint.NONE,
            ),
        )
    }
}
