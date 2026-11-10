package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import mx.kompara.ui.components.CalculationRow
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The v3 period ledger, kept only for the month the dormant IMSS PDF prints (MonthPresentation,
 * after launch per docs/v4-simple-launch-plan.md §3). Ganancias uses PeriodCalculation instead.
 */

private val spanish = Locale.forLanguageTag("es-MX")
internal fun LocalDate.dated(today: LocalDate): String = format(DateTimeFormatter.ofPattern(
    if (year == today.year) "EEEE d MMM" else "EEEE d MMM yyyy", spanish))
internal fun PeriodMoney.isToday() = window.start == window.asOf.atZone(window.zone).toLocalDate()
internal fun PeriodMoney.dayLabel() = if (isToday()) "Llevas hoy · aprox."
    else "Te quedaron el ${window.start.dated(window.asOf.atZone(window.zone).toLocalDate())} · aprox."
internal fun PeriodMoney.tripCount() = if (trips == 1) "1 viaje tomado" else "$trips viajes tomados"
internal fun PeriodMoney.energy() = if (contributions.any { it.runningCostVersion?.inputs?.mode == RunningCostMode.ELECTRIC }) "energía" else "gasolina"
internal fun PeriodMoney.typical() = contributions.any { it.runningCostVersion?.confirmation == CostConfirmation.TYPICAL }
internal fun PeriodMoney.basis() = if (typical()) "con costos típicos" else if (carConfigured) "ya restamos ${energy()}, desgaste y coche" else "ya restamos ${energy()} y desgaste"

data class DayCalculation(val title: String, val rows: List<CalculationRow>, val notes: List<String>)
fun dayCalculation(money: PeriodMoney): DayCalculation {
    val before = if (money.carConfigured) "antes de ${money.energy()}, desgaste y coche" else "antes de ${money.energy()} y desgaste"
    val km = money.contributions.map { it.record.km }.let { values -> if (values.any { it == null }) null else values.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add) }
    val rows = buildList {
        add(CalculationRow(money.tripCount(), money.payment?.let(Formatters::formatMxnWhole) ?: "—", (if (money.inProgress > 0) "${money.inProgress} en curso · " else "") + before))
        add(CalculationRow(money.energy().replaceFirstChar { it.titlecase() } + " y desgaste", money.runningExpense?.negate()?.let(Formatters::formatMxnWhole) ?: "—", km?.let { Formatters.formatKm(it.toDouble()) } ?: "Faltan kilómetros"))
        if (money.carConfigured) add(CalculationRow("Lo del coche", Formatters.formatMxnWhole(money.carCost.negate()), "${Formatters.formatMxnWhole(money.carCost.multiply(BigDecimal(7)))} ÷ 7 días"))
        add(CalculationRow(money.dayLabel(), money.periodResult?.let(Formatters::formatMxnWhole) ?: "—", total = true))
    }
    val roundedParts = if (money.payment != null && money.runningExpense != null) money.payment!!.setScale(0, RoundingMode.HALF_UP)
        .subtract(money.runningExpense!!.setScale(0, RoundingMode.HALF_UP)).subtract(money.carCost.setScale(0, RoundingMode.HALF_UP)) else null
    return DayCalculation("Cómo se calculó · ${if (money.isToday()) "hoy" else Formatters.formatDayLabel(money.window.start.toString())}", rows, buildList {
        if (roundedParts != null && money.periodResult != null && roundedParts.compareTo(money.periodResult!!.setScale(0, RoundingMode.HALF_UP)) != 0)
            add("El total se calcula antes de redondear cada cifra.")
        if (money.typical()) add("Con costos típicos")
        if (money.contributions.any { it.runningCostVersion?.correctsVersionId != null })
            add("Aplicaste el rendimiento corregido a estos viajes. Conservamos el precio de cada fecha.")
        if (money.unknownHistoricalCost) add("No conservamos tus costos de ese día")
        add("El pago final lo confirma tu resumen de la app. Antes de impuestos.")
    })
}
