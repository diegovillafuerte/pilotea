package mx.kompara.billing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grant-based premium merge tests (B-056): a backend referral/partner grant ([PremiumGrantSource])
 * unlocks PREMIUM with no Play purchase, the later expiry wins when both a paid sub and a grant are
 * present, an expired/absent grant leaves the Play-derived entitlement untouched, and refreshGrant()
 * lights premium up immediately after a redemption.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementGrantMergeTest {

    private val premiumToken = "token-abc"

    private fun purchase(
        state: PurchaseState = PurchaseState.PURCHASED,
        acknowledged: Boolean = true,
        trial: Boolean = false,
        expiry: Long? = null,
    ) = BillingPurchase(
        purchaseToken = premiumToken,
        productId = BillingProducts.PREMIUM_SUBSCRIPTION,
        state = state,
        acknowledged = acknowledged,
        autoRenewing = true,
        trial = trial,
        expiryTimeMillis = expiry,
    )

    private class FakeCache(var current: Entitlement = Entitlement.Free) : EntitlementCache {
        override suspend fun read(): Entitlement = current
        override suspend fun write(entitlement: Entitlement) { current = entitlement }
    }

    private fun grant(untilMillis: Long?) = PremiumGrantSource { untilMillis }

    private val future = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
    private val past = System.currentTimeMillis() - 24 * 60 * 60 * 1000

    @Test
    fun `a live grant alone makes the driver premium with no Play purchase`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            grant(future),
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        // No purchases emitted — yet the grant entitles premium.
        billing.emit(emptyList())
        advanceUntilIdle()

        assertTrue(repo.isPremium())
        val ent = repo.entitlement.value as Entitlement.Premium
        assertFalse(ent.trial) // a grant is never a trial
        assertEquals(future, ent.expiresAt)
    }

    @Test
    fun `no grant leaves the free entitlement untouched`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            grant(null),
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        billing.emit(emptyList())
        advanceUntilIdle()

        assertFalse(repo.isPremium())
    }

    @Test
    fun `an expired grant does not grant premium`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            grant(past),
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        billing.emit(emptyList())
        advanceUntilIdle()

        assertFalse(repo.isPremium())
    }

    @Test
    fun `when both a paid sub and a grant are present the later expiry wins`() = runTest {
        val billing = FakeBillingClient()
        val subExpiry = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000 // sooner than the grant
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            grant(future), // later
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(expiry = subExpiry)))
        advanceUntilIdle()

        assertTrue(repo.isPremium())
        assertEquals(future, (repo.entitlement.value as Entitlement.Premium).expiresAt)
    }

    @Test
    fun `a grant earlier than the paid sub keeps the sub expiry`() = runTest {
        val billing = FakeBillingClient()
        val subExpiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000 // later than the grant
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            grant(future), // earlier than subExpiry
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(expiry = subExpiry)))
        advanceUntilIdle()

        assertEquals(subExpiry, (repo.entitlement.value as Entitlement.Premium).expiresAt)
    }

    @Test
    fun `a null-expiry paid sub is not clamped to the grant`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            grant(future),
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(expiry = null)))
        advanceUntilIdle()

        assertTrue(repo.isPremium())
        // Open-ended (unknown) expiry stays null rather than being narrowed to the grant.
        assertEquals(null, (repo.entitlement.value as Entitlement.Premium).expiresAt)
    }

    @Test
    fun `refreshGrant lights premium up after a redemption`() = runTest {
        val billing = FakeBillingClient()
        // Grant source starts empty, then flips to a live grant (simulating a redemption mid-session).
        var until: Long? = null
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            PremiumGrantSource { until },
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        billing.emit(emptyList())
        advanceUntilIdle()
        assertFalse(repo.isPremium())

        // Redemption grants premium days; the UI calls refreshGrant().
        until = future
        repo.refreshGrant()
        advanceUntilIdle()

        assertTrue(repo.isPremium())
        assertEquals(future, (repo.entitlement.value as Entitlement.Premium).expiresAt)
    }

    @Test
    fun `a failing grant source degrades gracefully to the Play-derived entitlement`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
            PremiumGrantSource { throw RuntimeException("boom") },
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase()))
        advanceUntilIdle()

        // Play purchase still entitles premium despite the grant source throwing.
        assertTrue(repo.isPremium())
    }
}
