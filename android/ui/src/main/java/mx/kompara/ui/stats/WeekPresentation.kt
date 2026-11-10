package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import mx.kompara.metrics.WeekMoneySnapshot
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.format.spanishDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val weekSpanish = Locale.forLanguageTag("es-MX")
fun weekRange(start: LocalDate, today: LocalDate = start): String {
    val end = start.plusDays(6)
    val month = spanishDate("MMM")
    val first = if (start.month == end.month && start.year == end.year) "${start.dayOfMonth}" else "${start.dayOfMonth} ${start.format(month)}" + if (start.year != end.year) " ${start.year}" else ""
    return "$first – ${end.dayOfMonth} ${end.format(month)}" + if (end.year != today.year || start.year != end.year) " ${end.year}" else ""
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

/** One bar of the week (L4, L5): its height relative to the best day; a day without trips is a hairline. Only the best carries its amount. */
data class WeekBar(val date: LocalDate, val letter: String, val fraction: Float, val best: Boolean, val value: String?, val elapsed: Boolean) {
    val label: String? get() = value?.takeIf { best }
}

/** The car as one bar under the week (plan 4.5): [hatched] is the car's share of the bar. */
data class WeekCar(val hatched: Float, val covered: Boolean, val label: String, val amount: String, val restLabel: String, val rest: String)

/** Semana (v4 L4, L5): Monday to Sunday, what the trips kept, seven bars and the week's car bill. */
data class WeekPresentation(val snapshot: WeekMoneySnapshot) {
    val money get() = snapshot.money
    val start: LocalDate get() = money.window.start
    val today: LocalDate get() = money.window.asOf.atZone(money.window.zone).toLocalDate()
    val open get() = start == today.monday()
    val title get() = weekRange(start, today) + if (open) " · en curso" else ""
    val label get() = if (open) "Esta semana llevas" else "Ganaste"
    val amount: String? get() = money.tripContribution?.let(Formatters::formatMxnWhole)
    val calculable get() = money.trips > 0
    val quietLine get() = when {
        money.trips == 0 -> if (open) "Todavía no hay viajes esta semana" else "No tuviste viajes esa semana."
        amount == null -> "${tripsLabel(money.trips)}\nFaltan datos para calcular lo que ${if (open) "llevas" else "ganaste"}"
        else -> "${tripsLabel(money.trips)} · ya restamos ${if (money.electric()) "energía" else "gasolina"} y desgaste"
    }
    val bars: List<WeekBar> get() {
        val values = snapshot.car.days.map { it?.coerceAtLeast(BigDecimal.ZERO) }
        val best = values.filterNotNull().maxOrNull()?.takeIf { it.signum() > 0 }
        val bestIndex = best?.let { values.indexOf(it) }
        return values.mapIndexed { i, value ->
            val date = start.plusDays(i.toLong())
            WeekBar(date, listOf("L", "M", "M", "J", "V", "S", "D")[i],
                if (value == null || best == null) 0f else value.divide(best, 6, RoundingMode.HALF_UP).toFloat(),
                i == bestIndex, snapshot.car.days[i]?.let(Formatters::formatMxnWhole), date <= today)
        }
    }
    val car: WeekCar? get() {
        val car = snapshot.car
        val bill = car.bill ?: return null
        if (amount == null) return null // no week figure, no car bar: it would count an uncalculated day as $0
        val covered = car.coveredOn
        fun share(part: BigDecimal, whole: BigDecimal) =
            if (whole.signum() <= 0) 0f else part.divide(whole, 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
        return if (covered != null) WeekCar(share(bill, car.earned), true,
            "El coche, cubierto el ${covered.getDisplayName(TextStyle.FULL, weekSpanish)}", Formatters.formatMxnWhole(bill),
            "Tuyo", Formatters.formatMxnWhole(car.yours!!))
        else WeekCar(share(car.earned, bill), false, "El coche",
            "${Formatters.formatMxnWhole(car.earned)} de ${Formatters.formatMxnWhole(bill)}",
            if (open) "Te faltan" else "Te faltaron", Formatters.formatMxnWhole(car.missing!!))
    }
    val calculation get() = periodCalculation(money, "semana", label)
    /** Read by the dormant premium eligibility (plan §3, after launch). */
    val breakdownAvailable get() = money.byApp.values.any { it.ratePerKm != null } || snapshot.days.any { it?.contributions?.isNotEmpty() == true }
}
