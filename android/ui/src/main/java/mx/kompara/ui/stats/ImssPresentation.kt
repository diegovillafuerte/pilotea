package mx.kompara.ui.stats

import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.metrics.imss.ImssApp
import mx.kompara.metrics.imss.ImssMonth
import mx.kompara.metrics.imss.ImssRule
import mx.kompara.metrics.imss.ImssState
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** One app's block on M1/M2: its amount toward the need, the bar, what it is made of and one sentence. */
data class ImssBlock(val platform: String, val name: String, val amount: String, val fraction: Float, val parts: String, val said: Emphasis?)

/**
 * The IMSS tab (M1/M2, plan 4.7). A pace and its shortfall are shown to the hundred: a pace that
 * reaches rounds up, a shortfall rounds up (never $0); a closed month's shortfall to the peso, from the
 * printed amount. No line certifies coverage: only the app and the IMSS can.
 */
data class ImssPresentation(
    val title: String,
    /** The amount one app has to pay, "$21,938". */
    val need: String,
    val rule: Emphasis,
    val blocks: List<ImssBlock>,
    /** "Aún no hay viajes en octubre." / "No hubo viajes en septiembre." when the month has no block. */
    val empty: String?,
    val combined: Emphasis?,
    /** "Ver mi vigencia en el IMSS" shows once an app is over the amount. */
    val vigencia: Boolean,
) {
    companion object {
        fun of(month: ImssMonth): ImssPresentation {
            val need = Formatters.formatMxnWhole(month.need)
            val blocks = month.apps.map { app -> block(app, need, month.need) }
            // The sum of the amounts printed above it, so it never reads a peso off them; claimed only when that sum reaches the need.
            val printed = month.apps.fold(BigDecimal.ZERO) { sum, app -> sum.add(app.counted.setScale(0, RoundingMode.HALF_UP)) }
            val combined = if (!month.combinedReaches || printed < month.need) null else Emphasis("Entre las ${count(month.apps.size)} ${if (month.current) "llevas" else "juntaste"} ",
                Formatters.formatMxnWhole(printed), ". En una sola app ya lo habrías alcanzado.")
            val empty = when {
                month.apps.isNotEmpty() -> null
                month.current -> "Aún no hay viajes en ${monthName(month.month)}."
                else -> "No hubo viajes en ${monthName(month.month)}."
            }
            return ImssPresentation(title(month.month, month.today), need, rule(month.need), blocks, empty, combined,
                month.apps.any { it.state == ImssState.OVER })
        }

        /** "Octubre · quedan 12 días", "… · queda 1 día", "… · último día"; another month is just its name. */
        fun title(month: YearMonth, today: LocalDate): String {
            val name = monthLabel(month, today)
            if (month != YearMonth.from(today)) return name
            return when (val left = month.lengthOfMonth() - today.dayOfMonth) {
                0 -> "$name · último día"
                1 -> "$name · queda 1 día"
                else -> "$name · quedan $left días"
            }
        }

        fun rule(need: BigDecimal): Emphasis = Emphasis("Una app te da IMSS completo si en el mes te paga ", Formatters.formatMxnWhole(need), " o más.")

        private fun block(app: ImssApp, needText: String, need: BigDecimal): ImssBlock {
            val name = appName(app.platform)
            val parts = "Viajes ${Formatters.formatMxnWhole(app.trips)} · " +
                (app.incentives?.let { "Incentivos ${Formatters.formatMxnWhole(BigDecimal(it))}" } ?: "Incentivos: agregar")
            val said = when (app.state) {
                ImssState.OVER -> Emphasis("", "Ya pasaste los $needText con $name",
                    // "lo que escribiste" are the typed incentives; without them only the trips were read.
                    if (app.incentives != null) ", según lo que leímos y lo que escribiste. Confírmalo en el IMSS."
                    else ", según lo que leímos. Confírmalo en el IMSS.")
                ImssState.WILL_REACH -> Emphasis("A este ritmo cierras en unos ${hundreds(app.pace!!, RoundingMode.CEILING)}. ", "Lo alcanzas.")
                // Short of the amount the pace keeps the nearest hundred, as M1 draws it (12,958 → "unos $13,000").
                ImssState.WILL_FALL_SHORT -> Emphasis("A este ritmo cierras en unos ${hundreds(app.pace!!, RoundingMode.HALF_UP)}. ",
                    "Te faltarían ${hundreds(app.shortBy!!, RoundingMode.CEILING)}.")
                ImssState.FELL_SHORT -> Emphasis("", "Te faltaron ${Formatters.formatMxnWhole(need.subtract(app.counted.setScale(0, RoundingMode.HALF_UP)))}.")
                ImssState.NO_PACE_YET -> null
            }
            val fraction = if (need.signum() > 0) app.counted.divide(need, MONEY_CONTEXT).toFloat().coerceIn(0f, 1f) else 0f
            return ImssBlock(app.platform, name, Formatters.formatMxnWhole(app.counted), fraction, parts, said)
        }

        private fun hundreds(value: BigDecimal, rounding: RoundingMode): String =
            Formatters.formatMxnWhole(value.divide(BigDecimal(100), MONEY_CONTEXT).setScale(0, rounding).multiply(BigDecimal(100)))

        private fun count(apps: Int) = when (apps) { 2 -> "dos"; 3 -> "tres"; else -> "$apps" }
    }
}

/** M3's copy, from the year's rule: the only place the deduction and the IVA appear. */
object ImssRuleText {
    fun lines(rule: ImssRule): List<Emphasis> = listOf(
        Emphasis("", "Si una app te paga ${Formatters.formatMxnWhole(rule.need)} o más en el mes,", " calificas para la cobertura completa del IMSS con esa app."),
        Emphasis("", "Cuentan", " tus viajes, tus incentivos y tus bonos por referidos. Las propinas no."),
        Emphasis("", "Cada app cuenta por separado", " y cada mes empieza de cero."),
    )

    fun source(rule: ImssRule): Emphasis = Emphasis("", "De dónde sale la cifra.",
        " A lo que te paga la app se le quita ${percent(rule.vehicleCostDeduction)} de gastos y luego ${percent(rule.iva)} de IVA. " +
            "Lo que queda debe llegar a ${Formatters.formatMxnWhole(rule.minMonthlyWageCdmx)}, el salario mínimo mensual de CDMX en ${rule.year}.")

    private fun percent(fraction: BigDecimal) = fraction.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString() + Formatters.NBSP + "%"
}
