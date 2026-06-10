package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A driving shift — an auto-inferred window of activity that trips roll up into.
 *
 * Shift/day/week rollups are produced by background WorkManager jobs
 * (android-technical-design.md §3). [endedAt] is null while the shift is still open.
 */
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Epoch millis when the shift started. */
    val startedAt: Long,

    /** Epoch millis when the shift ended; null while still active. */
    val endedAt: Long? = null,

    /**
     * Epoch millis of the most recent capture event attributed to this shift. Drives the inactivity
     * close heuristic (B-039): a gap of [INACTIVITY_GAP_MS] without events closes the shift, retro-
     * actively at [lastEventAt]. While the shift is open this advances on every event.
     */
    val lastEventAt: Long = startedAt,

    /** Number of trips completed during the shift. */
    val tripCount: Int = 0,

    /** Total gross earnings in MXN across the shift. */
    val grossMxn: Double = 0.0,

    /** Total net earnings in MXN after the cost profile is applied. */
    val netMxn: Double = 0.0,

    /** Total distance driven in km during the shift. */
    val distanceKm: Double = 0.0,
) {
    companion object {
        /**
         * Inactivity gap that bounds a shift: the first event after >= this much silence opens a new
         * shift, and this much silence after the last event closes the open one (B-039). 30 minutes
         * is a deliberate guess that needs on-device calibration — see techdebt.md.
         */
        const val INACTIVITY_GAP_MS: Long = 30L * 60L * 1000L
    }
}
