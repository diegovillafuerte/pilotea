package mx.kompara.metrics.recommendation

/**
 * The traffic-light category of a [Recommendation], driving both the card accent colour and the
 * priority ordering the engine uses to pick the top 3 (B-048).
 *
 * The three categories mirror the web MVP's recommendation types, collapsed onto a clean triple:
 * the web had `positive | warning | actionable | info`; here `actionable` folds into [INFO] (an
 * actionable tip is "do this thing"), keeping the card styling to three accents (verde/ámbar/azul)
 * that match the verdict palette drivers already read.
 *
 * Ordering for the top-3 cut is [WARNING] first (a leak of money the driver should plug now), then
 * [INFO] (an action that would earn more), then [POSITIVE] (praise — nice to see but never bumps a
 * warning off the screen). See [Recommendation.priority].
 */
enum class RecommendationType {
    /** Something is costing the driver money — render with the ámbar/red accent. Highest priority. */
    WARNING,

    /** An actionable tip that would earn more — render with the azul accent. Middle priority. */
    INFO,

    /** Praise / reinforcement — render with the verde accent. Lowest priority. */
    POSITIVE,
}

/**
 * One rule-based tip surfaced on the Inicio "Consejos" section (B-048). Produced by
 * [RecommendationEngine] from a [RecommendationContext]; rendered by the `:ui` `RecommendationCard`.
 *
 * Copy lives in code (Spanish, `tú`, direct, no jargon) rather than `strings.xml`: the bodies are
 * heavily parameterised with pesos/percentages/hour-blocks the engine computes, the engine is a pure
 * `:metrics` module with no Android resources, and keeping the rule + its copy in one place makes the
 * exhaustive per-rule tests assert the exact wording. (TODO TD: if these strings ever need
 * translation or designer review they can move to `strings.xml` with formatter args — tracked as a
 * follow-up, not a launch blocker.)
 *
 * @property id stable identifier for the rule that fired (used for de-dup, tests, and analytics; not
 *   shown to the driver).
 * @property type the [RecommendationType] — drives the accent colour and the top-3 ordering.
 * @property title one short line, glanceable in a car.
 * @property body one or two sentences with the concrete number behind the tip.
 * @property premium true when this tip leans on a premium-only signal (city percentiles or the
 *   cross-platform comparison) and must be shown behind the paywall gate; false for the basic
 *   streak/goal/best-hours/acceptance tips that are part of the free hook.
 */
data class Recommendation(
    val id: String,
    val type: RecommendationType,
    val title: String,
    val body: String,
    val premium: Boolean,
) {
    /** Lower sorts first. Warnings before actionable tips before praise (see [RecommendationType]). */
    val priority: Int
        get() = when (type) {
            RecommendationType.WARNING -> 0
            RecommendationType.INFO -> 1
            RecommendationType.POSITIVE -> 2
        }
}
