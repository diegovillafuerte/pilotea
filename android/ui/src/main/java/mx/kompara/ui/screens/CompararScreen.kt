package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.billing.GateState
import mx.kompara.data.model.Platform
import mx.kompara.metrics.compare.CompareMetric
import mx.kompara.metrics.compare.CompareResult
import mx.kompara.metrics.compare.CompareRow
import mx.kompara.metrics.compare.CompareVerdict
import mx.kompara.metrics.compare.CompareWinner
import mx.kompara.metrics.compare.PlatformMetrics
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.paywall.PaywallGate
import mx.kompara.ui.stats.CompareMode
import mx.kompara.ui.stats.CompareUiData
import mx.kompara.ui.stats.CompareUiState
import mx.kompara.ui.stats.CompareUiText
import mx.kompara.ui.stats.CompararViewModel
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.VerdictGreen

/**
 * The Comparar tab (B-047): which platform paid better per metric this week, from auto-captured weekly
 * aggregates. Three states (B-047 req 3): no data → empty CTA to the Lector; one platform → "agrega
 * otra" + a clearly-marked example teaser; 2+ → the real comparison with a verdict summary on top and
 * per-metric paired bars (winner in verde + badge; "No comparable" rows dimmed with a reason).
 *
 * Premium gate (B-047 req 4 / B-050): the verdict summary shows FREE as the tease; the per-metric
 * breakdown is wrapped in the [PaywallGate] for [GateSurface.COMPARE]. Documented choice — the headline
 * insight hooks the driver, the detailed proof is the premium payoff.
 */
@Composable
fun CompararScreen(
    modifier: Modifier = Modifier,
    onUpgrade: (GateSurface) -> Unit = {},
    onOpenReader: () -> Unit = {},
    viewModel: CompararViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gateState by viewModel.gateState.collectAsStateWithLifecycle()
    CompararContent(
        state = state,
        gateState = gateState,
        onSelectWeek = viewModel::selectWeek,
        onSelectPair = viewModel::selectPair,
        onUpgrade = onUpgrade,
        onOpenReader = onOpenReader,
        gateFunnel = viewModel.gateFunnel,
        modifier = modifier,
    )
}

@Composable
private fun CompararContent(
    state: CompareUiState,
    gateState: GateState,
    onSelectWeek: (String) -> Unit,
    onSelectPair: (Platform, Platform) -> Unit,
    onUpgrade: (GateSurface) -> Unit,
    onOpenReader: () -> Unit,
    gateFunnel: mx.kompara.ui.paywall.GateFunnel,
    modifier: Modifier = Modifier,
) {
    if (state.loading) return
    val data = state.data
    if (data == null) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.comparar_no_data_title),
            body = stringResource(R.string.comparar_no_data_body),
            ctaText = stringResource(R.string.comparar_no_data_cta),
            onCtaClick = onOpenReader,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WeekPickerRow(
            weeks = state.availableWeeks,
            selected = data.weekStart,
            onSelect = onSelectWeek,
        )

        when (val mode = data.mode) {
            CompareMode.Empty -> EmptyBody(onOpenReader)
            is CompareMode.SinglePlatform -> SinglePlatformBody(mode.platform)
            is CompareMode.Comparison -> ComparisonBody(
                mode = mode,
                gateState = gateState,
                onSelectPair = onSelectPair,
                onUpgrade = onUpgrade,
                gateFunnel = gateFunnel,
            )
        }
    }
}

@Composable
private fun WeekPickerRow(
    weeks: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    if (weeks.size <= 1) {
        Text(
            text = Formatters.formatWeekRangeLabel(selected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.comparar_week_picker_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Horizontal scroll so a long history doesn't overflow at 360 dp.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            weeks.forEach { week ->
                FilterChip(
                    selected = week == selected,
                    onClick = { onSelect(week) },
                    label = { Text(Formatters.formatWeekRangeLabel(week)) },
                )
            }
        }
    }
}

