package mx.kompara.sync.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import mx.kompara.sync.BuildConfig
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.DriverDto
import mx.kompara.sync.api.UpdateProfileBody
import mx.kompara.sync.di.AuthDataStore
import mx.kompara.sync.di.DevAuthBypass
import mx.kompara.sync.verification.VerificationSignals
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the driver's authentication lifecycle and the anonymous-first identity.
 *
 * Responsibilities:
 *  - Generate + persist a stable anonymous device UUID on first run (so the
 *    reader works with zero signup and local data can later merge into an
 *    account). The id never changes for an install.
 *  - Drive the WhatsApp OTP flow ([requestOtp] / [verifyOtp]) and persist the
 *    session token + cached driver profile.
 *  - Expose [sessionState] as a [Flow] for UI to react to (Unknown → Anonymous
 *    → Authenticated).
 *  - [logout] revokes the server session and clears local auth (the device id
 *    is intentionally retained so the install identity is stable).
 *
 * The session token is persisted in plaintext DataStore for now — hardening to
 * the Android Keystore / EncryptedSharedPreferences is tracked in techdebt.
 *
 * Debug builds enable an offline login bypass ([devBypassEnabled]): entering the
 * fixed [DEV_OTP_CODE] mints a local-only session with no backend call, so the
 * reader can be tested on-device without standing up the backend or an approved
 * Twilio sender (TD-022). The flag is wired to `BuildConfig.DEBUG`, so release
 * builds always take the real OTP path.
 */
