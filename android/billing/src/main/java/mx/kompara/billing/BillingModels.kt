package mx.kompara.billing

/**
 * Domain-level mirrors of the Play Billing wire types. [BillingClientFacade] returns these (rather
 * than `com.android.billingclient.*` types) so [EntitlementRepository] and its tests never touch
 * the Play SDK directly — the only place the SDK appears is [PlayBillingClientImpl].
 */

/**
 * A purchase as Play reports it (queryPurchases / the purchase-update listener).
 *
 * @param purchaseToken the opaque token the backend verifies against the Play Developer API.
 * @param productId the subscription product id (always [BillingProducts.PREMIUM_SUBSCRIPTION] here).
 * @param state lifecycle state from Play.
 * @param acknowledged whether Play has recorded our acknowledgement. An unacknowledged purchase
 *   older than 3 days is auto-refunded by Play, so we must acknowledge promptly.
 * @param autoRenewing false once the driver cancels (still entitled until [expiryTimeMillis]).
 * @param trial true while inside the free-trial phase (no charge yet).
 * @param expiryTimeMillis best-effort period end; null when Play hasn't surfaced it (e.g. PENDING).
 * @param obfuscatedAccountId the driver id we attached at purchase time, when signed in.
 */
data class BillingPurchase(
    val purchaseToken: String,
    val productId: String,
    val state: PurchaseState,
    val acknowledged: Boolean,
    val autoRenewing: Boolean,
    val trial: Boolean,
    val expiryTimeMillis: Long? = null,
    val obfuscatedAccountId: String? = null,
)

/**
 * Purchase lifecycle states the app reacts to. Maps Play's PurchaseState plus the
 * subscription-specific server states (grace period / account hold) that arrive via RTDN/
 * queryPurchases so the entitlement can degrade correctly.
 */
enum class PurchaseState {
    /** Purchased and entitled. */
    PURCHASED,

    /** Payment pending (e.g. cash/OXXO). Not entitled yet; flips to PURCHASED on completion. */
    PENDING,

    /** Renewal payment failed but the driver is still entitled during the grace window. */
    GRACE_PERIOD,

    /** Renewal failed past grace — entitlement suspended (account hold). NOT entitled. */
    ON_HOLD,

    /** No longer entitled (expired/revoked). */
    EXPIRED,
}

/** Subscription product details resolved from Play at runtime (prices come from here, never code). */
data class SubscriptionProduct(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    /** The opaque token launchBillingFlow needs to start the purchase for this specific offer. */
    val offerToken: String,
    /** Human-readable formatted price for the recurring phase (e.g. "$79.00"). From Play, localized. */
    val formattedPrice: String,
    /** ISO 4217 currency code (e.g. "MXN"). From Play. */
    val priceCurrencyCode: String,
    /** Price in micro-units of the currency (price * 1_000_000). From Play. */
    val priceAmountMicros: Long,
    /** True when [offerId] resolves to a free-trial pricing phase (amount 0 for the first phase). */
    val hasFreeTrial: Boolean,
)

/** Outcome of a [BillingClientFacade] connection attempt. */
sealed interface BillingConnectionResult {
    data object Connected : BillingConnectionResult

    /**
     * The billing service is unavailable on this device/build (Play not installed, emulator
     * without Play, China builds, etc.). The app falls back to the FREE tier and logs loudly.
     */
    data class Unavailable(val reason: String) : BillingConnectionResult
}

/** Result of launching the purchase flow. The actual purchase arrives via the update listener. */
sealed interface LaunchResult {
    data object Launched : LaunchResult

    data object AlreadyOwned : LaunchResult

    data object UserCanceled : LaunchResult

    data class Error(val code: Int, val message: String) : LaunchResult
}
