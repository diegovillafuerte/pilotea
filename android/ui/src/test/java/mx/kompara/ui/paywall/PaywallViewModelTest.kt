package mx.kompara.ui.paywall

import android.app.Activity
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mx.kompara.billing.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class PaywallViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()
    private val monthly = SubscriptionProduct("kompara_premium", "monthly", null, "base", "MX$99.50", "MXN", 99_500_000, false)
    private class Cache(var current: Entitlement = Entitlement.Free) : EntitlementCache {
        override suspend fun read() = current
        override suspend fun write(entitlement: Entitlement) { current = entitlement }
    }
    private class Funnel : GateFunnel {
        val events = mutableListOf<Pair<GateSurface, GateEvent>>()
        override suspend fun record(surface: GateSurface, event: GateEvent) { events += surface to event }
    }
    private class Trials : PromotionalTrials {
        override val supported = true
        var eligible = TrialEligibility.ELIGIBLE
        var starts = 0; var checks = 0
        override suspend fun eligibility(accountId: String): TrialEligibility { checks++; return eligible }
        override suspend fun start(accountId: String): PromotionalGrant { starts++; return PromotionalGrant(System.currentTimeMillis() + 14 * 86400000L) }
    }
    private val evidence = PremiumEvidence(listOf("El detalle de cada semana"))
    private fun repo(billing: BillingClientFacade) = EntitlementRepository(billing, SubscriptionBackend.NONE, Cache(), BillingLogger.NOOP)
    private fun vm(billing: BillingClientFacade = FakeBillingClient(products = listOf(monthly)),
        source: PremiumSalesSource = PremiumSalesSource { evidence }, identity: PremiumIdentity = PremiumIdentity { null },
        trials: PromotionalTrials = PromotionalTrials.UNSUPPORTED, repository: EntitlementRepository = repo(billing), funnel: Funnel = Funnel()) =
        PaywallViewModel(billing, repository, funnel, source, identity, trials).also { it.onOpened(GateSurface.HISTORY) }
    private fun activity() = Robolectric.buildActivity(Activity::class.java).create().get()

    @Test fun `actual localized monthly price excludes card required Play trial`() = runTest(dispatcher) {
        val model = vm(FakeBillingClient(products = FakeBillingClient.DEFAULT_PRODUCTS + monthly))
        advanceUntilIdle()
        val ready = model.uiState.value as PaywallUiState.Ready
        assertEquals("MX$99.50", ready.formattedPrice); assertFalse(ready.promotional)
        assertNull(ready.product.offerId)
        val trialOnly = vm(FakeBillingClient())
        advanceUntilIdle(); assertEquals(PaywallUiState.PlayUnavailable, trialOnly.uiState.value)
    }
    @Test fun `Play unavailable is retryable without launch action`() = runTest(dispatcher) {
        val billing = FakeBillingClient(available = false)
        val model = vm(billing); advanceUntilIdle()
        assertEquals(PaywallUiState.PlayUnavailable, model.uiState.value)
        model.purchase(activity()); advanceUntilIdle(); assertTrue(billing.launchedAccountIds.isEmpty())
    }
    @Test fun `eligibility precedes offers and is rechecked before purchase`() = runTest(dispatcher) {
        var available = true
        val billing = FakeBillingClient(products = listOf(monthly))
        val model = vm(billing, source = PremiumSalesSource { if (available) evidence else PremiumEvidence() })
        advanceUntilIdle(); available = false
        model.purchase(activity()); advanceUntilIdle()
        assertEquals(PaywallUiState.NoResult, model.uiState.value); assertTrue(billing.launchedAccountIds.isEmpty())
        model.loadOffer(); advanceUntilIdle(); assertEquals(PaywallUiState.NoResult, model.uiState.value)
    }
    @Test fun `purchase uses account identity and distinct purchase event with duplicate taps ignored`() = runTest(dispatcher) {
        val billing = FakeBillingClient(products = listOf(monthly)); val funnel = Funnel()
        val model = vm(billing, identity = PremiumIdentity { "driver-id" }, funnel = funnel); advanceUntilIdle()
        model.purchase(activity()); model.purchase(activity()); advanceUntilIdle()
        assertEquals(listOf("driver-id"), billing.launchedAccountIds)
        assertTrue(funnel.events.contains(GateSurface.HISTORY to GateEvent.PURCHASE_STARTED))
        assertFalse(funnel.events.any { it.second == GateEvent.TRIAL_STARTED })
    }
    @Test fun `no account returns from auth without starting promotional clock`() = runTest(dispatcher) {
        var account: String? = null; val trials = Trials()
        val billing = FakeBillingClient(products = listOf(monthly))
        val model = vm(billing, identity = PremiumIdentity { account }, trials = trials)
        advanceUntilIdle(); model.purchase(activity()); advanceUntilIdle()
        assertEquals(PaywallMessage.SignIn, model.message.value); assertEquals(0, trials.starts)
        account = "verified"; model.consumeMessage(); model.loadOffer(); advanceUntilIdle()
        assertEquals(TrialEligibility.ELIGIBLE, (model.uiState.value as PaywallUiState.Ready).trial)
        assertEquals(0, trials.starts); assertTrue(billing.launchedAccountIds.isEmpty())
        model.purchase(activity()); advanceUntilIdle()
        assertEquals(1, trials.starts); assertTrue(model.entitlement.value is Entitlement.Premium)
        assertTrue(billing.launchedAccountIds.isEmpty()); assertTrue(trials.checks >= 2)
    }
    @Test fun `changed trial eligibility cannot start and failures never convert to a Play trial`() = runTest(dispatcher) {
        val trials = Trials(); val billing = FakeBillingClient(products = listOf(monthly))
        val model = vm(billing, identity = PremiumIdentity { "id" }, trials = trials); advanceUntilIdle()
        trials.eligible = TrialEligibility.USED
        model.purchase(activity()); advanceUntilIdle()
        assertEquals(PaywallMessage.OfferChanged, model.message.value); assertEquals(0, trials.starts)
        assertTrue(billing.launchedAccountIds.isEmpty())
    }
    @Test fun `restore with owned purchase and with no purchase reports distinct results`() = runTest(dispatcher) {
        val billing = FakeBillingClient(products = listOf(monthly)); val model = vm(billing); advanceUntilIdle()
        model.restore(); advanceUntilIdle(); assertEquals(PaywallMessage.NothingToRestore, model.message.value)
        billing.setOwnedPurchases(listOf(BillingPurchase("token", "kompara_premium", PurchaseState.PURCHASED, true, true, false)))
        model.restore(); advanceUntilIdle(); assertEquals(PaywallMessage.Restored, model.message.value)
        model.consumeMessage(); assertNull(model.message.value)
    }
    @Test fun `restore transport failure is not nothing to restore`() = runTest(dispatcher) {
        val billing = object : BillingClientFacade by FakeBillingClient(products = listOf(monthly)) {
            override suspend fun queryPurchases(): List<BillingPurchase> = error("offline")
        }
        val model = vm(billing); advanceUntilIdle(); model.restore(); advanceUntilIdle()
        assertEquals(PaywallMessage.RestoreFailed, model.message.value)
    }
    @Test fun `price changes after viewing require another explicit tap`() = runTest(dispatcher) {
        var product = monthly
        val delegate = FakeBillingClient()
        val billing = object : BillingClientFacade by delegate {
            override suspend fun queryProductDetails() = listOf(product)
        }
        val model = vm(billing); advanceUntilIdle(); product = monthly.copy(formattedPrice = "€4,99", priceCurrencyCode = "EUR", priceAmountMicros = 4_990_000)
        model.purchase(activity()); advanceUntilIdle()
        assertEquals(PaywallMessage.OfferChanged, model.message.value); assertTrue(delegate.launchedAccountIds.isEmpty())
    }
}
