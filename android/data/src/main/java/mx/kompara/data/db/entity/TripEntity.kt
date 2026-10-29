package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An observed trip, with lifecycle separate from itemized payment evidence.
 *
 * Trips replace uploads as the primary data source (android-technical-design.md §3): the
 * capture pipeline links an accepted [OfferEntity] to the resulting trip via [offerId].
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** FK to the originating offer, when one was captured. */
    val offerId: Long? = null,

    /** FK to the [ShiftEntity] this trip rolls up into, when a shift was open. */
    val shiftId: Long? = null,

    /** Epoch millis when the trip started. */
    val startedAt: Long,

    /**
     * Epoch millis when the trip completed; null while the trip is still in progress (B-039 persists
     * the open trip the instant it's inferred so a crash mid-trip doesn't lose it). Closed by the
     * trip-end heuristic.
     */
    val endedAt: Long? = null,

    /** Source platform, stored as [mx.kompara.data.model.Platform] name. */
    val platform: String,

    /**
     * Gross earnings for the trip in MXN. On the captured path this is the *offer fare* used as an
     * estimate ([estimated] = true) — we cannot read the final settled fare on device, only what the
     * offer promised. Aggregate statements never confirm this individual trip.
     */
    val grossMxn: Double?,

    /** Actual distance driven in km (offer estimate on the captured path). */
    val distanceKm: Double?,

    /** Actual trip duration in minutes (offer estimate on the captured path). */
    val durationMin: Double?,

    /**
     * True when [grossMxn]/[distanceKm]/[durationMin] are the offer's *estimate* rather than realized
     * values — always true for captured trips (B-039). Surfaced so the UI can mark earnings as
     * "estimado" and so reconciliation (B-045) knows captured numbers are provisional.
     */
    val estimated: Boolean = true,
    val lifecycle: String = if (endedAt == null) TripLifecycle.IN_PROGRESS.name else TripLifecycle.FINISHED.name,
    val lastObservedAt: Long = endedAt ?: startedAt,
    val paymentBasis: String = "DRIVER_PAYMENT",
    val inputProvenance: String = "CAPTURED",
    /** Only itemized evidence can confirm this trip; finishing and weekly imports cannot. */
    val confirmationEvidenceId: String? = null,
    val confirmationSource: String? = null,
    val confirmedAt: Long? = null,
    val cancellationSource: String? = null,
)
