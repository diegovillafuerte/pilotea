package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single offer card captured from a host app (Uber/DiDi) before the driver accepts.
 *
 * Monetary values are stored in MXN (the product currency). Distances are in kilometres,
 * durations in minutes. [verdict] is the metrics engine's traffic-light call, persisted
 * for later analysis; null until the engine has run.
 */
@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Epoch millis when the offer was captured. */
    val seenAt: Long,

    /** Source platform, stored as [mx.kompara.data.model.Platform] name. */
    val platform: String,

    /** Total promised fare in MXN. */
    val fareMxn: Double,

    /** Trip distance in km, including the pickup leg. */
    val distanceKm: Double,

    /** Estimated total trip time in minutes (pickup + ride). */
    val durationMin: Double,

    /** Pickup distance in km, when the card exposes it separately. */
    val pickupKm: Double? = null,

    /** Whether the offer card had a surge/multiplier indicator. */
    val surge: Boolean = false,

    /** Traffic-light verdict name, null until the metrics engine evaluates the offer. */
    val verdict: String? = null,

    /** True once the driver accepted (card dismissed + state transition inferred). */
    val accepted: Boolean = false,

    /**
     * Lifecycle outcome ([OfferOutcome] name): PENDING until resolved, then ACCEPTED / DECLINED /
     * EXPIRED. Drives acceptance-rate analytics; only ACCEPTED offers feed earnings. Added in B-039.
     */
    val outcome: String = OfferOutcome.PENDING.name,

    /** Epoch millis the outcome was decided; null while still [OfferOutcome.PENDING]. */
    val resolvedAt: Long? = null,

    /** FK to the [ShiftEntity] this offer was seen during, when a shift was open. */
    val shiftId: Long? = null,
)
