package mx.kompara.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the Play Billing Library: connection lifecycle, product-details query, purchase
 * flow, restore (queryPurchases), and acknowledgement.
 *
 * Everything testable lives behind this interface. [PlayBillingClientImpl] is the real Play-backed
 * implementation; [FakeBillingClient] drives unit tests and stands in when Play is unavailable
 * (emulator without Play, no Play Store, etc.) — logging loudly so we notice in the field.
 *
 * NOTE: prices are never read from this layer's call sites; they ride on [SubscriptionProduct] and
 * originate from Play's [com.android.billingclient.api.ProductDetails] at runtime.
 */
interface BillingClientFacade {
    /**
     * A hot stream of purchase updates. Emits the latest set of purchases whenever Play notifies
     * the purchase-update listener (new purchase, restore, state change). The first collection also
     * triggers a [queryPurchases] so a fresh subscriber sees current state.
     */
    val purchaseUpdates: Flow<List<BillingPurchase>>

    /**
     * Ensure the billing service is connected, retrying transient disconnects with backoff. Safe to
     * call repeatedly; returns [BillingConnectionResult.Connected] when ready or
     * [BillingConnectionResult.Unavailable] when Play billing isn't supported on this device/build.
     */
    suspend fun ensureConnected(): BillingConnectionResult

    /**
     * Query subscription [ProductDetails] for [BillingProducts.PREMIUM_SUBSCRIPTION] and return the
     * available offers (monthly base plan + free-trial offer) as [SubscriptionProduct]s. Empty when
     * the product isn't configured or billing is unavailable.
     */
    suspend fun queryProductDetails(): List<SubscriptionProduct>

    /**
     * Launch the Play purchase UI for [product] from [activity]. The resulting purchase (if any)
     * arrives asynchronously on [purchaseUpdates]. [obfuscatedAccountId] is the driver id when
     * signed in (account-linked entitlements survive reinstall + device change).
     */
    suspend fun launchBillingFlow(
        activity: Activity,
        product: SubscriptionProduct,
        obfuscatedAccountId: String?,
    ): LaunchResult

    /**
     * Restore / re-read owned subscriptions (queryPurchases). Used on app start, after reinstall,
     * and on demand from settings. The same set is also pushed to [purchaseUpdates].
     */
    suspend fun queryPurchases(): List<BillingPurchase>

    /**
     * Acknowledge [purchaseToken] so Play does not auto-refund it. Idempotent — a no-op when already
     * acknowledged. Returns true on success.
     */
    suspend fun acknowledgePurchase(purchaseToken: String): Boolean
}
