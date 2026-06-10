package mx.kompara.sync.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the B-044 import endpoint (POST /v1/imports), consumed by B-045's import flow.
 *
 * The request is multipart/form-data (built in [ApiClient.importWeek]), not JSON — these types model
 * only the JSON *response* shape. Field names match the backend's response exactly (snake_case), so
 * the serializer maps them without renaming except where a Kotlin idiom reads better via
 * [SerialName].
 *
 * Both a dry-run preview (`?dry_run=true`) and a real import return [ImportResponse]; the preview
 * carries `import_id == null` and `dry_run == true`, a confirmed import carries a non-null id and
 * `dry_run == false`. A parse/validation failure instead returns `{ "error": "<spanish>" }`, surfaced
 * by [ApiClient] as an [ApiException] whose message is that Spanish string.
 */

/** One file to upload, with the bytes already read off the content resolver. */
data class ImportFile(
    /** Display name (used for the multipart part filename and the backend's size-error message). */
    val fileName: String,
    /** MIME type — must be one of image/png, image/jpeg, image/webp, application/pdf. */
    val mimeType: String,
    /** Raw file bytes. */
    val bytes: ByteArray,
) {
    // Data classes over ByteArray need structural equals/hashCode for sane test assertions.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImportFile) return false
        return fileName == other.fileName && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/** The parsed weekly metrics the backend extracts (subset that the review screen + local upsert use). */
@Serializable
data class ImportMetrics(
    @SerialName("week_start") val weekStart: String,
    @SerialName("net_earnings") val netEarnings: Double? = null,
    @SerialName("gross_earnings") val grossEarnings: Double? = null,
    @SerialName("total_trips") val totalTrips: Int? = null,
    @SerialName("total_km") val totalKm: Double? = null,
    @SerialName("hours_online") val hoursOnline: Double? = null,
    @SerialName("earnings_per_trip") val earningsPerTrip: Double? = null,
    @SerialName("earnings_per_km") val earningsPerKm: Double? = null,
    @SerialName("earnings_per_hour") val earningsPerHour: Double? = null,
    @SerialName("trips_per_hour") val tripsPerHour: Double? = null,
    @SerialName("platform_commission_pct") val platformCommissionPct: Double? = null,
)

/** Success response of POST /v1/imports (dry-run preview or confirmed import). */
@Serializable
data class ImportResponse(
    @SerialName("import_id") val importId: String? = null,
    val metrics: ImportMetrics,
    @SerialName("data_completeness") val dataCompleteness: Double,
    @SerialName("dry_run") val dryRun: Boolean = false,
)
