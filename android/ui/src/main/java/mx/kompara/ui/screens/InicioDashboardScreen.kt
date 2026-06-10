package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import mx.kompara.billing.GateState
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.KomparaProgressBar
import mx.kompara.ui.components.MetricCard
import mx.kompara.ui.components.WatchdogBanner
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.onboarding.WatchdogState
import mx.kompara.ui.paywall.GateFunnel
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.paywall.PaywallGate
import mx.kompara.ui.stats.CompletenessHint
import mx.kompara.ui.stats.GoalProgress
import mx.kompara.ui.stats.InicioDashboardViewModel
import mx.kompara.ui.stats.InicioUiState
import mx.kompara.ui.stats.MetricCardValues
import mx.kompara.ui.stats.MetricPercentiles
import mx.kompara.ui.stats.StreakDisplay
import mx.kompara.ui.stats.platformChipLabel
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/**
 * The Inicio dashboard (B-040 req 1): this-week net header, streak badge, weekly-goal progress, the
 * watchdog banner, platform chips, five metric cards, the completeness hint and the cost-profile
 * first-run nudge. Replaces the old placeholder.
 *
 * @param onOpenCostProfile open the cost-profile editor (nudge CTA).
 * @param onOpenToday open the day-detail for today ("Hoy" section).
 * @param onOpenReaderTrial CTA on the new-driver empty state.
 * @param onOpenShareCard open the shareable earnings-card preview (B-055; header share icon).
 */
