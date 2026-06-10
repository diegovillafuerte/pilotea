package mx.kompara.ui.stats

import mx.kompara.ui.format.Formatters

/**
 * The five Inicio/week-summary metric cards as ready-to-render label+value pairs, formatted es-MX.
 * Keeping the mapping here (not in the composable) makes the "null rate ⇒ dash" rule unit-testable.
 *
 * @property labelRes a `:ui` string resource id for the card label.
 * @property value the formatted figure ("$8.40/km", "—", …).
 */
data class MetricCardValue(val labelRes: Int, val value: String)

/** Builds the five metric-card values from a [PeriodStats]. */
object MetricCardValues {

    fun of(stats: PeriodStats): List<MetricCardValue> = listOf(
        MetricCardValue(
            labelRes = mx.kompara.ui.R.string.metric_per_trip,
            value = stats.earningsPerTrip?.let { Formatters.formatMxn(it) } ?: Formatters.DASH,
        ),
        MetricCardValue(
            labelRes = mx.kompara.ui.R.string.metric_per_km,
            value = stats.earningsPerKm?.let { Formatters.formatPerKm(it) } ?: Formatters.DASH,
        ),
        MetricCardValue(
            labelRes = mx.kompara.ui.R.string.metric_per_hour,
            value = stats.earningsPerHour?.let { Formatters.formatPerHour(it) } ?: Formatters.DASH,
        ),
        MetricCardValue(
            labelRes = mx.kompara.ui.R.string.metric_trips_per_hour,
            value = stats.tripsPerHour?.let { Formatters.formatPerHourCount(it) } ?: Formatters.DASH,
        ),
        MetricCardValue(
            labelRes = mx.kompara.ui.R.string.metric_acceptance_rate,
            value = stats.acceptanceRate?.let { Formatters.formatPercent(it) } ?: Formatters.DASH,
        ),
    )
}
