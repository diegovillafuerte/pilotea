package mx.kompara.metrics.recommendation

import mx.kompara.metrics.percentile.PercentileResult

/**
 * Everything [RecommendationEngine] needs to evaluate its rules for one driver-week (B-048). A pure
 * value object assembled by the `:ui` viewmodel from the week's aggregates, the percentile repo, the
 * captured offers and the streak — so the engine itself has zero IO and is exhaustively unit-testable
 * against crafted fixtures.
 *
 * EVERY field is nullable / defaulted to "absent" so a rule that needs a signal can guard on it and
 * stay silent on insufficient data — the engine never invents a tip from a missing number.
 *
 * @property earningsPerHour the week's net $/hr for the selected scope, or null when no online hours.
 * @property earningsPerTrip the week's net $/trip, or null when no trips.
 * @property hoursOnline online hours this week (used as a sufficiency guard — a few minutes of data
 *   shouldn't trigger praise/warnings).
 * @property totalTrips completed trips this week (sufficiency guard).
 * @property streakWeeks consecutive-weeks streak from the [mx.kompara.data.rollup.StreakCalculator].
 * @property goalReached whether the weekly net goal was met this week, or null when no goal is set.
 * @property goalMet alias kept null-safe via [hasGoal].
 * @property percentiles per-metric city standings (B-046); empty when benchmarks aren't cached or no
 *   concrete platform is selected. Premium-gated signal.
 * @property city human label for the driver's city, woven into the percentile praise copy.
 * @property commissionPct the week's platform commission %, when known (imported weeks only); null on
 *   a pure captured week. Premium-gated signal.
 * @property offers the resolved offers seen this week, for the capture-powered rules (missed-good,
 *   acceptance guidance). Empty when none were captured.
 * @property acceptanceRate fraction of resolved offers accepted this week, or null when none resolved.
 * @property bestHour the single best (day-of-week × hour) earning block this week, or null when there
 *   isn't enough spread to name one. Basic (free) signal.
 * @property goalNetMxn the weekly net goal in MXN, when set — used by the missed-good-offers rule to
 *   judge which declined offers "would have met the goal". null when no goal.
 */
data class RecommendationContext(
    val earningsPerHour: Double? = null,
    val earningsPerTrip: Double? = null,
    val hoursOnline: Double = 0.0,
    val totalTrips: Int = 0,
    val streakWeeks: Int = 0,
    val goalReached: Boolean? = null,
    val percentiles: List<PercentileResult> = emptyList(),
    val city: String? = null,
    val commissionPct: Double? = null,
    val offers: List<OfferSummary> = emptyList(),
    val acceptanceRate: Double? = null,
    val bestHour: BestHourBlock? = null,
    val crossPlatform: List<CrossPlatformRate> = emptyList(),
    val goalNetMxn: Double? = null,
) {
    /** Whether a weekly goal is set (drives the goal-dependent rules). */
    val hasGoal: Boolean get() = goalNetMxn != null

    /** O(1) lookup of a city percentile by backend metric key. */
    fun percentile(metric: String): PercentileResult? = percentiles.firstOrNull { it.metric == metric }
}

/**
 * A resolved offer the driver saw this week, distilled to just what the recommendation rules need
 * (B-048). Built from the `OfferEntity` rows: [verdictGreen] from its persisted `verdict`, [accepted]
 * / [declined] from its `outcome` (DECLINED + EXPIRED both count as "not taken").
 *
 * @property fareMxn the offer's promised fare.
 * @property verdictGreen whether the metrics engine judged this offer GREEN (a "good" offer worth
 *   taking) — used by the missed-good-offers rule.
 * @property accepted the driver took it.
 * @property declined the driver passed on it (DECLINED or EXPIRED — both "not taken").
 */
data class OfferSummary(
    val fareMxn: Double,
    val verdictGreen: Boolean,
    val accepted: Boolean,
    val declined: Boolean,
)

/**
 * The driver's single best earning block this week: a (day-of-week, hour-of-day) bucket with its net
 * (B-048 "tus mejores horas"). Pre-computed by a pure `:metrics` helper from the week's trips so the
 * engine stays time-logic-free.
 *
 * @property dayOfWeek ISO day-of-week 1..7 (1 = Monday), matching `java.time.DayOfWeek.value`.
 * @property hour hour-of-day 0..23, local zone.
 * @property netMxn net earned in that block this week.
 * @property tripCount trips that fell in the block (a sufficiency guard — one trip isn't a pattern).
 */
data class BestHourBlock(
    val dayOfWeek: Int,
    val hour: Int,
    val netMxn: Double,
    val tripCount: Int,
)

/**
 * One platform's net $/km this week, for the cross-platform tip (B-048). The rule compares the best
 * and worst platform and suggests shifting hours when the spread is material. Premium-gated signal.
 *
 * @property platform the platform name (e.g. "UBER").
 * @property netPerKm net MXN per km for that platform this week.
 */
data class CrossPlatformRate(
    val platform: String,
    val netPerKm: Double,
)
