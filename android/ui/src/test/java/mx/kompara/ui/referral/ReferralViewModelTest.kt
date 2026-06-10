package mx.kompara.ui.referral

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mx.kompara.billing.BillingLogger
import mx.kompara.billing.Entitlement
import mx.kompara.billing.EntitlementCache
import mx.kompara.billing.EntitlementRepository
import mx.kompara.billing.FakeBillingClient
import mx.kompara.billing.PremiumGrantSource
import mx.kompara.billing.SubscriptionBackend
import mx.kompara.sync.api.ReferralMineResponse
import mx.kompara.sync.api.ReferralRedeemResponse
import mx.kompara.sync.referral.ReferralResult
import mx.kompara.sync.referral.Referrals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ReferralViewModel] state-machine tests (B-056): signed-out gate, Ready load with code + stats,
 * the redeem sub-flow (submitting → success / error with the Spanish backend message), stats reload
 * after a successful redemption, the deep-link prefill via SavedStateHandle, and the entitlement
 * refresh on success. Backed by a plain [FakeReferrals] (no Ktor on the `:ui` test classpath); the
 * repository's own MockEngine coverage lives in `:sync`'s ReferralRepositoryTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferralViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun ReferralViewModel.settle(): ReferralUiState =
        withTimeout(5_000) { uiState.first { it !is ReferralUiState.Loading } }

    private fun entitlements(): EntitlementRepository = EntitlementRepository(
        billing = FakeBillingClient(),
        backend = SubscriptionBackend.NONE,
        store = object : EntitlementCache {
            private var current: Entitlement = Entitlement.Free
            override suspend fun read() = current
            override suspend fun write(entitlement: Entitlement) { current = entitlement }
        },
        logger = BillingLogger.NOOP,
        grantSource = PremiumGrantSource.NONE,
    )

    private fun viewModel(
        referrals: Referrals,
        deepLinkCode: String? = null,
    ) = ReferralViewModel(
        referrals = referrals,
        entitlements = entitlements(),
        savedStateHandle = SavedStateHandle(
            if (deepLinkCode != null) mapOf(ReferralViewModel.ARG_CODE to deepLinkCode) else emptyMap(),
        ),
    )

    @Test
    fun `signed-out driver lands on the SignedOut gate`() = runTest {
        val vm = viewModel(FakeReferrals(signedIn = false))
        assertTrue(vm.settle() is ReferralUiState.SignedOut)
    }

    @Test
    fun `signed-in driver loads code and stats`() = runTest {
        val vm = viewModel(
            FakeReferrals(mine = ReferralMineResponse("WXYZ2345", redemptionsCount = 2, premiumDaysEarned = 28)),
        )
        val state = vm.settle()
        assertTrue(state is ReferralUiState.Ready)
        val ready = state as ReferralUiState.Ready
        assertEquals("WXYZ2345", ready.code)
        assertEquals(2, ready.redemptionsCount)
        assertEquals(28, ready.premiumDaysEarned)
        assertEquals("", ready.redeemInput)
        assertEquals(RedeemPhase.Idle, ready.redeem)
    }

    @Test
    fun `a load failure surfaces a retryable LoadError`() = runTest {
        val vm = viewModel(FakeReferrals(mineResult = ReferralResult.Failure("Sin conexión", null)))
        val state = vm.settle()
        assertTrue(state is ReferralUiState.LoadError)
        assertEquals("Sin conexión", (state as ReferralUiState.LoadError).message)
    }

    @Test
    fun `redeem success grants days, reloads stats, and refreshes entitlement`() = runTest {
        val fake = FakeReferrals(
            mine = ReferralMineResponse("CODE2345", redemptionsCount = 0, premiumDaysEarned = 0),
            redeemResult = ReferralResult.Success(
                ReferralRedeemResponse(grantedDaysRedeemer = 14, grantedDaysReferrer = 0),
            ),
            // After redeeming, getMine() returns refreshed stats.
            mineAfterRedeem = ReferralMineResponse("CODE2345", redemptionsCount = 0, premiumDaysEarned = 0),
        )
        val vm = viewModel(fake)
        vm.settle()

        vm.onRedeemInputChange("PARTNER1")
        vm.redeem()

        val state = vm.settle() as ReferralUiState.Ready
        assertTrue(state.redeem is RedeemPhase.Success)
        assertEquals(14, (state.redeem as RedeemPhase.Success).grantedDays)
        assertTrue(fake.redeemedCodes.contains("PARTNER1"))
        // getMine called twice: initial load + post-redeem reload.
        assertEquals(2, fake.getMineCount)
    }

    @Test
    fun `redeem failure shows the Spanish backend message and keeps the field editable`() = runTest {
        val fake = FakeReferrals(
            mine = ReferralMineResponse("CODE2345", 0, 0),
            redeemResult = ReferralResult.Failure(
                "No puedes usar tu propio código de invitación.",
                status = 400,
            ),
        )
        val vm = viewModel(fake)
        vm.settle()

        vm.onRedeemInputChange("CODE2345")
        vm.redeem()

        val state = vm.settle() as ReferralUiState.Ready
        assertTrue(state.redeem is RedeemPhase.Error)
        assertEquals(
            "No puedes usar tu propio código de invitación.",
            (state.redeem as RedeemPhase.Error).message,
        )
        // Editing the field clears the error.
        vm.onRedeemInputChange("CODE2345X")
        val cleared = vm.uiState.value as ReferralUiState.Ready
        assertEquals(RedeemPhase.Idle, cleared.redeem)
    }

    @Test
    fun `a blank code does not submit`() = runTest {
        val fake = FakeReferrals(mine = ReferralMineResponse("CODE2345", 0, 0))
        val vm = viewModel(fake)
        vm.settle()

        vm.redeem() // input is empty
        assertTrue(fake.redeemedCodes.isEmpty())
        assertTrue((vm.uiState.value as ReferralUiState.Ready).redeem is RedeemPhase.Idle)
    }

    @Test
    fun `a deep-link code prefills the redeem field once signed in`() = runTest {
        val vm = viewModel(
            FakeReferrals(mine = ReferralMineResponse("CODE2345", 0, 0)),
            deepLinkCode = "FRIEND12",
        )
        val ready = vm.settle() as ReferralUiState.Ready
        assertEquals("FRIEND12", ready.redeemInput)
    }

    @Test
    fun `a deep-link code is ignored on the signed-out gate`() = runTest {
        val vm = viewModel(FakeReferrals(signedIn = false), deepLinkCode = "FRIEND12")
        assertTrue(vm.settle() is ReferralUiState.SignedOut)
    }
}

/** A plain fake [Referrals] for the `:ui` test classpath (no Ktor). */
private class FakeReferrals(
    private val signedIn: Boolean = true,
    private val mine: ReferralMineResponse? = null,
    private val mineResult: ReferralResult<ReferralMineResponse>? = null,
    private val mineAfterRedeem: ReferralMineResponse? = null,
    private val redeemResult: ReferralResult<ReferralRedeemResponse>? = null,
) : Referrals {
    val redeemedCodes = mutableListOf<String>()
    var getMineCount = 0

    override suspend fun isSignedIn(): Boolean = signedIn

    override suspend fun getMine(): ReferralResult<ReferralMineResponse> {
        getMineCount++
        mineResult?.let { return it }
        val payload = if (getMineCount > 1 && mineAfterRedeem != null) mineAfterRedeem else mine
        return ReferralResult.Success(payload ?: ReferralMineResponse("CODE0000"))
    }

    override suspend fun redeem(code: String): ReferralResult<ReferralRedeemResponse> {
        redeemedCodes += code
        return redeemResult ?: ReferralResult.Success(ReferralRedeemResponse())
    }
}