@Composable
fun InicioDashboardScreen(
    modifier: Modifier = Modifier,
    onOpenCostProfile: () -> Unit = {},
    onOpenToday: () -> Unit = {},
    onOpenReaderTrial: () -> Unit = {},
    onUpgrade: (GateSurface) -> Unit = {},
    onOpenShareCard: () -> Unit = {},
    dashboardViewModel: InicioDashboardViewModel = hiltViewModel(),
) {
    val watchdogState by dashboardViewModel.watchdogState.collectAsStateWithLifecycle()
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        if (watchdogState == WatchdogState.DROPPED) {
            WatchdogBanner(onReEnable = dashboardViewModel::reEnableReader)
        }
        when {
            state.loading -> Spacer(Modifier.fillMaxSize())
            !state.hasData -> EmptyState(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.dashboard_empty_title),
                body = stringResource(R.string.dashboard_empty_body),
                ctaText = stringResource(R.string.dashboard_empty_cta),
                onCtaClick = onOpenReaderTrial,
            )
            else -> DashboardContent(
                state = state,
                onSelectPlatform = dashboardViewModel::selectPlatform,
                onOpenCostProfile = onOpenCostProfile,
                onOpenToday = onOpenToday,
                gateFunnel = dashboardViewModel.gateFunnel,
                onUpgrade = onUpgrade,
                onOpenShareCard = onOpenShareCard,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: InicioUiState,
    onSelectPlatform: (mx.kompara.data.model.Platform?) -> Unit,
    onOpenCostProfile: () -> Unit,
    onOpenToday: () -> Unit,
    gateFunnel: GateFunnel? = null,
    onUpgrade: (GateSurface) -> Unit = {},
    onOpenShareCard: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(net = state.period.netEarningsMxn, streak = state.streak, onOpenShareCard = onOpenShareCard)

        if (state.goal.hasGoal) {
            GoalBar(goal = state.goal)
        }

        if (!state.costProfileSet) {
            CostNudge(onOpenCostProfile = onOpenCostProfile)
        }

        if (state.chips.isNotEmpty()) {
            PlatformChips(
                chips = state.chips,
                selected = state.selectedPlatform,
                onSelect = onSelectPlatform,
            )
        }

        MetricCardValues.of(state.period).forEachIndexed { index, card ->
            PercentileMetricCard(
                label = stringResource(card.labelRes),
                value = card.value,
                percentile = MetricPercentiles.forCard(index, state.percentiles.byMetric),
                locked = state.percentiles.locked,
            )
        }

        // B-050: when benchmarks are gated, a single tease-then-gate upsell beneath the cards routes the
        // conversion through the shared PaywallGate (the per-card bars already show the dimmed stand-in).
        if (state.percentiles.locked && gateFunnel != null) {
            PaywallGate(
                surface = GateSurface.BENCHMARKS,
                state = GateState.LOCKED,
                valueHint = stringResource(R.string.gate_hint_benchmarks),
                funnel = gateFunnel,
                onUpgrade = onUpgrade,
                ctaText = stringResource(R.string.paywall_cta),
            ) {
                // Teaser preview content (blurred by the gate): a neutral comparison strip.
                Card(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {}
            }
        }

        if (state.completeness != CompletenessHint.NONE) {
            CompletenessNote(hint = state.completeness)
        }

        Text(
            text = stringResource(R.string.day_view_detail),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenToday)
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Header(net: Double, streak: StreakDisplay, onOpenShareCard: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_net_label),
            style = KomparaType.metricLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Formatters.formatMxn(net),
                style = KomparaType.metricValueLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (streak.visible) {
                    StreakBadge(streak)
                }
                IconButton(onClick = onOpenShareCard) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.share_card_open_desc),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakBadge(streak: StreakDisplay) {
    val text = if (streak.singular) {
        stringResource(R.string.dashboard_streak_singular, streak.weeks)
    } else {
        stringResource(R.string.dashboard_streak_plural, streak.weeks)
    }
    Text(
        text = "🔥 $text",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun GoalBar(goal: GoalProgress) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.dashboard_goal_progress,
                Formatters.formatMxn(goal.netMxn),
                Formatters.formatMxn(goal.goalMxn),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        KomparaProgressBar(progress = goal.fraction)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (goal.reached) {
                stringResource(R.string.dashboard_goal_reached)
            } else {
                stringResource(R.string.dashboard_goal_remaining, Formatters.formatMxn(goal.remainingMxn))
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CostNudge(onOpenCostProfile: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCostProfile),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.cost_nudge_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cost_nudge_cta),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlatformChips(
    chips: List<mx.kompara.data.model.Platform?>,
    selected: mx.kompara.data.model.Platform?,
    onSelect: (mx.kompara.data.model.Platform?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            FilterChip(
                selected = chip == selected,
                onClick = { onSelect(chip) },
                label = { Text(stringResource(platformChipLabel(chip))) },
            )
        }
    }
}

@Composable
private fun CompletenessNote(hint: CompletenessHint) {
    val text = when (hint) {
        CompletenessHint.HOURS_INFERRED -> stringResource(R.string.completeness_hours_inferred)
        CompletenessHint.HOURS_MISSING -> stringResource(R.string.completeness_hours_missing)
        CompletenessHint.NONE -> return
    }
    Text(
        text = "ⓘ $text",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true, name = "Dashboard — con datos")
@Composable
private fun DashboardContentPreview() {
    KomparaTheme {
        DashboardContent(
            state = InicioUiState(
                loading = false,
                hasData = true,
                period = mx.kompara.ui.stats.PeriodStats(
                    netEarningsMxn = 3450.0,
                    grossEarningsMxn = 4200.0,
                    totalTrips = 38,
                    totalKm = 410.0,
                    hoursOnline = 22.5,
                    earningsPerTrip = 90.8,
                    earningsPerKm = 8.4,
                    earningsPerHour = 153.3,
                    tripsPerHour = 1.7,
                    acceptanceRate = 0.62,
                ),
                chips = listOf(null, mx.kompara.data.model.Platform.UBER, mx.kompara.data.model.Platform.DIDI),
                selectedPlatform = null,
                goal = GoalProgress.of(5000.0, 3450.0),
                streak = StreakDisplay(4),
                completeness = CompletenessHint.HOURS_INFERRED,
                costProfileSet = false,
            ),
            onSelectPlatform = {},
            onOpenCostProfile = {},
            onOpenToday = {},
        )
    }
}
