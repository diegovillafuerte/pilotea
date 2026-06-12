package mx.kompara.overlay

import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.format.Formatters

/**
 * The fully-resolved, render-ready content of the verdict chip. Every figure is pre-formatted into
 * an es-MX string (or the em-dash placeholder when the input was missing) so the Composable is a
 * dumb projection of this state — which keeps the verdict-to-chip mapping unit-testable on the JVM
 * without touching Compose.
 *
 * @property level traffic-light level (drives the colour)
 * @property netPerKm collapsed hero figure, e.g. "$7.20/km" or [MISSING] when distance was unknown
 * @property netPerHour collapsed second figure, e.g. "$186/h" or [MISSING] when time was unknown
 * @property netProfit expanded detail, e.g. "$87.50" or [MISSING] (the host app already shows the
 *           gross fare, so the net total is context, not the headline)
 * @property netPerMin expanded detail, e.g. "$3.40/min" or [MISSING]
 * @property grossPerKm expanded detail (what Uber shows), e.g. "$9.10/km" or [MISSING]
 * @property hasMissingData true when the engine judged on partial data; the chip shows a hint
 * @property missingHintKind which gap to surface in the hint (distance vs. fare vs. generic)
 * @property explainKind the one-line "¿por qué?" shown in the expanded detail, derived from the
 *   per-metric levels (which floor passed/failed); [ExplainKind.NONE] when a metric was untestable
 *   (the missing-data hint already covers that case)
 */
data class VerdictChipState(
    val level: VerdictLevel,
    val netPerKm: String,
    val netPerHour: String,
    val netProfit: String,
    val netPerMin: String,
    val grossPerKm: String,
    val hasMissingData: Boolean,
    val missingHintKind: MissingHintKind,
    val explainKind: ExplainKind,
) {
    /** Which one-line verdict explanation to show in the expanded detail. */
    enum class ExplainKind {
        /** A metric was untestable (missing data) — no explanation, the hint covers it. */
        NONE,

        /** Both metrics at/above their green floors. */
        BOTH_STRONG,

        /** Hour is green but km fell below its green floor. */
        KM_WEAK,

        /** Km is green but hour fell below its green floor. */
        HOUR_WEAK,

        /** Neither metric reached its green floor. */
        BOTH_WEAK,
    }

    enum class MissingHintKind {
        /** Nothing missing — no hint shown. */
        NONE,

        /** A distance leg was absent → "Sin datos de distancia". */
        DISTANCE,

        /** The fare itself was absent → "Sin tarifa". */
        FARE,

        /** Some other input was absent → generic "Datos incompletos". */
        GENERIC,
    }

    companion object {
        /** Placeholder for a figure the engine could not compute (missing input). */
        const val MISSING: String = "—"

        /**
         * Project [OfferMetrics] into render-ready chip state. Pure: only string formatting and a
         * little classification of which missing-data hint to show.
         */
        fun from(metrics: OfferMetrics): VerdictChipState {
            val missing = metrics.verdict.missingInputs
            val hint = when {
                missing.isEmpty() -> MissingHintKind.NONE
                "fareMxn" in missing -> MissingHintKind.FARE
                missing.any { it == "pickupKm" || it == "tripKm" } -> MissingHintKind.DISTANCE
                else -> MissingHintKind.GENERIC
            }
            return VerdictChipState(
                level = metrics.verdict.level,
                netPerKm = perKm(metrics.netPerKm),
                netPerHour = perHour(metrics.netPerHour),
                netProfit = money(metrics.netMxn),
                netPerMin = perMin(metrics.netPerMin),
                grossPerKm = perKm(metrics.grossPerKm),
                hasMissingData = missing.isNotEmpty(),
                missingHintKind = hint,
                explainKind = explain(metrics.verdict.netPerKmLevel, metrics.verdict.netPerHourLevel),
            )
        }

        /** Map the per-metric levels to the one-line explanation. */
        private fun explain(km: VerdictLevel?, hour: VerdictLevel?): ExplainKind = when {
            km == null || hour == null -> ExplainKind.NONE
            km == VerdictLevel.GREEN && hour == VerdictLevel.GREEN -> ExplainKind.BOTH_STRONG
            hour == VerdictLevel.GREEN -> ExplainKind.KM_WEAK
            km == VerdictLevel.GREEN -> ExplainKind.HOUR_WEAK
            else -> ExplainKind.BOTH_WEAK
        }

        private fun money(value: Double?): String =
            value?.let(Formatters::formatMxn) ?: MISSING

        private fun perKm(value: Double?): String =
            value?.let { Formatters.formatMxn(it) + "/km" } ?: MISSING

        private fun perMin(value: Double?): String =
            value?.let { Formatters.formatMxn(it) + "/min" } ?: MISSING

        private fun perHour(value: Double?): String =
            value?.let(Formatters::formatPerHourWhole) ?: MISSING
    }
}
