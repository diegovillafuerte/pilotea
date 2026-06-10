package mx.kompara.metrics

/**
 * The driver's cost structure, expressed the way the engine consumes it: marginal per-km costs
 * (fuel + maintenance) and prorated fixed costs (insurance, vehicle rent/financing) spread over a
 * working day.
 *
 * This is the engine-side, pure-Kotlin twin of `:data`'s `CostProfileEntity`. The mapping between
 * the two lives in `:data` (`CostProfileMapper`) so `:metrics` stays free of Android/Room.
 *
 * @property fuelCostPerKm fuel cost in MXN per km. Derive it from rendimiento (km/L) and gas price
 *   with [fuelCostPerKmFrom].
 * @property maintenancePerKm maintenance/depreciation in MXN per km (tyres, service, wear).
 * @property dailyFixedCosts fixed costs in MXN per working day (insurance + rent/financing). Used
 *   only when computing a shift-level break-even, not subtracted from a single offer.
 * @property workDaysPerWeek how many days the driver works per week (1..7). Lets callers convert a
 *   weekly fixed cost into [dailyFixedCosts] and reason about per-shift break-even.
 */
data class CostProfile(
    val fuelCostPerKm: Double,
    val maintenancePerKm: Double,
    val dailyFixedCosts: Double = 0.0,
    val workDaysPerWeek: Int = DEFAULT_WORK_DAYS_PER_WEEK,
) {
    /** Marginal cost of driving one more km: fuel + maintenance. Fixed costs are not marginal. */
    val marginalCostPerKm: Double get() = fuelCostPerKm + maintenancePerKm

    companion object {
        /** A common Mexican ride-hail schedule (Mon–Sat) used when the driver hasn't said. */
        const val DEFAULT_WORK_DAYS_PER_WEEK: Int = 6

        /**
         * Profile with all costs zero — net == gross. Used as a safe fallback when the driver has
         * not configured a profile yet, so verdicts still render (just optimistically).
         */
        val ZERO = CostProfile(
            fuelCostPerKm = 0.0,
            maintenancePerKm = 0.0,
            dailyFixedCosts = 0.0,
            workDaysPerWeek = DEFAULT_WORK_DAYS_PER_WEEK,
        )

        /**
         * Derive fuel cost in MXN/km from vehicle efficiency and fuel price.
         *
         * rendimiento is km per litre; gasPricePerLitre is MXN per litre. Cost/km = price ÷
         * rendimiento. Returns 0.0 for a non-positive rendimiento to avoid a divide-by-zero (an
         * unknown/zero efficiency means "don't penalize", not "infinite cost").
         *
         * @param rendimientoKmPerLitre vehicle efficiency in km/L (e.g. 14.0 for a typical sedan)
         * @param gasPricePerLitreMxn fuel price in MXN/L (e.g. 24.50 for Magna in 2026)
         */
        fun fuelCostPerKmFrom(
            rendimientoKmPerLitre: Double,
            gasPricePerLitreMxn: Double,
        ): Double = if (rendimientoKmPerLitre > 0.0) gasPricePerLitreMxn / rendimientoKmPerLitre else 0.0
    }
}
