package mx.kompara.overlay

import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters

/** Atomic visual state plus the gross comparison still used by the legacy simulator narrative. */
data class VerdictChipState(val regla: ReglaModel, val grossPerKm: String) {
    val level: VerdictLevel get() = when ((regla.band as? ReglaBand.Rated)?.position) {
        ReglaPosition.ABOVE -> VerdictLevel.GREEN
        ReglaPosition.MEETS -> VerdictLevel.YELLOW
        ReglaPosition.BELOW -> VerdictLevel.RED
        null -> VerdictLevel.UNKNOWN
    }
    val preferred: PreferredMetric get() = if (regla.unit == ReglaUnit.KM) PreferredMetric.IPK else PreferredMetric.IPH
    val heroRate: String get() = regla.rateText?.plus(regla.unit.suffix)
        ?: (regla.band as ReglaBand.Unavailable).input.headline

    companion object {
        /** [threshold] is the evaluated pair (the recorded snapshot for live offers), not today's pair. */
        fun from(metrics: OfferMetrics, threshold: PlatformThreshold): VerdictChipState {
            val limits = reading(threshold, metrics.verdict.preferredMetric)
            val rate = (if (limits.unit == ReglaUnit.KM) metrics.perKmDecimal else metrics.perHourDecimal)?.toBigDecimalOrNull()
            val position = when (metrics.verdict.level) {
                VerdictLevel.GREEN -> ReglaPosition.ABOVE
                VerdictLevel.YELLOW -> ReglaPosition.MEETS
                VerdictLevel.RED -> ReglaPosition.BELOW
                VerdictLevel.UNKNOWN -> null
            }
            val band = if (position != null && rate != null) ReglaBand.Rated(position, rate)
                else ReglaBand.Unavailable(when (metrics.verdict.unavailableReason) {
                    "FARE" -> ReglaUnavailable.FARE
                    "TIME" -> ReglaUnavailable.TIME
                    "DISTANCE" -> ReglaUnavailable.DISTANCE
                    else -> ReglaUnavailable.UNCONFIRMED
                })
            return VerdictChipState(limits.copy(band = band), metrics.grossPerKm?.let(Formatters::formatPerKm) ?: "—")
        }

        /** No previous trip amount is retained while a new session is being evaluated. */
        fun reading(threshold: PlatformThreshold, preferred: PreferredMetric): ReglaModel {
            val km = preferred == PreferredMetric.IPK
            return ReglaModel(ReglaBand.Unavailable(ReglaUnavailable.READING),
                (if (km) threshold.minPerKmMxn else threshold.minPerHourMxn).toBigDecimal(),
                (if (km) threshold.redPerKmMxn else threshold.redPerHourMxn).toBigDecimal(),
                if (km) ReglaUnit.KM else ReglaUnit.HOUR)
        }
    }
}
