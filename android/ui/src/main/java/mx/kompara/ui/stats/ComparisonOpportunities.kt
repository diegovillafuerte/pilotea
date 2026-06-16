package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.data.model.PopulationStat
import mx.kompara.metrics.recommendation.Recommendation
import mx.kompara.metrics.recommendation.RecommendationType
import mx.kompara.ui.format.Formatters

/**
 * The "Dónde puedes ganar más" section of Comparar (B-088): opportunities derived **from the
 * comparison** — which app pays the population better, and how far the driver is from the top. These
 * are comparison insights (the driver's own best-day/best-hour advice lives on Inicio). Pure, so the
 * thresholds are unit-tested without Room or Compose; emits [Recommendation]s so the screen reuses
 * `RecommendationCard`.
 */
object ComparisonOpportunities {

    private const val UBER = "uber"
    private const val DIDI = "didi"
    private const val ALL = "all"
    private const val EPH = "earnings_per_hour"

    /** A platform pays meaningfully better only when its average leads the other by ≥ this. */
    private const val MIN_GAP_PCT = 15
    private const val MAX = 3

    /**
     * @param blended the driver's blended weekly stats.
     * @param platformStats the cached city benchmarks (uber/didi/all × metric).
     * @param platformMix the driver's **trips** per platform this week — the app-mix signal.
     *   (Trips are per-platform; online hours are replicated across platform-rows on the captured
     *   path, so hours can't tell which app the driver leaned on — codex review, S-024.)
     */
    fun build(
        blended: PeriodStats,
        platformStats: List<PopulationStat>,
        platformMix: Map<Platform, Double>,
    ): List<Recommendation> {
        val mean = platformStats.associate { (it.platform.lowercase() to it.metric) to it.mean }
        val p90 = platformStats.associate { (it.platform.lowercase() to it.metric) to it.p90 }
        val out = mutableListOf<Recommendation>()

        val driving = platformMix.filterValues { it > 0.0 }.keys
        val uberEph = mean[UBER to EPH]
        val didiEph = mean[DIDI to EPH]

        if (driving.size == 1 && (driving.first() == Platform.UBER || driving.first() == Platform.DIDI)) {
            // Single-app: nudge the OTHER app when the population earns there.
            val one = driving.first()
            val other = if (one == Platform.UBER) Platform.DIDI else Platform.UBER
            val otherMean = mean[other.wire() to EPH]
            if (otherMean != null) {
                out += Recommendation(
                    id = "compare_try_other_app",
                    type = RecommendationType.INFO,
                    title = "Aún no manejas ${other.display()}",
                    body = "El promedio ${other.display()} gana ${Formatters.formatPerHour(otherMean)} " +
                        "— pruébalo y compáralo con tu ${one.display()}.",
                    premium = true,
                )
            }
        } else if (uberEph != null && didiEph != null) {
            // 2+ apps: which platform pays the population more, and does the driver under-index there?
            val uberWins = uberEph >= didiEph
            val betterVal = if (uberWins) uberEph else didiEph
            val worseVal = if (uberWins) didiEph else uberEph
            val better = if (uberWins) Platform.UBER else Platform.DIDI
            val worse = if (uberWins) Platform.DIDI else Platform.UBER
            if (worseVal > 0.0) {
                val gapPct = Math.round((betterVal - worseVal) / worseVal * 100).toInt()
                val underIndexed = (platformMix[better] ?: 0.0) <= (platformMix[worse] ?: 0.0)
                if (gapPct >= MIN_GAP_PCT && underIndexed) {
                    out += Recommendation(
                        id = "compare_platform_mix",
                        type = RecommendationType.INFO,
                        title = "Los choferes ganan $gapPct% más con ${better.display()}",
                        body = "Tú manejas más ${worse.display()} — prueba más ${better.display()}.",
                        premium = true,
                    )
                }
            }
        }

        // Top-of-city gap: how far the driver is from the top 20% (p90 of all drivers) per hour.
        val allP90 = p90[ALL to EPH]
        val driverEph = blended.earningsPerHour
        if (allP90 != null && driverEph != null && allP90 > driverEph) {
            out += Recommendation(
                id = "compare_top_gap",
                type = RecommendationType.INFO,
                title = "El top 20% gana ${Formatters.formatPerHour(allP90 - driverEph)} más que tú",
                body = "Ahí es donde más puedes subir.",
                premium = true,
            )
        }

        return out.take(MAX)
    }

    private fun Platform.wire(): String = name.lowercase()

    private fun Platform.display(): String = when (this) {
        Platform.UBER -> "Uber"
        Platform.DIDI -> "DiDi"
        Platform.INDRIVE -> "inDrive"
        else -> "otra app"
    }
}
