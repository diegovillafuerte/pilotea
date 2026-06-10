package mx.kompara.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for premium gating (B-050). Every gated surface flows through here so the
 * policy can't drift per-screen.
 *
 * It combines three reactive inputs:
 *  1. [EntitlementRepository.capabilities] → whether the driver holds an active premium entitlement
 *     (paid / trial / grace), the real paying signal.
 *  2. [DebugPremiumSource] → the debug-only "unlock premium" override (B-046). **Additive**: it only
 *     ever adds access for demos; it never locks anything.
 *  3. [PaywallConfigSource] → the remote kill switch. When the paywall is disabled (launch promo) every
 *     premium surface unlocks for everyone; it defaults to ENABLED so a missing/failed config never
 *     accidentally unlocks premium.
 *
 * …into a reactive [GateStates] snapshot ([gateStates]) and per-capability [GateState] flows
 * ([gateFor]). The UI's [PaywallGate] and the gated viewmodels observe these — they never recombine the
 * raw entitlement/flag/kill-switch themselves.
 *
 * READER IS UNGATEABLE BY CONSTRUCTION: the reader and "today" stats are not represented by any
 * [Capability], so no call can produce a gate for them. The unit tests assert this directly.
 */
@Singleton
class TierGatekeeper @Inject constructor(
    private val entitlementRepository: EntitlementRepository,
    private val debugPremiumSource: DebugPremiumSource,
    private val paywallConfigSource: PaywallConfigSource,
) {

    /** The full gate snapshot for all capabilities, recomputed whenever any input changes. */
    val gateStates: Flow<GateStates> = combine(
        entitlementRepository.entitlement,
        debugPremiumSource.debugPremiumEnabled().distinctUntilChanged(),
        paywallConfigSource.paywallEnabled().distinctUntilChanged(),
    ) { entitlement, debug, paywallEnabled ->
        GateStates.derive(
            premium = entitlement is Entitlement.Premium,
            debugOverride = debug,
            paywallEnabled = paywallEnabled,
        )
    }.distinctUntilChanged()

    /** The reactive [GateState] for a single [capability] — what a [PaywallGate] subscribes to. */
    fun gateFor(capability: Capability): Flow<GateState> =
        gateStates
            .map { it.stateFor(capability) }
            .distinctUntilChanged()
}

/**
 * Supplies the debug-only "unlock premium" override (B-046). Declared in `:billing` so the gatekeeper
 * depends on an abstraction, not on `:data`'s SettingsRepository; the `:app`/`:ui` layer binds it to the
 * persisted setting. Always returns false in a release build (the toggle is debug-only).
 */
fun interface DebugPremiumSource {
    /** Reactive debug-premium flag. Additive: true unlocks premium surfaces for demos only. */
    fun debugPremiumEnabled(): Flow<Boolean>
}

/**
 * Supplies the remote paywall kill switch (B-050). Declared in `:billing` so the gatekeeper has no HTTP
 * dependency; `:sync` implements the cached repo (GET /v1/config/app) and `:app` binds it.
 *
 * MUST default to TRUE (paywall enabled) on a fresh install / offline / fetch failure so a transport
 * problem never silently unlocks premium for everyone. Only an explicit `paywallEnabled=false` from the
 * backend flips it to launch-promo mode.
 */
fun interface PaywallConfigSource {
    /** Reactive kill switch. true = gating active (default); false = everything unlocked (promo). */
    fun paywallEnabled(): Flow<Boolean>
}
