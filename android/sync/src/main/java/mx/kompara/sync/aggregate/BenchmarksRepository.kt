package mx.kompara.sync.aggregate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.kompara.data.db.dao.PopulationStatDao
import mx.kompara.data.db.entity.PopulationStatEntity
import mx.kompara.data.model.City
import mx.kompara.data.model.PopulationStat
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.PopulationStatDto
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns population-benchmark delivery (B-043): fetch `GET /v1/benchmarks?city=&platform=` for the
 * driver's city, cache it in Room ([PopulationStatEntity]), and expose the cached breakpoints as a
 * reactive [Flow] the percentile feature (B-046) observes.
 *
 * **Offline-first with a 24h TTL + last-known-good.** [refresh] fetches only when the cache is older
 * than [TTL_MS] (or empty); within the TTL it's a no-op so the UI keeps serving the cached values.
 * A failed fetch is swallowed — the previously-cached benchmarks (however stale) remain, so a driver
 * who fetched once keeps working percentiles forever offline. This is the acceptance criterion
 * "benchmarks available offline after first fetch".
 *
 * **City-change invalidation.** The cache is keyed on city; [refresh] prunes any cell for a city
 * other than the requested one, so switching cities drops the previous city's stale rows and the new
 * city's benchmarks are fetched. (The cell key also includes platform/metric/period.)
 *
 * **Read API for B-046.** [observe] / [observeForPlatform] return `Flow<List<PopulationStat>>` (clean
 * domain models, no Room types). B-046 maps a driver's own metric value against these breakpoints to
 * compute a percentile; this repository's job ends at delivering fresh-or-cached benchmarks.
 */
@Singleton
class BenchmarksRepository @Inject constructor(
    private val dao: PopulationStatDao,
    private val api: ApiClient,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Outcome of a [refresh] call, for the worker/caller and tests. */
    enum class RefreshResult {
        /** Fetched fresh benchmarks from the backend and cached them. */
        FETCHED,

        /** Cache was still within the TTL — no network call made. */
        FRESH_CACHE,

        /** Fetch failed; the previous cache (if any) was retained. */
        FAILED,
    }

    /**
     * Cached benchmarks for [city] across all platforms, as a reactive [Flow] of domain models.
     * Always reflects whatever is cached (possibly empty before the first successful fetch).
     */
    fun observe(city: City): Flow<List<PopulationStat>> =
        dao.observeForCity(city.key).map { rows -> rows.map(PopulationStat::from) }

    /** Cached benchmarks for one [city] × [platform] — the shape B-046 percentiles consume. */
    fun observeForPlatform(city: City, platform: String): Flow<List<PopulationStat>> =
        dao.observeForCityPlatform(city.key, platform.lowercase())
            .map { rows -> rows.map(PopulationStat::from) }

    /**
     * Cached benchmarks for one [city] × [platform] **including the `national` fallback** rows — the
     * exact read shape the percentile engine (B-046) needs to apply its < 20-sample national fallback.
     * Emits both the city cells and the national cells for the platform.
     */
    fun observeForPercentiles(city: City, platform: String): Flow<List<PopulationStat>> =
        dao.observeForCityOrNational(city.key, platform.lowercase())
            .map { rows -> rows.map(PopulationStat::from) }

    /**
     * Ensure the [city]'s benchmarks are cached and fresh. Fetches per platform only when the cache
     * is empty or older than the 24h TTL; otherwise no-ops. Always prunes other-city rows first so a
     * city change invalidates the previous city's cache. Never throws — a fetch failure returns
     * [RefreshResult.FAILED] and leaves the existing cache intact (offline tolerance).
     *
     * @param force when true, ignore the TTL and re-fetch (e.g. an explicit pull-to-refresh).
     */
    suspend fun refresh(
        city: City,
        platforms: List<String> = DEFAULT_PLATFORMS,
        force: Boolean = false,
    ): RefreshResult {
        // City-change invalidation: drop any cached cell not for the requested city.
        dao.deleteOtherCities(city.key)

        var fetchedAny = false
        var anyFreshCache = false
        var anyFailed = false

        for (platform in platforms) {
            val wire = platform.lowercase()

            // Fetch the driver's city cell and the shared `national` fallback cell. B-046's percentile
            // engine needs `national` cached locally so its < 20-sample fallback works offline; the
            // backend `/v1/benchmarks` only ever returns the requested city, so we ask for it
            // explicitly. Each cell is TTL-gated independently.
            for (statCity in listOf(city.key, NATIONAL_CITY)) {
                if (!force && isFresh(statCity, wire)) {
                    anyFreshCache = true
                    continue
                }
                val result = runCatching { api.getBenchmarks(city = statCity, platform = wire) }
                result.fold(
                    onSuccess = { response ->
                        val now = clock.millis()
                        val rows = response.stats.map { it.toEntity(now) }
                        if (rows.isNotEmpty()) dao.upsert(rows)
                        fetchedAny = true
                    },
                    onFailure = { anyFailed = true },
                )
            }
        }

        return when {
            fetchedAny -> RefreshResult.FETCHED
            anyFailed -> RefreshResult.FAILED
            anyFreshCache -> RefreshResult.FRESH_CACHE
            else -> RefreshResult.FRESH_CACHE
        }
    }

    /** Whether the cache for [city] × [platform] is within the TTL (so a fetch can be skipped). */
    private suspend fun isFresh(city: String, platform: String): Boolean {
        val latest = dao.latestFetchedAt(city, platform) ?: return false
        return clock.millis() - latest < TTL_MS
    }

    private fun PopulationStatDto.toEntity(fetchedAt: Long): PopulationStatEntity =
        PopulationStatEntity(
            city = city,
            platform = platform,
            metricName = metricName,
            period = period,
            sampleSize = sampleSize,
            p10 = p10.toDouble(),
            p25 = p25.toDouble(),
            p50 = p50.toDouble(),
            p75 = p75.toDouble(),
            p90 = p90.toDouble(),
            mean = mean.toDouble(),
            isSynthetic = isSynthetic,
            fetchedAt = fetchedAt,
        )

    companion object {
        /** 24-hour cache TTL — benchmarks change slowly (folded weekly at most). */
        const val TTL_MS: Long = 24L * 60 * 60 * 1000

        /** The shared fallback city slug B-046 uses when a city sample is < 20. */
        const val NATIONAL_CITY: String = "national"

        /** Platforms benchmarks are fetched for by default (launch scope + inDrive fast-follow). */
        val DEFAULT_PLATFORMS = listOf("uber", "didi", "indrive")
    }
}
