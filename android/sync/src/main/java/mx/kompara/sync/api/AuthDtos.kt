package mx.kompara.sync.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the WhatsApp OTP auth + profile endpoints (backend B-042).
 *
 * Field names match the backend's JSON exactly; kept separate from any domain
 * model so the transport contract can evolve independently of app state.
 */

@Serializable
data class OtpRequestBody(val phone: String)

@Serializable
data class OtpVerifyBody(
    val phone: String,
    val code: String,
    val deviceId: String? = null,
)

@Serializable
data class DeviceRegisterBody(val deviceId: String)

/** Driver profile as returned by /v1/auth/otp/verify and /v1/me. */
@Serializable
data class DriverDto(
    val id: String,
    val phone: String,
    val name: String? = null,
    val city: String? = null,
    val platforms: List<String>? = null,
    val tier: String,
)

/** Response of /v1/auth/otp/verify: raw session token + driver profile. */
@Serializable
data class VerifyResponse(
    val token: String,
    val driver: DriverDto,
)

/** Response of GET /v1/me and PATCH /v1/me. */
@Serializable
data class MeResponse(
    val driver: DriverDto,
    /**
     * Grant-based premium expiry as epoch millis, or null when the driver holds no referral/partner
     * grant (B-056). The billing layer merges this into the [mx.kompara.billing.Entitlement] so a
     * grant unlocks premium without a Play purchase.
     */
    val premiumUntilMillis: Long? = null,
    /**
     * Import/data verification (derived server-side from the driver's imports; account-onboarding
     * design §3). Gates the population-dependent paid surfaces (benchmarks/compare). Defaults to false
     * so an older/missing field fails closed (unverified). Cached client-side by
     * [mx.kompara.sync.verification.VerificationStatusRepository].
     */
    val verified: Boolean = false,
)

/** Body for PATCH /v1/me. Null fields are omitted server-side as no-ops. */
@Serializable
data class UpdateProfileBody(
    val name: String? = null,
    val city: String? = null,
    val platforms: List<String>? = null,
)

/** Generic { "ok": true } / { "error": "..." } envelope. */
@Serializable
data class OkResponse(
    val ok: Boolean = false,
    val error: String? = null,
)

/**
 * Error from a non-2xx response. Carries the HTTP status so the repository can
 * distinguish "wrong code" (401) from transport failures.
 */
class ApiException(
    val status: Int,
    @SerialName("error") override val message: String,
) : Exception(message)
