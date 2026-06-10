package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A store-and-forward "Reportar tarjeta no leída" report (B-034): a PII-scrubbed
 * accessibility snapshot the on-device parser failed to read, queued locally
 * until the `:sync` uploader posts it to `POST /v1/fixture-reports` and deletes
 * it on ack.
 *
 * The [snapshotJson] is the serialized **scrubbed** `ParserSnapshot` (run through
 * `SnapshotScrubber` before it ever reaches this entity). It is the one field
 * here that contains node text, and that text is masked. [consented] records the
 * explicit per-report consent the user gave when tapping the report button — a
 * report is never created without it, and the uploader re-checks it before send.
 */
@Entity(tableName = "fixture_reports")
data class FixtureReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Host package the snapshot came from. */
    val hostPackage: String,

    /** Host app versionCode (null when unknown). */
    val hostVersionCode: Long? = null,

    /** Spec revision that was active when the parse failed (null when no spec). */
    val specVersion: Int? = null,

    /** Why the parser produced no card: NO_SPEC / NOT_AN_OFFER / OTHER. */
    val reason: String,

    /** Serialized, PII-scrubbed `ParserSnapshot` JSON. */
    val snapshotJson: String,

    /** Explicit per-report consent flag captured at report time. */
    val consented: Boolean,

    /** When the report was queued (epoch millis). */
    val createdAt: Long,
)