@Composable
private fun EmptyBody(onOpenReader: () -> Unit) {
    EmptyState(
        icon = Icons.AutoMirrored.Filled.List,
        title = stringResource(R.string.comparar_no_data_title),
        body = stringResource(R.string.comparar_no_data_body),
        ctaText = stringResource(R.string.comparar_no_data_cta),
        onCtaClick = onOpenReader,
    )
}

@Composable
private fun SinglePlatformBody(platform: Platform) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.comparar_single_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.comparar_single_body, CompareUiText.platformName(platform)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A clearly-marked static example so the driver sees the shape of the payoff.
        ExampleTeaser()
    }
}

/** A static "Ejemplo" comparison so a single-platform driver sees what they'd get (clearly fake). */
@Composable
private fun ExampleTeaser() {
    val example = exampleResult()
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            VerdictCard(example.verdict)
            Spacer(Modifier.height(12.dp))
            example.comparableRows.forEach { row ->
                CompareRowBars(row, Platform.UBER, Platform.DIDI)
                Spacer(Modifier.height(8.dp))
            }
        }
        // The "Ejemplo" tag pinned to the corner.
        Text(
            text = stringResource(R.string.comparar_example_tag),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ComparisonBody(
    mode: CompareMode.Comparison,
    gateState: GateState,
    onSelectPair: (Platform, Platform) -> Unit,
    onUpgrade: (GateSurface) -> Unit,
    gateFunnel: mx.kompara.ui.paywall.GateFunnel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Platform-pair chooser only when 3+ platforms have data.
        if (mode.showsChips) {
            PlatformPairChooser(
                platforms = mode.platforms,
                selectedA = mode.platformA,
                selectedB = mode.platformB,
                onSelectPair = onSelectPair,
            )
        }

        // Verdict summary — FREE tease (B-047 req 4).
        VerdictCard(mode.result.verdict)

        // Per-metric breakdown — premium-gated.
        Text(
            text = stringResource(R.string.comparar_breakdown_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PaywallGate(
            surface = GateSurface.COMPARE,
            state = gateState,
            valueHint = stringResource(R.string.comparar_gate_breakdown_hint),
            funnel = gateFunnel,
            onUpgrade = onUpgrade,
            ctaText = stringResource(R.string.paywall_cta),
        ) {
            MetricBreakdown(mode.result, mode.platformA, mode.platformB)
        }
    }
}

@Composable
private fun PlatformPairChooser(
    platforms: List<Platform>,
    selectedA: Platform,
    selectedB: Platform,
    onSelectPair: (Platform, Platform) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        platforms.forEach { platform ->
            val isSelected = platform == selectedA || platform == selectedB
            FilterChip(
                selected = isSelected,
                onClick = {
                    // Toggle the platform into/out of the pair, keeping exactly two distinct.
                    val next = nextPair(platforms, selectedA, selectedB, platform)
                    onSelectPair(next.first, next.second)
                },
                label = { Text(CompareUiText.platformName(platform)) },
            )
        }
    }
}

/** Compute the next selected pair when [tapped] is toggled, always returning two distinct platforms. */
private fun nextPair(
    platforms: List<Platform>,
    a: Platform,
    b: Platform,
    tapped: Platform,
): Pair<Platform, Platform> {
    return when (tapped) {
        a -> {
            // Drop A: pick the first other platform that isn't B.
            val replacement = platforms.firstOrNull { it != b && it != a } ?: b
            replacement to b
        }
        b -> {
            val replacement = platforms.firstOrNull { it != a && it != b } ?: a
            a to replacement
        }
        else -> a to tapped // replace B with the newly-tapped platform
    }
}

/** The headline verdict card. Always shown (free tease). */
@Composable
private fun VerdictCard(verdict: CompareVerdict?, modifier: Modifier = Modifier) {
    val sentence = verdict?.let {
        CompareUiText.verdictSentence(it) { name -> runCatching { Platform.valueOf(name) }.getOrNull() }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.comparar_verdict_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sentence ?: stringResource(R.string.comparar_not_comparable),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MetricBreakdown(result: CompareResult, platformA: Platform, platformB: Platform) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        result.rows.forEach { row ->
            if (row.comparable) {
                CompareRowBars(row, platformA, platformB)
            } else {
                NotComparableRow(row)
            }
        }
    }
}

/**
 * One metric: the label, then two horizontally-paired bars (A on top, B below) whose widths are
 * proportional to the values; the winner's bar is verde with a "Gana" badge.
 */
@Composable
private fun CompareRowBars(row: CompareRow, platformA: Platform, platformB: Platform) {
    val valueA = row.valueA ?: 0.0
    val valueB = row.valueB ?: 0.0
    // Proportional widths: scale to the larger value (for commission, the bar still shows magnitude;
    // the verde winner highlights "better" regardless of bar length).
    val maxValue = maxOf(valueA, valueB).coerceAtLeast(1e-9)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = CompareUiText.metricLabel(row.metric),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.pctDifference?.takeIf { it > 0.0 }?.let { pct ->
                Text(
                    text = "+${CompareUiText.pctLabel(pct)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = VerdictGreen,
                )
            }
        }
        PairedBar(
            label = CompareUiText.platformName(platformA),
            value = CompareUiText.metricValue(row.metric, valueA),
            fraction = (valueA / maxValue).toFloat(),
            isWinner = row.winner == CompareWinner.A,
        )
        PairedBar(
            label = CompareUiText.platformName(platformB),
            value = CompareUiText.metricValue(row.metric, valueB),
            fraction = (valueB / maxValue).toFloat(),
            isWinner = row.winner == CompareWinner.B,
        )
    }
}

