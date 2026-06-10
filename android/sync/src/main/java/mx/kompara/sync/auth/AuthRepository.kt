package mx.kompara.sync.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.DriverDto
import mx.kompara.sync.api.UpdateProfileBody
import mx.kompara.sync.di.AuthDataStore
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
 */
@Singleton
class AuthRepository @Inject constructor(
    @AuthDataStore private val dataStore: DataStore<Preferences>,
    private val api: ApiClient,
    private val json: Json,
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
        api.requestOtp(phone)
    }

    /**
     * Verify [code] for [phone]. On success the anonymous device id is sent so
     * the backend merges local data into the account; the session token + driver
     * profile are persisted and [sessionState] flips to Authenticated.
     */
    suspend fun verifyOtp(phone: String, code: String): DriverDto {
        val device = deviceId()
        val res = api.verifyOtp(phone = phone, code = code, deviceId = device)
        persistSession(res.token, res.driver)
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
        dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(driverKey)
        }
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

    private companion object {
        const val KEY_DEVICE_ID = "anonymous_device_id"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_DRIVER_JSON = "driver_profile_json"
    }
}
