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

    /** Number of trips completed during the shift. */
    val tripCount: Int = 0,

    /** Total gross earnings in MXN across the shift. */
    val grossMxn: Double = 0.0,

    /** Total net earnings in MXN after the cost profile is applied. */
    val netMxn: Double = 0.0,

    /** Total distance driven in km during the shift. */
    val distanceKm: Double = 0.0,
)
