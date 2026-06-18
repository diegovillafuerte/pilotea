package mx.kompara.overlay.simulator

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.VerdictLevel
import mx.kompara.overlay.R
import mx.kompara.overlay.ThresholdSheet
import mx.kompara.overlay.VerdictChipState
import mx.kompara.overlay.VerdictChipUi
import mx.kompara.parsers.snapshot.DemoSnapshots
import mx.kompara.ui.components.brandColor
import mx.kompara.ui.theme.KomparaTheme
import java.util.Locale

/**
 * The in-app offer simulator (B-037). Shows the magic before the first shift: a mock Uber/DiDi offer
 * card with the **real** verdict chip overlaid, walked through a guided 3-offer script
 * (good → marginal → bad), plus a floor playground (the preferred metric's, B-079) that re-grades
 * all three live. Reachable from
 * Ajustes and designed to be linked from the onboarding done-screen; it also doubles as the
 * Play-review demo, so the flow is self-explanatory.
 *
 * All verdicts come from [SimulatorViewModel] running the live pipeline — none are hardcoded here.
 */
@Composable
fun SimulatorScreen(
    modifier: Modifier = Modifier,
    viewModel: SimulatorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SimulatorContent(
        state = state,
        onSelectPlatform = viewModel::selectPlatform,
        onStep = viewModel::goToStep,
        onGreenFloor = viewModel::setGreenFloor,
        modifier = modifier,
    )
}

/** Stateless body so it can be previewed and (later) screenshot-tested without Hilt. */
@Composable
internal fun SimulatorContent(
    state: SimulatorUiState,
    onSelectPlatform: (Platform) -> Unit,
    onStep: (Int) -> Unit,
    onGreenFloor: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.sim_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.sim_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        PlatformToggle(selected = state.platform, onSelect = onSelectPlatform)

        val current = state.current
        if (current != null) {
            MockOfferCardWithChip(platform = state.platform, step = current)
            // Tap an outcome to preview that sample offer. The demo deck is ordered
            // good → marginal → bad, so segment index 0/1/2 maps straight onto the step index;
            // onStep re-grades the real chip through the live pipeline (not a hardcoded swatch).
            VerdictSegmentedControl(selectedIndex = state.stepIndex, onSelect = onStep)
            VerdictHeadline(step = current)
        }

        ThresholdPlayground(
            preferred = state.preferredMetric,
            threshold = state.threshold,
            onGreenFloor = onGreenFloor,
        )
    }
}

@Composable
private fun PlatformToggle(selected: Platform, onSelect: (Platform) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics {
            contentDescription = "platform-toggle"
        },
    ) {
        BrandFilterChip(
            selected = selected == Platform.UBER,
            onClick = { onSelect(Platform.UBER) },
            label = stringResource(R.string.sim_platform_uber),
        )
        BrandFilterChip(
            selected = selected == Platform.DIDI,
            onClick = { onSelect(Platform.DIDI) },
            label = stringResource(R.string.sim_platform_didi),
        )
    }
}

/**
 * A brand-emerald pill mirroring `:ui`'s KomparaChip selected look (16% emerald fill, emerald label,
 * 40% emerald hairline) but built from local Material3 primitives so `:overlay` adds no new `:ui`
 * dependency. A bare [FilterChip] would fall back to a non-brand selected container.
 */
@Composable
private fun BrandFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    val primary = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(percent = 50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = primary.copy(alpha = 0.16f),
            selectedLabelColor = primary,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outline,
        ),
    )
}

/**
 * A simple Compose mock of a host-app offer card (the fixture's visible text) with the real
 * [VerdictChipUi] overlaid in the bottom-right — exactly where the floating chip rests over the live
 * app, but embedded in this screen (no WindowManager).
 */
