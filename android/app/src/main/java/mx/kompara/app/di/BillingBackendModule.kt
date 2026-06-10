package mx.kompara.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.billing.BillingPurchase
import mx.kompara.billing.PremiumGrantSource
import mx.kompara.billing.PurchaseState
import mx.kompara.billing.ServerSubscription
import mx.kompara.billing.ServerSubscriptionStatus
import mx.kompara.billing.SubscriptionBackend
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.SubscriptionDto
import mx.kompara.sync.api.SubscriptionSyncBody
import javax.inject.Singleton

/**
 * Bridges `:billing`'s [SubscriptionBackend] seam to `:sync`'s [ApiClient] (B-049).
 *
 * `:billing` declares the backend as an interface so it never depends on the HTTP stack; the app —
 * which depends on both — supplies the real implementation that posts the purchase token to
 * POST /v1/subscriptions/sync and maps the server's reply back to a [ServerSubscription].
 *
 * Best-effort: a non-2xx (e.g. 401 when anonymous) or transport error yields null so the
 * EntitlementRepository falls back to the client-derived entitlement.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingBackendModule {

    @Provides
    @Singleton
    fun provideSubscriptionBackend(api: ApiClient): SubscriptionBackend =
        object : SubscriptionBackend {
            override suspend fun syncPurchase(purchase: BillingPurchase): ServerSubscription? =
                // Best-effort: any failure (401 when anonymous, transport error) degrades to the
                // client-derived entitlement, so we swallow it and return null.
                runCatching { api.syncSubscription(purchase.toSyncBody()).toServerSubscription() }
                    .getOrNull()
        }

    /**
     * Bridges `:billing`'s [PremiumGrantSource] to the grant-based `premium_until` from GET /v1/me
     * (B-056). A referral/partner redemption grants premium days with no Play purchase; the
     * EntitlementRepository merges this expiry to unlock premium. Best-effort: any failure (401 when
     * anonymous, transport error) yields null and the entitlement falls back to the Play-derived state.
     */
    @Provides
    @Singleton
    fun providePremiumGrantSource(api: ApiClient): PremiumGrantSource =
        PremiumGrantSource {
            runCatching { api.getMeFull().premiumUntilMillis }.getOrNull()
        }

    private fun BillingPurchase.toSyncBody(): SubscriptionSyncBody =
        SubscriptionSyncBody(
            purchaseToken = purchaseToken,
            productId = productId,
            status = state.toWireStatus(),
            trial = trial,
            expiresAtMillis = expiryTimeMillis,
        )

    private fun PurchaseState.toWireStatus(): String = when (this) {
        PurchaseState.PURCHASED -> "active"
        PurchaseState.GRACE_PERIOD -> "grace"
        PurchaseState.ON_HOLD -> "hold"
        PurchaseState.PENDING -> "active" // pending payment; server records token, status firms via RTDN
        PurchaseState.EXPIRED -> "expired"
    }

    private fun SubscriptionDto.toServerSubscription(): ServerSubscription =
        ServerSubscription(
            status = when (status.lowercase()) {
                "active" -> ServerSubscriptionStatus.ACTIVE
                "canceled" -> ServerSubscriptionStatus.CANCELED
                "grace" -> ServerSubscriptionStatus.GRACE
                "hold" -> ServerSubscriptionStatus.HOLD
                else -> ServerSubscriptionStatus.EXPIRED
            },
            trial = trial,
            expiresAtMillis = expiresAtMillis,
        )
}
