package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.billing.GateState
import mx.kompara.data.model.Platform
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.metrics.recommendation.Recommendation
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.KomparaWordmark
import mx.kompara.ui.components.PercentileBadge
import mx.kompara.ui.components.PercentileBar
import mx.kompara.ui.components.RecommendationCard
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.paywall.GateFunnel
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.paywall.PaywallGate
import mx.kompara.ui.stats.ComparisonRow
import mx.kompara.ui.stats.CompareUiState
import mx.kompara.ui.stats.MetricUnit
import mx.kompara.ui.stats.WeeklyComparison
import mx.kompara.ui.theme.BrandGreen
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.VerdictGreen

/**
 * The Comparar tab (S-024) — a weekly benchmarking hub. A branded, shareable percentile hero (FREE)
 * over a benchmark table comparing the driver's blended value to each platform's city average and to
 * their percentile vs. all drivers, plus comparison opportunities. The table + opportunities are the
 * premium payoff behind [GateSurface.COMPARE]. Weekly only — day/hour is deferred (B-090).
 */
@Composable
fun CompararScreen(
    modifier: Modifier = Modifier,
    onUpgrade: (GateSurface) -> Unit = {},
    onImport: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenInicio: () -> Unit = {},
    viewModel: mx.kompara.ui.stats.CompararViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gateState by viewModel.gateState.collectAsStateWithLifecycle()
    CompararContent(
        state = state,
        gateState = gateState,
        onSelectWeek = viewModel::selectWeek,
        onUpgrade = onUpgrade,
        onImport = onImport,
        onShare = onShare,
        onOpenInicio = onOpenInicio,
        gateFunnel = viewModel.gateFunnel,
        modifier = modifier,
    )
}

