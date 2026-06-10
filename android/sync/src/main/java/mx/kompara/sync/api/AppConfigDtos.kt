package mx.kompara.sync.api

import kotlinx.serialization.Serializable

/**
 * Wire DTO for the app-config endpoint (B-050). Field names match the backend's response shape exactly
 * (GET /v1/config/app). Today the only field is the paywall kill switch.
 */
@Serializable
data class AppConfigResponse(
    /** true = premium gating active (normal); false = everything unlocked (launch promo). */
    val paywallEnabled: Boolean,
)
