package mx.kompara.ui.fiscal

import mx.kompara.metrics.fiscal.FiscalMonthSummary
import mx.kompara.metrics.fiscal.FiscalTotals
import mx.kompara.metrics.fiscal.PlatformFiscalSummary
import mx.kompara.metrics.imss.CoverageStatus
import mx.kompara.metrics.imss.PlatformImssStatus
import mx.kompara.ui.format.Formatters

/**
 * Pure mapper from the calculator output ([FiscalMonthSummary]) + the B-051 IMSS statuses into the
 * render-ready [FiscalReportData] (B-052). All the peso formatting, platform labelling, and the
 * IMSS-coverage one-liner happen here so [FiscalPdfRenderer] is pure geometry and the whole
 * composition is unit-testable on the JVM (no Android, no Context — the caller passes the few
 * localized template strings it pulls from resources).
 */
object FiscalReportComposer {

    /**
     * Compose the report content. [strings] supplies the handful of localized strings the composer
     * needs (titles, headers, disclaimer, the IMSS-line templates); [driverName] is omitted when null.
     */
    fun compose(
        summary: FiscalMonthSummary,
        monthLabel: String,
        imssStatuses: List<PlatformImssStatus>,
        driverName: String?,
        generatedOnLabel: String,
        strings: ReportStrings,
    ): FiscalReportData {
        val rows = summary.platforms.map { it.toRow() }
        return FiscalReportData(
            wordmark = FiscalReportData.WORDMARK,
            title = strings.title,
            driverName = driverName?.takeIf { it.isNotBlank() },
            monthLabel = monthLabel,
            columnHeaders = strings.columnHeaders,
            rows = rows,
            monthTotals = summary.monthTotals.toRow(strings.monthTotalsLabel),
            ytdTotals = summary.ytdTotals.toRow(strings.ytdTotalsLabel),
            monthWithheldTotal = Formatters.formatMxn(summary.monthTotals.totalWithheldMxn),
            imssCoverageLine = imssLine(imssStatuses, strings),
            approximationNote = if (summary.anyCommissionApproximated) strings.approximationNote else null,
            disclaimer = strings.disclaimer,
            ratesNote = strings.ratesNote,
            generatedOn = generatedOnLabel,
        )
    }

    private fun PlatformFiscalSummary.toRow(): FiscalReportRow = FiscalReportRow(
        label = platformLabel(platform) + if (commissionApproximated) " *" else "",
        grossMxn = Formatters.formatMxn(grossMxn),
        netMxn = Formatters.formatMxn(fiscalNetMxn),
        isrMxn = Formatters.formatMxn(estimatedIsrMxn),
        ivaMxn = Formatters.formatMxn(estimatedIvaMxn),
    )

    private fun FiscalTotals.toRow(label: String): FiscalReportRow = FiscalReportRow(
        label = label,
        grossMxn = Formatters.formatMxn(grossMxn),
        netMxn = Formatters.formatMxn(fiscalNetMxn),
        isrMxn = Formatters.formatMxn(estimatedIsrMxn),
        ivaMxn = Formatters.formatMxn(estimatedIvaMxn),
    )

    /**
     * One-line IMSS coverage summary for the PDF, reusing the B-051 per-platform statuses: list the
     * platforms covered this month, or note none were. Pure string assembly off the same data the
     * Fiscal tab shows, so the PDF and the tab never disagree.
     */
    private fun imssLine(statuses: List<PlatformImssStatus>, strings: ReportStrings): String {
        val active = statuses.filter { it.netSoFarMxn > 0.0 }
        if (active.isEmpty()) return strings.imssNoData
        val covered = active.filter { it.status == CoverageStatus.COVERED }.map { platformLabel(it.platform) }
        return if (covered.isEmpty()) {
            strings.imssNoneCovered
        } else {
            strings.imssCoveredPrefix + " " + covered.joinToString(", ")
        }
    }

    /** Title-case a stored platform enum name for display ("UBER" → "Uber", "DIDI" → "DiDi"). */
    fun platformLabel(name: String): String = when (name.uppercase()) {
        "UBER" -> "Uber"
        "DIDI" -> "DiDi"
        "INDRIVE" -> "inDrive"
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * The localized strings the composer needs. Held as a plain data class (not Context lookups) so
     * the composer is pure; the caller resolves these from string resources once.
     */
    data class ReportStrings(
        val title: String,
        /** 5 headers: Plataforma | Bruto | Neto | ISR ret. | IVA ret. */
        val columnHeaders: List<String>,
        val monthTotalsLabel: String,
        val ytdTotalsLabel: String,
        val approximationNote: String,
        val disclaimer: String,
        val ratesNote: String,
        val imssCoveredPrefix: String,
        val imssNoneCovered: String,
        val imssNoData: String,
    )
}
