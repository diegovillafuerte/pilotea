package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.PopulationStatEntity

/**
 * Persistence for the locally-cached population benchmarks (B-043).
 *
 * Writes REPLACE on the composite `(city, platform, metricName, period)` key so a fresh fetch
 * overwrites the matching cell. Reads expose a [Flow] the percentile UI (B-046) observes, plus the
 * freshest-fetch timestamp the BenchmarksRepository uses to enforce its 24h TTL, and a city-scoped
 * purge so a city change invalidates the previous city's cache.
 */
@Dao
interface PopulationStatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<PopulationStatEntity>)

    /** All cached benchmark cells for one city, newest-fetched first then by platform/metric. */
    @Query(
        "SELECT * FROM population_stats WHERE city = :city " +
            "ORDER BY platform ASC, metricName ASC",
    )
    fun observeForCity(city: String): Flow<List<PopulationStatEntity>>

    /** Cached cells for a single city × platform — the read shape B-046 percentiles need. */
    @Query(
        "SELECT * FROM population_stats WHERE city = :city AND platform = :platform " +
            "ORDER BY metricName ASC",
    )
    fun observeForCityPlatform(city: String, platform: String): Flow<List<PopulationStatEntity>>

    /** Snapshot of cached cells for a city × platform (non-reactive; used by the repository). */
    @Query("SELECT * FROM population_stats WHERE city = :city AND platform = :platform")
    suspend fun forCityPlatform(city: String, platform: String): List<PopulationStatEntity>

    /**
     * Cached cells for one platform across BOTH the driver's [city] and the `national` fallback row,
     * as a reactive [Flow]. This is the read shape the percentile feature (B-046) needs: it must see
     * the national breakpoints to fall back when the city sample is < 20. Ordered city-first so a
     * consumer can split the two with a simple partition on `city`.
     */
    @Query(
        "SELECT * FROM population_stats " +
            "WHERE platform = :platform AND (city = :city OR city = 'national') " +
            "ORDER BY city DESC, metricName ASC",
    )
    fun observeForCityOrNational(city: String, platform: String): Flow<List<PopulationStatEntity>>

    /**
     * The most recent [PopulationStatEntity.fetchedAt] for a city × platform, or null when nothing is
     * cached. Drives the TTL decision: a fetch is skipped while `now - max(fetchedAt) < TTL`.
     */
    @Query(
        "SELECT MAX(fetchedAt) FROM population_stats WHERE city = :city AND platform = :platform",
    )
    suspend fun latestFetchedAt(city: String, platform: String): Long?

    /**
     * Delete every cached cell whose city is NOT [city] AND NOT the `national` fallback — invalidates a
     * previous city's cache on a city change, but always retains the `national` row so the percentile
     * feature (B-046) keeps its < 20-sample fallback offline regardless of which city is active.
     */
    @Query("DELETE FROM population_stats WHERE city <> :city AND city <> 'national'")
    suspend fun deleteOtherCities(city: String)

    /** Delete all cached benchmarks (e.g. a forced refresh). */
    @Query("DELETE FROM population_stats")
    suspend fun clear()
}
