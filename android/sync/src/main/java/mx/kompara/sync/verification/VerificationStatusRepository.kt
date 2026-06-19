package mx.kompara.sync.verification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.intPreferencesKey
import mx.kompara.sync.api.ApiClient
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Side-channel the import + auth paths use to keep the cached verification flag honest without
 * depending on the whole [VerificationStatusRepository] (account-onboarding design §3). A successful
 * non-dry-run import is itself the verification event, so the import repo marks it immediately
 * (instant unlock, no round-trip); logout/account-delete resets it so account A's verified state never
 * leaks to account B on the same device (a resale vector). Defaults to a no-op so manual test
 * construction stays decoupled; the live impl is [VerificationStatusRepository].
 */
interface VerificationSignals {
    /**
     * Snapshot the session generation BEFORE a verifying action (an import upload) begins, to pass to
     * [markVerified]. The generation bumps on every [reset] / 401-invalidation, so the later mark can
     * tell whether the session changed mid-import and refuse to write a stale positive.
     */
    suspend fun sessionGeneration(): Int

    /**
     * Optimistically mark the driver verified after a successful import — but ONLY if the session is
     * still [generation] (no logout/reset/account-switch happened during the import); otherwise the
     * positive is discarded so it can't leak into another account. Bumps the generation on a write so a
     * concurrent in-flight [refresh] that snapshotted the old one is discarded (won't overwrite this).
     */
    suspend fun markVerified(generation: Int)

    /** Clear cached verification on logout / account delete so it doesn't survive a session change. */
    suspend fun reset()

    /**
     * Re-fetch the new session's verified status from `/v1/me` (force, ignoring the TTL). Called right
     * after a successful sign-in so a verified driver who logs out then back in — or switches to an
     * already-verified account in the same process — rehydrates immediately instead of being stuck at
     * the post-[reset] `false` until the next app restart. Best-effort (swallows failures).
     */
    suspend fun syncFromServer()

    companion object {
        val NONE: VerificationSignals = object : VerificationSignals {
            override suspend fun sessionGeneration() = 0
            override suspend fun markVerified(generation: Int) = Unit
            override suspend fun reset() = Unit
            override suspend fun syncFromServer() = Unit
        }
    }
}

/**
 * Owns the driver's import/data-verification status (account-onboarding design §3): caches the derived
 * `verified` flag from `GET /v1/me` in a preferences [DataStore] and exposes it as the reactive [Flow]
 * the tier gatekeeper's verification source observes. Mirrors
 * [mx.kompara.sync.config.PaywallConfigRepository] in shape.
 *
 * **Seed + fail-safe.** [verified] always emits a value: the cached flag, or [DEFAULT_VERIFIED]
 * (FALSE — fail closed) when nothing is cached. It is backed by the DataStore (a fast local read), so
 * the gatekeeper's `combine` is never stalled on a network round-trip (the contract in
 * `TierGatekeeper.VerificationSource`). A failed [refresh] never clears the cache, so a transport
 * hiccup can't re-lock an already-verified driver mid-session (sticky-positive); only a *successful*
 * `/v1/me` returning `verified=false` (a server-side revocation) downgrades it.
 *
 * **Offline-first with a TTL.** [refresh] fetches only when the cache is empty or older than [TTL_MS].
 * [markVerified] writes TRUE immediately (the just-succeeded import); [reset] clears the cache.
 */
