package mx.kompara.billing

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Verifies account-linking: launchBillingFlow threads the driver id through as the
 * `obfuscatedAccountId` so entitlements survive reinstall + device change (B-049 acceptance).
 *
 * Runs under Robolectric only because [BillingClientFacade.launchBillingFlow] takes a real
 * [Activity]; the rest of the billing suite is pure-JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FakeBillingClientLaunchTest {

    @Test
    fun `launch passes the driver id as obfuscated account id`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val billing = FakeBillingClient()
        val product = billing.queryProductDetails().first()

        val result = billing.launchBillingFlow(
            activity = activity,
            product = product,
            obfuscatedAccountId = "driver-123",
        )

        assertEquals(LaunchResult.Launched, result)
        assertEquals(listOf("driver-123"), billing.launchedAccountIds)
    }

    @Test
    fun `anonymous launch passes a null obfuscated account id`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val billing = FakeBillingClient()
        val product = billing.queryProductDetails().first()

        billing.launchBillingFlow(activity, product, obfuscatedAccountId = null)

        assertEquals(listOf<String?>(null), billing.launchedAccountIds)
    }
}
