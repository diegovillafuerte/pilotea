package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The driver's cost profile — the inputs that turn gross earnings into net.
 *
 * Per android-technical-design.md §3: fuel $/km, maintenance $/km, insurance and
 * rent/financing as per-day fixed costs. A single active profile is expected
 * ([SINGLETON_ID]), but the table allows history.
 */
@Entity(tableName = "cost_profile")
data class CostProfileEntity(
    @PrimaryKey
    val id: Long = SINGLETON_ID,

    /** Epoch millis the profile was last edited. */
    val updatedAt: Long,

    /** Fuel cost in MXN per km driven. */
    val fuelPerKmMxn: Double,

    /** Maintenance cost in MXN per km (tyres, service, depreciation). */
    val maintenancePerKmMxn: Double,

    /** Insurance cost in MXN per active day. */
    val insurancePerDayMxn: Double,

    /** Vehicle rent or financing cost in MXN per active day. */
    val rentPerDayMxn: Double,
) {
    companion object {
        /** Id of the single active cost profile. */
        const val SINGLETON_ID: Long = 1L
    }
}
