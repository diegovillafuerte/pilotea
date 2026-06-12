package mx.kompara.overlay.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.Platform
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
 * (good → marginal → bad), plus a $/km playground that re-grades all three live. Reachable from
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
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onStep = viewModel::goToStep,
        onPerKmFloor = viewModel::setPerKmFloor,
        modifier = modifier,
    )
}

/** Stateless body so it can be previewed and (later) screenshot-tested without Hilt. */
@Composable
internal fun SimulatorContent(
    state: SimulatorUiState,
    onSelectPlatform: (Platform) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStep: (Int) -> Unit,
    onPerKmFloor: (Double) -> Unit,
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
            VerdictHeadline(step = current)
            StepNavigation(
                stepIndex = state.stepIndex,
                stepCount = state.stepCount,
                isFirst = state.isFirstStep,
                isLast = state.isLastStep,
                onPrevious = onPrevious,
                onNext = onNext,
                onStep = onStep,
            )
        }

        ThresholdPlayground(perKm = state.threshold.minPerKmMxn, onPerKmFloor = onPerKmFloor)
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
        FilterChip(
            selected = selected == Platform.UBER,
            onClick = { onSelect(Platform.UBER) },
            label = { Text(stringResource(R.string.sim_platform_uber)) },
        )
        FilterChip(
            selected = selected == Platform.DIDI,
            onClick = { onSelect(Platform.DIDI) },
            label = { Text(stringResource(R.string.sim_platform_didi)) },
        )
    }
}

/**
 * A simple Compose mock of a host-app offer card (the fixture's visible text) with the real
 * [VerdictChipUi] overlaid in the top-right — exactly where the floating chip sits over the live
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
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1B1F),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(headerRes),
                    color = Color(0xFFB0B0B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                // The fixture's visible lines, rendered like a host card (fare big, rest as rows).
                step.visibleText.forEachIndexed { i, line ->
                    Text(
                        text = line,
                        color = Color.White,
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
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )
    }
}

/** Find which visible line is the fare so the mock renders it as the hero figure. */
private fun fareLineIndex(step: SimulatorStep): Int =
    step.visibleText.indexOfFirst { it.contains('$') }.let { if (it >= 0) it else -1 }

@Composable
private fun VerdictHeadline(step: SimulatorStep) {
    val netPerKm = step.chipState.netPerKm
    val headlineRes = when (step.shape) {
        DemoSnapshots.Shape.GOOD -> R.string.sim_verdict_good
        DemoSnapshots.Shape.MARGINAL -> R.string.sim_verdict_marginal
        DemoSnapshots.Shape.BAD -> R.string.sim_verdict_bad
    }
    val whyRes = when (step.shape) {
        DemoSnapshots.Shape.GOOD -> R.string.sim_why_good
        DemoSnapshots.Shape.MARGINAL -> R.string.sim_why_marginal
        DemoSnapshots.Shape.BAD -> R.string.sim_why_bad
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(headlineRes, netPerKm),
            color = step.chipState.level.brandColor,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(text = stringResource(whyRes), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.sim_net_per_km_label) + ": " + step.chipState.netPerKm,
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

@Composable
private fun StepNavigation(
    stepIndex: Int,
    stepCount: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStep: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sim_step_label, stepIndex + 1, stepCount),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until stepCount) {
                val dotDesc = stringResource(R.string.sim_step_dot_desc, i + 1)
                Box(
                    modifier = Modifier
                        .size(if (i == stepIndex) 12.dp else 9.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == stepIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        )
                        .semantics { contentDescription = dotDesc },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPrevious, enabled = !isFirst) {
                Text(stringResource(R.string.sim_previous))
            }
            Button(onClick = onNext, enabled = !isLast) {
                Text(stringResource(R.string.sim_next))
            }
        }
    }
}

@Composable
private fun ThresholdPlayground(perKm: Double, onPerKmFloor: (Double) -> Unit) {
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
                text = stringResource(R.string.sim_playground_title),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.sim_playground_value,
                    String.format(Locale.forLanguageTag("es-MX"), "%.1f", perKm),
                ),
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
            )
            Slider(
                value = ThresholdSheet.clampPerKm(perKm).toFloat(),
                onValueChange = { onPerKmFloor(it.toDouble()) },
                valueRange = ThresholdSheet.MIN_PER_KM.toFloat()..ThresholdSheet.MAX_PER_KM.toFloat(),
                steps = ThresholdSheet.stepCount,
            )
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
            onSelectPlatform = {}, onNext = {}, onPrevious = {}, onStep = {}, onPerKmFloor = {},
        )
    }
}
