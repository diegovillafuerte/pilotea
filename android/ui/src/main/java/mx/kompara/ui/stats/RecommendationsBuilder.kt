package mx.kompara.ui.stats

import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.metrics.VerdictLevel
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.metrics.recommendation.BestHourBlock
import mx.kompara.metrics.recommendation.CrossPlatformRate
import mx.kompara.metrics.recommendation.OfferSummary
import mx.kompara.metrics.recommendation.RecommendationContext
import mx.kompara.metrics.recommendation.RecommendationEngine

/**
 * The pure glue between the Inicio inputs and the `:metrics` [RecommendationEngine] (B-048): folds the
 * current week's [PeriodStats], the city percentiles (B-046), the captured offers and the best-hour
 * block into a [RecommendationContext], runs the engine, and returns the top-3
 * [mx.kompara.metrics.recommendation.Recommendation]s.
 *
 * Extracted from the viewmodel so the "what feeds which rule" mapping is unit-testable with fake data
 * and no Room/Compose — mirroring [InicioStats]/[MetricPercentiles]. The viewmodel just supplies the
 * already-loaded inputs.
 */
object RecommendationsBuilder {

    private val engine = RecommendationEngine()

    /**
     * Build the [RecommendationContext] for one week and run the engine.
     *
     * @param period the current week's folded stats for the selected scope.
     * @param percentiles city percentiles for the week's metrics (empty when not cached / not premium-
     *   visible — the percentile-dependent rules then guard themselves out).
     * @param cityLabel the driver's city display name, woven into the percentile praise copy.
     * @param streakWeeks the consecutive-weeks streak.
     * @param weeklyNetGoalMxn the weekly net goal, or null when none set.
     * @param offers the current week's captured offers (verdicts + outcomes).
     * @param bestHour the week's single best (day-of-week × hour) block, or null when none.
     * @param crossPlatform per-platform net $/km this week (2+ entries enable the cross-platform tip).
     */
    fun build(
        period: PeriodStats,
        percentiles: List<PercentileResult>,
        cityLabel: String?,
        streakWeeks: Int,
        weeklyNetGoalMxn: Double?,
        offers: List<OfferEntity>,
        bestHour: BestHourBlock?,
        crossPlatform: List<CrossPlatformRate>,
    ) = engine.recommend(
        RecommendationContext(
            earningsPerHour = period.earningsPerHour,
            earningsPerTrip = period.earningsPerTrip,
            hoursOnline = period.hoursOnline,
            totalTrips = period.totalTrips,
            streakWeeks = streakWeeks,
            goalReached = weeklyNetGoalMxn?.let { period.netEarningsMxn >= it },
            percentiles = percentiles,
            city = cityLabel,
            // Commission isn't on the captured path (WeeklyAggregateEntity omits it); a future import
            // merge can supply it. Passed null here so the high-commission rule stays silent.
            commissionPct = null,
            offers = offers.map { it.toSummary() },
            acceptanceRate = period.acceptanceRate,
            bestHour = bestHour,
            crossPlatform = crossPlatform,
            goalNetMxn = weeklyNetGoalMxn,
        ),
    )

    /** An [OfferEntity] distilled to the [OfferSummary] the engine reads: verdict + resolved outcome. */
    private fun OfferEntity.toSummary(): OfferSummary {
        val outcome = runCatching { OfferOutcome.valueOf(outcome) }.getOrDefault(OfferOutcome.PENDING)
        return OfferSummary(
            fareMxn = fareMxn,
            verdictGreen = verdict == VerdictLevel.GREEN.name,
            accepted = outcome == OfferOutcome.ACCEPTED,
            // DECLINED + EXPIRED both count as "not taken" for the offer rules.
            declined = outcome == OfferOutcome.DECLINED || outcome == OfferOutcome.EXPIRED,
        )
    }
}
