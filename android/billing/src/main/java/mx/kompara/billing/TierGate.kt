package mx.kompara.billing

/**
 * The premium surfaces that [TierGatekeeper] gates (B-050). Each maps to one [Capabilities] flag.
 *
 * The reader and basic "today" stats are deliberately NOT here — they are the free hook and are never
 * gated by construction (no [Capability] can lock them; see [TierGatekeeper.gateFor]). Adding a reader
 * capability here would be the only way to gate it, and that is forbidden — the gatekeeper test asserts
 * the reader stays ungated.
 */
enum class Capability {
    /** City percentile bars/badges on the metric cards (B-046). */
    BENCHMARKS,

    /** The Uber/DiDi comparison surface (Comparar tab — built next wave; gate hook only for now). */
    COMPARE,

    /** Full history beyond the free window (current + previous week). */
    HISTORY,

    /** The fiscal / IMSS tracker tab content (B-051). */
    FISCAL,

    /** Advanced recommendations slot (hook for a future wave). */
    RECOMMENDATIONS,
}

/**
 * Whether a [Capability] is available to the driver right now, and — when locked — why a tease-then-gate
 * surface should render the upsell. A surface observes a [GateState] and decides what to paint.
 */
enum class GateState {
    /**
     * Unlocked: render the real premium content. Reached when the driver is premium (paid/trial/grace),
     * the debug override is on, or the remote kill switch has disabled the paywall (launch promo).
     */
    UNLOCKED,

    /**
     * Locked: render the tease-then-gate preview (blurred/dimmed content + lock + value hint + CTA),
     * never the real data. Reached for a free driver while the paywall is enabled.
     */
    LOCKED,
    ;

    val isUnlocked: Boolean get() = this == UNLOCKED
    val isLocked: Boolean get() = this == LOCKED
}

/**
 * The resolved gate state for every [Capability], plus the inputs that produced it — a single snapshot
 * the UI layer can read so each surface doesn't re-derive the policy. Built by [TierGatekeeper].
 *
 * @property premium the driver holds an active premium entitlement (paid/trial/grace).
 * @property debugOverride the debug-only "unlock premium" toggle is on (additive; demo aid).
 * @property paywallEnabled the remote kill switch — false means launch-promo mode (everything unlocked).
 */
data class GateStates(
    val premium: Boolean,
    val debugOverride: Boolean,
    val paywallEnabled: Boolean,
    /** Whether the driver is import/data-verified (≥1 parsed import). See [VERIFICATION_REQUIRED]. */
    val driverVerified: Boolean,
    private val states: Map<Capability, GateState>,
) {
    /** The gate state for [capability] (defaults to LOCKED if somehow absent — fail closed). */
    fun stateFor(capability: Capability): GateState = states[capability] ?: GateState.LOCKED

    /** Convenience: is [capability] unlocked right now? */
    fun isUnlocked(capability: Capability): Boolean = stateFor(capability).isUnlocked

    companion object {
        /**
         * The population-dependent paid surfaces that ALSO require import/data verification on top of a
         * premium entitlement (account-onboarding design §0.5): comparing against other drivers is only
         * meaningful for a verified real driver, and it deters paid-account resale. The driver's OWN
         * paid data (HISTORY, FISCAL, RECOMMENDATIONS) is deliberately NOT here — gating it would lock a
         * paying driver out of data they generated.
         */
        val VERIFICATION_REQUIRED: Set<Capability> = setOf(Capability.BENCHMARKS, Capability.COMPARE)

        /**
         * Build the snapshot. Promo (`!paywallEnabled`) and the debug override BYPASS everything —
         * including verification — so a launch promo unlocks all surfaces for everyone. Otherwise a
         * capability unlocks when the driver is premium, EXCEPT the [VERIFICATION_REQUIRED] surfaces,
         * which additionally require [driverVerified]. Reader/today-stats are not capabilities here, so
         * they can't be locked.
         *
         * `driverVerified` defaults to `true` so the verification term is INERT until the real
         * verification source is wired in (the import wizard must ship first — see GateModule); a
         * default of true means existing call sites behave exactly as before.
         */
        fun derive(
            premium: Boolean,
            debugOverride: Boolean,
            paywallEnabled: Boolean,
            driverVerified: Boolean = true,
        ): GateStates {
            val bypass = !paywallEnabled || debugOverride // launch promo / demo: unlock all, no verification
            val states = Capability.entries.associateWith { cap ->
                val unlocked = when {
                    bypass -> true
                    !premium -> false
                    cap in VERIFICATION_REQUIRED -> driverVerified
                    else -> true
                }
                if (unlocked) GateState.UNLOCKED else GateState.LOCKED
            }
            return GateStates(
                premium = premium,
                debugOverride = debugOverride,
                paywallEnabled = paywallEnabled,
                driverVerified = driverVerified,
                states = states,
            )
        }
    }
}
