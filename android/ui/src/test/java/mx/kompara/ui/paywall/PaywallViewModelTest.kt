package mx.kompara.ui.paywall

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mx.kompara.billing.BillingLogger
import mx.kompara.billing.BillingProducts
import mx.kompara.billing.BillingPurchase
import mx.kompara.billing.Entitlement
import mx.kompara.billing.EntitlementCache
import mx.kompara.billing.EntitlementRepository
import mx.kompara.billing.FakeBillingClient
import mx.kompara.billing.PurchaseState
import mx.kompara.billing.SubscriptionBackend
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * [PaywallViewModel] tests (B-050): offer loaded from Play (price/trial copy at runtime), the trial CTA
 * launches the billing flow + records the funnel, restore reports its outcome, and the graceful
 * Play-unavailable state. Runs under Robolectric for a real [Activity] (launchBillingFlow takes one).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaywallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private class RecordingFunnel : GateFunnel {
        val recorded = mutableListOf<Pair<GateSurface, GateEvent>>()
        override suspend fun record(surface: GateSurface, event: GateEvent) {
            recorded += surface to event
        }
    }

    private class FakeCache(var current: Entitlement = Entitlement.Free) : EntitlementCache {
        override suspend fun read(): Entitlement = current
        override suspend fun write(entitlement: Entitlement) { current = entitlement }
    }

    private fun repo(billing: FakeBillingClient) =
        EntitlementRepository(billing, SubscriptionBackend.NONE, FakeCache(), BillingLogger.NOOP)

    @Test
    fun `loads the offer with the price and trial copy from Play`() = runTest(dispatcher) {
        val billing = FakeBillingClient() // DEFAULT_PRODUCTS has a $79 trial offer
        val funnel = RecordingFunnel()
        val vm = PaywallViewModel(billing, repo(billing), funnel)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertTrue(state is PaywallUiState.Ready)
        state as PaywallUiState.Ready
        assertEquals("$79.00", state.formattedPrice)
        assertTrue(state.hasFreeTrial)
    }

    @Test
    fun `Play unavailable yields the graceful state, not a dead CTA`() = runTest(dispatcher) {
        val billing = FakeBillingClient(available = false)
        val vm = PaywallViewModel(billing, repo(billing), RecordingFunnel())
        advanceUntilIdle()

        assertEquals(PaywallUiState.PlayUnavailable, vm.uiState.first())
    }

    @Test
    fun `startTrial launches the billing flow and records TRIAL_STARTED for the surface`() =
        runTest(dispatcher) {
            val activity = Robolectric.buildActivity(Activity::class.java).create().get()
            val billing = FakeBillingClient()
            val funnel = RecordingFunnel()
            val vm = PaywallViewModel(billing, repo(billing), funnel)
            advanceUntilIdle()

            vm.startTrial(activity, GateSurface.FISCAL)
            advanceUntilIdle()

            assertEquals(listOf(GateSurface.FISCAL to GateEvent.TRIAL_STARTED), funnel.recorded)
            // The fake records the (anonymous) launch.
            assertEquals(listOf<String?>(null), billing.launchedAccountIds)
        }

    @Test
    fun `startTrial when Play is unavailable surfaces a message and does not record`() =
        runTest(dispatcher) {
            val activity = Robolectric.buildActivity(Activity::class.java).create().get()
            val billing = FakeBillingClient(available = false)
            val funnel = RecordingFunnel()
            val vm = PaywallViewModel(billing, repo(billing), funnel)
            advanceUntilIdle()

            vm.startTrial(activity, GateSurface.FISCAL)
            advanceUntilIdle()

            assertEquals(PaywallMessage.PlayUnavailable, vm.message.first())
            assertTrue(funnel.recorded.isEmpty())
        }

    @Test
    fun `restore with an owned premium reports Restored`() = runTest(dispatcher) {
        val billing = FakeBillingClient()
        billing.setOwnedPurchases(
            listOf(
                BillingPurchase(
                    purchaseToken = "tok",
                    productId = BillingProducts.PREMIUM_SUBSCRIPTION,
                    state = PurchaseState.PURCHASED,
                    acknowledged = true,
                    autoRenewing = true,
                    trial = false,
                    expiryTimeMillis = 9_999_999_999_000L,
                ),
            ),
        )
        val vm = PaywallViewModel(billing, repo(billing), RecordingFunnel())
        advanceUntilIdle()

        vm.restore()
        advanceUntilIdle()

        assertEquals(PaywallMessage.Restored, vm.message.first())
    }

    @Test
    fun `restore with nothing owned reports NothingToRestore`() = runTest(dispatcher) {
        val billing = FakeBillingClient() // no owned purchases
        val vm = PaywallViewModel(billing, repo(billing), RecordingFunnel())
        advanceUntilIdle()

        vm.restore()
        advanceUntilIdle()

        assertEquals(PaywallMessage.NothingToRestore, vm.message.first())
    }

    @Test
    fun `onOpened records PAYWALL_OPENED for the originating surface`() = runTest(dispatcher) {
        val billing = FakeBillingClient()
        val funnel = RecordingFunnel()
        val vm = PaywallViewModel(billing, repo(billing), funnel)
        advanceUntilIdle()

        vm.onOpened(GateSurface.HISTORY)
        advanceUntilIdle()

        assertTrue(funnel.recorded.contains(GateSurface.HISTORY to GateEvent.PAYWALL_OPENED))
    }

    @Test
    fun `consumeMessage clears the one-shot message`() = runTest(dispatcher) {
        val billing = FakeBillingClient()
        val vm = PaywallViewModel(billing, repo(billing), RecordingFunnel())
        advanceUntilIdle()
        vm.restore()
        advanceUntilIdle()

        vm.consumeMessage()
        assertNull(vm.message.first())
    }
}
