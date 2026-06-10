package mx.kompara.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the driver's [Entitlement].
 *
 * Pipeline: Play purchases (via [BillingClientFacade]) → acknowledge → best-effort backend sync →
 * derive [Entitlement] → persist last-known (offline grace) → publish on [entitlement].
 *
 * Derivation rules (purchase lifecycle → entitlement):
 *  - PURCHASED / GRACE_PERIOD → PREMIUM (trial flag from the purchase). Grace keeps the driver
 *    entitled while a renewal payment retries.
 *  - PENDING → not yet entitled (FREE) until the payment completes and flips to PURCHASED.
 *  - ON_HOLD / EXPIRED → FREE (account hold suspends entitlement).
 *  - The server's view ([ServerSubscription]), when present, overrides the client guess (it has
 *    verified the token and seen RTDN state the client may not have).
 *
 * Offline: on init the last-known entitlement is loaded from [EntitlementStore] so premium survives
 * a launch with no connectivity; a live Play/backend refresh then overwrites it.
 *
 * Account-linking: [obfuscatedAccountId] is the driver id when signed in, passed into the purchase
 * flow so entitlements are account-linked and survive reinstall + device change.
 *
 * Grant-based premium (B-056): a referral/partner redemption grants premium days on the server with
 * NO Play purchase ([PremiumGrantSource], backed by `premium_until` from GET /v1/me). The repository
 * MERGES the grant into the entitlement — a live grant makes the driver PREMIUM even with no owned
 * purchase, and when both a purchase and a grant are present the later expiry wins. The grant source
 * defaults to [PremiumGrantSource.NONE] so the Play-only flow (and its tests) is unchanged.
 */
