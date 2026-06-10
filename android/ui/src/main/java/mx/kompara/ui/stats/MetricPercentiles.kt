package mx.kompara.ui.stats

import mx.kompara.metrics.percentile.PercentileResult

/**
 * Glue between the Inicio/week-summary [PeriodStats] and the percentile engine (B-046). Pure so the
 * "which metrics feed the percentile" mapping and the top-X% label math are unit-testable without a
 * repository or Compose.
 *
 * The five metric cards [MetricCardValues] renders map to backend `metric_name` keys; this object
 * builds the metric→value map the `PercentileRepository` consumes, and indexes the resulting
 * [PercentileResult]s by metric so a card can look up its own standing.
 */
object MetricPercentiles {

    /** The metric-card index → backend metric key, in the SAME order as [MetricCardValues.of]. */
    val CARD_METRIC_KEYS: List<String> = listOf(
        "earnings_per_trip",
        "earnings_per_km",
        "earnings_per_hour",
        "trips_per_hour",
        // The 5th card is acceptance rate, which has no population benchmark today, so it gets no
        // percentile. Kept here as a placeholder so [forCard] stays index-aligned with the cards.
        ACCEPTANCE_PLACEHOLDER,
    )

    /** Sentinel for the acceptance-rate card, which has no benchmark cell → never gets a percentile. */
    const val ACCEPTANCE_PLACEHOLDER: String = "acceptance_rate_no_benchmark"

    /**
     * The driver's metric values for the percentile engine, keyed by backend metric name. Only the
     * four benchmarked efficiency metrics are included (acceptance has no benchmark); a null rate is
     * carried through so the repository skips it. Insertion order matches [CARD_METRIC_KEYS].
     */
    fun metricValues(stats: PeriodStats): Map<String, Double?> = linkedMapOf(
        "earnings_per_trip" to stats.earningsPerTrip,
        "earnings_per_km" to stats.earningsPerKm,
        "earnings_per_hour" to stats.earningsPerHour,
        "trips_per_hour" to stats.tripsPerHour,
    )

    /** Index [results] by metric key for O(1) per-card lookup. */
    fun byMetric(results: List<PercentileResult>): Map<String, PercentileResult> =
        results.associateBy { it.metric }

    /**
     * The [PercentileResult] for the metric card at [cardIndex] (0-based, aligned with
     * [MetricCardValues.of]), or null when that card has no percentile (acceptance, or no benchmark
     * cached, or the driver had no value for the metric).
     */
    fun forCard(cardIndex: Int, byMetric: Map<String, PercentileResult>): PercentileResult? {
        val key = CARD_METRIC_KEYS.getOrNull(cardIndex) ?: return null
        if (key == ACCEPTANCE_PLACEHOLDER) return null
        return byMetric[key]
    }
}
