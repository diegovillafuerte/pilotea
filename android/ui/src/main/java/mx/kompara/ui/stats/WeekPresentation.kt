package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import mx.kompara.metrics.WeekMoneySnapshot
import mx.kompara.ui.components.BarValue
import mx.kompara.ui.components.CalculationRow
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val weekSpanish = Locale.forLanguageTag("es-MX")
fun weekRange(start: LocalDate, today: LocalDate = start): String {
    val end = start.plusDays(6)
    val month = DateTimeFormatter.ofPattern("MMM", weekSpanish)
    val first = if (start.month == end.month && start.year == end.year) "${start.dayOfMonth}" else "${start.dayOfMonth} ${start.format(month)}" + if (start.year != end.year) " ${start.year}" else ""
    return "$first – ${end.dayOfMonth} ${end.format(month)}" + if (end.year != today.year || start.year != end.year) " ${end.year}" else ""
}
fun weekSelectorLabel(start: LocalDate, today: LocalDate): String {
    val ago = ChronoUnit.WEEKS.between(start, today.monday())
    return weekRange(start, today) + when (ago) { 0L -> " · en curso"; 1L -> " · semana pasada"; else -> " · hace $ago semanas" }
}
fun appName(app: String): String = when (app.uppercase()) { "UBER" -> "Uber"; "DIDI" -> "DiDi"; "INDRIVE" -> "inDrive"; else -> app }

data class StatementDifference(val app: String, val captured: BigDecimal, val statement: BigDecimal) {
    val difference: BigDecimal get() = statement.subtract(captured)
    val display: String get() = (if (difference.setScale(0, RoundingMode.HALF_UP).signum() > 0) "+" else "") + Formatters.formatMxnWhole(difference)
}

/** A difference requires comparable driver payments, matching app, exact week and work coverage. */
fun statementDifferences(money: PeriodMoney, statements: List<IncomeRecord>): List<StatementDifference> =
    statements.groupBy { it.app }.mapNotNull { (app, candidates) ->
        val statement = candidates.singleOrNull {
            it.source != MoneySource.CAPTURED_ESTIMATE && it.eligible && it.paymentBasis == PaymentBasis.DRIVER_PAYMENT &&
                it.coverage.start == money.window.start && it.coverage.endInclusive == money.window.endInclusive && it.payment != null
        } ?: return@mapNotNull null
        val captured = money.contributions.filter { it.record.app == app }
        if (captured.isEmpty() || captured.any { it.record.source != MoneySource.CAPTURED_ESTIMATE || it.record.paymentBasis != PaymentBasis.DRIVER_PAYMENT || it.record.payment == null } ||
            captured.sumOf { it.record.trips } != statement.trips || captured.any { it.record.inProgress > 0 }) return@mapNotNull null
        StatementDifference(app, captured.fold(BigDecimal.ZERO) { sum, it -> sum + it.record.payment!! }, statement.payment!!)
    }

