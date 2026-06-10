package mx.kompara.data.db.entity

import androidx.room.Entity

/**
 * A per-platform weekly rollup — the on-device twin of the web MVP's `weekly_data` row
 * (migrations/0000…sql), so the two data sources are directly comparable and B-045 can sync/merge
 * captured weeks against imported ones.
 *
 * **Field parity with web `weekly_data`** (documented per CLAUDE.md): the core economic fields map
 * 1:1 — `net_earnings`→[netEarningsMxn], `gross_earnings`→[grossEarningsMxn], `total_trips`→
 * [totalTrips], `total_km`→[totalKm], `hours_online`→[hoursOnline], `earnings_per_trip`→
 * [earningsPerTrip], `earnings_per_km`→[earningsPerKm], `earnings_per_hour`→[earningsPerHour],
 * `trips_per_hour`→[tripsPerHour]. Fields the capture path can't observe (commission, taxes, tips,
 * incentives, surge/wait splits, cash/card, peak day) are intentionally omitted here — an imported
 * row (B-045) carries those. [acceptanceRate] is *new* on the captured path (the web upload flow
 * never saw declined offers) and has no web counterpart.
 *
 * **Composite primary key `(platform, weekStart, source)`** — see [AggregateSource] for the
 * reconciliation contract. [weekStart] is the ISO date (yyyy-MM-dd) of the **Monday** that opens the
 * week, matching the web MVP convention.
 */
@Entity(
    tableName = "weekly_aggregates",
    primaryKeys = ["platform", "weekStart", "source"],
)
data class WeeklyAggregateEntity(
    /** [mx.kompara.data.model.Platform] name. */
    val platform: String,

    /** ISO date (yyyy-MM-dd) of the Monday that opens the week. */
    val weekStart: String,

    /** [AggregateSource] name — CAPTURED here; IMPORTED rows come from B-045. */
    val source: String,

    /** Net earnings in MXN (gross minus marginal cost on the captured path). */
    val netEarningsMxn: Double,

    /** Gross earnings in MXN (sum of trip gross; offer-fare estimates on the captured path). */
    val grossEarningsMxn: Double,

    /** Completed trips in the week. */
    val totalTrips: Int,

    /** Total distance driven in km. */
    val totalKm: Double,

    /** Hours online, taken from shift wall-clock time overlapping the week. */
    val hoursOnline: Double,

    /** Net earnings per completed trip; null when [totalTrips] is 0. */
    val earningsPerTrip: Double?,

    /** Net earnings per km; null when [totalKm] is 0. */
    val earningsPerKm: Double?,

    /** Net earnings per online hour; null when [hoursOnline] is 0. */
    val earningsPerHour: Double?,

    /** Trips per online hour; null when [hoursOnline] is 0. */
    val tripsPerHour: Double?,

    /**
     * Fraction of resolved offers that were accepted (ACCEPTED / (ACCEPTED+DECLINED+EXPIRED)); null
     * when no offers resolved. New on the captured path — no web `weekly_data` equivalent.
     */
    val acceptanceRate: Double?,

    /** Epoch millis this row was last recomputed. */
    val computedAt: Long,
)
