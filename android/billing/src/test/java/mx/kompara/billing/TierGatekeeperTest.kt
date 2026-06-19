package mx.kompara.billing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gating policy matrix for [TierGatekeeper] + [GateStates] (B-050).
 *
 * Covers free / premium / trial / debug-override / kill-switch combinations, the per-surface gate
 * state, and the load-bearing invariant that the reader / today-stats are NOT gateable by construction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TierGatekeeperTest {

    // ── pure derive() matrix ────────────────────────────────────────────────

    @Test
    fun `free driver with paywall on locks every capability`() {
        val states = GateStates.derive(premium = false, debugOverride = false, paywallEnabled = true)
        Capability.entries.forEach { cap ->
            assertEquals("expected $cap LOCKED", GateState.LOCKED, states.stateFor(cap))
        }
    }

    @Test
    fun `premium driver unlocks every capability`() {
        val states = GateStates.derive(premium = true, debugOverride = false, paywallEnabled = true)
        Capability.entries.forEach { cap ->
            assertEquals("expected $cap UNLOCKED", GateState.UNLOCKED, states.stateFor(cap))
        }
        assertTrue(states.premium)
    }

    @Test
    fun `debug override unlocks even for a free driver`() {
        val states = GateStates.derive(premium = false, debugOverride = true, paywallEnabled = true)
        Capability.entries.forEach { cap -> assertTrue(states.isUnlocked(cap)) }
    }

    @Test
    fun `kill switch off unlocks everything regardless of entitlement (launch promo)`() {
        val states = GateStates.derive(premium = false, debugOverride = false, paywallEnabled = false)
        Capability.entries.forEach { cap ->
            assertEquals(GateState.UNLOCKED, states.stateFor(cap))
        }
    }

    @Test
    fun `kill switch off overrides a free non-debug driver on every surface`() {
        // The most important promo case: nothing paying, nothing debug, yet everything is open.
        val states = GateStates.derive(premium = false, debugOverride = false, paywallEnabled = false)
        assertTrue(states.isUnlocked(Capability.BENCHMARKS))
        assertTrue(states.isUnlocked(Capability.HISTORY))
        assertTrue(states.isUnlocked(Capability.FISCAL))
        assertTrue(states.isUnlocked(Capability.COMPARE))
        assertTrue(states.isUnlocked(Capability.RECOMMENDATIONS))
    }

    // ── import/data verification gates ONLY benchmarks + compare ────────────

    @Test
    fun `premium but unverified locks ONLY benchmarks and compare`() {
        val states = GateStates.derive(
            premium = true,
            debugOverride = false,
            paywallEnabled = true,
            driverVerified = false,
        )
        // Population-dependent surfaces require verification → the distinct NEEDS_VERIFICATION state
        // (a paid driver can't satisfy it by paying again; the CTA routes to import, not the paywall).
        assertEquals(GateState.NEEDS_VERIFICATION, states.stateFor(Capability.BENCHMARKS))
        assertEquals(GateState.NEEDS_VERIFICATION, states.stateFor(Capability.COMPARE))
        // The driver's OWN paid data stays unlocked — they paid for it.
        assertEquals(GateState.UNLOCKED, states.stateFor(Capability.HISTORY))
        assertEquals(GateState.UNLOCKED, states.stateFor(Capability.FISCAL))
        assertEquals(GateState.UNLOCKED, states.stateFor(Capability.RECOMMENDATIONS))
        assertFalse(states.driverVerified)
    }

    @Test
    fun `a free unverified driver sees LOCKED not NEEDS_VERIFICATION (pay first)`() {
        // Verification only becomes the gate AFTER the driver pays — a free driver hits the paywall.
        val states = GateStates.derive(
            premium = false,
            debugOverride = false,
            paywallEnabled = true,
            driverVerified = false,
        )
        assertEquals(GateState.LOCKED, states.stateFor(Capability.BENCHMARKS))
        assertEquals(GateState.LOCKED, states.stateFor(Capability.COMPARE))
    }

    @Test
    fun `verified premium unlocks benchmarks and compare too`() {
        val states = GateStates.derive(
            premium = true,
            debugOverride = false,
            paywallEnabled = true,
            driverVerified = true,
        )
        Capability.entries.forEach { cap -> assertTrue(states.isUnlocked(cap)) }
    }

    @Test
    fun `promo bypasses verification (everything unlocked even unverified)`() {
        val states = GateStates.derive(
            premium = false,
            debugOverride = false,
            paywallEnabled = false,
            driverVerified = false,
        )
        Capability.entries.forEach { cap -> assertTrue(states.isUnlocked(cap)) }
    }

    @Test
    fun `debug override bypasses verification`() {
        val states = GateStates.derive(
            premium = false,
            debugOverride = true,
            paywallEnabled = true,
            driverVerified = false,
        )
        Capability.entries.forEach { cap -> assertTrue(states.isUnlocked(cap)) }
    }

    @Test
    fun `driverVerified defaults to true so the term is inert`() {
        // Existing call sites omit driverVerified — premium must unlock everything as before.
        val states = GateStates.derive(premium = true, debugOverride = false, paywallEnabled = true)
        Capability.entries.forEach { cap -> assertTrue(states.isUnlocked(cap)) }
    }

    // ── reader is ungateable by construction ────────────────────────────────

    @Test
    fun `no capability represents the reader or today-stats (reader never gated)`() {
        // The free hook must stay free: there is deliberately no Capability that locks the reader or
        // basic today-stats, so the gatekeeper cannot produce a gate for them under any input.
        val names = Capability.entries.map { it.name }
        assertFalse(names.any { it.contains("READER", ignoreCase = true) })
        assertFalse(names.any { it.contains("OVERLAY", ignoreCase = true) })
        assertFalse(names.any { it.contains("CAPTURE", ignoreCase = true) })
        assertFalse(names.any { it.contains("TODAY", ignoreCase = true) })
        assertFalse(names.any { it.contains("STATS", ignoreCase = true) })
        // And the only capabilities are the four premium surfaces + the recommendations hook.
        assertEquals(
            setOf("BENCHMARKS", "COMPARE", "HISTORY", "FISCAL", "RECOMMENDATIONS"),
            names.toSet(),
        )
    }

    // ── reactive gatekeeper wired to a real EntitlementRepository ────────────

    private fun gatekeeper(
        billing: FakeBillingClient,
        debug: MutableStateFlow<Boolean>,
        paywallEnabled: MutableStateFlow<Boolean>,
        verified: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): Pair<TierGatekeeper, EntitlementRepository> {
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            object : EntitlementCache {
                private var current: Entitlement = Entitlement.Free
                override suspend fun read(): Entitlement = current
                override suspend fun write(entitlement: Entitlement) { current = entitlement }
            },
            BillingLogger.NOOP,
        )
        val gk = TierGatekeeper(
            repo,
            { debug },
            { paywallEnabled },
            { verified },
        )
        return gk to repo
    }

    private fun premiumPurchase(trial: Boolean = false) = BillingPurchase(
        purchaseToken = "tok",
        productId = BillingProducts.PREMIUM_SUBSCRIPTION,
        state = PurchaseState.PURCHASED,
        acknowledged = true,
        autoRenewing = true,
        trial = trial,
        expiryTimeMillis = 9_999_999_999_000L,
    )

    @Test
    fun `gateStates tracks a live purchase from locked to unlocked`() = runTest {
        val billing = FakeBillingClient()
        val debug = MutableStateFlow(false)
        val paywall = MutableStateFlow(true)
        val (gk, repo) = gatekeeper(billing, debug, paywall)
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        assertEquals(GateState.LOCKED, gk.gateFor(Capability.BENCHMARKS).first())

        billing.emit(listOf(premiumPurchase()))
        advanceUntilIdle()

        assertEquals(GateState.UNLOCKED, gk.gateFor(Capability.BENCHMARKS).first())
        assertTrue(gk.gateStates.first().premium)
    }

    @Test
    fun `trial entitlement unlocks the same as paid`() = runTest {
        val billing = FakeBillingClient()
        val (gk, repo) = gatekeeper(billing, MutableStateFlow(false), MutableStateFlow(true))
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(premiumPurchase(trial = true)))
        advanceUntilIdle()

        assertEquals(GateState.UNLOCKED, gk.gateFor(Capability.FISCAL).first())
    }

    @Test
    fun `debug override flips a free driver to unlocked reactively`() = runTest {
        val billing = FakeBillingClient()
        val debug = MutableStateFlow(false)
        val (gk, repo) = gatekeeper(billing, debug, MutableStateFlow(true))
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        assertEquals(GateState.LOCKED, gk.gateFor(Capability.HISTORY).first())

        debug.value = true
        advanceUntilIdle()
        assertEquals(GateState.UNLOCKED, gk.gateFor(Capability.HISTORY).first())
    }

    @Test
    fun `verification gates compare for a premium driver and flips reactively`() = runTest {
        val billing = FakeBillingClient()
        val verified = MutableStateFlow(false)
        val (gk, repo) = gatekeeper(billing, MutableStateFlow(false), MutableStateFlow(true), verified)
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        // Premium but unverified: COMPARE needs verification, but the driver's own HISTORY stays unlocked.
        billing.emit(listOf(premiumPurchase()))
        advanceUntilIdle()
        assertEquals(GateState.NEEDS_VERIFICATION, gk.gateFor(Capability.COMPARE).first())
        assertEquals(GateState.UNLOCKED, gk.gateFor(Capability.HISTORY).first())

        // Verifying (e.g. a successful import) flips COMPARE open with no new purchase.
        verified.value = true
        advanceUntilIdle()
        assertEquals(GateState.UNLOCKED, gk.gateFor(Capability.COMPARE).first())
    }

    @Test
    fun `kill switch off unlocks reactively then re-locks when re-enabled`() = runTest {
        val billing = FakeBillingClient()
        val paywall = MutableStateFlow(true)
        val (gk, repo) = gatekeeper(billing, MutableStateFlow(false), paywall)
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        assertEquals(GateState.LOCKED, gk.gateFor(Capability.COMPARE).first())

        paywall.value = false // launch promo
        advanceUntilIdle()
        assertEquals(GateState.UNLOCKED, gk.gateFor(Capability.COMPARE).first())

        paywall.value = true // promo ends
        advanceUntilIdle()
        assertEquals(GateState.LOCKED, gk.gateFor(Capability.COMPARE).first())
    }
}
