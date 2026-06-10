package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The driver's cost profile — the inputs that turn gross earnings into net.
 *
 * Per android-technical-design.md §3: fuel $/km, maintenance $/km, insurance and
 * rent/financing as per-day fixed costs. A single active profile is expected
 * ([SINGLETON_ID]), but the table allows history.
 *
 * B-032 added [rendimientoKmPerLitre], [gasPricePerLitreMxn] and [workDaysPerWeek] so the metrics
 * engine can derive fuel $/km from efficiency × fuel price and reason about per-shift break-even.
 * These columns are *additive* with defaults; schema v1 was not shipped to users, so the exported
 * `data/schemas/.../1.json` is regenerated in place rather than bumping to v2 (see CLAUDE.md /
 * techdebt.md). All new fields default to 0 / a sensible work week so existing inserts in tests
 * keep compiling.
 */
@Entity(tableName = "cost_profile")
data class CostProfileEntity(
    @PrimaryKey
    val id: Long = SINGLETON_ID,

    /** Epoch millis the profile was last edited. */
    val updatedAt: Long,

    /** Fuel cost in MXN per km driven (usually derived from rendimiento × gas price). */
    val fuelPerKmMxn: Double,

    /** Maintenance cost in MXN per km (tyres, service, depreciation). */
    val maintenancePerKmMxn: Double,

    /** Insurance cost in MXN per active day. */
    val insurancePerDayMxn: Double,

    /** Vehicle rent or financing cost in MXN per active day. */
    val rentPerDayMxn: Double,

    /** Vehicle efficiency in km per litre (rendimiento); 0 when the driver hasn't entered it. */
    val rendimientoKmPerLitre: Double = 0.0,

    /** Fuel price in MXN per litre at the time of editing; 0 when unknown. */
    val gasPricePerLitreMxn: Double = 0.0,

    /** Days the driver works per week (1..7); used for per-shift break-even. */
    val workDaysPerWeek: Int = DEFAULT_WORK_DAYS_PER_WEEK,
) {
    companion object {
        /** Id of the single active cost profile. */
        const val SINGLETON_ID: Long = 1L

        /** A common Mexican ride-hail schedule (Mon–Sat) used when the driver hasn't said. */
        const val DEFAULT_WORK_DAYS_PER_WEEK: Int = 6
    }
}
