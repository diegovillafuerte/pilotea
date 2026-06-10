package mx.kompara.ui.fiscal

/**
 * The fully-formatted, render-ready content of one monthly fiscal PDF (B-052). Every field is a
 * display string already run through [mx.kompara.ui.format.Formatters] — the renderer only positions
 * and draws, so the layout is testable without touching pesos math and the calculator stays Android-
 * free. Built by [FiscalReportComposer] from a [mx.kompara.metrics.fiscal.FiscalMonthSummary] plus the
 * IMSS coverage line.
 */
data class FiscalReportData(
    /** "Kompara" wordmark line (constant, kept here so the renderer has no string literals). */
    val wordmark: String,
    /** Report title, e.g. "Resumen fiscal mensual". */
    val title: String,
    /** Driver name line, or null to omit (we don't always know the driver's name). */
    val driverName: String?,
    /** The month label, e.g. "Junio 2026". */
    val monthLabel: String,
    /** Column header labels, left→right: Plataforma | Bruto | Neto | ISR ret. | IVA ret. */
    val columnHeaders: List<String>,
    /** One formatted row per platform. */
    val rows: List<FiscalReportRow>,
    /** The "Totales del mes" row (formatted). */
    val monthTotals: FiscalReportRow,
    /** The "Acumulado del año (YTD)" row (formatted). */
    val ytdTotals: FiscalReportRow,
    /** Total estimated withholdings line for the month (ISR + IVA), e.g. "$1,010.00". */
    val monthWithheldTotal: String,
    /** One-line IMSS coverage summary (reuses the B-051 calculator), e.g. "IMSS: cubierto en Uber…". */
    val imssCoverageLine: String,
    /** Footnote shown when any platform's commission/net is an approximation, or null. */
    val approximationNote: String?,
    /** The disclaimer footer text. */
    val disclaimer: String,
    /** The rates-and-source footer line, e.g. "Estimación bajo régimen de plataformas (ISR 2.1%…)". */
    val ratesNote: String,
    /** Generation date line, e.g. "Generado el 10 jun 2026". */
    val generatedOn: String,
) {
    companion object {
        const val WORDMARK = "Kompara"
    }
}

/** One formatted table row: platform name + four right-aligned money columns. */
data class FiscalReportRow(
    val label: String,
    val grossMxn: String,
    val netMxn: String,
    val isrMxn: String,
    val ivaMxn: String,
) {
    /** The four money cells left→right, parallel to the money column headers. */
    fun moneyCells(): List<String> = listOf(grossMxn, netMxn, isrMxn, ivaMxn)
}
