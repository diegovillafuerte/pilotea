package mx.kompara.billing

/**
 * Seam to the Kompara backend's subscription endpoints (POST /v1/subscriptions/sync). The app wires
 * the real implementation on top of `:sync`'s ApiClient; [EntitlementRepository] depends only on
 * this interface so it stays free of the HTTP stack and testable with a fake.
 *
 * Keeping this in `:billing` (rather than extending the shared `:sync` ApiClient here) avoids
 * touching files that sibling tasks edit.
 */
interface SubscriptionBackend {
    /**
     * Push a purchase token + client-observed state to the server after a purchase or restore. The
     * server records it and (eventually) verifies it against the Play Developer API. Returns the
     * server's acknowledged view of the subscription, or null when offline / not signed in.
     *
     * Best-effort: callers must tolerate a null result and fall back to the client-derived
     * entitlement + last-known DataStore value.
     */
    suspend fun syncPurchase(purchase: BillingPurchase): ServerSubscription?

    companion object {
        /** No backend wired (anonymous / offline / tests that only exercise client derivation). */
        val NONE: SubscriptionBackend = object : SubscriptionBackend {
            override suspend fun syncPurchase(purchase: BillingPurchase): ServerSubscription? = null
        }
    }
}

/** The server's view of a subscription after sync, used to confirm/override the client guess. */
data class ServerSubscription(
    val status: ServerSubscriptionStatus,
    val trial: Boolean,
    val expiresAtMillis: Long?,
)

/** Mirrors the backend `subscriptions.status` enum. */
enum class ServerSubscriptionStatus {
    ACTIVE,
    CANCELED,
    GRACE,
    HOLD,
    EXPIRED,
}
