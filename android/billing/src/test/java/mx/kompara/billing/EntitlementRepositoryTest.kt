package mx.kompara.billing

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Lifecycle tests for [EntitlementRepository], driven entirely by [FakeBillingClient] + an in-memory
 * [EntitlementCache] (no Play environment, no device). Covers: purchase → PREMIUM, the trial flag,
 * restore on reinstall, acknowledgement within the flow, pending → active, grace/hold mapping, and
 * the offline last-known fallback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementRepositoryTest {

    private val premiumToken = "token-abc"

    private fun purchase(
        state: PurchaseState = PurchaseState.PURCHASED,
        acknowledged: Boolean = false,
        trial: Boolean = false,
        expiry: Long? = null,
        token: String = premiumToken,
    ) = BillingPurchase(
        purchaseToken = token,
        productId = BillingProducts.PREMIUM_SUBSCRIPTION,
        state = state,
        acknowledged = acknowledged,
        autoRenewing = true,
        trial = trial,
        expiryTimeMillis = expiry,
    )

    private class FakeCache(var current: Entitlement = Entitlement.Free) : EntitlementCache {
        override suspend fun read(): Entitlement = current
        override suspend fun write(entitlement: Entitlement) {
            current = entitlement
        }
    }

    private class RecordingBackend(
        private val response: ServerSubscription? = null,
    ) : SubscriptionBackend {
        val synced = mutableListOf<BillingPurchase>()
        override suspend fun syncPurchase(purchase: BillingPurchase): ServerSubscription? {
            synced += purchase
            return response
        }
    }

    @Test
    fun `purchase transitions entitlement to premium`() = runTest {
        val billing = FakeBillingClient()
        val cache = FakeCache()
        val repo = EntitlementRepository(billing, SubscriptionBackend.NONE, cache, BillingLogger.NOOP)

        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        assertEquals(Entitlement.Free, repo.entitlement.value)

        billing.emit(listOf(purchase(state = PurchaseState.PURCHASED)))
        advanceUntilIdle()

        assertTrue(repo.isPremium())
        val ent = repo.entitlement.value
        assertTrue(ent is Entitlement.Premium)
        assertFalse((ent as Entitlement.Premium).trial)
    }

    @Test
    fun `trial purchase sets the trial flag on premium`() = runTest {
        val billing = FakeBillingClient()
        val cache = FakeCache()
        val repo = EntitlementRepository(billing, SubscriptionBackend.NONE, cache, BillingLogger.NOOP)
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(trial = true, expiry = 9_999_999_999_000L)))
        advanceUntilIdle()

        val ent = repo.entitlement.value
        assertTrue(ent is Entitlement.Premium)
        assertTrue((ent as Entitlement.Premium).trial)
        assertEquals(9_999_999_999_000L, ent.expiresAt)
    }

    @Test
    fun `purchase is acknowledged within the flow`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(acknowledged = false)))
        advanceUntilIdle()

        assertEquals(listOf(premiumToken), billing.acknowledgedTokens)
    }

    @Test
    fun `already-acknowledged purchase is not re-acknowledged`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(acknowledged = true)))
        advanceUntilIdle()

        assertTrue(billing.acknowledgedTokens.isEmpty())
        assertTrue(repo.isPremium())
    }

    @Test
    fun `pending purchase is not premium until it activates`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(state = PurchaseState.PENDING)))
        advanceUntilIdle()
        assertFalse(repo.isPremium())

        // Cash payment clears → PURCHASED.
        billing.completePending(premiumToken, expiryTimeMillis = 9_999_999_999_000L)
        advanceUntilIdle()
        assertTrue(repo.isPremium())
        // It also gets acknowledged once active.
        assertEquals(listOf(premiumToken), billing.acknowledgedTokens)
    }

    @Test
    fun `grace period keeps the driver entitled`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(state = PurchaseState.GRACE_PERIOD, acknowledged = true)))
        advanceUntilIdle()

        assertTrue(repo.isPremium())
    }

    @Test
    fun `account hold suspends entitlement`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(state = PurchaseState.ON_HOLD, acknowledged = true)))
        advanceUntilIdle()

        assertFalse(repo.isPremium())
        assertEquals(Entitlement.Free, repo.entitlement.value)
    }

    @Test
    fun `restore on reinstall re-derives premium from owned purchases`() = runTest {
        val billing = FakeBillingClient()
        // Simulate a reinstall: the listener emits nothing, but Play still owns the sub.
        billing.setOwnedPurchases(listOf(purchase(acknowledged = true)))
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
        )

        val restored = repo.restore()

        assertTrue(restored is Entitlement.Premium)
        assertTrue(repo.isPremium())
    }

    @Test
    fun `offline last-known premium is used when billing is unavailable`() = runTest {
        val billing = FakeBillingClient(available = false)
        // Last-known: premium with a future expiry (well within grace).
        val cache = FakeCache(
            Entitlement.Premium(trial = false, expiresAt = System.currentTimeMillis() + 86_400_000L),
        )
        val repo = EntitlementRepository(billing, SubscriptionBackend.NONE, cache, BillingLogger.NOOP)

        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        assertTrue(repo.isPremium())
    }

    @Test
    fun `stale last-known premium past grace window downgrades to free`() = runTest {
        val billing = FakeBillingClient(available = false)
        // Expired 10 days ago — beyond the 3-day grace window.
        val cache = FakeCache(
            Entitlement.Premium(
                trial = false,
                expiresAt = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000,
            ),
        )
        val repo = EntitlementRepository(billing, SubscriptionBackend.NONE, cache, BillingLogger.NOOP)

        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        assertFalse(repo.isPremium())
    }

    @Test
    fun `server view overrides client guess and is synced`() = runTest {
        val billing = FakeBillingClient()
        // Client sees PURCHASED, but the server says ON_HOLD → entitlement is FREE.
        val backend = RecordingBackend(
            ServerSubscription(
                status = ServerSubscriptionStatus.HOLD,
                trial = false,
                expiresAtMillis = null,
            ),
        )
        val repo = EntitlementRepository(billing, backend, FakeCache(), BillingLogger.NOOP)
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        billing.emit(listOf(purchase(state = PurchaseState.PURCHASED, acknowledged = true)))
        advanceUntilIdle()

        assertFalse(repo.isPremium())
        assertEquals(1, backend.synced.size)
        assertEquals(premiumToken, backend.synced.first().purchaseToken)
    }

    @Test
    fun `capabilities are all true under premium and false under free`() = runTest {
        val billing = FakeBillingClient()
        val repo = EntitlementRepository(
            billing,
            SubscriptionBackend.NONE,
            FakeCache(),
            BillingLogger.NOOP,
        )
        repo.start(TestScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()

        // Free: everything gated.
        assertEquals(Capabilities.FREE, repo.capabilitiesNow())
        assertFalse(repo.capabilities.first().canSeeBenchmarks)

        billing.emit(listOf(purchase(acknowledged = true)))
        advanceUntilIdle()

        val caps = repo.capabilitiesNow()
        assertTrue(caps.canSeeBenchmarks)
        assertTrue(caps.canSeeCompare)
        assertTrue(caps.canSeeHistory)
        assertTrue(caps.canSeeFiscal)
    }

    @Test
    fun `product details carry runtime pricing and the free-trial offer`() = runTest {
        val billing = FakeBillingClient()
        val products = billing.queryProductDetails()

        assertEquals(1, products.size)
        val product = products.first()
        assertEquals(BillingProducts.PREMIUM_SUBSCRIPTION, product.productId)
        assertEquals(BillingProducts.MONTHLY_BASE_PLAN, product.basePlanId)
        assertEquals(BillingProducts.TRIAL_OFFER, product.offerId)
        // Prices come from the product details at runtime — never hardcoded at call sites.
        assertEquals("MXN", product.priceCurrencyCode)
        assertTrue(product.priceAmountMicros > 0)
        assertTrue(product.hasFreeTrial)
    }
}
