package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * A privacy-safe parse-health counter row (B-034). One row per
 * `(hostPackage, hostVersionCode, specVersion, dayUtc)`, accumulating how many
 * times the on-device spec engine attempted, succeeded, and failed to read an
 * offer card — plus a coarse [variant] tag for the card shape it recognized.
 *
 * **No screen content is ever stored here, by construction.** Every column is
 * either a host identifier (package + version code), a spec revision, a UTC day
 * bucket, a short structural tag the spec itself defines (e.g. `surge`,
 * `multi_stop`, or `_total` for the un-tagged aggregate), or an integer count.
 * There is no field that can hold passenger names, fares, addresses, or any
 * extracted text — so a counter row cannot leak PII even if mishandled.
 *
 * The composite primary key makes upsert-increment cheap (see
 * [mx.kompara.data.db.dao.TelemetryCounterDao.increment]); rows are deleted
 * after the `:sync` uploader gets an ack from the backend.
 */
@Entity(
    tableName = "telemetry_counters",
    primaryKeys = ["hostPackage", "hostVersionCode", "specVersion", "variant", "dayUtc"],
    indices = [Index(value = ["dayUtc"])],
)
data class TelemetryCounterEntity(
    /** Host app package, e.g. `com.ubercab.driver`. */
    val hostPackage: String,

    /** Host app versionCode the snapshots were captured under (0 when unknown). */
    val hostVersionCode: Long,

    /** Spec revision that produced (or failed to produce) the card. */
    val specVersion: Int,

    /**
     * Card-variant tag from the spec (e.g. `surge`, `multi_stop`), or the
     * sentinel [TOTAL] for the package-level attempt/success/failure aggregate
     * that isn't tied to a recognized variant. Free-form spec vocabulary only —
     * never extracted text.
     */
    val variant: String,

    /** UTC calendar day bucket, `YYYY-MM-DD`. */
    val dayUtc: String,

    /** Number of parse attempts in this bucket. */
    val attempts: Long = 0,

    /** Number of attempts that yielded a recognized card. */
    val successes: Long = 0,

    /** Number of attempts that yielded no card (NO_SPEC / NOT_AN_OFFER). */
    val failures: Long = 0,
) {
    companion object {
        /** Variant sentinel for the package-level aggregate (no specific card shape). */
        const val TOTAL = "_total"
    }
}
