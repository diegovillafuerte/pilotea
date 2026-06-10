package mx.kompara.sync.referral

import mx.kompara.sync.aggregate.SessionGate
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.DeviceIdProvider
import mx.kompara.sync.api.ReferralMineResponse
import mx.kompara.sync.api.ReferralRedeemBody
import mx.kompara.sync.api.ReferralRedeemResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The data-side contract the B-056 "Invita y gana" flow's ViewModel ([mx.kompara.ui.referral.
 * ReferralViewModel], in `:ui`) depends on. A seam (not the concrete [ReferralRepository]) so the
 * ViewModel is unit-testable in `:ui` with a plain fake — no Ktor/MockEngine on the `:ui` test
 * classpath. The Hilt binding ([ReferralModule]) provides [ReferralRepository] as the live impl.
 */
interface Referrals {
    /** True when a session token is held — the referral surfaces require an account. */
    suspend fun isSignedIn(): Boolean

    /** Fetch the driver's own code + stats (auto-created server-side on first call). */
    suspend fun getMine(): ReferralResult<ReferralMineResponse>

    /** Redeem [code]; the device id is supplied automatically. Maps failures to a Spanish message. */
    suspend fun redeem(code: String): ReferralResult<ReferralRedeemResponse>
}

/**
 * A referral call outcome. [Success] carries the payload; [Failure] carries a ready-to-show Spanish
 * [message] (the backend's exact copy when it answered, or a generic transport message) plus the HTTP
 * [status] when known (null for a transport error) so the UI can decide retryability.
 */
sealed interface ReferralResult<out T> {
    data class Success<T>(val value: T) : ReferralResult<T>
    data class Failure(val message: String, val status: Int?) : ReferralResult<Nothing>
}

/**
 * Drives the B-056 referral program on the data side: reads the driver's code/stats and redeems a
 * code, mapping every failure to a user-facing Spanish message.
 *
 * The redemption's `deviceId` is the stable anonymous install id (the same one used for OTP merge and
 * device-authed endpoints) — the backend uses it as a fraud heuristic (one redemption per device).
 *
 * Error mapping: a [ApiException] (non-2xx) surfaces its message — for redemption these are the
 * backend's exact Spanish strings (unknown code, self-referral, already redeemed, account too old,
 * device reused). Any other failure (transport) maps to a generic retryable Spanish message.
 */
@Singleton
class ReferralRepository @Inject constructor(
    private val api: ApiClient,
    private val session: SessionGate,
    private val deviceIdProvider: DeviceIdProvider,
) : Referrals {

    override suspend fun isSignedIn(): Boolean = session.isSignedIn()

    override suspend fun getMine(): ReferralResult<ReferralMineResponse> =
        runCall { api.getReferralMine() }

    override suspend fun redeem(code: String): ReferralResult<ReferralRedeemResponse> {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return ReferralResult.Failure(EMPTY_CODE_MESSAGE, status = null)
        }
        val deviceId = deviceIdProvider.deviceId()
        return runCall { api.redeemReferral(ReferralRedeemBody(code = trimmed, deviceId = deviceId)) }
    }

    private suspend fun <T> runCall(block: suspend () -> T): ReferralResult<T> = try {
        ReferralResult.Success(block())
    } catch (e: ApiException) {
        // The backend's Spanish copy (or its status description) is exactly what to show.
        ReferralResult.Failure(message = e.message, status = e.status)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        ReferralResult.Failure(message = TRANSPORT_MESSAGE, status = null)
    }

    private companion object {
        const val EMPTY_CODE_MESSAGE = "Escribe un código para canjear."
        const val TRANSPORT_MESSAGE =
            "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."
    }
}
