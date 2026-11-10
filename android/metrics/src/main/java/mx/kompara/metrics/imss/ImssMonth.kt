package mx.kompara.metrics.imss

import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.model.Platform
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * The founder's IMSS rule (2026-11-09): an app gives full coverage when what it paid in the month
 * (trips, incentives and referral bonuses; never tips) × (1 − vehicle cost deduction) × (1 − IVA)
 * reaches CDMX's monthly minimum wage. So one app has to pay [need] in the month. Each app counts
 * on its own. The year's three numbers live here, in one place.
 */
data class ImssRule(
    val year: Int,
    val minMonthlyWageCdmx: BigDecimal,
    val vehicleCostDeduction: BigDecimal,
    val iva: BigDecimal,
) {
    /** wage ÷ ((1 − deduction) × (1 − IVA)), rounded up to the peso: shown and compared as this. */
    val need: BigDecimal get() = minMonthlyWageCdmx.divide(
        BigDecimal.ONE.subtract(vehicleCostDeduction).multiply(BigDecimal.ONE.subtract(iva)), MONEY_CONTEXT,
    ).setScale(0, RoundingMode.CEILING)

    companion object {
        /** 2026: CDMX minimum monthly wage, vehicle deduction 48 %, IVA 16 % → $21,938. */
        val Y2026 = ImssRule(2026, BigDecimal("9582.47"), BigDecimal("0.48"), BigDecimal("0.16"))
    }
}

enum class ImssState {
    /** counted ≥ need: "Ya pasaste los $21,938 con Uber". */
    OVER,
    /** Current month, the pace reaches the need: "Lo alcanzas". */
    WILL_REACH,
    /** Current month, the pace falls short by [ImssApp.shortBy]: "Te faltarían $9,000". */
    WILL_FALL_SHORT,
    /** A past month that closed short by [ImssApp.shortBy]: "Te faltaron $9,651". */
    FELL_SHORT,
    /** Current month before day [ImssMonth.PACE_FROM_DAY], or an app with no trips in it: no pace, only the amount. */
    NO_PACE_YET,
}

/**
 * One app's month. [trips] = fares of its taken trips that started in the month; [incentives] =
 * what the driver typed (null when nothing was typed); [counted] = both; [pace] = trips ÷ day of
 * month × days in month + incentives, unrounded (the screen shows it to the hundred); null with no
 * trips in the month.
 */
data class ImssApp(
    val platform: String,
    val trips: BigDecimal,
    val incentives: Int?,
    val counted: BigDecimal,
    val pace: BigDecimal?,
    val state: ImssState,
    /** need − pace for [ImssState.WILL_FALL_SHORT], need − counted for [ImssState.FELL_SHORT]; else null. */
    val shortBy: BigDecimal?,
)

data class ImssMonth(
    val month: YearMonth,
    val today: LocalDate,
    val need: BigDecimal,
    /**
     * Apps with taken trips in the month or the month before, or typed incentives in the month, Uber,
     * DiDi, inDrive first: an app that drove last month keeps a block, so this month's bonus can be typed.
     */
    val apps: List<ImssApp>,
) {
    val current: Boolean get() = YearMonth.from(today) == month
    val hasTrips: Boolean get() = apps.any { it.trips.signum() > 0 }
    /** What all apps counted together: "Entre las dos llevas $22,229". */
    val combined: BigDecimal get() = apps.fold(BigDecimal.ZERO) { sum, app -> sum.add(app.counted) }
    /** Two or more apps, none over the need, but together they are: "En una sola app ya lo habrías alcanzado". */
    val combinedReaches: Boolean get() = apps.size >= 2 && apps.none { it.state == ImssState.OVER } && combined >= need

    companion object {
        /** The pace is shown from this day of the month on. */
        const val PACE_FROM_DAY = 7

        /**
         * [trips]: fares by platform of the month's taken trips; [incentives]: typed whole pesos by platform;
         * [lastMonth]: platforms with taken trips in the month before.
         */
        fun of(month: YearMonth, today: LocalDate, trips: Map<String, BigDecimal>, incentives: Map<String, Int>,
            rule: ImssRule = ImssRule.Y2026, lastMonth: Set<String> = emptySet()): ImssMonth {
            val need = rule.need
            val phase = month.compareTo(YearMonth.from(today))
            val order = Platform.entries.map { it.name }
            val apps = (trips.keys + incentives.keys + lastMonth).distinct()
                .sortedWith(compareBy<String> { order.indexOf(it).takeIf { i -> i >= 0 } ?: order.size }.thenBy { it })
                .map { platform ->
                    val fares = trips[platform] ?: BigDecimal.ZERO
                    val typed = incentives[platform]
                    val extra = BigDecimal(typed ?: 0)
                    val counted = fares.add(extra)
                    val pace = if (phase == 0 && today.dayOfMonth >= PACE_FROM_DAY && fares.signum() > 0) fares.multiply(BigDecimal(month.lengthOfMonth()))
                        .divide(BigDecimal(today.dayOfMonth), MONEY_CONTEXT).add(extra) else null
                    val state = when {
                        counted >= need -> ImssState.OVER
                        phase < 0 -> ImssState.FELL_SHORT
                        pace == null -> ImssState.NO_PACE_YET
                        pace >= need -> ImssState.WILL_REACH
                        else -> ImssState.WILL_FALL_SHORT
                    }
                    val shortBy = when (state) {
                        ImssState.FELL_SHORT -> need.subtract(counted)
                        ImssState.WILL_FALL_SHORT -> need.subtract(pace)
                        else -> null
                    }
                    ImssApp(platform, fares, typed, counted, pace, state, shortBy)
                }
            return ImssMonth(month, today, need, apps)
        }
    }
}
