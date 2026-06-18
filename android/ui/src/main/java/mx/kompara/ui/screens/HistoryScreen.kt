package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.billing.GateState
import mx.kompara.ui.R
import mx.kompara.ui.components.ButtonSize
import mx.kompara.ui.components.ButtonVariant
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.KomparaButton
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.KomparaStatusChip
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.components.StatusLevel
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.paywall.GateFunnel
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.paywall.PaywallGate
import mx.kompara.ui.stats.HistoryUiState
import mx.kompara.ui.stats.HistoryViewModel
import mx.kompara.ui.stats.HistoryWeek
import mx.kompara.ui.stats.WeekSourceBadge
import mx.kompara.ui.theme.KomparaTheme

/**
 * The History tab (B-040 req 3): the weeks list with a source badge (capturado/importado). Tapping a
 * week opens its summary ([onOpenWeek]). The "Importar semana" CTA ([onImportWeek]) opens the B-045
 * import flow to backfill weeks the reader didn't capture live.
 *
 * B-050: the free tier sees the current + previous week; older weeks render blurred behind a
 * [PaywallGate] whose CTA opens the paywall ([onUpgrade]).
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onOpenWeek: (String) -> Unit = {},
    onImportWeek: () -> Unit = {},
    onUpgrade: (GateSurface) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onOpenWeek = onOpenWeek,
        onImportWeek = onImportWeek,
        onUpgrade = onUpgrade,
        gateFunnel = viewModel.gateFunnel,
        modifier = modifier,
    )
}

@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onOpenWeek: (String) -> Unit,
    onImportWeek: () -> Unit,
    onUpgrade: (GateSurface) -> Unit = {},
    gateFunnel: GateFunnel? = null,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading -> Spacer(modifier.fillMaxSize())
        state.isEmpty -> Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EmptyState(
                icon = Icons.Filled.DateRange,
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.history_import_cta),
                onClick = onImportWeek,
            )
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header row: screen title on the left + a small tonal "Importar semana" inline action
            // on the right (import is a secondary action, so it demotes from a full-width PRIMARY).
            // weight(1f, fill=false) + the 8dp spacer keep the title from ever clipping the button.
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    KomparaButton(
                        text = stringResource(R.string.history_import_cta),
                        onClick = onImportWeek,
                        variant = ButtonVariant.TONAL,
                        size = ButtonSize.SM,
                    )
                }
            }
            items(state.weeks, key = { it.weekStart + it.source.name }) { week ->
                WeekRow(week = week, onClick = { onOpenWeek(week.weekStart) })
            }
            // B-050: the premium-locked older weeks, teased behind the shared gate.
            if (state.lockedWeeks.isNotEmpty() && gateFunnel != null) {
                item(key = "history-gate") {
                    PaywallGate(
                        surface = GateSurface.HISTORY,
                        state = GateState.LOCKED,
                        valueHint = stringResource(R.string.history_free_limit_body),
                        funnel = gateFunnel,
                        onUpgrade = onUpgrade,
                        ctaText = stringResource(R.string.paywall_cta),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.lockedWeeks.forEach { week ->
                                WeekRow(week = week, onClick = {})
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(week: HistoryWeek, onClick: () -> Unit) {
    // Slim list row (mock .listrow): leads with the net peso figure a driver scans for, with a muted
    // date range below and a single neutral chip on the right. The full per-week metrics
    // (trips · hours) live one tap deeper in the week summary. weight(1f) + the 8dp spacer keep the
    // chip from ever being clipped by a long money string.
    KomparaCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Formatters.formatMxn(week.period.netEarningsMxn),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = Formatters.formatWeekRangeLabel(week.weekStart),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            SourceBadge(week.source)
        }
    }
}

@Composable
private fun SourceBadge(source: WeekSourceBadge) {
    val text = when (source) {
        WeekSourceBadge.CAPTURADO -> stringResource(R.string.history_badge_capturado)
        WeekSourceBadge.IMPORTADO -> stringResource(R.string.history_badge_importado)
    }
    // Design shows both source labels as a neutral status pill — the label text carries the
    // capturado/importado distinction, so colour is not the only signal.
    KomparaStatusChip(label = text, level = StatusLevel.NEUTRAL)
}

@Preview(showBackground = true, name = "History — con semanas")
@Composable
private fun HistoryContentPreview() {
    KomparaTheme {
        HistoryContent(
            state = HistoryUiState(
                loading = false,
                partition = mx.kompara.ui.stats.HistoryPartition(
                    visible = listOf(
                        HistoryWeek(
                            weekStart = "2026-06-08",
                            source = WeekSourceBadge.CAPTURADO,
                            period = mx.kompara.ui.stats.PeriodStats(
                                netEarningsMxn = 3450.0, grossEarningsMxn = 4200.0, totalTrips = 38,
                                totalKm = 410.0, hoursOnline = 22.5, earningsPerTrip = 90.8,
                                earningsPerKm = 8.4, earningsPerHour = 153.3, tripsPerHour = 1.7,
                                acceptanceRate = 0.62,
                            ),
                        ),
                    ),
                    locked = emptyList(),
                ),
            ),
            onOpenWeek = {},
            onImportWeek = {},
        )
    }
}