@Composable
private fun CompararContent(
    state: CompareUiState,
    gateState: GateState,
    onSelectWeek: (String) -> Unit,
    onUpgrade: (GateSurface) -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onOpenInicio: () -> Unit,
    gateFunnel: GateFunnel,
    modifier: Modifier = Modifier,
) {
    if (state.loading) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val data = state.data
            if (data == null) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.comparar_no_data_title),
                    body = stringResource(R.string.comparar_no_data_body),
                    ctaText = stringResource(R.string.comparar_no_data_cta),
                    onCtaClick = onImport,
                )
            } else {
                val c = data.comparison
                WeekDropdown(state.availableWeeks, data.weekStart, onSelectWeek)
                ShareableHeroCard(standing = c.standing, onShare = onShare)
                c.singlePlatform?.let { SinglePlatformNote(it) }
                PaywallGate(
                    surface = GateSurface.COMPARE,
                    state = gateState,
                    valueHint = stringResource(R.string.gate_hint_compare),
                    funnel = gateFunnel,
                    onUpgrade = onUpgrade,
                    ctaText = stringResource(R.string.paywall_cta),
                ) {
                    // Never hand real premium values to the (only alpha-dimmed on API 26–30) tease:
                    // when locked, render the table shape with masked values + no opportunities.
                    val gated = if (gateState.isLocked) c.maskedForLock() else c
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        BenchmarkTable(gated)
                        OpportunitiesSection(gated.opportunities)
                    }
                }
                CrossLinkToInicio(onOpenInicio)
                Text(
                    text = stringResource(R.string.comparar_soon_day_hour),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun WeekDropdown(weeks: List<String>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(enabled = weeks.size > 1) { open = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Formatters.formatWeekRangeLabel(selected),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (weeks.size > 1) {
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            weeks.forEach { week ->
                DropdownMenuItem(
                    text = { Text(Formatters.formatWeekRangeLabel(week)) },
                    onClick = {
                        open = false
                        onSelect(week)
                    },
                )
            }
        }
    }
}

/** The free, branded, screenshot-worthy hero: the driver's city standing. */
@Composable
private fun ShareableHeroCard(standing: PercentileResult?, onShare: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomparaWordmark()
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.share_card_share_cta),
                        tint = VerdictGreen,
                    )
                }
            }
            if (standing == null) {
                Text(
                    text = stringResource(R.string.comparar_hero_pending),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.comparar_hero_standing, standing.displayPercentile),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PercentileBar(
                    displayPercentile = standing.displayPercentile,
                    contentDescription = standingBarDescription(standing),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.comparar_hero_place_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PercentileBadge(
                        topPercent = standing.topPercent,
                        contentDescription = stringResource(
                            R.string.percentile_badge_description,
                            standing.displayPercentile,
                        ),
                    )
                }
                if (standing.isSynthetic) {
                    Text(
                        text = stringResource(R.string.percentile_synthetic_tag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun standingBarDescription(standing: PercentileResult): String =
    if (standing.isNationalFallback) {
        stringResource(R.string.percentile_bar_description_national, standing.displayPercentile)
    } else {
        stringResource(R.string.percentile_bar_description, standing.displayPercentile)
    }

@Composable
private fun SinglePlatformNote(platform: Platform) {
    Text(
        text = stringResource(R.string.comparar_single_note, platformName(platform)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ─── Benchmark table ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun BenchmarkTable(comparison: WeeklyComparison) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            TableHeader()
            comparison.rows.forEach { row -> TableRow(row) }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.comparar_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeaderCell(stringResource(R.string.comparar_col_metric), 1.4f, Alignment.Start)
        HeaderCell(stringResource(R.string.comparar_col_tu), 1f, Alignment.CenterHorizontally)
        HeaderCell("Uber", 1f, Alignment.CenterHorizontally)
        HeaderCell("DiDi", 1f, Alignment.CenterHorizontally)
        HeaderCell(stringResource(R.string.comparar_col_place), 1f, Alignment.CenterHorizontally)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: Alignment.Horizontal,
) {
    Column(modifier = Modifier.weight(weight), horizontalAlignment = align) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TableRow(row: ComparisonRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Métrica
        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = metricLabel(row.metric),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.lowerIsBetter) {
                Text(
                    text = stringResource(R.string.comparar_lower_better),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Tú (blended) — the protagonist, emerald.
        ValueCell(formatOrDash(row.unit, row.tu), 1f, BrandGreen, FontWeight.SemiBold)
        // City averages.
        ValueCell(formatOrDash(row.unit, row.uberAvg), 1f, MaterialTheme.colorScheme.onSurfaceVariant)
        ValueCell(formatOrDash(row.unit, row.didiAvg), 1f, MaterialTheme.colorScheme.onSurfaceVariant)
        // Tu lugar (percentile vs all drivers).
        PercentileCell(row.percentile, 1f)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ValueCell(
    text: String,
    weight: Float,
    color: Color,
    weight2: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = weight2,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PercentileCell(p: PercentileResult?, weight: Float) {
    Column(
        modifier = Modifier.weight(weight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (p == null) {
            Text(
                text = Formatters.DASH,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            Text(
                text = stringResource(R.string.metric_percentile_format, p.topPercent),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = VerdictGreen,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((p.displayPercentile / 100f).coerceIn(0.02f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BrandGreen),
                )
            }
        }
    }
}

// ─── Opportunities + cross-link ─────────────────────────────────────────────────────────────────────

@Composable
private fun OpportunitiesSection(items: List<Recommendation>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.comparar_opps_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        items.forEach { rec -> RecommendationCard(type = rec.type, title = rec.title, body = rec.body) }
    }
}

@Composable
private fun CrossLinkToInicio(onOpenInicio: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpenInicio)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.comparar_xlink_inicio),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.comparar_xlink_inicio_cta),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─── Formatting helpers ─────────────────────────────────────────────────────────────────────────────

/**
 * Strip every premium value (driver value, city averages, percentile) and the opportunities so the
 * locked tease can't leak real numbers through the blur/alpha. The metric labels + table shape stay,
 * so a free driver still sees the *structure* of the payoff (the hero already gave one free taste).
 */
private fun WeeklyComparison.maskedForLock(): WeeklyComparison =
    copy(
        rows = rows.map { it.copy(tu = null, uberAvg = null, didiAvg = null, percentile = null) },
        opportunities = emptyList(),
    )

private fun formatOrDash(unit: MetricUnit, value: Double?): String =
    if (value == null) Formatters.DASH else formatMetric(unit, value)

// Whole numbers so values fit one line — except viajes por hora, which keeps one decimal (S-024 feedback).
private fun formatMetric(unit: MetricUnit, v: Double): String = when (unit) {
    MetricUnit.MXN -> Formatters.formatMxnWhole(v)
    MetricUnit.PER_HOUR -> Formatters.formatPerHourWhole(v)
    MetricUnit.PER_KM -> Formatters.formatPerKmOneDecimal(v)
    MetricUnit.COUNT_PER_HOUR -> Formatters.formatPerHourCount(v)
    MetricUnit.PERCENT -> "${Math.round(v)}%"
}

@Composable
private fun metricLabel(key: String): String = stringResource(
    when (key) {
        "net_earnings" -> R.string.comparar_metric_net
        "earnings_per_hour" -> R.string.comparar_metric_iph
        "earnings_per_km" -> R.string.comparar_metric_ipk
        "earnings_per_trip" -> R.string.comparar_metric_ipt
        "trips_per_hour" -> R.string.comparar_metric_tph
        "platform_commission_pct" -> R.string.comparar_metric_take
        else -> R.string.comparar_metric_net
    },
)

private fun platformName(platform: Platform): String = when (platform) {
    Platform.UBER -> "Uber"
    Platform.DIDI -> "DiDi"
    Platform.INDRIVE -> "inDrive"
    else -> "tu app"
}

// ─── Previews ───────────────────────────────────────────────────────────────────────────────────────

private fun previewComparison(singlePlatform: Platform? = null): WeeklyComparison = WeeklyComparison(
    weekStart = "2026-06-02",
    rows = mx.kompara.ui.stats.COMPARE_METRICS.mapIndexed { i, spec ->
        ComparisonRow(
            metric = spec.key,
            unit = spec.unit,
            tu = listOf(3200.0, 160.0, 9.2, 54.0, 2.3, null)[i],
            uberAvg = if (spec.uberNa != null) null else listOf(2900.0, 150.0, 0.0, 52.0, 2.1, 24.0)[i],
            didiAvg = if (spec.didiNa != null) null else listOf(2750.0, 145.0, 9.5, 58.0, 2.4, 0.0)[i],
            percentile = if (i == 5) null else PercentileResult(spec.key, 0.0, 78, 78, 1500, false, true),
            lowerIsBetter = spec.lowerIsBetter,
            uberNa = spec.uberNa,
            didiNa = spec.didiNa,
        )
    },
    standing = PercentileResult("earnings_per_hour", 0.0, 78, 78, 1500, false, true),
    standingMetric = "earnings_per_hour",
    platformsWithData = if (singlePlatform != null) listOf(singlePlatform) else listOf(Platform.UBER, Platform.DIDI),
    singlePlatform = singlePlatform,
)

@Preview(showBackground = true, name = "Comparar — completo")
@Composable
private fun CompararFullPreview() {
    KomparaTheme {
        CompararContent(
            state = CompareUiState(false, listOf("2026-06-02", "2026-05-26"), mx.kompara.ui.stats.CompareUiData("2026-06-02", previewComparison())),
            gateState = GateState.UNLOCKED,
            onSelectWeek = {}, onUpgrade = {}, onImport = {}, onShare = {}, onOpenInicio = {},
            gateFunnel = object : GateFunnel {
                override suspend fun record(surface: GateSurface, event: mx.kompara.ui.paywall.GateEvent) {}
            },
        )
    }
}

@Preview(showBackground = true, name = "Comparar — vacío")
@Composable
private fun CompararEmptyPreview() {
    KomparaTheme {
        CompararContent(
            state = CompareUiState(false, emptyList(), null),
            gateState = GateState.UNLOCKED,
            onSelectWeek = {}, onUpgrade = {}, onImport = {}, onShare = {}, onOpenInicio = {},
            gateFunnel = object : GateFunnel {
                override suspend fun record(surface: GateSurface, event: mx.kompara.ui.paywall.GateEvent) {}
            },
        )
    }
}