data class WeekPresentation(val snapshot: WeekMoneySnapshot, val premium: Boolean = false) {
    val money get() = snapshot.money
    val start get() = money.window.start
    val today get() = money.window.asOf.atZone(money.window.zone).toLocalDate()
    val open get() = start == today.monday()
    val label get() = if (open) "Llevas esta semana · aprox." else "Te quedaron esa semana · aprox."
    val hasData get() = snapshot.hasOffers || money.contributions.isNotEmpty() || money.paymentOnly.isNotEmpty() || money.carConfigured
    val amount get() = money.periodResult?.takeIf { money.contributions.isNotEmpty() || money.carConfigured }?.let(Formatters::formatMxnWhole)
    val breakdownAvailable get() = money.byApp.values.any { it.ratePerKm != null } || snapshot.days.any { it?.contributions?.isNotEmpty() == true }
    val locked get() = !premium && breakdownAvailable && !HistoryWeeks.isFree(start, today)
    val quietLine get() = if (money.periodResult == null) "${money.tripCount()},\nfaltan datos para calcular lo que te quedó" else money.tripCount() + (if (money.inProgress > 0) " (${money.inProgress} en curso)" else "") +
        ",\n${money.basis()}"
    val days get() = snapshot.days
    val bars get() = days.mapIndexed { i, day -> BarValue(listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")[i],
        day?.periodResult?.toDouble(), day?.periodResult?.let(Formatters::formatMxnWhole) ?: "—") }
    val roundingNote: String? get() {
        val children = days.filterNotNull()
        val exactChildren = children.fold(BigDecimal.ZERO) { sum, day -> sum + (day.periodResult ?: return null) }
        // Different source coverage is not rounding (e.g. an undated statement with no day allocation).
        if (money.periodResult == null || exactChildren.subtract(money.periodResult).abs() > BigDecimal("0.000000000001")) return null
        val residual = money.roundingResidual(children) ?: return null
        if (children.isEmpty() || residual.signum() == 0) return null
        val sum = children.fold(BigDecimal.ZERO) { total, day -> total + (day.periodResult ?: return null).setScale(0, RoundingMode.HALF_UP) }
        return "Redondeado una vez · los días suman ${Formatters.formatMxnWhole(sum)}"
    }
    val apps get() = money.byApp.entries.sortedWith(compareByDescending<Map.Entry<String, AppContribution>> { it.value.ratePerKm }.thenBy { it.key })
    val finding: String? get() {
        val rates = apps.filter { it.value.ratePerKm != null }
        if (rates.size != 2) return null
        val delta = rates[0].value.ratePerKm!!.subtract(rates[1].value.ratePerKm!!)
        if (delta.setScale(2, RoundingMode.HALF_UP).signum() == 0) return "Las dos apps te dejaron lo mismo por km"
        return "${appName(rates[0].key)} te dejó ${Formatters.formatMxn(delta)} más por km"
    }
    val differences get() = statementDifferences(snapshot.capturedMoney ?: money, snapshot.statements.filter { statement ->
        statement.id in snapshot.selectedStatements || snapshot.statements.none { it.app == statement.app && it.id in snapshot.selectedStatements }
    })
    val dailyCarLine: String? get() = money.carCharges.lastOrNull()?.let { "Cada día se restan ${Formatters.formatMxnWhole(it.amount)} del coche" }
    fun dayLabel(index: Int): String {
        val date = start.plusDays(index.toLong())
        val day = days[index] ?: return date.format(DateTimeFormatter.ofPattern("EEEE d", weekSpanish)).replaceFirstChar { it.titlecase(weekSpanish) }
        return date.format(DateTimeFormatter.ofPattern("EEEE d", weekSpanish)).replaceFirstChar { it.titlecase(weekSpanish) } +
            if (day.trips > 0) " · ${day.trips} viajes" else ""
    }
    fun dayNote(index: Int): String? = days[index]?.let {
        if (it.trips == 0) if (it.carConfigured) "Sin viajes; lo del coche corre igual" else "Sin viajes" else null
    }
}

fun weekCalculation(week: WeekPresentation): DayCalculation {
    val money = week.money
    val base = dayCalculation(money)
    val rows = base.rows.mapIndexed { index, row -> when {
        index == 0 -> row.copy(detail = money.byApp.entries.sortedBy { if (it.key == "UBER") "0" else it.key }
            .joinToString(" · ") { "${appName(it.key)} ${it.value.payment?.let(Formatters::formatMxnWhole) ?: "—"}" })
        row.label == "Lo del coche" -> row.copy(detail = if (money.carCharges.map { it.versionId }.distinct().size == 1)
            "${Formatters.formatMxnWhole(money.carCharges.first().amount.multiply(BigDecimal(7)))} por semana" else "Según el costo vigente de cada día")
        row.total -> row.copy(label = week.label)
        else -> row
    } }
    return base.copy(title = "Cómo se calculó · semana", rows = rows)
}
