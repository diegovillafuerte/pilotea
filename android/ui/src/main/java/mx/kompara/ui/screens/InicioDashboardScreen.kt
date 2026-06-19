package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import mx.kompara.ui.components.KomparaChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.billing.GateState
import mx.kompara.ui.R
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.metrics.recommendation.Recommendation
import mx.kompara.metrics.recommendation.RecommendationType
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.KomparaProgressBar
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.LockedPercentileBadge
import mx.kompara.ui.components.PercentileBadge
import mx.kompara.ui.components.RecommendationCard
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
import mx.kompara.ui.stats.MetricCardValue
import mx.kompara.ui.stats.MetricCardValues
import mx.kompara.ui.stats.MetricPercentiles
import mx.kompara.ui.stats.PercentilesUiState
import mx.kompara.ui.stats.RecommendationsUiState
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
    onImport: () -> Unit = {},
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
                onImport = onImport,
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
    onImport: () -> Unit = {},
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

        // The four headline tiles, 2-up like the design's Inicio hub. Acceptance rate (the 5th
        // metric) stays on Día/Tu mes; Inicio shows the four money/efficiency tiles only.
        MetricGrid(
            cards = MetricCardValues.of(state.period).take(4),
            percentiles = state.percentiles,
        )

        // B-050: when benchmarks are gated, a single tease-then-gate upsell beneath the cards routes the
        // conversion through the shared PaywallGate (the per-card bars already show the dimmed stand-in).
        if (state.percentiles.locked && gateFunnel != null) {
            PaywallGate(
                surface = GateSurface.BENCHMARKS,
                state = state.percentiles.gateState,
                valueHint = stringResource(R.string.gate_hint_benchmarks),
                funnel = gateFunnel,
                onUpgrade = onUpgrade,
                ctaText = stringResource(R.string.paywall_cta),
                // Premium-but-unverified (PR-E): the CTA imports a week to verify, not the paywall.
                onVerify = { onImport() },
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

        if (state.recommendations.hasAny) {
            RecommendationsSection(
                state = state.recommendations,
                gateFunnel = gateFunnel,
                onUpgrade = onUpgrade,
            )
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
    // Nudge/accent card recipe (design tokens): brand-tint fill + a brand hairline, NOT a verdict
    // colour — verde/amarillo/rojo are reserved for verdicts only.
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenCostProfile)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.cost_nudge_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.cost_nudge_cta),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
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
            KomparaChip(
                selected = chip == selected,
                onClick = { onSelect(chip) },
                label = stringResource(platformChipLabel(chip)),
            )
        }
    }
}

/**
 * The Inicio metric tiles in a 2-column grid (the design system's Inicio hub): each tile is a
 * [MetricCard] with an inline "Top X%" pill. The per-card 20-person percentile bars now live on
 * Comparar (the Inicio = individual / Comparar = comparison split). An odd final tile sits half-width.
 */
@Composable
private fun MetricGrid(cards: List<MetricCardValue>, percentiles: PercentilesUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEachIndexed { colIndex, card ->
                    val index = rowIndex * 2 + colIndex
                    InicioMetricTile(
                        label = stringResource(card.labelRes),
                        value = card.value,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        badge = metricBadge(
                            MetricPercentiles.forCard(index, percentiles.byMetric),
                            percentiles.locked,
                        ),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One Inicio metric tile: the label on its OWN full-width line (so it stays fully readable in a
 * narrow 2-up grid cell — no competing with the badge), then the big value with an optional
 * "Top X%" / locked pill beside it.
 */
@Composable
private fun InicioMetricTile(
    label: String,
    value: String,
    badge: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    KomparaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = KomparaType.metricLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = KomparaType.metricValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                Spacer(Modifier.height(8.dp))
                badge()
            }
        }
    }
}

/** The inline percentile pill for a metric tile: "Top X%", the premium-locked stand-in, or none. */
private fun metricBadge(
    percentile: PercentileResult?,
    locked: Boolean,
): (@Composable () -> Unit)? = when {
    percentile == null -> null
    locked -> {
        { LockedPercentileBadge() }
    }
    else -> {
        {
            PercentileBadge(
                topPercent = percentile.topPercent,
                contentDescription = stringResource(
                    R.string.percentile_badge_description,
                    percentile.displayPercentile,
                ),
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

/**
 * The "Consejos" section (B-048): a header, the free recommendation cards rendered verbatim, then the
 * premium ones — rendered verbatim when unlocked, or wrapped in a single [PaywallGate] tease when the
 * [Capability.RECOMMENDATIONS][mx.kompara.billing.Capability.RECOMMENDATIONS] gate is locked. The
 * caller only mounts this when [RecommendationsUiState.hasAny] is true, so the section never shows
 * empty.
 */
@Composable
private fun RecommendationsSection(
    state: RecommendationsUiState,
    gateFunnel: GateFunnel? = null,
    onUpgrade: (GateSurface) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.recommendations_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        state.free.forEach { rec -> RecommendationItem(rec) }

        if (state.hasPremium) {
            if (state.locked && gateFunnel != null) {
                // One tease-then-gate wrapper over the premium tips: the driver sees the shape + upsell.
                PaywallGate(
                    surface = GateSurface.RECOMMENDATIONS,
                    state = GateState.LOCKED,
                    valueHint = stringResource(R.string.gate_hint_recommendations),
                    funnel = gateFunnel,
                    onUpgrade = onUpgrade,
                    ctaText = stringResource(R.string.paywall_cta),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.premium.forEach { rec -> RecommendationItem(rec) }
                    }
                }
            } else if (!state.locked) {
                state.premium.forEach { rec -> RecommendationItem(rec) }
            }
        }
    }
}

@Composable
private fun RecommendationItem(rec: Recommendation) {
    RecommendationCard(type = rec.type, title = rec.title, body = rec.body)
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
                recommendations = RecommendationsUiState(
                    recommendations = listOf(
                        Recommendation(
                            id = "missed_good_offers",
                            type = RecommendationType.WARNING,
                            title = "Dejaste ir buenos viajes",
                            body = "Rechazaste 3 ofertas que sí te convenían — \$420 que se te fueron.",
                            premium = false,
                        ),
                        Recommendation(
                            id = "best_hours",
                            type = RecommendationType.INFO,
                            title = "Tus mejores horas",
                            body = "Tu mejor bloque fue el viernes de 19:00 a 20:00: \$340 netos.",
                            premium = false,
                        ),
                    ),
                    locked = false,
                ),
            ),
            onSelectPlatform = {},
            onOpenCostProfile = {},
            onOpenToday = {},
        )
    }
}