@Composable
private fun MockOfferCardWithChip(platform: Platform, step: SimulatorStep) {
    val headerRes =
        if (platform == Platform.DIDI) R.string.sim_mock_didi_header else R.string.sim_mock_uber_header
    val cardDesc = stringResource(R.string.sim_mock_card_desc)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDesc },
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            // min height keeps room for the bottom-right chip's hint/secondary-rate panel
            // (matches the mock's 200px host).
            Column(
                modifier = Modifier
                    .heightIn(min = 200.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(headerRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                // The fixture's visible lines, rendered like a host card (fare big, rest as rows).
                step.visibleText.forEachIndexed { i, line ->
                    Text(
                        text = line,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (i == fareLineIndex(step)) 30.sp else 15.sp,
                        fontWeight = if (i == fareLineIndex(step)) FontWeight.Black else FontWeight.Normal,
                    )
                }
            }
        }
        // Real verdict chip, overlaid in-screen (drag is a no-op here — see VerdictChipCallbacks).
        VerdictChipUi(
            state = step.chipState,
            threshold = mx.kompara.data.settings.PlatformThreshold.DEFAULT,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        )
    }
}

/** Find which visible line is the fare so the mock renders it as the hero figure. */
private fun fareLineIndex(step: SimulatorStep): Int =
    step.visibleText.indexOfFirst { it.contains('$') }.let { if (it >= 0) it else -1 }

/**
 * The headline copy follows the **live** verdict [level], not the demo's fixed shape: once the
 * playground floor re-grades the chip (e.g. rojo → amarillo) the text and the chip colour must agree
 * (B-074 F4). Kept as a pure function so the mapping is unit-testable without Compose.
 */
@StringRes
internal fun headlineResFor(level: VerdictLevel): Int = when (level) {
    VerdictLevel.GREEN -> R.string.sim_verdict_good
    VerdictLevel.YELLOW -> R.string.sim_verdict_marginal
    VerdictLevel.RED -> R.string.sim_verdict_bad
}