@Singleton
class VerificationStatusRepository @Inject constructor(
    @VerificationDataStore private val dataStore: DataStore<Preferences>,
    private val api: ApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : VerificationSignals {

    /** Outcome of a [refresh] call, for the caller and tests. */
    enum class RefreshResult { FETCHED, FRESH_CACHE, FAILED, STALE }

    /**
     * The verification flag as a reactive [Flow]. Always non-null: the cached flag, or the safe default
     * (FALSE = unverified, fail closed) when nothing is cached. The tier gatekeeper binds to this.
     */
    val verified: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_VERIFIED] ?: DEFAULT_VERIFIED }

    /** Snapshot read (safe default when empty) — for one-shot callers. */
    suspend fun current(): Boolean = dataStore.data.first()[KEY_VERIFIED] ?: DEFAULT_VERIFIED

    /**
     * Ensure the flag is cached and reasonably fresh from `GET /v1/me`. Fetches only when the cache is
     * empty or older than [TTL_MS] (unless [force]). Never throws — a failure returns [RefreshResult.
     * FAILED] and leaves the cache (or the safe default) intact.
     */
    suspend fun refresh(force: Boolean = false): RefreshResult {
        val before = dataStore.data.first()
        val fetchedAt = before[KEY_FETCHED_AT]
        if (!force && fetchedAt != null && clock.millis() - fetchedAt < TTL_MS) {
            return RefreshResult.FRESH_CACHE
        }
        // Snapshot the session generation before the network call; a reset/401-invalidation (logout or
        // account switch) bumps it, and we discard this fetch's result if it changed meanwhile.
        val gen = before[KEY_GENERATION] ?: 0
        return runCatching { api.getMeFull().verified }.fold(
            onSuccess = { isVerified ->
                var wrote = false
                dataStore.edit { prefs ->
                    // Re-check the generation INSIDE the atomic edit (no TOCTOU): if a reset raced this
                    // fetch, leave the cleared cache alone so a previous account's status can't leak.
                    if ((prefs[KEY_GENERATION] ?: 0) == gen) {
                        prefs[KEY_VERIFIED] = isVerified
                        prefs[KEY_FETCHED_AT] = clock.millis()
                        wrote = true
                    }
                }
                if (wrote) RefreshResult.FETCHED else RefreshResult.STALE
            },
            onFailure = { RefreshResult.FAILED },
        )
    }

    /** Read the current session generation (snapshot it before an import to pass to [markVerified]). */
    override suspend fun sessionGeneration(): Int = dataStore.data.first()[KEY_GENERATION] ?: 0

    /**
     * A successful non-dry-run import is the verification event — cache TRUE now (instant unlock), but
     * only if [generation] still matches (no logout/reset during the import; else the positive is for a
     * session that's gone, so discard it). On a write, bumps the generation so a routine app-open
     * [refresh] that started before the import (and could return a pre-import `false`) is discarded
     * instead of overwriting this proof. Pins the fetch timestamp too, so a server-side revocation
     * reconciles only on the next out-of-TTL refresh (intentional optimistic window — the import just
     * proved verification).
     */
    override suspend fun markVerified(generation: Int) {
        dataStore.edit { prefs ->
            if ((prefs[KEY_GENERATION] ?: 0) != generation) return@edit
            prefs[KEY_VERIFIED] = true
            prefs[KEY_FETCHED_AT] = clock.millis()
            prefs[KEY_GENERATION] = generation + 1
        }
    }

    /** Logout / account delete: drop the cached flag so it can't survive into another account's session. */
    override suspend fun reset() {
        clearVerification(dataStore)
    }

    /** Sign-in: pull the new account's verified status now so the gate reflects it without a restart. */
    override suspend fun syncFromServer() {
        refresh(force = true)
    }

    companion object {
        /** Fail-safe default: unverified unless the server (or a local import) says otherwise. */
        const val DEFAULT_VERIFIED = false

        /** 6-hour cache TTL — verification changes rarely (an import or a revocation), so this is ample. */
        const val TTL_MS: Long = 6L * 60 * 60 * 1000

        // internal (not private) so the 401 SessionInvalidator can clear the cache directly without
        // depending on this repository — ApiClient depends on the invalidator, and this repo depends on
        // ApiClient, so injecting the repo there would be a Hilt cycle (see ApiModule).
        internal val KEY_VERIFIED = booleanPreferencesKey("driver_verified")
        internal val KEY_FETCHED_AT = longPreferencesKey("driver_verified_fetched_at")

        // Monotonic session generation; bumped on every clear so a concurrent in-flight refresh can
        // detect it raced a reset/invalidation and discard its write. PERSISTED (not in-memory) so the
        // logout path (repo.reset) AND the 401 path (the SessionInvalidator, which clears the store
        // directly to stay acyclic) coordinate through the one store both already touch.
        internal val KEY_GENERATION = intPreferencesKey("driver_verified_generation")

        /**
         * Atomically clear the verification cache and bump the session generation. Shared by [reset] and
         * the 401 [mx.kompara.sync.api.SessionInvalidator] (in ApiModule) so both invalidation paths
         * invalidate any racing refresh identically.
         */
        internal suspend fun clearVerification(dataStore: DataStore<Preferences>) {
            dataStore.edit { prefs ->
                prefs[KEY_GENERATION] = (prefs[KEY_GENERATION] ?: 0) + 1
                prefs.remove(KEY_VERIFIED)
                prefs.remove(KEY_FETCHED_AT)
            }
        }
    }
}
