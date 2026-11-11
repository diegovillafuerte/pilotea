package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import mx.kompara.metrics.MonthMoneySnapshot
import mx.kompara.ui.components.BarValue
import mx.kompara.ui.components.CalculationRow
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.format.spanishDate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthSpanish = Locale.forLanguageTag("es-MX")
fun monthName(month: YearMonth): String = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL", monthSpanish))
fun monthLabel(month: YearMonth, today: LocalDate): String = monthName(month).replaceFirstChar { it.titlecase(monthSpanish) } +
    if (month.year != today.year) " ${month.year}" else ""
fun monthRange(start: LocalDate, end: LocalDate): String =
    (if (start == end) "${start.dayOfMonth}" else "${start.dayOfMonth} – ${end.dayOfMonth}") + " " + end.format(spanishDate("MMM"))

/** One dated net ledger and separately named statement-payment scopes. */
data class MonthPresentation(val snapshot: MonthMoneySnapshot, val premium: Boolean = false) {
    val money get() = snapshot.money
    val month get() = YearMonth.from(money.window.start)
    val today get() = money.window.asOf.atZone(money.window.zone).toLocalDate()
    val open get() = month == YearMonth.from(today)
    val imports get() = money.paymentOnly.map { it.record }
    val paymentScopes get() = imports + snapshot.unallocated
    val importOnly get() = money.contributions.isEmpty() && paymentScopes.isNotEmpty()
    val hasData get() = snapshot.hasOffers || money.contributions.isNotEmpty() || imports.isNotEmpty() || snapshot.unallocated.isNotEmpty() || money.carConfigured
    val locked get() = mx.kompara.billing.FreeDetailWindow.monthLocked(month, today, money.contributions.isNotEmpty(), premium)
    val selector get() = monthLabel(month, today) + when { importOnly -> " · antes de Kompara"; open -> " · en curso"; else -> "" }
    val totalLabel get() = "${if (open) "Llevas" else "Te quedaron"} en ${monthName(month)}"
    val label get() = if (importOnly && paymentScopes.size == 1) paymentLabel(paymentScopes.single()) else if (importOnly) "Pagos de tus resúmenes · sin detalle por día" else "$totalLabel · aprox."
    val amount get() = (if (importOnly) if (paymentScopes.any { it.payment == null }) null else paymentScopes.fold(BigDecimal.ZERO) { sum, it -> sum + it.payment!! }
        else money.periodResult?.takeIf { money.contributions.isNotEmpty() || money.carConfigured })?.let(Formatters::formatMxnWhole)
    val basis get() = money.basis()
    val quietLine get() = if (importOnly) (if (paymentScopes.all { it.tripsKnown }) "${paymentScopes.sumOf { it.trips }} viajes según tu resumen, sin km" else "Sin número de viajes ni kilómetros en tu resumen") else
        money.tripCount() + (if (money.inProgress > 0) " (${money.inProgress} en curso)" else "") + ",\n" +
            if (money.periodResult == null) "faltan datos para calcular lo que te quedó" else basis
    val choices get() = (snapshot.history + month + YearMonth.from(today)).distinct().filter { it <= YearMonth.from(today) }.sortedDescending()
    // Empty pre-reader calendar intervals are gaps, not invented net bars for a statement.
    val weeks get() = snapshot.weeks.filter { it.contributions.isNotEmpty() || it.carConfigured && !importOnly }
    val plotWeeks get() = (weeks.map { it.window.start } + imports.map { it.coverage.start }).distinct().sorted()
    val bars get() = plotWeeks.map { start ->
        val week = weeks.firstOrNull { it.window.start == start }
        val end = week?.window?.endInclusive ?: imports.first { it.coverage.start == start }.coverage.endInclusive
        val label = if (start == today && end == today) "hoy" else if (start == end) "${start.dayOfMonth}" else "${start.dayOfMonth} – ${end.dayOfMonth}"
        BarValue(label, week?.periodResult?.toDouble(), week?.periodResult?.let(Formatters::formatMxnWhole) ?: "—")
    }
    val roundingNote: String? get() {
        val residual = money.roundingResidual(weeks) ?: return null
        val sum = weeks.fold(BigDecimal.ZERO) { total, it -> total + (it.periodResult ?: return null) }
        if (money.periodResult == null || sum.subtract(money.periodResult).abs() > BigDecimal("0.000000000001") || residual.signum() == 0) return null
        return "El total se calcula antes de redondear cada semana."
    }
}

fun paymentLabel(record: IncomeRecord): String = (if (record.paymentBasis == PaymentBasis.UNCONFIRMED) "Pago leído de" else "Te pagó") +
    " ${appName(record.app)} del " + record.coverage.start.format(spanishDate(if (record.coverage.start.month == record.coverage.endInclusive.month) "d" else "d MMM")) + " al " +
    record.coverage.endInclusive.format(spanishDate("d MMM"))

fun monthCalculation(month: MonthPresentation): DayCalculation {
    val money = month.money
    if (month.importOnly) return DayCalculation("Cómo se calculó · ${monthName(month.month)}", month.paymentScopes.map {
        CalculationRow(paymentLabel(it), it.payment?.let(Formatters::formatMxnWhole) ?: "—", if (it.tripsKnown) "${it.trips} viajes según tu resumen, sin km" else "Sin número de viajes ni kilómetros en tu resumen")
    }, listOf("Sin kilómetros no podemos calcular gasolina y desgaste ni lo que te quedó.",
        "Este resumen no incluye el detalle por día.") +
        (if (month.snapshot.unallocated.isNotEmpty()) listOf("La semana cruza de mes. Su pago no se reparte entre meses ni entra en los pagos del mes para el IMSS.") else emptyList()) +
        (if (month.paymentScopes.any { it.paymentBasis == PaymentBasis.UNCONFIRMED }) listOf("El pago leído del resumen está sin confirmar.") else emptyList()) +
        if (money.carConfigured) listOf("Lo del coche: ${Formatters.formatMxnWhole(money.carCost)} en ${money.carCharges.size} días. No se resta de este pago.") else emptyList())
    val base = dayCalculation(money)
    val dated = if (month.open) "Del 1 de ${monthName(month.month)} hasta hoy a las ${money.window.asOf.atZone(money.window.zone).format(DateTimeFormatter.ofPattern("HH:mm"))}" else null
    return base.copy(title = "Cómo se calculó · ${monthName(month.month)}", rows = base.rows.map { row -> when {
        row.label == "Lo del coche" -> row.copy(detail = if (money.carCharges.map { it.versionId }.distinct().size == 1)
            "${Formatters.formatMxnWhole(money.carCharges.first().amount.multiply(BigDecimal(7)))} ÷ 7 × ${money.carCharges.size} días" else "Según el costo vigente de cada día")
        row.total -> row.copy(label = "${month.totalLabel} · aprox.", detail = dated)
        else -> row
    } }, notes = base.notes.filter { it != "El pago final lo confirma tu resumen de la app. Antes de impuestos." } + listOfNotNull(month.roundingNote) +
        if (month.imports.isNotEmpty()) listOf("Los pagos sin detalle por día no entran en el total.") else emptyList())
}
