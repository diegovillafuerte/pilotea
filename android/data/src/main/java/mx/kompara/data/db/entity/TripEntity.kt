package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A completed trip, inferred from the offer→accept→complete lifecycle.
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

    /** Epoch millis when the trip started. */
    val startedAt: Long,

    /** Epoch millis when the trip completed. */
    val endedAt: Long,

    /** Source platform, stored as [mx.kompara.data.model.Platform] name. */
    val platform: String,

    /** Realized gross earnings for the trip in MXN. */
    val grossMxn: Double,

    /** Actual distance driven in km. */
    val distanceKm: Double,

    /** Actual trip duration in minutes. */
    val durationMin: Double,
)
