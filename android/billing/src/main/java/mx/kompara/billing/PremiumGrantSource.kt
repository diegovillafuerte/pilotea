package mx.kompara.billing

/**
 * Seam to the backend's grant-based premium expiry (B-056: `premium_until` from GET /v1/me).
 *
 * A referral or partner-code redemption grants premium days on the server with NO Play purchase, so
 * the entitlement can be PREMIUM from a grant alone. [EntitlementRepository] depends only on this
 * interface so it stays free of the HTTP stack and testable with a fake; the `:app` module supplies
 * the real implementation on top of `:sync`'s ApiClient (kept out of `:billing` to avoid coupling and
 * editing shared `:sync` files, mirroring [SubscriptionBackend]).
 */
fun interface PremiumGrantSource {
    /**
     * The driver's grant-based premium expiry as epoch millis, or null when there is no live grant
     * (or the call failed / the driver is signed out). Best-effort: callers must tolerate null and
     * fall back to the Play-purchase-derived entitlement.
     */
    suspend fun premiumUntilMillis(): Long?

    companion object {
        /** No grant source wired (anonymous / offline / tests that only exercise Play purchases). */
        val NONE: PremiumGrantSource = PremiumGrantSource { null }
    }
}
