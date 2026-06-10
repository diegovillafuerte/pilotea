package mx.kompara.ui.fiscal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.kompara.metrics.fiscal.FiscalMonthSummary
import mx.kompara.metrics.imss.PlatformImssStatus
import mx.kompara.ui.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-call fiscal-PDF export (B-052): resolve the localized report strings from resources, compose the
 * render-ready [FiscalReportData] ([FiscalReportComposer]), write it to the FileProvider cache
 * ([FiscalPdfWriter]), fire the `application/pdf` share chooser ([FiscalPdfSharer]), and bump the
 * anonymous export-funnel counter. Holds the Android Context/string lookups so the calculator,
 * composer, and renderer stay pure and the viewmodel stays thin.
 */
@Singleton
class FiscalReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: FiscalPdfWriter,
    private val sharer: FiscalPdfSharer,
) {

    /**
     * Compose → write → share the [summary] for [monthLabel] (the month's display name) and
     * [imssStatuses] (the B-051 coverage line). [driverName] is included in the header when present.
     * Returns false (and shares nothing) when there's nothing to export (empty month).
     */
    suspend fun export(
        summary: FiscalMonthSummary,
        monthLabel: String,
        imssStatuses: List<PlatformImssStatus>,
        driverName: String?,
    ): Boolean {
        if (summary.isEmpty) return false
        val data = FiscalReportComposer.compose(
            summary = summary,
            monthLabel = monthLabel,
            imssStatuses = imssStatuses,
            driverName = driverName,
            generatedOnLabel = context.getString(R.string.fiscal_pdf_generated_on, todayLabel()),
            strings = reportStrings(),
        )
        val uri = withContext(Dispatchers.IO) { writer.write(data, summary.month) }
        sharer.share(
            pdfUri = uri,
            subject = context.getString(R.string.fiscal_pdf_share_subject, monthLabel),
            body = context.getString(R.string.fiscal_pdf_share_body, monthLabel),
        )
        sharer.recordExport()
        return true
    }

    private fun reportStrings(): FiscalReportComposer.ReportStrings = FiscalReportComposer.ReportStrings(
        title = context.getString(R.string.fiscal_pdf_title),
        columnHeaders = listOf(
            context.getString(R.string.fiscal_pdf_col_platform),
            context.getString(R.string.fiscal_pdf_col_gross),
            context.getString(R.string.fiscal_pdf_col_net),
            context.getString(R.string.fiscal_pdf_col_isr),
            context.getString(R.string.fiscal_pdf_col_iva),
        ),
        monthTotalsLabel = context.getString(R.string.fiscal_pdf_month_totals),
        ytdTotalsLabel = context.getString(R.string.fiscal_pdf_ytd_totals),
        approximationNote = context.getString(R.string.fiscal_pdf_approx_note),
        disclaimer = context.getString(R.string.fiscal_pdf_disclaimer),
        ratesNote = context.getString(R.string.fiscal_pdf_rates_note),
        imssCoveredPrefix = context.getString(R.string.fiscal_pdf_imss_covered_prefix),
        imssNoneCovered = context.getString(R.string.fiscal_pdf_imss_none_covered),
        imssNoData = context.getString(R.string.fiscal_pdf_imss_no_data),
    )

    private fun todayLabel(): String =
        Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(DAY_LABEL)

    private companion object {
        val MX: Locale = Locale.forLanguageTag("es-MX")
        val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", MX)
    }
}
