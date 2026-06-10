package mx.kompara.sync.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mx.kompara.sync.api.ApiClient
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the remote paywall kill switch (B-050): fetch `GET /v1/config/app`, cache the flag in a
 * preferences [DataStore], and expose it as a reactive [Flow] the tier gatekeeper observes.
 *
 * **Fails safe to gating ON.** [paywallEnabled] always emits a value: the cached flag, or — when the
 * cache is empty (fresh install / never fetched / offline) — the [DEFAULT_PAYWALL_ENABLED] (TRUE). A
 * fetch failure never clears the cache, so a transport hiccup can never accidentally unlock premium for
 * everyone. Only an explicit `paywallEnabled=false` from the backend flips the app into launch-promo
 * mode (everything unlocked).
 *
 * **Offline-first with a TTL.** [refresh] fetches only when the cache is empty or older than [TTL_MS];
 * within the TTL it's a no-op so a near-static flag isn't polled. A failed fetch is swallowed and the
 * previous cached value (however stale) is retained — mirroring
 * [mx.kompara.sync.fiscal.FiscalConfigRepository].
 */
@Singleton
class PaywallConfigRepository @Inject constructor(
    @PaywallConfigDataStore private val dataStore: DataStore<Preferences>,
    private val api: ApiClient,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Outcome of a [refresh] call, for the worker/caller and tests. */
    enum class RefreshResult {
        /** Fetched fresh config and cached it. */
        FETCHED,

        /** Cache was still within the TTL — no network call made. */
        FRESH_CACHE,

        /** Fetch failed; the previous cache (or the safe default) is retained. */
        FAILED,
    }

    /**
     * The paywall kill switch as a reactive [Flow]. Always non-null: the cached flag, or the safe
     * default (TRUE = gating active) when nothing is cached. The tier gatekeeper binds to this.
     */
    val paywallEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_PAYWALL_ENABLED] ?: DEFAULT_PAYWALL_ENABLED }

    /** Snapshot read (safe default when empty) — for one-shot callers. */
    suspend fun current(): Boolean =
        dataStore.data.first()[KEY_PAYWALL_ENABLED] ?: DEFAULT_PAYWALL_ENABLED

    /**
     * Ensure the kill switch is cached and reasonably fresh. Fetches only when the cache is empty or
     * older than [TTL_MS]; otherwise no-ops. Never throws — a failure returns [RefreshResult.FAILED]
     * and leaves the cache (or the safe default) intact.
     *
     * @param force when true, ignore the TTL and re-fetch.
     */
    suspend fun refresh(force: Boolean = false): RefreshResult {
        val fetchedAt = dataStore.data.first()[KEY_FETCHED_AT]
        if (!force && fetchedAt != null && clock.millis() - fetchedAt < TTL_MS) {
            return RefreshResult.FRESH_CACHE
        }

        return runCatching { api.getAppConfig() }.fold(
            onSuccess = { response ->
                dataStore.edit { prefs ->
                    prefs[KEY_PAYWALL_ENABLED] = response.paywallEnabled
                    prefs[KEY_FETCHED_AT] = clock.millis()
                }
                RefreshResult.FETCHED
            },
            onFailure = { RefreshResult.FAILED },
        )
    }

    companion object {
        /** Fail-safe default: gating is ON unless the backend explicitly says otherwise. */
        const val DEFAULT_PAYWALL_ENABLED = true

        /** 6-hour cache TTL — the flag changes rarely (a promo window), so this is ample. */
        const val TTL_MS: Long = 6L * 60 * 60 * 1000

        private val KEY_PAYWALL_ENABLED = booleanPreferencesKey("paywall_enabled")
        private val KEY_FETCHED_AT = longPreferencesKey("paywall_config_fetched_at")
    }
}
