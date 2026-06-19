package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.WeekSummaryUiState
import mx.kompara.ui.stats.WeekSummaryViewModel

/**
 * Week summary reached from the History list (B-040 req 3): the same MetricCards as Inicio, plus a
 * net/bruto/viajes/km/horas strip, for the selected week. Platform chips appear when the week has
 * data from more than one platform.
 */
@Composable
fun WeekSummaryScreen(
    modifier: Modifier = Modifier,
    onOpenShareCard: () -> Unit = {},
    onImport: () -> Unit = {},
    viewModel: WeekSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeekSummaryContent(
        state = state,
        onSelectPlatform = viewModel::selectPlatform,
        onOpenShareCard = onOpenShareCard,
        onImport = onImport,
        modifier = modifier,
    )
}

@Composable
private fun WeekSummaryContent(
    state: WeekSummaryUiState,
    onSelectPlatform: (mx.kompara.data.model.Platform?) -> Unit,
    onOpenShareCard: () -> Unit = {},
    onImport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!state.loading && !state.hasData) {
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
            text = Formatters.formatWeekLabel(state.weekStart),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        StatsPlatformChips(
            chips = state.chips,
            selected = state.selectedPlatform,
            onSelect = onSelectPlatform,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryRow(stringResource(R.string.summary_net), Formatters.formatMxn(state.period.netEarningsMxn))
            SummaryRow(stringResource(R.string.summary_gross), Formatters.formatMxn(state.period.grossEarningsMxn))
            SummaryRow(stringResource(R.string.summary_trips), state.period.totalTrips.toString())
            SummaryRow(stringResource(R.string.summary_km), Formatters.formatKm(state.period.totalKm))
            SummaryRow(stringResource(R.string.summary_hours), Formatters.formatHours(state.period.hoursOnline))
        }

        Spacer(Modifier.padding(top = 0.dp))
        MetricCardsBlock(state.period, percentiles = state.percentiles)

        // PR-E: a premium-but-unverified driver's locked benchmark stand-in here would otherwise be a
        // dead-end ("disponible con Premium" with no action they can take — paying can't satisfy
        // verification). Offer the same import-to-verify CTA the Inicio/Comparar gates route to.
        if (state.percentiles.gateState.isNeedsVerification) {
            PrimaryButton(
                text = stringResource(R.string.gate_verify_cta),
                onClick = onImport,
            )
        }

        // B-055: share this week as a "Tu Semana" card.
        PrimaryButton(
            text = stringResource(R.string.share_card_share_cta),
            onClick = onOpenShareCard,
        )
    }
}
