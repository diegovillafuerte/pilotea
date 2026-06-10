package mx.kompara.sync.fiscal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.kompara.data.db.dao.FiscalConfigDao
import mx.kompara.data.db.entity.FiscalConfigEntity
import mx.kompara.data.settings.FiscalDefaults
import mx.kompara.sync.api.ApiClient
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns IMSS fiscal-config delivery (B-051): fetch `GET /v1/config/fiscal`, cache it in Room
 * ([FiscalConfigEntity]), and expose the resolved [FiscalConfig] as a reactive [Flow] the Fiscal tab
 * observes.
 *
 * **Never blocks the UI.** [observe] always emits a value: the latest cached row mapped to a
 * [FiscalConfig], or — when the cache is empty (never fetched / fresh install offline) — the
 * compile-time [FiscalDefaults]. The Fiscal tab can therefore render the threshold tracker the
 * instant it opens, before any network call resolves.
 *
 * **Offline-first with a TTL + last-known-good.** [refresh] fetches only when the cache is older than
 * [TTL_MS] (or empty); within the TTL it's a no-op so we don't hammer a near-static endpoint. A
 * failed fetch is swallowed — the previously-cached values (however stale) remain, mirroring
 * [mx.kompara.sync.aggregate.BenchmarksRepository]. The values change at most yearly, so even a
 * months-stale cache is correct until CONASAMI publishes the next minimum wage.
 */
@Singleton
class FiscalConfigRepository @Inject constructor(
    private val dao: FiscalConfigDao,
    private val api: ApiClient,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Outcome of a [refresh] call, for the worker/caller and tests. */
    enum class RefreshResult {
        /** Fetched fresh config from the backend and cached it. */
        FETCHED,

        /** Cache was still within the TTL — no network call made. */
        FRESH_CACHE,

        /** Fetch failed; the previous cache (if any) — or the default — is retained. */
        FAILED,
    }

    /**
     * The resolved fiscal config as a reactive [Flow]. Always non-null: the cached latest row, or the
     * compile-time default when nothing is cached yet. The UI binds directly to this.
     */
    fun observe(): Flow<FiscalConfig> =
        dao.observeLatest().map { row -> row?.toDomain() ?: FiscalConfig.DEFAULT }

    /** Snapshot resolve (default when empty) — for callers that need a one-shot value (e.g. a worker). */
    suspend fun current(): FiscalConfig = dao.latest()?.toDomain() ?: FiscalConfig.DEFAULT

    /**
     * Ensure the fiscal config is cached and reasonably fresh. Fetches only when the cache is empty or
     * older than [TTL_MS]; otherwise no-ops. Never throws — a fetch failure (incl. a 404 when nothing
     * is seeded) returns [RefreshResult.FAILED] and leaves the cache (or the default) intact.
     *
     * @param force when true, ignore the TTL and re-fetch.
     */
    suspend fun refresh(force: Boolean = false): RefreshResult {
        val cached = dao.latest()
        if (!force && cached != null && clock.millis() - cached.fetchedAt < TTL_MS) {
            return RefreshResult.FRESH_CACHE
        }

        return runCatching { api.getFiscalConfig() }.fold(
            onSuccess = { response ->
                dao.upsert(
                    FiscalConfigEntity(
                        year = response.year,
                        minimumWageDailyMxn = response.minimumWageDailyMxn,
                        imssMonthlyThresholdMxn = response.imssMonthlyThresholdMxn,
                        updatedAt = parseInstantMs(response.updatedAt),
                        fetchedAt = clock.millis(),
                    ),
                )
                RefreshResult.FETCHED
            },
            onFailure = { RefreshResult.FAILED },
        )
    }

    private fun FiscalConfigEntity.toDomain(): FiscalConfig = FiscalConfig(
        year = year,
        minimumWageDailyMxn = minimumWageDailyMxn,
        imssMonthlyThresholdMxn = imssMonthlyThresholdMxn,
        isDefault = false,
    )

    /** Parse an ISO-8601 timestamp to epoch millis; 0 on a malformed string (display-only field). */
    private fun parseInstantMs(iso: String): Long =
        runCatching { Instant.from(DateTimeFormatter.ISO_INSTANT.parse(iso)).toEpochMilli() }
            .getOrElse { runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L) }

    companion object {
        /** 24-hour cache TTL — the values change at most yearly, so daily freshness is ample. */
        const val TTL_MS: Long = 24L * 60 * 60 * 1000
    }
}

/**
 * Resolved IMSS fiscal config the UI/calculator consume. [isDefault] flags that this came from the
 * compile-time fallback (no successful fetch yet), so the UI can hint the figures are provisional.
 */
data class FiscalConfig(
    val year: Int,
    val minimumWageDailyMxn: Double,
    val imssMonthlyThresholdMxn: Double,
    val isDefault: Boolean,
) {
    companion object {
        /** The compile-time fallback — see [FiscalDefaults]. */
        val DEFAULT = FiscalConfig(
            year = FiscalDefaults.DEFAULT_YEAR,
            minimumWageDailyMxn = FiscalDefaults.DEFAULT_MINIMUM_WAGE_DAILY_MXN,
            imssMonthlyThresholdMxn = FiscalDefaults.DEFAULT_IMSS_MONTHLY_THRESHOLD_MXN,
            isDefault = true,
        )
    }
}
