package mx.kompara.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real Play Billing Library ([BillingClient]) implementation of [BillingClientFacade].
 *
 * Untested by unit tests (no Play environment in CI — real-store validation is tracked as a launch
 * blocker in techdebt, internal track B-053). All app/test logic flows through the interface; this
 * class only translates between Play's callback API and our suspend/Flow contract.
 *
 * Connection lifecycle: a single long-lived client with auto-reconnection enabled, plus an explicit
 * retry-with-backoff in [ensureConnected] for the initial connect. The purchase-update listener
 * fans new purchases into [purchaseUpdates]; the first collector also triggers a [queryPurchases]
 * so it sees current state.
 */
class PlayBillingClientImpl(
    context: Context,
    private val logger: BillingLogger,
) : BillingClientFacade {

    private val purchaseFlow = MutableSharedFlow<List<BillingPurchase>>(
        replay = 1,
        extraBufferCapacity = 8,
    )

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchaseFlow.tryEmit(purchases.map { it.toBillingPurchase() })
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            logger.d("Purchase flow canceled by user")
        } else {
            logger.w("Purchase update error ${result.responseCode}: ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            // Billing 9 requires enableOneTimeProducts() even for subscription-only apps —
            // build() throws IllegalArgumentException without it (crashes on real devices only).
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    override val purchaseUpdates: Flow<List<BillingPurchase>> = purchaseFlow.onStart {
        // A fresh collector should see current ownership without waiting for a callback.
        if (ensureConnected() is BillingConnectionResult.Connected) {
            emit(queryPurchases())
        }
    }

    override suspend fun ensureConnected(): BillingConnectionResult {
        if (client.isReady) return BillingConnectionResult.Connected

        var attempt = 0
        var lastReason = "unknown"
        while (attempt < MAX_CONNECT_ATTEMPTS) {
            val result = connectOnce()
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK ->
                    return BillingConnectionResult.Connected

                // Billing genuinely not supported here — no point retrying; fall back to FREE.
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
                -> {
                    val reason = "code=${result.responseCode} ${result.debugMessage}"
                    logger.w("Play Billing unavailable on this device/build: $reason")
                    return BillingConnectionResult.Unavailable(reason)
                }

                else -> {
                    lastReason = "code=${result.responseCode} ${result.debugMessage}"
                    attempt++
                    if (attempt < MAX_CONNECT_ATTEMPTS) {
                        delay(BACKOFF_BASE_MILLIS * attempt)
                    }
                }
            }
        }
        logger.w("Play Billing connect failed after $MAX_CONNECT_ATTEMPTS attempts: $lastReason")
        return BillingConnectionResult.Unavailable(lastReason)
    }

    private suspend fun connectOnce(): BillingResult = suspendCancellableCoroutine { cont ->
        client.startConnection(object : BillingClientStateListener {
            private var resumed = false
            override fun onBillingSetupFinished(result: BillingResult) {
                if (!resumed) {
                    resumed = true
                    cont.resume(result)
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto-reconnection handles re-establishing; nothing to resume here.
                logger.d("Billing service disconnected")
            }
        })
    }

    override suspend fun queryProductDetails(): List<SubscriptionProduct> {
        if (ensureConnected() !is BillingConnectionResult.Connected) return emptyList()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingProducts.PREMIUM_SUBSCRIPTION)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()

        val result = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(queryResult.productDetailsList)
                } else {
                    logger.w("queryProductDetails failed ${billingResult.responseCode}: ${billingResult.debugMessage}")
                    cont.resume(emptyList())
                }
            }
        }

        return result.flatMap { details ->
            details.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
                // The recurring (paid) phase is the last pricing phase; a free-trial offer has a
                // leading phase with amount 0. Prices come ONLY from here (never hardcoded).
                val phases = offer.pricingPhases.pricingPhaseList
                val recurring = phases.lastOrNull() ?: return@mapNotNull null
                val hasFreeTrial = phases.any { it.priceAmountMicros == 0L }
                SubscriptionProduct(
                    productId = details.productId,
                    basePlanId = offer.basePlanId,
                    offerId = offer.offerId,
                    offerToken = offer.offerToken,
                    formattedPrice = recurring.formattedPrice,
                    priceCurrencyCode = recurring.priceCurrencyCode,
                    priceAmountMicros = recurring.priceAmountMicros,
                    hasFreeTrial = hasFreeTrial,
                )
            }
        }
    }

    override suspend fun launchBillingFlow(
        activity: Activity,
        product: SubscriptionProduct,
        obfuscatedAccountId: String?,
    ): LaunchResult {
        if (ensureConnected() !is BillingConnectionResult.Connected) {
            return LaunchResult.Error(code = -1, message = "Billing not connected")
        }

        // Re-resolve the full ProductDetails for this product so we can pass the offer token.
        val details = queryRawProductDetails().firstOrNull { it.productId == product.productId }
            ?: return LaunchResult.Error(code = -1, message = "Product details unavailable")

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(product.offerToken)
                        .build(),
                ),
            )
            // Account-linking: entitlements survive reinstall + device change when signed in.
            .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
            .build()

        val result = client.launchBillingFlow(activity, params)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> LaunchResult.Launched
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> LaunchResult.AlreadyOwned
            BillingClient.BillingResponseCode.USER_CANCELED -> LaunchResult.UserCanceled
            else -> LaunchResult.Error(result.responseCode, result.debugMessage)
        }
    }

    override suspend fun queryPurchases(): List<BillingPurchase> {
        if (ensureConnected() !is BillingConnectionResult.Connected) return emptyList()

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            // Include held subs so the entitlement can map ON_HOLD instead of silently vanishing.
            .includeSuspendedSubscriptions(true)
            .build()

        return suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(purchases.map { it.toBillingPurchase() })
                } else {
                    logger.w("queryPurchases failed ${result.responseCode}: ${result.debugMessage}")
                    cont.resume(emptyList())
                }
            }
        }
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Boolean {
        if (ensureConnected() !is BillingConnectionResult.Connected) return false
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        return suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }

    private suspend fun queryRawProductDetails(): List<com.android.billingclient.api.ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingProducts.PREMIUM_SUBSCRIPTION)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                cont.resume(
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryResult.productDetailsList
                    } else {
                        emptyList()
                    },
                )
            }
        }
    }

    /**
     * Map a Play [Purchase] to our [BillingPurchase]. Grace-period vs. active is hard to distinguish
     * purely client-side — Play reports both as PURCHASED — so the authoritative grace/hold state
     * comes from the backend (RTDN). Here we surface [Purchase.isSuspended] as ON_HOLD and otherwise
     * map PURCHASED/PENDING straight through.
     */
    private fun Purchase.toBillingPurchase(): BillingPurchase {
        val state = when {
            isSuspended -> PurchaseState.ON_HOLD
            purchaseState == Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            purchaseState == Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.EXPIRED
        }
        return BillingPurchase(
            purchaseToken = purchaseToken,
            productId = products.firstOrNull() ?: BillingProducts.PREMIUM_SUBSCRIPTION,
            state = state,
            acknowledged = isAcknowledged,
            autoRenewing = isAutoRenewing,
            // A free-trial purchase is acknowledged like any other; the trial flag is derived from
            // the offer pricing, which the backend confirms via the Play Developer API. Client-side
            // we conservatively default false and let the backend set it authoritatively.
            trial = false,
            expiryTimeMillis = null,
            obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId,
        )
    }

    private companion object {
        const val MAX_CONNECT_ATTEMPTS = 5
        const val BACKOFF_BASE_MILLIS = 500L
    }
}
