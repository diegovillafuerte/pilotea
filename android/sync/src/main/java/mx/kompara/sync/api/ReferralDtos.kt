package mx.kompara.sync.api

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the referral & partners program (backend B-056).
 *
 * Field names match the backend JSON exactly. The driver's own code + stats come from
 * GET /v1/referrals/mine; a redemption is POST /v1/referrals/redeem. Spanish error strings on a
 * failed redemption arrive as an [ApiException] message (the backend's exact copy).
 */

/** Response of GET /v1/referrals/mine. */
@Serializable
data class ReferralMineResponse(
    val code: String,
    val redemptionsCount: Int = 0,
    val premiumDaysEarned: Int = 0,
    /** Grant-based premium expiry as epoch millis, or null when the driver holds no grant. */
    val premiumUntilMillis: Long? = null,
)

/** Body for POST /v1/referrals/redeem. */
@Serializable
data class ReferralRedeemBody(
    val code: String,
    val deviceId: String,
)

/** Response of POST /v1/referrals/redeem: how many days each side received + the new expiry. */
@Serializable
data class ReferralRedeemResponse(
    val grantedDaysRedeemer: Int = 0,
    val grantedDaysReferrer: Int = 0,
    val premiumUntilMillis: Long? = null,
)
