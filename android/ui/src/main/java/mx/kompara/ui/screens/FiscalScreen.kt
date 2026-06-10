package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.Platform
import mx.kompara.metrics.imss.CoverageStatus
import mx.kompara.metrics.imss.MonthPhase
import mx.kompara.metrics.imss.PlatformImssStatus
import mx.kompara.ui.R
import mx.kompara.ui.components.KomparaProgressBar
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.FiscalUiState
import mx.kompara.ui.stats.FiscalViewModel
import mx.kompara.ui.stats.platformChipLabel
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import mx.kompara.ui.theme.OnVerdictGreen
import mx.kompara.ui.theme.OnVerdictRed
import mx.kompara.ui.theme.OnVerdictYellow
import mx.kompara.ui.theme.VerdictGreen
import mx.kompara.ui.theme.VerdictRed
import mx.kompara.ui.theme.VerdictYellow

/**
 * The Fiscal tab's IMSS threshold tracker (B-051): a month picker, per-platform progress toward the
 * monthly minimum-wage threshold with pacing + projection + a coverage chip, a plain-Spanish
 * explainer, and an honest disclaimer. Replaces the old placeholder.
 */
@Composable
fun FiscalScreen(
    modifier: Modifier = Modifier,
    viewModel: FiscalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FiscalContent(state = state, onSelectMonth = viewModel::selectMonthOffset, modifier = modifier)
}

@Composable
private fun FiscalContent(
    state: FiscalUiState,
    onSelectMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Spacer(modifier.fillMaxSize())
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.fiscal_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        MonthPicker(state = state, onSelectMonth = onSelectMonth)

        Text(
            text = stringResource(R.string.fiscal_threshold_label, Formatters.formatMxn(state.thresholdMxn)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.usingDefaultConfig) {
            Text(
                text = stringResource(R.string.fiscal_using_default_config),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isEmpty) {
            EmptyMonthNote()
        } else {
            state.sections.forEach { section ->
                PlatformImssCard(section = section)
            }
        }

        ExplainerCard()
        DisclaimerNote()
    }
}

@Composable
private fun MonthPicker(state: FiscalUiState, onSelectMonth: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.fiscal_month_picker_label),
            style = KomparaType.metricLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.availableMonthOffsets.forEachIndexed { index, offset ->
                FilterChip(
                    selected = offset == state.monthOffset,
                    onClick = { onSelectMonth(offset) },
                    label = { Text(state.monthLabels.getOrElse(index) { "" }) },
                )
            }
        }
    }
}

@Composable
private fun PlatformImssCard(section: PlatformImssStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(platformChipLabel(platformOf(section.platform))),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                CoverageChip(section)
            }

            Text(
                text = stringResource(
                    R.string.fiscal_platform_net,
                    Formatters.formatMxn(section.netSoFarMxn),
                    Formatters.formatMxn(section.thresholdMxn),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            KomparaProgressBar(
                progress = section.progress.toFloat(),
                fillColor = section.status.fillColor,
            )

            // Pacing only makes sense for the current/future month while not yet covered.
            if (!section.covered && section.phase != MonthPhase.PAST && section.daysRemaining > 0) {
                Text(
                    text = if (section.daysRemaining == 1) {
                        stringResource(R.string.fiscal_pacing_singular, Formatters.formatMxn(section.remainingMxn))
                    } else {
                        stringResource(
                            R.string.fiscal_pacing,
                            Formatters.formatMxn(section.remainingMxn),
                            section.daysRemaining,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.fiscal_projection,
                        Formatters.formatMxn(section.projectedMonthEndMxn),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (section.covered) {
                Text(
                    text = stringResource(R.string.fiscal_covered_amount, Formatters.formatMxn(section.netSoFarMxn)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoverageChip(section: PlatformImssStatus) {
    val labelRes = coverageLabelRes(section.status, section.phase)
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = section.status.onFillColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(section.status.fillColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyMonthNote() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.fiscal_empty_month_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.fiscal_empty_month_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExplainerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // TODO(legal-B038): explainer copy needs counsel review against current IMSS/SAT
            // guidance on the same cadence as the B-036 disclosure copy.
            Text(
                text = stringResource(R.string.fiscal_explainer_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.fiscal_explainer_body), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.fiscal_explainer_per_platform),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.fiscal_explainer_pilot), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DisclaimerNote() {
    Text(
        text = "ⓘ " + stringResource(R.string.fiscal_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ─── colour + label mapping ──────────────────────────────────────────────────────────────────────

private val CoverageStatus.fillColor: Color
    get() = when (this) {
        CoverageStatus.COVERED -> VerdictGreen
        CoverageStatus.ON_TRACK -> VerdictYellow
        CoverageStatus.UNLIKELY -> VerdictRed
    }

private val CoverageStatus.onFillColor: Color
    get() = when (this) {
        CoverageStatus.COVERED -> OnVerdictGreen
        CoverageStatus.ON_TRACK -> OnVerdictYellow
        CoverageStatus.UNLIKELY -> OnVerdictRed
    }

private fun coverageLabelRes(status: CoverageStatus, phase: MonthPhase): Int = when {
    phase == MonthPhase.PAST && status == CoverageStatus.COVERED -> R.string.fiscal_status_covered_past
    phase == MonthPhase.PAST -> R.string.fiscal_status_unlikely_past
    status == CoverageStatus.COVERED -> R.string.fiscal_status_covered
    status == CoverageStatus.ON_TRACK -> R.string.fiscal_status_on_track
    else -> R.string.fiscal_status_unlikely
}

/** Map the stored platform name back to the enum for the chip label (UNKNOWN on a bad value). */
private fun platformOf(name: String): Platform =
    runCatching { Platform.valueOf(name) }.getOrDefault(Platform.UNKNOWN)

@Preview(showBackground = true, name = "Fiscal — IMSS tracker")
@Composable
private fun FiscalContentPreview() {
    KomparaTheme {
        FiscalContent(
            state = FiscalUiState(
                loading = false,
                monthOffset = 0,
                monthLabel = "Junio 2026",
                availableMonthOffsets = listOf(0, 1, 2),
                monthLabels = listOf("Junio 2026", "Mayo 2026", "Abril 2026"),
                thresholdMxn = 8364.0,
                usingDefaultConfig = false,
                sections = listOf(
                    PlatformImssStatus(
                        platform = "UBER",
                        netSoFarMxn = 5200.0,
                        thresholdMxn = 8364.0,
                        remainingMxn = 3164.0,
                        progress = 0.62,
                        daysRemaining = 11,
                        projectedMonthEndMxn = 9100.0,
                        status = CoverageStatus.ON_TRACK,
                        phase = MonthPhase.CURRENT,
                    ),
                    PlatformImssStatus(
                        platform = "DIDI",
                        netSoFarMxn = 8900.0,
                        thresholdMxn = 8364.0,
                        remainingMxn = 0.0,
                        progress = 1.0,
                        daysRemaining = 11,
                        projectedMonthEndMxn = 14000.0,
                        status = CoverageStatus.COVERED,
                        phase = MonthPhase.CURRENT,
                    ),
                ),
            ),
            onSelectMonth = {},
        )
    }
}
