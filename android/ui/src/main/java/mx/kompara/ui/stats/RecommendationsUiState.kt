package mx.kompara.ui.stats

import mx.kompara.metrics.recommendation.Recommendation

/**
 * The "Consejos" section render state on the Inicio dashboard (B-048).
 *
 * Carries the engine's top-3 [recommendations] (already priority-ordered) plus the premium gate
 * [locked] flag. The screen renders each free card directly and wraps the premium ones in the shared
 * `PaywallGate` when [locked] — so a free driver sees the *shape* of the advanced tip and the upsell,
 * never the real copy. When there are no recommendations at all the whole section is hidden (the
 * screen checks [hasAny]).
 *
 * @property recommendations the top-3, priority-ordered; premium ones are flagged on each item.
 * @property locked true when the advanced-recommendations capability is gated for this driver — set
 *   from the [mx.kompara.billing.Capability.RECOMMENDATIONS] gate.
 */
data class RecommendationsUiState(
    val recommendations: List<Recommendation>,
    val locked: Boolean,
) {
    /** Whether the section should render at all. */
    val hasAny: Boolean get() = recommendations.isNotEmpty()

    /** The free tips, always shown verbatim. */
    val free: List<Recommendation> get() = recommendations.filterNot { it.premium }

    /** The premium tips — shown verbatim when unlocked, behind the gate when [locked]. */
    val premium: List<Recommendation> get() = recommendations.filter { it.premium }

    /** True when there is at least one premium tip to gate/tease. */
    val hasPremium: Boolean get() = premium.isNotEmpty()

    companion object {
        /** Nothing to show — the default before any data is folded. */
        val EMPTY = RecommendationsUiState(recommendations = emptyList(), locked = false)
    }
}
