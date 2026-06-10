package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.HistoryUiState
import mx.kompara.ui.stats.HistoryViewModel
import mx.kompara.ui.stats.HistoryWeek
import mx.kompara.ui.stats.WeekSourceBadge
import mx.kompara.ui.theme.KomparaTheme

/**
 * The History tab (B-040 req 3): the weeks list with a source badge (capturado/importado). Tapping a
 * week opens its summary ([onOpenWeek]). The "Importar semana" CTA ([onImportWeek]) opens the B-045
 * import flow to backfill weeks the reader didn't capture live.
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onOpenWeek: (String) -> Unit = {},
    onImportWeek: () -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(state = state, onOpenWeek = onOpenWeek, onImportWeek = onImportWeek, modifier = modifier)
}

@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onOpenWeek: (String) -> Unit,
    onImportWeek: () -> Unit,
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
            item(key = "import-cta") {
                PrimaryButton(
                    text = stringResource(R.string.history_import_cta),
                    onClick = onImportWeek,
                )
            }
            items(state.weeks, key = { it.weekStart + it.source.name }) { week ->
                WeekRow(week = week, onClick = { onOpenWeek(week.weekStart) })
            }
        }
    }
}

@Composable
private fun WeekRow(week: HistoryWeek, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Formatters.formatWeekLabel(week.weekStart),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SourceBadge(week.source)
            }
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                text = Formatters.formatMxn(week.period.netEarningsMxn),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${week.period.totalTrips} ${stringResource(R.string.summary_trips).lowercase()} · " +
                    Formatters.formatHours(week.period.hoursOnline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceBadge(source: WeekSourceBadge) {
    val (text, container, content) = when (source) {
        WeekSourceBadge.CAPTURADO -> Triple(
            stringResource(R.string.history_badge_capturado),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        WeekSourceBadge.IMPORTADO -> Triple(
            stringResource(R.string.history_badge_importado),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true, name = "History — con semanas")
@Composable
private fun HistoryContentPreview() {
    KomparaTheme {
        HistoryContent(
            state = HistoryUiState(
                loading = false,
                weeks = listOf(
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
            ),
            onOpenWeek = {},
            onImportWeek = {},
        )
    }
}
