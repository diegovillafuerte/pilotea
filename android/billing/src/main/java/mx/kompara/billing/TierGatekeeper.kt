package mx.kompara.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for premium gating (B-050). Every gated surface flows through here so the
 * policy can't drift per-screen.
 *
 * It combines four reactive inputs:
 *  1. [EntitlementRepository.capabilities] → whether the driver holds an active premium entitlement
 *     (paid / trial / grace), the real paying signal.
 *  2. [DebugPremiumSource] → the debug-only "unlock premium" override (B-046). **Additive**: it only
 *     ever adds access for demos; it never locks anything.
 *  3. [PaywallConfigSource] → the remote kill switch. When the paywall is disabled (launch promo) every
 *     premium surface unlocks for everyone; it defaults to ENABLED so a missing/failed config never
 *     accidentally unlocks premium.
 *  4. [VerificationSource] → import/data verification, gating ONLY the population-dependent surfaces
 *     ([GateStates.VERIFICATION_REQUIRED]); promo/debug bypass it. LIVE (PR-E): bound in `:app`'s
 *     GateModule to the cached `GET /v1/me` `verified` flag — see [VerificationSource] and GateModule.
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
    // Defaulted to a constant `true` ONLY so manual test construction and any unbound path stay inert
    // (behave as before). The REAL source bound in `:app`'s GateModule fails CLOSED (false) when nothing
    // is cached — do NOT mistake this `true` default for the production contract.
    private val verificationSource: VerificationSource = VerificationSource { flowOf(true) },
) {

    /** The full gate snapshot for all capabilities, recomputed whenever any input changes. */
    val gateStates: Flow<GateStates> = combine(
        entitlementRepository.entitlement,
        debugPremiumSource.debugPremiumEnabled().distinctUntilChanged(),
        paywallConfigSource.paywallEnabled().distinctUntilChanged(),
        verificationSource.verified().distinctUntilChanged(),
    ) { entitlement, debug, paywallEnabled, verified ->
        GateStates.derive(
            premium = entitlement is Entitlement.Premium,
            debugOverride = debug,
            paywallEnabled = paywallEnabled,
            driverVerified = verified,
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

/**
 * Supplies the driver's import/data-verification status (account-onboarding design §3). Declared in
 * `:billing` so the gatekeeper has no HTTP dependency; `:app`'s GateModule binds it (PR-E) to the
 * cached `GET /v1/me` `verified` flag ([mx.kompara.sync.verification.VerificationStatusRepository]).
 * Gates ONLY the population-dependent surfaces ([GateStates.VERIFICATION_REQUIRED]); promo/debug bypass.
 *
 * Contract the live impl satisfies:
 *  - **Seed fast, never stall the combine.** Emit from a value-backed flow (a DataStore `data.map{}`, a
 *    fast local read — like [PaywallConfigSource]), never `emptyFlow()`-then-network: the combine
 *    doesn't emit until EVERY source has emitted once, so a network-then-emit source would stall the
 *    WHOLE gate (incl. HISTORY/FISCAL, which don't depend on verification).
 *  - **Fail CLOSED when nothing is cached.** A fresh install / never-fetched cache seeds `false`
 *    (unverified) — a premium driver then gets the NEEDS_VERIFICATION import CTA, not a free unlock.
 *    (Do NOT "fix" this to `true`; that would silently unlock benchmarks/compare for every unverified
 *    premium driver and defeat the resale deterrent.)
 *  - **Sticky-positive AFTER a positive is observed.** Once `verified=true` is cached, a transient
 *    `/v1/me` failure must NOT re-lock the driver mid-session (the cache is kept on failure). Only a
 *    *successful* server `false` (a revocation) downgrades it; logout / 401 invalidation clears it.
 */
fun interface VerificationSource {
    /** Reactive verification flag. true = import/data-verified (or verification not yet enforced). */
    fun verified(): Flow<Boolean>
}
