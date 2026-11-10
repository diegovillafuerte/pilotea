package mx.kompara.ui.stats

import mx.kompara.data.accounting.PeriodMoney
import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.ui.components.CalculationRow
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode

/** Cómo se calculó (v4 L8): the fares of the taken trips, gasoline and wear, and what is left. Nothing subtracts the car. */
data class PeriodCalculation(val title: String, val rows: List<CalculationRow>, val notes: List<String>)

/** The one sentence that says the figures are estimates (plan 4.1). */
const val ESTIMATE_NOTE = "Es un estimado. El pago final lo confirma tu app de viajes."

internal fun tripsLabel(trips: Int) = if (trips == 1) "1 viaje" else "$trips viajes"
internal fun PeriodMoney.electric() = contributions.any { it.runningCostVersion?.inputs?.mode == RunningCostMode.ELECTRIC }
internal fun PeriodMoney.runningLabel() = if (electric()) "Energía y desgaste" else "Gasolina y desgaste"

fun periodCalculation(money: PeriodMoney, period: String, total: String): PeriodCalculation {
    val kms = money.contributions.map { it.record.km }
    val km = if (kms.any { it == null }) null else kms.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)
    // One cost in force over the period shows its multiplier; a period that spans more than one shows only the km.
    val cost = money.contributions.map { it.runningCostVersion }.distinct().singleOrNull()?.inputs?.costPerKm
    val running = when {
        km == null -> "Faltan kilómetros"
        cost != null -> "${Formatters.formatKm(km.toDouble())} × ${Formatters.formatMxn(cost)}"
        else -> Formatters.formatKm(km.toDouble())
    }
    val rows = listOf(
        CalculationRow(tripsLabel(money.trips), money.payment?.let(Formatters::formatMxnWhole) ?: "—", "Las tarifas de tus ofertas"),
        CalculationRow(money.runningLabel(), money.runningExpense?.negate()?.let(Formatters::formatMxnWhole) ?: "—", running),
        CalculationRow(total, money.tripContribution?.let(Formatters::formatMxnWhole) ?: "—", total = true),
    )
    val payment = money.payment; val expense = money.runningExpense; val result = money.tripContribution
    val rounded = payment != null && expense != null && result != null && payment.setScale(0, RoundingMode.HALF_UP)
        .subtract(expense.setScale(0, RoundingMode.HALF_UP)).compareTo(result.setScale(0, RoundingMode.HALF_UP)) != 0
    return PeriodCalculation("Cómo se calculó · $period", rows, listOfNotNull(
        "El total se calcula antes de redondear cada cifra.".takeIf { rounded },
        "No conservamos tus costos de ese día".takeIf { money.unknownHistoricalCost },
        ESTIMATE_NOTE,
    ))
}