@Composable
private fun VerdictHeadline(step: SimulatorStep) {
    val heroRate = step.chipState.heroRate
    val headlineRes = headlineResFor(step.chipState.level)
    // The "¿por qué?" narrative stays keyed to the demo shape — it tells that scenario's
    // pickup-distance story, which the slider doesn't change.
    val whyRes = when (step.shape) {
        DemoSnapshots.Shape.GOOD -> R.string.sim_why_good
        DemoSnapshots.Shape.MARGINAL -> R.string.sim_why_marginal
        DemoSnapshots.Shape.BAD -> R.string.sim_why_bad
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(headlineRes, heroRate),
            color = step.chipState.level.brandColor,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(text = stringResource(whyRes), style = MaterialTheme.typography.bodyMedium)
        // The net row leads with the metric the driver chose to drive by (B-079); the gross $/km
        // row stays as the "Uber te muestra el bruto, esto es lo neto" contrast.
        val netLabel = when (step.chipState.preferred) {
            PreferredMetric.IPK -> R.string.sim_net_per_km_label
            PreferredMetric.IPH -> R.string.sim_net_per_hour_label
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(netLabel) + ": " + step.chipState.heroRate,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.sim_gross_per_km_label) + ": " + step.chipState.grossPerKm,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The 3-way verdict picker (mock's `.seg`): a self-explanatory selector that swaps the sample offer
 * so the driver taps the outcome they want to preview instead of paging. The active segment uses the
 * brand primary fill — it is a SELECTOR, not a verdict, so it never uses verde/amarillo/rojo (those
 * stay exclusive to the chip's real verdict). Indices 0/1/2 map to good/marginal/bad via [onSelect].
 */
@Composable
private fun VerdictSegmentedControl(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.sim_seg_good),
        stringResource(R.string.sim_seg_marginal),
        stringResource(R.string.sim_seg_bad),
    )
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "verdict-segmented-control" },
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val active = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThresholdPlayground(
    preferred: PreferredMetric,
    threshold: mx.kompara.data.settings.PlatformThreshold,
    onGreenFloor: (Double) -> Unit,
) {
    val green = when (preferred) {
        PreferredMetric.IPK -> threshold.minPerKmMxn
        PreferredMetric.IPH -> threshold.minPerHourMxn
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    when (preferred) {
                        PreferredMetric.IPK -> R.string.sim_playground_title
                        PreferredMetric.IPH -> R.string.sim_playground_title_hour
                    },
                ),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when (preferred) {
                    PreferredMetric.IPK -> stringResource(
                        R.string.sim_playground_value,
                        String.format(Locale.forLanguageTag("es-MX"), "%.1f", green),
                    )
                    PreferredMetric.IPH -> stringResource(
                        R.string.sim_playground_value_hour,
                        String.format(Locale.forLanguageTag("es-MX"), "%.0f", green),
                    )
                },
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
            )
            when (preferred) {
                PreferredMetric.IPK -> Slider(
                    value = ThresholdSheet.clampPerKm(green).toFloat(),
                    onValueChange = { onGreenFloor(it.toDouble()) },
                    valueRange = ThresholdSheet.MIN_PER_KM.toFloat()..ThresholdSheet.MAX_PER_KM.toFloat(),
                    steps = ThresholdSheet.stepCount,
                )
                PreferredMetric.IPH -> Slider(
                    value = ThresholdSheet.clampPerHour(green).toFloat(),
                    onValueChange = { onGreenFloor(it.toDouble()) },
                    valueRange = ThresholdSheet.MIN_PER_HOUR.toFloat()..ThresholdSheet.MAX_PER_HOUR.toFloat(),
                    steps = ThresholdSheet.hourStepCount,
                )
            }
            Text(
                text = stringResource(R.string.sim_playground_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// --- Previews (good / marginal / bad use canned chip states; pipeline runs at runtime) ----------

private fun previewStep(shape: DemoSnapshots.Shape, level: VerdictLevel, netPerKm: String): SimulatorStep =
    SimulatorStep(
        id = "preview_${shape.name.lowercase()}",
        shape = shape,
        chipState = VerdictChipState(
            level = level,
            netPerKm = netPerKm,
            netPerHour = "$450/h",
            netProfit = "$120.00",
            netPerMin = "$6.00/min",
            grossPerKm = netPerKm,
            hasMissingData = false,
            missingHintKind = VerdictChipState.MissingHintKind.NONE,
            explainKind = VerdictChipState.ExplainKind.BOTH_STRONG,
        ),
        visibleText = listOf("UberX", "MX\$135.00", "4 min (1.5 km) de distancia", "14 min (7.5 km) de viaje", "Aceptar"),
        result = SimulatorResult(
            offer = DemoSnapshots.UBER.first(),
            platform = Platform.UBER,
            card = mx.kompara.parsers.model.OfferCard(platform = "com.ubercab.driver"),
            visibleText = emptyList(),
            metrics = mx.kompara.metrics.OfferMetrics(
                grossMxn = 135.0, netMxn = 135.0, totalKm = 9.0, totalMin = 18.0,
                grossPerKm = 15.0, grossPerMin = 7.5, netPerKm = 15.0, netPerMin = 7.5, netPerHour = 450.0,
                verdict = mx.kompara.metrics.Verdict(level, 15.0, 450.0, 135.0, 15.0, emptyList()),
            ),
        ),
    )

@Preview(showBackground = true, name = "Simulador — Verde")
@Composable
private fun SimulatorGoodPreview() {
    KomparaTheme {
        SimulatorContent(
            state = SimulatorUiState(
                platform = Platform.UBER,
                stepIndex = 0,
                offers = listOf(
                    previewStep(DemoSnapshots.Shape.GOOD, VerdictLevel.GREEN, "$15.00/km"),
                    previewStep(DemoSnapshots.Shape.MARGINAL, VerdictLevel.YELLOW, "$7.75/km"),
                    previewStep(DemoSnapshots.Shape.BAD, VerdictLevel.RED, "$4.36/km"),
                ),
                threshold = mx.kompara.data.settings.PlatformThreshold.DEFAULT,
            ),
            onSelectPlatform = {}, onStep = {}, onGreenFloor = {},
        )
    }
}
