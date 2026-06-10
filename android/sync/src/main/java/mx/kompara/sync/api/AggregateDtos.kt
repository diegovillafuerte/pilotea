package mx.kompara.sync.api

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the B-043 consented aggregate sync + benchmarks endpoints. Field names match the
 * backend's zod schemas exactly (POST /v1/aggregates, GET /v1/benchmarks).
 *
 * **Privacy invariant (asserted in AggregateSyncWorker tests):** [AggregateUploadBody] carries ONLY
 * derived weekly aggregate fields — earnings totals + the five efficiency metrics, keyed by
 * platform/week. There is NO offer-level, trip-level, or raw-text field anywhere in this shape, so
 * raw capture data can never leave the device through this path.
 */

/**
 * Body for POST /v1/aggregates — one weekly aggregate (driver × platform × week). The backend takes
 * the driver id from the bearer session (never the body), so this carries no identity beyond the
 * platform/week the row is for.
 *
 * Decimal-valued fields are sent as strings to match the backend's `decimalString` coercion and
 * avoid float-formatting drift across the wire.
 */
@Serializable
data class AggregateUploadBody(
    /** Platform wire name, lower-case: "uber" | "didi" | "indrive". */
    val platform: String,
    /** ISO date (YYYY-MM-DD) of the Monday that opens the week. */
    val weekStart: String,
    val netEarnings: String,
    val grossEarnings: String,
    val totalTrips: Int,
    val totalKm: String? = null,
    val hoursOnline: String? = null,
    val earningsPerTrip: String? = null,
    val earningsPerKm: String? = null,
    val earningsPerHour: String? = null,
    val tripsPerHour: String? = null,
    /** "captured" (live Android capture) | "imported" (upload parsing). */
    val source: String,
)

/** One population benchmark cell as returned inside [BenchmarksResponse.stats]. */
@Serializable
data class PopulationStatDto(
    val city: String,
    val platform: String,
    val metricName: String,
    val period: String,
    val sampleSize: Int,
    val p10: String,
    val p25: String,
    val p50: String,
    val p75: String,
    val p90: String,
    val mean: String,
    val isSynthetic: Boolean = true,
)

/** Response of GET /v1/benchmarks?city=&platform=&period=. */
@Serializable
data class BenchmarksResponse(
    val city: String,
    val platform: String,
    val period: String,
    val stats: List<PopulationStatDto> = emptyList(),
)