@Singleton
class AuthRepository @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
    private val api: ApiClient,
    private val json: Json,
    @DevAuthBypass private val devBypassEnabled: Boolean = BuildConfig.DEBUG,
    private val verification: VerificationSignals = VerificationSignals.NONE,
) {
    private val deviceIdKey = stringPreferencesKey(KEY_DEVICE_ID)
    private val tokenKey = stringPreferencesKey(KEY_SESSION_TOKEN)
    private val driverKey = stringPreferencesKey(KEY_DRIVER_JSON)

    /**
     * Reactive auth state. Emits [SessionState.Authenticated] when a token and a
     * cached driver are present, otherwise [SessionState.Anonymous]. (The
     * [SessionState.Unknown] state is reserved for callers that want to model
     * the pre-load moment explicitly; the flow itself resolves eagerly.)
     */
    val sessionState: Flow<SessionState> = dataStore.data.map { prefs ->
        val token = prefs[tokenKey]
        val driverJson = prefs[driverKey]
        if (token != null && driverJson != null) {
            SessionState.Authenticated(json.decodeFromString(DriverDto.serializer(), driverJson))
        } else {
            SessionState.Anonymous
        }
    }

    /** Current raw session token, or null when anonymous. Used by [ApiClient]. */
    suspend fun currentToken(): String? = dataStore.data.first()[tokenKey]

    /**
     * The fixed dev OTP code that completes signup offline, or null when the bypass is disabled
     * (release builds). The signup UI surfaces this as a hint on the code screen. See [verifyOtp].
     */
    val devBypassCode: String? get() = if (devBypassEnabled) DEV_OTP_CODE else null

    /**
     * The stable anonymous device id for this install, generated + persisted on
     * first call. Subsequent calls return the same value. Also (re)registers the
     * device with the backend so anonymous telemetry/config can be associated.
     */
    suspend fun deviceId(): String {
        val existing = dataStore.data.first()[deviceIdKey]
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        dataStore.edit { it[deviceIdKey] = generated }
        // Best-effort anonymous registration; failures must not block first run.
        runCatching { api.registerDevice(generated) }
        return generated
    }

    /** Request a WhatsApp OTP for [phone]. Backend always returns 200. */
    suspend fun requestOtp(phone: String) {
        if (devBypassEnabled) {
            // Debug builds may run with no reachable backend (no local server / `adb reverse`,
            // TD-022). Still attempt the real request so a running backend logs the real code,
            // but never fail — the dev advances to the code screen and completes signup offline
            // with the fixed dev code ([DEV_OTP_CODE]).
            runCatching { api.requestOtp(phone) }
            return
        }
        api.requestOtp(phone)
    }

    /**
     * Verify [code] for [phone]. On success the anonymous device id is sent so
     * the backend merges local data into the account; the session token + driver
     * profile are persisted and [sessionState] flips to Authenticated.
     *
     * In debug builds, the fixed [DEV_OTP_CODE] short-circuits to a local-only session with no
     * backend call, so on-device testing needs no infra (TD-022). Any bearer call that local
     * token later makes fails just as it would with no session — and a real 401 clears it via the
     * SessionInvalidator. Release builds never enter this branch ([devBypassEnabled] is false).
     */
    suspend fun verifyOtp(phone: String, code: String): DriverDto {
        if (devBypassEnabled && code == DEV_OTP_CODE) {
            val driver = devDriver(phone)
            persistSession(DEV_SESSION_TOKEN, driver)
            return driver
        }
        val device = deviceId()
        val res = api.verifyOtp(phone = phone, code = code, deviceId = device)
        persistSession(res.token, res.driver)
        // Rehydrate verification for the now-signed-in account so a verified driver who logs back in
        // (or switches to an already-verified account in this process) isn't stuck at the post-reset
        // `false` until the next app restart. Best-effort — the seam swallows a failed /v1/me.
        verification.syncFromServer()
        return res.driver
    }

    /** Refresh and re-cache the driver profile from /v1/me (requires a session). */
    suspend fun refreshProfile(): DriverDto {
        val driver = api.getMe()
        cacheDriver(driver)
        return driver
    }

    /** Update profile fields via PATCH /v1/me and re-cache the result. */
    suspend fun updateProfile(
        name: String? = null,
        city: String? = null,
        platforms: List<String>? = null,
    ): DriverDto {
        val driver = api.updateMe(UpdateProfileBody(name = name, city = city, platforms = platforms))
        cacheDriver(driver)
        return driver
    }

    /**
     * Revoke the server session (best-effort) and clear local auth. The device
     * id is retained so the anonymous install identity stays stable.
     */
    suspend fun logout() {
        runCatching { api.logout() }
        clearLocalAuth()
    }

    /**
     * Permanently delete the driver account server-side (DELETE /v1/me; Play data-safety, B-069), then
     * clear local auth so the root gate returns to signup. The delete cascades the session + all
     * consented data server-side; the device id is retained so the (now-anonymous) reader keeps working.
     * Rethrows on failure so the UI can surface an error instead of pretending the account is gone.
     */
    suspend fun deleteAccount() {
        api.deleteMe()
        clearLocalAuth()
    }

    private suspend fun clearLocalAuth() {
        dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(driverKey)
        }
        // Reset cached import/data verification so it can't survive into another account's session on
        // this device (a resale vector; account-onboarding design §0.5). Re-proven on the next import.
        verification.reset()
    }

    private suspend fun persistSession(token: String, driver: DriverDto) {
        dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[driverKey] = json.encodeToString(DriverDto.serializer(), driver)
        }
    }

    private suspend fun cacheDriver(driver: DriverDto) {
        dataStore.edit { prefs ->
            prefs[driverKey] = json.encodeToString(DriverDto.serializer(), driver)
        }
    }

    /** Synthetic driver for the debug-only offline login bypass. See [verifyOtp]. */
    private fun devDriver(phone: String) = DriverDto(
        id = DEV_DRIVER_ID,
        phone = phone,
        name = null,
        city = null,
        platforms = null,
        tier = "free",
    )

    private companion object {
        const val KEY_DEVICE_ID = "anonymous_device_id"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_DRIVER_JSON = "driver_profile_json"

        /** Fixed code that triggers the debug-only offline login bypass. See [verifyOtp]. */
        const val DEV_OTP_CODE = "000000"
        const val DEV_SESSION_TOKEN = "dev-bypass-session"
        const val DEV_DRIVER_ID = "dev-driver"
    }
}
