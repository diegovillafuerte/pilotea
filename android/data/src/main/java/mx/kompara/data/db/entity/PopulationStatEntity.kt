package mx.kompara.data.db.entity

import androidx.room.Entity

/**
 * A locally-cached population benchmark breakpoint set (B-043) — the on-device twin of the backend's
 * `population_stats` row. One row per `(city, platform, metricName, period)`, mirroring the backend's
 * unique key, so the percentile UI (B-046) can render a driver's standing against their city's
 * distribution **offline** after the first fetch.
 *
 * Populated by [mx.kompara.sync] BenchmarksRepository from `GET /v1/benchmarks?city=&platform=`. The
 * repository caches with a 24h TTL and last-known-good fallback: a stale-but-present cache still
 * serves the UI when the network is unavailable. [fetchedAt] drives the TTL; rows for a city other
 * than the driver's current one are pruned on a city change so the cache reflects the active city.
 *
 * The percentile fields are the same five breakpoints + mean as the backend (DECIMAL there → Double
 * here). [isSynthetic] tells the UI whether a cell is still a synthetic seed (true) or has been
 * folded from real consented data (false), so it can caveat "estimated" benchmarks if desired.
 */
@Entity(
    tableName = "population_stats",
    primaryKeys = ["city", "platform", "metricName", "period"],
)
data class PopulationStatEntity(
    /** City slug (e.g. "cdmx"); matches [mx.kompara.data.model.City.key] / backend `city`. */
    val city: String,

    /** [mx.kompara.data.model.Platform] wire name, lower-case (e.g. "uber"); matches backend. */
    val platform: String,

    /** Metric key (e.g. "earnings_per_trip"); matches backend `metric_name`. */
    val metricName: String,

    /** Stat period (e.g. "current"); matches backend `period`. */
    val period: String,

    /** Number of samples behind this cell (synthetic seed size or real driver-week count). */
    val sampleSize: Int,

    val p10: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p90: Double,
    val mean: Double,

    /** False once folded from real consented data; true while still a synthetic seed. */
    val isSynthetic: Boolean,

    /** Epoch millis this row was fetched from the backend — drives the 24h cache TTL. */
    val fetchedAt: Long,
)
