package mx.kompara.sync.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin typed wrapper over the Kompara backend's HTTP API.
 *
 * - Base URL comes from `BuildConfig.API_BASE_URL` (wired in [ApiModule]).
 * - The bearer token is pulled lazily from a [TokenProvider] per request, so a
 *   single long-lived [HttpClient] transparently uses the current session.
 * - Non-2xx responses raise [ApiException] carrying the HTTP status, letting
 *   callers distinguish "wrong/expired code" (401) from transport failures.
 *
 * JSON (de)serialization is configured on the injected [HttpClient] via the
 * ContentNegotiation plugin (see [ApiModule]); the same client is swapped for a
 * Ktor `MockEngine` in tests.
 */

/** Supplies the current raw session token (or null when logged out). */
fun interface TokenProvider {
    suspend fun currentToken(): String?
}

@Singleton
class ApiClient @Inject constructor(
    private val http: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: TokenProvider,
) {
    /** POST /v1/auth/otp/request — request a WhatsApp OTP. Always succeeds (200). */
    suspend fun requestOtp(phone: String) {
        ensureOk(
            http.post("$baseUrl/v1/auth/otp/request") {
                contentType(ContentType.Application.Json)
                setBody(OtpRequestBody(phone))
            },
        )
    }

    /** POST /v1/auth/otp/verify — verify the code; returns token + driver. */
    suspend fun verifyOtp(phone: String, code: String, deviceId: String?): VerifyResponse {
        val res = http.post("$baseUrl/v1/auth/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody(OtpVerifyBody(phone = phone, code = code, deviceId = deviceId))
        }
        ensureOk(res)
        return res.body()
    }

    /** POST /v1/devices/register — anonymous-first device registration. */
    suspend fun registerDevice(deviceId: String) {
        ensureOk(
            http.post("$baseUrl/v1/devices/register") {
                contentType(ContentType.Application.Json)
                setBody(DeviceRegisterBody(deviceId))
            },
        )
    }

    /** POST /v1/auth/logout — revoke the current session (bearer). */
    suspend fun logout() {
        ensureOk(
            http.post("$baseUrl/v1/auth/logout") {
                bearer()
            },
        )
    }

    /** GET /v1/me — fetch the authenticated driver profile (bearer). */
    suspend fun getMe(): DriverDto {
        val res = http.get("$baseUrl/v1/me") { bearer() }
        ensureOk(res)
        return res.body<MeResponse>().driver
    }

    /** PATCH /v1/me — update profile fields (bearer). */
    suspend fun updateMe(body: UpdateProfileBody): DriverDto {
        val res = http.patch("$baseUrl/v1/me") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureOk(res)
        return res.body<MeResponse>().driver
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.bearer() {
        tokenProvider.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun ensureOk(res: HttpResponse) {
        if (res.status.isSuccess()) return
        val message = runCatching { res.body<OkResponse>().error }.getOrNull()
            ?: res.status.description
        throw ApiException(res.status.value, message)
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
}
