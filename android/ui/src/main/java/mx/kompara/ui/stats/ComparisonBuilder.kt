package mx.kompara.ui.stats

import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.model.PopulationStat
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.metrics.recommendation.Recommendation

/**
 * The pure core of the Comparar tab (S-024): assemble the weekly benchmark table from the driver's
 * blended weekly stats, the platform city-averages, and the driver's percentiles. No Room, no repos,
 * no Compose — so the blend, the per-platform N/A, and the standing pick are unit-tested directly.
 */
object ComparisonBuilder {

    private const val UBER = "uber"
    private const val DIDI = "didi"

    /** Lead-metric priority for the hero's overall standing (first present wins) — matches the verdict order. */
    private val STANDING_PRIORITY = listOf("earnings_per_hour", "earnings_per_km", "earnings_per_trip")

    /**
     * @param weekRows the week's rows (one per platform, IMPORTED-preferred — see [CompareState.rowsForWeek]).
     * @param platforms the platforms with data this week (≥1; empty ⇒ the caller shows the empty state).
     * @param platformStats every cached [PopulationStat] for the driver's city (all platforms).
     * @param percentilesByMetric the driver's blended-value percentiles vs. the `all` population, by metric key.
     * @param opportunities comparison advice (B-088); empty here.
     */
    fun build(
        weekStart: String,
        weekRows: List<WeeklyAggregateEntity>,
        platforms: List<Platform>,
        platformStats: List<PopulationStat>,
        percentilesByMetric: Map<String, PercentileResult>,
        opportunities: List<Recommendation> = emptyList(),
    ): WeeklyComparison {
        // The blended "Tú": sum totals across apps, recompute the rates (PeriodStats.fromWeekly does this).
        val blended = PeriodStats.fromWeekly(weekRows, platform = null)
        // (platform, metric) → average, for O(1) lookup of each platform's city mean.
        val meanByPlatformMetric: Map<Pair<String, String>, Double> =
            platformStats.associate { (it.platform.lowercase() to it.metric) to it.mean }

        val rows = COMPARE_METRICS.map { spec ->
            ComparisonRow(
                metric = spec.key,
                unit = spec.unit,
                tu = tuValue(spec.key, blended),
                // A platform that intrinsically doesn't report a metric shows "—", never the population mean.
                uberAvg = if (spec.uberNa != null) null else meanByPlatformMetric[UBER to spec.key],
                didiAvg = if (spec.didiNa != null) null else meanByPlatformMetric[DIDI to spec.key],
                percentile = percentilesByMetric[spec.key],
                lowerIsBetter = spec.lowerIsBetter,
                uberNa = spec.uberNa,
                didiNa = spec.didiNa,
            )
        }

        val standingMetric = STANDING_PRIORITY.firstOrNull { percentilesByMetric[it] != null }
        return WeeklyComparison(
            weekStart = weekStart,
            rows = rows,
            standing = standingMetric?.let { percentilesByMetric[it] },
            standingMetric = standingMetric,
            platformsWithData = platforms,
            singlePlatform = platforms.singleOrNull(),
            opportunities = opportunities,
        )
    }

    /** The driver's blended value for [key]; commission is absent on the captured path (null). */
    private fun tuValue(key: String, s: PeriodStats): Double? = when (key) {
        "net_earnings" -> s.netEarningsMxn
        "earnings_per_hour" -> s.earningsPerHour
        "earnings_per_km" -> s.earningsPerKm
        "earnings_per_trip" -> s.earningsPerTrip
        "trips_per_hour" -> s.tripsPerHour
        "platform_commission_pct" -> null
        else -> null
    }
}
