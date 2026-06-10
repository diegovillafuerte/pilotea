package mx.kompara.billing

/**
 * The driver's premium entitlement, derived from Play purchases + backend acknowledgement and
 * persisted (last-known) in DataStore for offline grace.
 *
 * Anonymous-first product: the realtime offer reader and basic stats are ALWAYS free and are
 * never gated by this type. Only the comparison/benchmark/history/fiscal surfaces (B-050) check
 * the capability flags below.
 */
sealed interface Entitlement {
    /** No active subscription. Reader + basic stats still work; premium surfaces are locked. */
    data object Free : Entitlement

    /**
     * An active premium subscription.
     *
     * @param trial true while the driver is inside the free-trial phase (`trial-7d` offer) — the
     *   first charge hasn't happened yet. UI surfaces "trial" copy; entitlements are identical to
     *   a paid sub.
     * @param expiresAt epoch millis the current paid/trial period ends (best-effort; null when the
     *   client can't yet derive it, e.g. a pending purchase). Used for the offline grace window.
     */
    data class Premium(
        val trial: Boolean,
        val expiresAt: Long? = null,
    ) : Entitlement
}

/**
 * Capability flags consumed by B-050 to gate premium surfaces. Defined here so the gating contract
 * is owned by the billing layer and can't drift per-screen.
 *
 * Under [Entitlement.Premium] every flag is true; under [Entitlement.Free] every flag is false.
 * The reader and basic stats are intentionally NOT represented here — they are always free.
 */
data class Capabilities(
    val canSeeBenchmarks: Boolean,
    val canSeeCompare: Boolean,
    val canSeeHistory: Boolean,
    val canSeeFiscal: Boolean,
) {
    companion object {
        /** All premium surfaces unlocked. */
        val PREMIUM = Capabilities(
            canSeeBenchmarks = true,
            canSeeCompare = true,
            canSeeHistory = true,
            canSeeFiscal = true,
        )

        /** All premium surfaces locked (reader/basic stats remain free, gated elsewhere). */
        val FREE = Capabilities(
            canSeeBenchmarks = false,
            canSeeCompare = false,
            canSeeHistory = false,
            canSeeFiscal = false,
        )

        /** Derive the capability set for an [entitlement]. */
        fun of(entitlement: Entitlement): Capabilities =
            when (entitlement) {
                is Entitlement.Premium -> PREMIUM
                Entitlement.Free -> FREE
            }
    }
}

/**
 * Product / base-plan / offer identifiers as configured in the Play Console. These are the only
 * Play identifiers the app hardcodes — PRICES are never hardcoded and always come from
 * [ProductDetails] at runtime (acceptance criterion).
 */
object BillingProducts {
    /** The single premium subscription product id. */
    const val PREMIUM_SUBSCRIPTION = "kompara_premium"

    /** The monthly recurring base plan under [PREMIUM_SUBSCRIPTION]. */
    const val MONTHLY_BASE_PLAN = "monthly"

    /** The card-free free-trial offer (7 days) attached to the monthly base plan. */
    const val TRIAL_OFFER = "trial-7d"
}
