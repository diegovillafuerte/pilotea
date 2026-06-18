package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import mx.kompara.billing.GateState
import mx.kompara.ui.R
import mx.kompara.ui.components.CardTone
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.KomparaChip
import mx.kompara.ui.components.KomparaProgressBar
import mx.kompara.ui.components.KomparaStatusChip
import mx.kompara.ui.components.StatusLevel
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.paywall.GateFunnel
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.paywall.PaywallGate
import mx.kompara.ui.stats.FiscalUiState
import mx.kompara.ui.stats.FiscalViewModel
import mx.kompara.ui.stats.platformChipLabel
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
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
    onUpgrade: (GateSurface) -> Unit = {},
    viewModel: FiscalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gateState by viewModel.gateState.collectAsStateWithLifecycle()
    FiscalContent(
        state = state,
        gateState = gateState,
        gateFunnel = viewModel.gateFunnel,
        onSelectMonth = viewModel::selectMonthOffset,
        onToggleYtd = viewModel::setYtdView,
        onExportPdf = viewModel::exportPdf,
        onUpgrade = onUpgrade,
        modifier = modifier,
    )
}

@Composable
private fun FiscalContent(
    state: FiscalUiState,
    onSelectMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onToggleYtd: (Boolean) -> Unit = {},
    onExportPdf: () -> Unit = {},
    gateState: GateState = GateState.UNLOCKED,
    gateFunnel: GateFunnel? = null,
    onUpgrade: (GateSurface) -> Unit = {},
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
            style = MaterialTheme.typography.headlineSmall,
        )

        // B-050: the IMSS tracker body is premium. Title stays visible so the driver still sees the tab;
        // the tracker itself is teased behind the gate for a free driver.
        val body: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthPicker(state = state, onSelectMonth = onSelectMonth)

                Text(
                    text = stringResource(
                        R.string.fiscal_threshold_label,
                        Formatters.formatMxn(state.thresholdMxn),
                    ),
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

                // B-052: monthly fiscal (platform-regime withholding) summary + PDF export.
                FiscalSummarySection(
                    state = state,
                    onToggleYtd = onToggleYtd,
                    onExportPdf = onExportPdf,
                )

                ExplainerCard()
                FiscalRegimeExplainerCard()
                DisclaimerNote()
            }
        }

        if (gateFunnel != null) {
            PaywallGate(
                surface = GateSurface.FISCAL,
                state = gateState,
                valueHint = stringResource(R.string.gate_hint_fiscal),
                funnel = gateFunnel,
                onUpgrade = onUpgrade,
                ctaText = stringResource(R.string.paywall_cta),
                content = body,
            )
        } else {
            body()
        }
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
                KomparaChip(
                    selected = offset == state.monthOffset,
                    onClick = { onSelectMonth(offset) },
                    label = state.monthLabels.getOrElse(index) { "" },
                )
            }
        }
    }
}

@Composable
private fun PlatformImssCard(section: PlatformImssStatus) {
    KomparaCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.VARIANT,
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
    KomparaStatusChip(
        label = stringResource(labelRes),
        level = section.status.statusLevel,
    )
}

