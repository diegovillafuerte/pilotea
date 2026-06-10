package mx.kompara.sync.api

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the subscription sync endpoint (backend B-049: POST /v1/subscriptions/sync).
 *
 * The client posts the Play purchase token + its client-observed state after a purchase or restore;
 * the server records it and returns its acknowledged view (status + trial + expiry). Field names
 * match the backend JSON exactly.
 */

/** Body for POST /v1/subscriptions/sync. */
@Serializable
data class SubscriptionSyncBody(
    val purchaseToken: String,
    val productId: String,
    /** active | canceled | grace | hold | expired (client's best guess; server may correct it). */
    val status: String,
    val trial: Boolean,
    /** Period end as epoch millis, when known. */
    val expiresAtMillis: Long? = null,
)

/** The server's acknowledged subscription state. */
@Serializable
data class SubscriptionDto(
    val status: String,
    val trial: Boolean,
    val expiresAtMillis: Long? = null,
)

/** Response of POST /v1/subscriptions/sync. */
@Serializable
data class SubscriptionSyncResponse(val subscription: SubscriptionDto)