@Singleton
class EntitlementRepository @Inject constructor(
    private val billing: BillingClientFacade,
    private val backend: SubscriptionBackend,
    private val store: EntitlementCache,
    private val logger: BillingLogger,
    private val grantSource: PremiumGrantSource = PremiumGrantSource.NONE,
) {
    private val _entitlement = MutableStateFlow<Entitlement>(Entitlement.Free)

    /** Reactive entitlement. Starts FREE, hydrates from last-known, then tracks live purchases. */
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    /** Reactive capability flags for B-050 gating, derived from [entitlement]. */
    val capabilities = _entitlement.map { Capabilities.of(it) }

    /** Snapshot capability flags (for non-Flow call sites). */
    fun capabilitiesNow(): Capabilities = Capabilities.of(_entitlement.value)

    /** Convenience: is the driver currently entitled to premium (incl. trial + grace)? */
    fun isPremium(): Boolean = _entitlement.value is Entitlement.Premium

    /**
     * Hydrate last-known entitlement from disk and start collecting live purchase updates. Call once
     * from app startup with a long-lived [scope]. The [scope]'s lifetime owns the collection.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            // 1) Offline grace: show last-known immediately so premium isn't briefly lost on a cold,
            //    offline launch. A live update below overwrites it once Play/backend answers.
            _entitlement.value = applyGrace(store.read())

            // 2) Track live purchase updates (also (re)connects + queries on first collection).
            //    When Play is unavailable we deliberately do NOT collect updates — an empty update
            //    would clobber the last-known premium we just restored for offline grace.
            when (val conn = billing.ensureConnected()) {
                is BillingConnectionResult.Unavailable -> {
                    logger.w("Play Billing unavailable (${conn.reason}); staying on last-known entitlement")
                    // A backend grant (B-056) can still entitle premium without Play — merge it onto
                    // the last-known so a referral grant unlocks premium even with no billing.
                    updateEntitlement(mergeGrant(_entitlement.value))
                    return@launch
                }
                BillingConnectionResult.Connected -> Unit
            }
            billing.purchaseUpdates.collect { purchases -> onPurchases(purchases) }
        }
    }

    /**
     * Re-query owned purchases (restore). Used after reinstall, sign-in, or from settings. Returns
     * the resulting entitlement.
     */
    suspend fun restore(): Entitlement {
        when (val conn = billing.ensureConnected()) {
            is BillingConnectionResult.Unavailable -> {
                logger.w("restore(): billing unavailable (${conn.reason}); last-known retained")
                return _entitlement.value
            }
            BillingConnectionResult.Connected -> Unit
        }
        onPurchases(billing.queryPurchases())
        return _entitlement.value
    }

    /**
     * Re-fetch the backend grant ([PremiumGrantSource]) and merge it into the current entitlement
     * (B-056). Call after a successful referral redemption so premium unlocks immediately without a
     * Play purchase. No-ops gracefully (keeps the current entitlement) when there is no live grant.
     */
    suspend fun refreshGrant() {
        updateEntitlement(mergeGrant(_entitlement.value))
    }

    /** Process a fresh set of purchases: acknowledge, sync to backend, derive + persist. */
    private suspend fun onPurchases(purchases: List<BillingPurchase>) {
        val premiumPurchase = purchases
            .filter { it.productId == BillingProducts.PREMIUM_SUBSCRIPTION }
            // Prefer the most-entitled purchase if Play returns more than one.
            .maxByOrNull { entitlementRank(it.state) }

        if (premiumPurchase == null) {
            // No owned purchase — a backend grant can still entitle premium (B-056).
            updateEntitlement(mergeGrant(Entitlement.Free))
            return
        }

        // Acknowledge within the flow so Play doesn't auto-refund (only for entitled, owned states).
        if (!premiumPurchase.acknowledged &&
            premiumPurchase.state == PurchaseState.PURCHASED
        ) {
            val ok = runCatching { billing.acknowledgePurchase(premiumPurchase.purchaseToken) }
                .getOrElse { e ->
                    logger.e("acknowledgePurchase failed", e)
                    false
                }
            if (ok) logger.d("Acknowledged purchase ${premiumPurchase.purchaseToken.take(8)}…")
        }

        // Best-effort server sync (records the token; server-side verification = techdebt B-053).
        val server = runCatching { backend.syncPurchase(premiumPurchase) }
            .getOrElse { e ->
                logger.w("Subscription backend sync failed; using client-derived entitlement", e)
                null
            }

        updateEntitlement(mergeGrant(deriveEntitlement(premiumPurchase, server)))
    }

    /**
     * Merge the backend grant ([PremiumGrantSource], B-056) into a Play-derived entitlement.
     *
     *  - A live grant (`premiumUntil > now`) makes the driver PREMIUM even when [playEntitlement] is
     *    FREE (grant-based premium, no Play purchase). The grant is never a trial.
     *  - When both a paid purchase and a grant are present, the LATER expiry wins (a grant stacked on
     *    top of a paid sub shouldn't shorten coverage; a paid sub outlasting a grant keeps its expiry).
     *  - No grant (or the source failed / returned null) leaves [playEntitlement] untouched.
     */
    private suspend fun mergeGrant(playEntitlement: Entitlement): Entitlement {
        val grantUntil = runCatching { grantSource.premiumUntilMillis() }
            .getOrElse { e ->
                logger.w("Premium-grant source failed; using Play-derived entitlement", e)
                null
            }
        if (grantUntil == null || grantUntil <= System.currentTimeMillis()) return playEntitlement

        return when (playEntitlement) {
            is Entitlement.Premium -> {
                val playExpiry = playEntitlement.expiresAt
                // A null Play expiry means "unknown / open-ended" — keep it (don't clamp to the grant).
                val merged = if (playExpiry == null) null else maxOf(playExpiry, grantUntil)
                playEntitlement.copy(expiresAt = merged)
            }
            Entitlement.Free -> Entitlement.Premium(trial = false, expiresAt = grantUntil)
        }
    }

    /** Set, persist, and publish the entitlement. */
    private suspend fun updateEntitlement(entitlement: Entitlement) {
        _entitlement.value = entitlement
        runCatching { store.write(entitlement) }
            .onFailure { logger.w("Persisting last-known entitlement failed", it) }
    }

    private companion object {
        /** Higher rank = more entitled, used to pick the best purchase when several exist. */
        fun entitlementRank(state: PurchaseState): Int = when (state) {
            PurchaseState.PURCHASED -> 4
            PurchaseState.GRACE_PERIOD -> 3
            PurchaseState.PENDING -> 2
            PurchaseState.ON_HOLD -> 1
            PurchaseState.EXPIRED -> 0
        }

        /**
         * Map a purchase (+ optional server view) to an [Entitlement]. The server view wins when
         * present because it has verified the token and may carry RTDN state the client hasn't seen.
         */
        fun deriveEntitlement(
            purchase: BillingPurchase,
            server: ServerSubscription?,
        ): Entitlement {
            if (server != null) {
                return when (server.status) {
                    ServerSubscriptionStatus.ACTIVE,
                    ServerSubscriptionStatus.GRACE,
                    ServerSubscriptionStatus.CANCELED, // canceled but still within paid period
                    -> Entitlement.Premium(trial = server.trial, expiresAt = server.expiresAtMillis)

                    ServerSubscriptionStatus.HOLD,
                    ServerSubscriptionStatus.EXPIRED,
                    -> Entitlement.Free
                }
            }
            return when (purchase.state) {
                PurchaseState.PURCHASED,
                PurchaseState.GRACE_PERIOD,
                -> Entitlement.Premium(trial = purchase.trial, expiresAt = purchase.expiryTimeMillis)

                PurchaseState.PENDING,
                PurchaseState.ON_HOLD,
                PurchaseState.EXPIRED,
                -> Entitlement.Free
            }
        }
    }

    /**
     * Apply the offline grace window to a last-known entitlement read from disk. A persisted premium
     * whose [Entitlement.Premium.expiresAt] is already well in the past is treated as FREE so a
     * long-stale cache doesn't grant premium forever; a null or future expiry is honored.
     */
    private fun applyGrace(stored: Entitlement): Entitlement {
        if (stored !is Entitlement.Premium) return stored
        val expiry = stored.expiresAt ?: return stored
        return if (System.currentTimeMillis() <= expiry + GRACE_WINDOW_MILLIS) {
            stored
        } else {
            logger.d("Last-known premium expired beyond grace window; downgrading to free")
            Entitlement.Free
        }
    }
}

/** Offline grace allowance past the last-known expiry (3 days) before forcing a re-check. */
private const val GRACE_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000