@Composable
private fun EmptyMonthNote() {
    KomparaCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.VARIANT,
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
        // Cards are tonal (no shadow) — flatten the default Material elevation.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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

// ─── B-052 fiscal withholding summary + PDF export ───────────────────────────────────────────────

@Composable
private fun FiscalSummarySection(
    state: FiscalUiState,
    onToggleYtd: (Boolean) -> Unit,
    onExportPdf: () -> Unit,
) {
    val summary = state.fiscalSummary ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.fiscal_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            // Month / YTD view toggle.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KomparaChip(
                    selected = !state.ytdView,
                    onClick = { onToggleYtd(false) },
                    label = stringResource(R.string.fiscal_summary_view_month),
                )
                KomparaChip(
                    selected = state.ytdView,
                    onClick = { onToggleYtd(true) },
                    label = stringResource(R.string.fiscal_summary_view_ytd),
                )
            }
        }

        if (summary.isEmpty) {
            Text(
                text = stringResource(R.string.fiscal_summary_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        summary.platforms.forEach { p -> FiscalPlatformCard(platform = p) }

        // Totals card (month or YTD per the toggle).
        val totals = if (state.ytdView) summary.ytdTotals else summary.monthTotals
        FiscalTotalsCard(totals = totals, ytd = state.ytdView)

        if (summary.anyCommissionApproximated) {
            Text(
                text = "* " + stringResource(R.string.fiscal_summary_approx_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        androidx.compose.material3.FilledTonalButton(
            onClick = onExportPdf,
            enabled = state.canExport && !state.exporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.exporting) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(0.dp))
                Text("  " + stringResource(R.string.fiscal_export_in_progress))
            } else {
                Text(stringResource(R.string.fiscal_export_pdf))
            }
        }
        Text(
            text = stringResource(R.string.fiscal_export_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FiscalPlatformCard(platform: mx.kompara.metrics.fiscal.PlatformFiscalSummary) {
    KomparaCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.VARIANT,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(platformChipLabel(platformOf(platform.platform))) +
                    if (platform.commissionApproximated) " *" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            FiscalRow(stringResource(R.string.fiscal_pdf_col_gross), Formatters.formatMxn(platform.grossMxn))
            FiscalRow(stringResource(R.string.fiscal_pdf_col_net), Formatters.formatMxn(platform.fiscalNetMxn))
            FiscalRow(
                stringResource(R.string.fiscal_summary_isr),
                Formatters.formatMxn(platform.estimatedIsrMxn),
            )
            FiscalRow(
                stringResource(R.string.fiscal_summary_iva),
                Formatters.formatMxn(platform.estimatedIvaMxn),
            )
            FiscalRow(
                stringResource(R.string.fiscal_summary_total_withheld),
                Formatters.formatMxn(platform.totalWithheldMxn),
                emphasize = true,
            )
        }
    }
}

@Composable
private fun FiscalTotalsCard(totals: mx.kompara.metrics.fiscal.FiscalTotals, ytd: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        // Cards are tonal (no shadow) — flatten the default Material elevation.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    if (ytd) R.string.fiscal_pdf_ytd_totals else R.string.fiscal_pdf_month_totals,
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            FiscalRow(stringResource(R.string.fiscal_pdf_col_gross), Formatters.formatMxn(totals.grossMxn))
            FiscalRow(stringResource(R.string.fiscal_pdf_col_net), Formatters.formatMxn(totals.fiscalNetMxn))
            FiscalRow(stringResource(R.string.fiscal_summary_isr), Formatters.formatMxn(totals.estimatedIsrMxn))
            FiscalRow(stringResource(R.string.fiscal_summary_iva), Formatters.formatMxn(totals.estimatedIvaMxn))
            FiscalRow(
                stringResource(R.string.fiscal_summary_total_withheld),
                Formatters.formatMxn(totals.totalWithheldMxn),
                emphasize = true,
            )
        }
    }
}

@Composable
private fun FiscalRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FiscalRegimeExplainerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        // Cards are tonal (no shadow) — flatten the default Material elevation.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // TODO(legal-B038): regime/CSF/harmonization copy needs counsel review on the same cadence
            // as the B-036 disclosure copy and the rate verification (launch gate).
            Text(
                text = stringResource(R.string.fiscal_regime_explainer_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.fiscal_regime_explainer_body), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.fiscal_regime_explainer_csf),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.fiscal_regime_explainer_harmonization),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─── colour + label mapping ──────────────────────────────────────────────────────────────────────

private val CoverageStatus.fillColor: Color
    get() = when (this) {
        CoverageStatus.COVERED -> VerdictGreen
        CoverageStatus.ON_TRACK -> VerdictYellow
        CoverageStatus.UNLIKELY -> VerdictRed
    }

private val CoverageStatus.statusLevel: StatusLevel
    get() = when (this) {
        CoverageStatus.COVERED -> StatusLevel.OK
        CoverageStatus.ON_TRACK -> StatusLevel.WARNING
        CoverageStatus.UNLIKELY -> StatusLevel.ERROR
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
                fiscalSummary = mx.kompara.metrics.fiscal.FiscalMonthSummary(
                    month = "2026-06",
                    year = 2026,
                    ratesYear = 2026,
                    platforms = listOf(
                        mx.kompara.metrics.fiscal.PlatformFiscalSummary(
                            platform = "UBER",
                            grossMxn = 12000.0,
                            fiscalNetMxn = 9000.0,
                            commissionMxn = 3000.0,
                            commissionApproximated = false,
                            estimatedIsrMxn = 252.0,
                            estimatedIvaMxn = 960.0,
                        ),
                        mx.kompara.metrics.fiscal.PlatformFiscalSummary(
                            platform = "DIDI",
                            grossMxn = 9000.0,
                            fiscalNetMxn = 9000.0,
                            commissionMxn = 0.0,
                            commissionApproximated = true,
                            estimatedIsrMxn = 189.0,
                            estimatedIvaMxn = 720.0,
                        ),
                    ),
                    monthTotals = mx.kompara.metrics.fiscal.FiscalTotals(21000.0, 18000.0, 441.0, 1680.0),
                    ytdTotals = mx.kompara.metrics.fiscal.FiscalTotals(120000.0, 96000.0, 2520.0, 9600.0),
                ),
            ),
            onSelectMonth = {},
        )
    }
}
