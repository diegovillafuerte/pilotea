package mx.kompara.sync.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mx.kompara.parsers.spec.SignedSpecBundle
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

/** Supplies the stable anonymous device id for device-authed endpoints (B-034 fixture reports). */
fun interface DeviceIdProvider {
    suspend fun deviceId(): String
}

@Singleton
class ApiClient @Inject constructor(
    private val http: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: TokenProvider,
    private val deviceIdProvider: DeviceIdProvider,
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

    /**
     * GET /v1/me — the full profile envelope incl. the grant-based `premiumUntilMillis` (B-056).
     * Used by the billing layer to merge a referral/partner grant into the entitlement (premium with
     * no Play purchase). Bearer-authed; a signed-out call yields a 401 [ApiException].
     */
    suspend fun getMeFull(): MeResponse {
        val res = http.get("$baseUrl/v1/me") { bearer() }
        ensureOk(res)
        return res.body()
    }

    /**
     * GET /v1/referrals/mine — the driver's own referral code + stats (B-056). The backend auto-mints
     * the code on first call. Bearer-authed; a signed-out call yields a 401 [ApiException].
     */
    suspend fun getReferralMine(): ReferralMineResponse {
        val res = http.get("$baseUrl/v1/referrals/mine") { bearer() }
        ensureOk(res)
        return res.body()
    }

    /**
     * POST /v1/referrals/redeem — redeem a referral/partner code (B-056). Bearer-authed. A validation
     * failure (unknown code, self-referral, already redeemed, account too old, device reused) surfaces
     * as an [ApiException] carrying the backend's exact Spanish error string.
     */
    suspend fun redeemReferral(body: ReferralRedeemBody): ReferralRedeemResponse {
        val res = http.post("$baseUrl/v1/referrals/redeem") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureOk(res)
        return res.body()
    }

    /**
     * GET /v1/parser-configs/bundle — the active signed parser-config bundle (B-033).
     *
     * Anonymous-allowed: the bundle is the same for every device, so this works pre-login (a bearer
     * is attached when present but isn't required). The response is the signed envelope; the caller
     * ([mx.kompara.sync.spec.SpecConfigRepository]) verifies the signature before trusting it.
     *
     * [packageName]/[versionCode] are sent as advisory hints so a future backend can pre-filter by
     * host version; today the backend returns the full bundle and the client resolves ranges locally.
     */
    suspend fun getParserConfigBundle(
        packageName: String? = null,
        versionCode: Long? = null,
    ): SignedSpecBundle {
        val res = http.get("$baseUrl/v1/parser-configs/bundle") {
            bearer()
            packageName?.let { parameter("package", it) }
            versionCode?.let { parameter("versionCode", it.toString()) }
        }
        ensureOk(res)
        return res.body()
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

    /**
     * POST /v1/subscriptions/sync — record a Play purchase token + client-observed state after a
     * purchase or restore (bearer / session-authed). Returns the server's acknowledged view of the
     * subscription. Real server-side verification against the Play Developer API is a launch blocker
     * tracked in techdebt (B-053); today the server trusts the client token.
     */
    suspend fun syncSubscription(body: SubscriptionSyncBody): SubscriptionDto {
        val res = http.post("$baseUrl/v1/subscriptions/sync") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureOk(res)
        return res.body<SubscriptionSyncResponse>().subscription
    }

    /**
     * POST /v1/telemetry — push one accumulated parse-health counter bucket
     * (B-034). Anonymous (no auth); the body is integer counters + host/spec
     * identifiers only — never screen content.
     */
    suspend fun uploadTelemetryCounter(body: TelemetryCounterBody) {
        ensureOk(
            http.post("$baseUrl/v1/telemetry") {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )
    }

    /**
     * POST /v1/fixture-reports — device-authed, consented submission of a
     * PII-scrubbed snapshot the parser couldn't read (B-034). Sends the stable
     * anonymous device id in `X-Device-Id`.
     */
    suspend fun uploadFixtureReport(body: FixtureReportBody) {
        ensureOk(
            http.post("$baseUrl/v1/fixture-reports") {
                header(HEADER_DEVICE_ID, deviceIdProvider.deviceId())
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )
    }

    /**
     * POST /v1/aggregates — upsert one consented weekly aggregate (B-043). Bearer-authed: the
     * backend derives the driver id from the session, never the body. The payload is ONLY derived
     * aggregate fields ([AggregateUploadBody]) — no offer/trip/raw data. Requires a session; sending
     * without one yields a 401 [ApiException] the caller treats as a permanent failure.
     */
    suspend fun pushAggregate(body: AggregateUploadBody) {
        ensureOk(
            http.post("$baseUrl/v1/aggregates") {
                bearer()
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )
    }

    /**
     * POST /v1/imports — upload a weekly summary (PDF/screenshot) for Claude Vision parsing (B-044),
     * driving B-045's import flow. Bearer-authed: the backend derives the driver id from the session,
     * so a signed-out call yields a 401 [ApiException] the caller treats as "necesitas una cuenta".
     *
     * Multipart body: `platform` (uber|didi|indrive), `upload_type` (pdf|screenshot), and one or more
     * `files` parts (DiDi sends 2). Each part carries its filename + Content-Type so the backend's
     * MIME validation matches the on-device pick.
     *
     * When [dryRun] is true the request adds `?dry_run=true`: the backend fully parses but persists
     * nothing, returning the metrics for the review screen (`import_id == null`, `dry_run == true`).
     * A confirmed import ([dryRun] false) persists and returns a non-null id. Both shapes deserialize
     * to [ImportResponse]; a parse/validation failure surfaces as an [ApiException] carrying the exact
     * Spanish error string from the backend.
     */
    suspend fun importWeek(
        platform: String,
        uploadType: String,
        files: List<ImportFile>,
        dryRun: Boolean = false,
    ): ImportResponse {
        val parts = formData {
            append("platform", platform)
            append("upload_type", uploadType)
            for (file in files) {
                append(
                    key = "files",
                    value = file.bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, file.mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName}\"")
                    },
                )
            }
        }
        val res = http.post("$baseUrl/v1/imports") {
            bearer()
            if (dryRun) parameter("dry_run", "true")
            setBody(MultiPartFormDataContent(parts))
        }
        ensureOk(res)
        return res.body()
    }

    /**
     * GET /v1/benchmarks?city=&platform=&period= — the population percentile breakpoints for a
     * city × platform (B-043). Anonymous-allowed (no auth needed); a bearer is attached when present
     * but isn't required, so benchmarks work pre-login. [period] defaults to "current".
     */
    suspend fun getBenchmarks(
        city: String,
        platform: String,
        period: String = "current",
    ): BenchmarksResponse {
        val res = http.get("$baseUrl/v1/benchmarks") {
            bearer()
            parameter("city", city)
            parameter("platform", platform)
            parameter("period", period)
        }
        ensureOk(res)
        return res.body()
    }

    /**
     * GET /v1/config/fiscal — the latest year's IMSS-threshold values (B-051). Public/anonymous: the
     * figures are the same for every device, so no auth is required (a bearer is attached when present
     * but ignored by the backend). A 404 (no config seeded) surfaces as an [ApiException] the caller
     * treats as "use the bundled default".
     */
    suspend fun getFiscalConfig(): FiscalConfigResponse {
        val res = http.get("$baseUrl/v1/config/fiscal") { bearer() }
        ensureOk(res)
        return res.body()
    }

    /**
     * GET /v1/config/app — app-wide runtime flags, today just the paywall kill switch (B-050).
     * Public/anonymous: the same value for every device, so no auth is required (a bearer is attached
     * when present but ignored). The caller ([mx.kompara.sync.config.PaywallConfigRepository]) caches the
     * result and falls back to gating-ON when this is unreachable, so a transport failure never unlocks
     * premium.
     */
    suspend fun getAppConfig(): AppConfigResponse {
        val res = http.get("$baseUrl/v1/config/app") { bearer() }
        ensureOk(res)
        return res.body()
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

    private companion object {
        const val HEADER_DEVICE_ID = "X-Device-Id"
    }
}
