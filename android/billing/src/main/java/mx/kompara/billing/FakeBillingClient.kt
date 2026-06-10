package mx.kompara.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [BillingClientFacade] for unit tests AND as the runtime fallback when Play Billing is
 * unavailable (emulator without Play, sideloaded build, Play Store missing). When wired as the
 * fallback it reports [BillingConnectionResult.Unavailable] and owns no purchases, so the app
 * cleanly degrades to FREE — and logs loudly via [logger] so we notice.
 *
 * As a test double it can be seeded with [products] and driven with [emit] / [setOwnedPurchases] /
 * [completePending] to script the full lifecycle without a device.
 */
class FakeBillingClient(
    private val available: Boolean = true,
    private val unavailableReason: String = "Play Billing unavailable on this device/build",
    private val logger: BillingLogger = BillingLogger.NOOP,
    products: List<SubscriptionProduct> = DEFAULT_PRODUCTS,
) : BillingClientFacade {

    private val _products = products.toMutableList()
    private val owned = MutableStateFlow<List<BillingPurchase>>(emptyList())

    /** Tokens acknowledged via [acknowledgePurchase]; assertable from tests. */
    val acknowledgedTokens = mutableListOf<String>()

    /** launchBillingFlow invocations, capturing the obfuscated account id passed (account-linking). */
    val launchedAccountIds = mutableListOf<String?>()

    /** Connection attempts; lets tests assert retry/connect happened. */
    var connectAttempts = 0
        private set

    override val purchaseUpdates: Flow<List<BillingPurchase>> = owned.asStateFlow()

    override suspend fun ensureConnected(): BillingConnectionResult {
        connectAttempts++
        return if (available) {
            BillingConnectionResult.Connected
        } else {
            logger.w("FakeBillingClient: $unavailableReason")
            BillingConnectionResult.Unavailable(unavailableReason)
        }
    }

    override suspend fun queryProductDetails(): List<SubscriptionProduct> =
        if (available) _products.toList() else emptyList()

    override suspend fun launchBillingFlow(
        activity: Activity,
        product: SubscriptionProduct,
        obfuscatedAccountId: String?,
    ): LaunchResult {
        if (!available) return LaunchResult.Error(code = -1, message = unavailableReason)
        launchedAccountIds += obfuscatedAccountId
        if (owned.value.any {
                it.productId == product.productId && it.state == PurchaseState.PURCHASED
            }
        ) {
            return LaunchResult.AlreadyOwned
        }
        return LaunchResult.Launched
    }

    override suspend fun queryPurchases(): List<BillingPurchase> =
        if (available) owned.value else emptyList()

    override suspend fun acknowledgePurchase(purchaseToken: String): Boolean {
        acknowledgedTokens += purchaseToken
        owned.value = owned.value.map {
            if (it.purchaseToken == purchaseToken) it.copy(acknowledged = true) else it
        }
        return true
    }

    // ── test-driving helpers ──────────────────────────────────────────────

    /** Push a new set of purchases through the update listener (simulates a Play callback). */
    fun emit(purchases: List<BillingPurchase>) {
        owned.value = purchases
    }

    /** Convenience for restore tests: set the owned set returned by [queryPurchases]. */
    fun setOwnedPurchases(purchases: List<BillingPurchase>) {
        owned.value = purchases
    }

    /** Flip a PENDING purchase to PURCHASED (cash payment cleared) and re-emit. */
    fun completePending(purchaseToken: String, expiryTimeMillis: Long? = null) {
        owned.value = owned.value.map {
            if (it.purchaseToken == purchaseToken && it.state == PurchaseState.PENDING) {
                it.copy(state = PurchaseState.PURCHASED, expiryTimeMillis = expiryTimeMillis)
            } else {
                it
            }
        }
    }

    companion object {
        /**
         * A representative offer set — note prices are PLACEHOLDERS that mimic what Play would
         * return at runtime; the app never reads prices from source, only from these
         * [SubscriptionProduct]s which in production come from Play.
         */
        val DEFAULT_PRODUCTS: List<SubscriptionProduct> = listOf(
            SubscriptionProduct(
                productId = BillingProducts.PREMIUM_SUBSCRIPTION,
                basePlanId = BillingProducts.MONTHLY_BASE_PLAN,
                offerId = BillingProducts.TRIAL_OFFER,
                offerToken = "fake-offer-token-trial",
                formattedPrice = "$79.00",
                priceCurrencyCode = "MXN",
                priceAmountMicros = 79_000_000,
                hasFreeTrial = true,
            ),
        )
    }
}
