package mx.kompara.data.db.entity

import androidx.room.Entity

/**
 * A per-platform daily rollup. Same economic fields as [WeeklyAggregateEntity], bucketed by calendar
 * day instead of ISO week, so the UI can draw a day-by-day breakdown (B-039) without rescanning every
 * trip. Daily rows sum into the weekly row for the Monday-anchored week they belong to.
 *
 * **Composite primary key `(platform, day, source)`** — same reconciliation contract as the weekly
 * table (see [AggregateSource]). [day] is the ISO date (yyyy-MM-dd) in the device's local zone, which
 * is also how trips are bucketed into days.
 */
@Entity(
    tableName = "daily_aggregates",
    primaryKeys = ["platform", "day", "source"],
)
data class DailyAggregateEntity(
    /** [mx.kompara.data.model.Platform] name. */
    val platform: String,

    /** ISO date (yyyy-MM-dd), local zone. */
    val day: String,

    /** [AggregateSource] name. */
    val source: String,

    /** Net earnings in MXN. */
    val netEarningsMxn: Double,

    /** Gross earnings in MXN. */
    val grossEarningsMxn: Double,

    /** Completed trips on the day. */
    val totalTrips: Int,

    /** Total distance driven in km. */
    val totalKm: Double,

    /** Hours online from shift wall-clock time overlapping the day. */
    val hoursOnline: Double,

    /** Net earnings per completed trip; null when [totalTrips] is 0. */
    val earningsPerTrip: Double?,

    /** Net earnings per km; null when [totalKm] is 0. */
    val earningsPerKm: Double?,

    /** Net earnings per online hour; null when [hoursOnline] is 0. */
    val earningsPerHour: Double?,

    /** Trips per online hour; null when [hoursOnline] is 0. */
    val tripsPerHour: Double?,

    /** Fraction of resolved offers accepted on the day; null when none resolved. */
    val acceptanceRate: Double?,

    /** Epoch millis this row was last recomputed. */
    val computedAt: Long,
)
