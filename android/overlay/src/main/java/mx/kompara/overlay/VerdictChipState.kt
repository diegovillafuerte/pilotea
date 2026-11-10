package mx.kompara.overlay

import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.CounterOffers
import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.VerdictLevel
import mx.kompara.metrics.rate
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
        /**
         * The verdict's rate in its own unit and basis (a GROSS judgement never shows a net number).
         * [counterOffers] are a Pon Tu Precio card's amounts; the pill draws only those still ahead.
         */
        fun from(metrics: OfferMetrics, counterOffers: CounterOffers? = null): VerdictChipState {
            val basis = metrics.verdict.rateBasis
            val rate = metrics.rate(metrics.verdict.preferredMetric, basis)
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
            return VerdictChipState(reading(metrics.verdict.preferredMetric).copy(band = band, basis = basis, counterOffers = counterOffers),
                metrics.grossPerKm?.let(Formatters::formatPerKm) ?: "—")
        }

        /** No previous trip amount is retained while a new session is being evaluated. */
        fun reading(preferred: PreferredMetric): ReglaModel =
            ReglaModel(ReglaBand.Unavailable(ReglaUnavailable.READING), if (preferred == PreferredMetric.IPK) ReglaUnit.KM else ReglaUnit.HOUR)
    }
}
