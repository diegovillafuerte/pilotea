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

    fun recorded(month: mx.kompara.ui.stats.MonthPresentation, generatedOn: String): FiscalReportData {
        val money = month.money
        val blank = FiscalReportRow("", "", "", "", "")
        val rows = money.byApp.entries.sortedBy { it.key }.map {
            mx.kompara.ui.stats.appName(it.key) + " · pago registrado" to (it.value.payment?.let(Formatters::formatMxnWhole) ?: "—")
        } + (if (month.importOnly) emptyList() else listOf(
            (mx.kompara.ui.stats.monthCalculation(month).rows.firstOrNull { it.label.endsWith("y desgaste") }?.label ?: "Gasolina y desgaste") to (money.runningExpense?.negate()?.let(Formatters::formatMxnWhole) ?: "—"),
            "Lo del coche" to Formatters.formatMxnWhole(money.carCost.negate()),
            "${month.totalLabel} · aprox." to (month.amount ?: "—"))) + month.imports.map {
            "${mx.kompara.ui.stats.appName(it.app)} · resumen ${mx.kompara.ui.stats.monthRange(it.coverage.start, it.coverage.endInclusive)}" to (it.payment?.let(Formatters::formatMxnWhole) ?: "—")
        }
        val notes = buildList {
            money.contributions.groupBy { it.record.source }.forEach { (source, rows) ->
                val apps = rows.map { mx.kompara.ui.stats.appName(it.record.app) }.distinct().joinToString(" y ")
                add(when (source) {
                    mx.kompara.data.accounting.MoneySource.CAPTURED_ESTIMATE -> "Fuente de $apps: viajes registrados en este teléfono. El pago final lo confirma tu resumen de la app."
                    mx.kompara.data.accounting.MoneySource.USER_CORRECTED_IMPORT -> "Fuente de $apps: resumen importado, con correcciones que guardaste en este teléfono."
                    else -> "Fuente de $apps: resumen importado; se usaron solo los campos elegidos con cobertura compatible."
                })
            }
            if (month.open) add("Registros hasta el " + money.window.asOf.atZone(money.window.zone)
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("es-MX"))))
            val calculationNotes = mx.kompara.ui.stats.monthCalculation(month).notes
            if (calculationNotes.any { it.startsWith("El total se calcula") }) add("El total se calcula antes de redondear cada cifra.")
            addAll(calculationNotes.filter { it == "Con costos típicos" || it == "No conservamos tus costos de ese día" })
            month.imports.forEach { record ->
                add("${mx.kompara.ui.stats.appName(record.app)} · ${mx.kompara.ui.stats.monthRange(record.coverage.start, record.coverage.endInclusive)}: resumen importado, sin detalle por día; pago ${record.payment?.let(Formatters::formatMxnWhole) ?: "—"}, no entra en el resultado.")
                if (record.paymentBasis == mx.kompara.data.accounting.PaymentBasis.UNCONFIRMED) add("El pago leído de ese resumen está sin confirmar.")
            }
            if (month.importOnly) add("Sin kilómetros no podemos calcular gasolina y desgaste ni lo que te quedó.")
            month.snapshot.unallocated.forEach { record ->
                add("${mx.kompara.ui.stats.appName(record.app)} · ${mx.kompara.ui.stats.weekRange(record.coverage.start, month.today)}: resumen sin detalle por día; no se reparte entre meses ni entra en estos totales.")
            }
            add("Antes de impuestos. No es constancia ni asesoría.")
        }
        return FiscalReportData(FiscalReportData.WORDMARK, "Resumen para tu contador", null,
            Formatters.formatMonthLabel(month.month.atDay(1).toString()), emptyList(), emptyList(), blank, blank,
            "", mx.kompara.ui.screens.IMSS_NON_REPRESENTATION, null, "No es constancia ni asesoría.", "", generatedOn,
            recordedRows = rows, sourceNotes = notes)
    }

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
