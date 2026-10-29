package mx.kompara.billing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class V3BillingTest {
    private val tomorrow = System.currentTimeMillis() + 86400000L
    private class Cache(var value: Entitlement, var plan: PlanStatus = PlanStatus()) : EntitlementCache {
        override suspend fun read() = value
        override suspend fun write(entitlement: Entitlement) { value = entitlement }
        override suspend fun readPlan() = plan
        override suspend fun writePlan(plan: PlanStatus) { this.plan = plan }
    }
    private fun purchase(state: PurchaseState, expires: Long? = null) = BillingPurchase("token", "kompara_premium", state, true, true, false, expires)
    @Test fun `pending retains cached promotional access without syncing active or acknowledging`() = runTest {
        val billing = FakeBillingClient(); billing.emit(listOf(purchase(PurchaseState.PENDING)))
        var syncs = 0
        val backend = object : SubscriptionBackend { override suspend fun syncPurchase(purchase: BillingPurchase): ServerSubscription { syncs++; return ServerSubscription(ServerSubscriptionStatus.ACTIVE, false, null) } }
        val cache = Cache(Entitlement.Premium(false, tomorrow), PlanStatus(grantUntil = tomorrow))
        val repo = EntitlementRepository(billing, backend, cache, BillingLogger.NOOP, PremiumGrantSource { tomorrow })
        repo.restore()
        assertEquals(Entitlement.Premium(false, tomorrow), repo.entitlement.value)
        assertEquals(PaymentStatus.PENDING, repo.plan.value.payment)
        assertEquals(0, syncs); assertTrue(billing.acknowledgedTokens.isEmpty())
        billing.completePending("token", tomorrow); repo.restore()
        assertEquals(PaymentStatus.ACTIVE, repo.plan.value.payment)
    }
    @Test fun `empty successful Play response with failed grant fetch retains cached positive access`() = runTest {
        val cache = Cache(Entitlement.Premium(false, tomorrow))
        val repo = EntitlementRepository(FakeBillingClient(), SubscriptionBackend.NONE, cache, BillingLogger.NOOP, PremiumGrantSource { error("offline") })
        repo.restore(); assertEquals(cache.value, repo.entitlement.value); assertTrue(repo.isPremium())
    }
    @Test fun `query failure never becomes an authoritative empty ownership list`() = runTest {
        val cached = Entitlement.Premium(false, tomorrow)
        val billing = object : BillingClientFacade by FakeBillingClient() { override suspend fun queryPurchases(): List<BillingPurchase> = error("offline") }
        val repo = EntitlementRepository(billing, SubscriptionBackend.NONE, Cache(cached), BillingLogger.NOOP)
        assertTrue(runCatching { repo.restore() }.isFailure); assertEquals(cached, repo.entitlement.value)
    }
    @Test fun `grace uses authoritative date cancellation expires and independent grant survives hold`() = runTest {
        var status = ServerSubscriptionStatus.GRACE; var expiry = tomorrow
        val billing = FakeBillingClient(); billing.emit(listOf(purchase(PurchaseState.PURCHASED)))
        val backend = object : SubscriptionBackend { override suspend fun syncPurchase(purchase: BillingPurchase) = ServerSubscription(status, false, expiry) }
        val repo = EntitlementRepository(billing, backend, Cache(Entitlement.Free), BillingLogger.NOOP)
        repo.restore(); assertEquals(PaymentStatus.GRACE, repo.plan.value.payment); assertEquals(tomorrow, repo.plan.value.paidUntil)
        assertNull(repo.plan.value.bankedDays)
        status = ServerSubscriptionStatus.CANCELED; repo.restore(); assertTrue(repo.isPremium())
        expiry = System.currentTimeMillis() - 1; repo.restore(); assertFalse(repo.isPremium())
        status = ServerSubscriptionStatus.GRACE; repo.restore(); assertFalse(repo.isPremium())
        status = ServerSubscriptionStatus.HOLD
        val granted = EntitlementRepository(billing, backend, Cache(Entitlement.Free), BillingLogger.NOOP, PremiumGrantSource { tomorrow })
        granted.restore(); assertTrue(granted.isPremium()); assertEquals(PaymentStatus.HOLD, granted.plan.value.payment)
    }
    @Test fun `calendar windows use dates across month year and leap transitions and never lock absent data`() {
        val monday = LocalDate.parse("2026-11-02")
        assertTrue(FreeDetailWindow.week(monday.minusWeeks(3), monday.plusDays(6)))
        assertFalse(FreeDetailWindow.week(monday.minusWeeks(4), monday))
        assertFalse(FreeDetailWindow.weekLocked(monday.minusWeeks(4), monday, false, false))
        assertTrue(FreeDetailWindow.week(LocalDate.parse("2025-12-29"), LocalDate.parse("2026-01-01")))
        assertTrue(FreeDetailWindow.month(YearMonth.of(2028, 2), LocalDate.of(2028, 2, 29)))
        assertFalse(FreeDetailWindow.month(YearMonth.of(2028, 2), LocalDate.of(2028, 3, 1)))
        assertFalse(FreeDetailWindow.monthLocked(YearMonth.of(2028, 2), LocalDate.of(2028, 3, 1), false, false))
        assertFalse(FreeDetailWindow.week(monday.plusWeeks(1), monday))
    }
    @Test fun `regular monthly selection rejects introductory yearly missing and free offers`() {
        val base = SubscriptionProduct("kompara_premium", "monthly", null, "token", "$79", "MXN", 79000000, false)
        assertEquals(base, (FakeBillingClient.DEFAULT_PRODUCTS + base).monthlyPurchase())
        for (invalid in listOf(base.copy(hasFreeTrial = true), base.copy(offerId = "discount"), base.copy(billingPeriod = "P1Y"), base.copy(priceAmountMicros = 0), base.copy(priceCurrencyCode = ""))) {
            assertNull(listOf(invalid).monthlyPurchase())
        }
        assertFalse(PromotionalTrials.UNSUPPORTED.supported)
    }
}