@Composable
private fun PairedBar(
    label: String,
    value: String,
    fraction: Float,
    isWinner: Boolean,
) {
    val barColor = if (isWinner) VerdictGreen else MaterialTheme.colorScheme.surfaceVariant
    val onBar = if (isWinner) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(56.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = onBar,
                    )
                    if (isWinner) {
                        Text(
                            text = stringResource(R.string.comparar_winner_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = onBar,
                        )
                    }
                }
            }
        }
    }
}

/** A dimmed "No comparable" row with the reason (e.g. "inDrive no reporta este dato"). */
@Composable
private fun NotComparableRow(row: CompareRow) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = CompareUiText.metricLabel(row.metric),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val reason = row.missingPlatform
            ?.let { name -> runCatching { Platform.valueOf(name) }.getOrNull() }
            ?.let { stringResource(R.string.comparar_not_comparable_reason, CompareUiText.platformName(it)) }
            ?: stringResource(R.string.comparar_not_comparable)
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ─── Previews / example data ──────────────────────────────────────────────────────────────────────

/** A static "ejemplo" result for the single-platform teaser — clearly fake, never real numbers. */
private fun exampleResult(): CompareResult =
    mx.kompara.metrics.compare.CompareCalculator.compare(
        PlatformMetrics.of(
            platform = Platform.UBER.name,
            earningsPerKm = 8.5,
            earningsPerHour = 165.0,
            earningsPerTrip = 52.0,
        ),
        PlatformMetrics.of(
            platform = Platform.DIDI.name,
            earningsPerKm = 9.5,
            earningsPerHour = 150.0,
            earningsPerTrip = 58.0,
        ),
    )

@Preview(showBackground = true, name = "Comparar — comparación")
@Composable
private fun CompararComparisonPreview() {
    KomparaTheme {
        val result = mx.kompara.metrics.compare.CompareCalculator.compare(
            PlatformMetrics.of(Platform.UBER.name, earningsPerKm = 10.0, earningsPerHour = 200.0, earningsPerTrip = 50.0, netEarnings = 1800.0, totalTrips = 60),
            PlatformMetrics.of(Platform.DIDI.name, earningsPerKm = 11.2, earningsPerHour = 150.0, earningsPerTrip = 60.0, netEarnings = 1500.0, totalTrips = 45),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VerdictCard(result.verdict)
            MetricBreakdown(result, Platform.UBER, Platform.DIDI)
        }
    }
}

@Preview(showBackground = true, name = "Comparar — una plataforma")
@Composable
private fun CompararSinglePreview() {
    KomparaTheme {
        Column(Modifier.padding(16.dp)) { SinglePlatformBody(Platform.UBER) }
    }
}
