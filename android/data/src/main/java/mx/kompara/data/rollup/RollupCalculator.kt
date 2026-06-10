package mx.kompara.data.rollup

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Pure, deterministic rollup math (B-039): folds captured offers, trips and shifts into per-platform
 * daily and weekly aggregates whose fields mirror the web MVP's `weekly_data` shape.
 *
 * No Android, no IO, no clock-of-its-own — every time input is passed in, so the whole thing is unit-
 * testable against hand-computed expectations. The caller (RollupWorker / incremental trip-close)
 * supplies the [zone] (device-local, so day/week boundaries match what the driver sees) and the
 * [marginalCostPerKm] from the active cost profile (net = gross − km × marginal cost).
 *
 * ## Bucketing rules
 * - A **trip** is attributed to the local day/week of its [TripEntity.startedAt] (a trip spanning
 *   midnight lands in exactly one bucket, deterministically).
 * - A **week** starts on **Monday** ([weekStartIso]) to match the web convention; [weekStart] is the
 *   ISO date of that Monday.
 * - **Hours online** come from shift wall-clock time *clipped to the bucket* (a shift overlapping two
 *   days contributes only the slice inside each day). This is platform-agnostic (a shift is a window
 *   of activity, not a platform), so the same online hours are attributed to every platform that had
 *   trips in the bucket — see [hoursForBucket]. Documented divergence from a naive per-platform split,
 *   which the capture stream can't provide.
 * - **Acceptance rate** counts resolved offers (ACCEPTED / (ACCEPTED+DECLINED+EXPIRED)); PENDING
 *   offers are ignored. Bucketed by [OfferEntity.seenAt].
 */
class RollupCalculator(
    private val zone: ZoneId,
    private val marginalCostPerKm: Double,
) {

    /** The full set of daily + weekly captured aggregates for the given inputs. */
    fun rollup(
        trips: List<TripEntity>,
        shifts: List<ShiftEntity>,
        offers: List<OfferEntity>,
        computedAt: Long,
    ): RollupResult {
        val daily = dailyAggregates(trips, shifts, offers, computedAt)
        val weekly = weeklyAggregates(trips, shifts, offers, computedAt)
        return RollupResult(daily = daily, weekly = weekly)
    }

    private fun dailyAggregates(
        trips: List<TripEntity>,
        shifts: List<ShiftEntity>,
        offers: List<OfferEntity>,
        computedAt: Long,
    ): List<DailyAggregateEntity> {
        // (platform, dayIso) -> accumulator
        val byBucket = mutableMapOf<Pair<String, String>, Accumulator>()
        for (trip in trips.filter { it.endedAt != null }) {
            val day = localDate(trip.startedAt)
            val key = trip.platform to day.format(ISO_DATE)
            byBucket.getOrPut(key) { Accumulator() }.addTrip(trip)
        }
        // Acceptance rate per (platform, day).
        val offerCounts = offerCountsByBucket(offers) { localDate(it).format(ISO_DATE) }
        // Online hours per day (platform-agnostic; attributed to every platform with trips that day).
        val platformsByDay = byBucket.keys.groupBy({ it.second }, { it.first })
        val hoursByDay = platformsByDay.keys.associateWith { day ->
            hoursForBucket(shifts, dayStart(day), dayStart(day).plusDays())
        }

        return byBucket.map { (key, acc) ->
            val (platform, day) = key
            val counts = offerCounts[platform to day]
            DailyAggregateEntity(
                platform = platform,
                day = day,
                source = AggregateSource.CAPTURED.name,
                netEarningsMxn = acc.net(marginalCostPerKm),
                grossEarningsMxn = acc.gross,
                totalTrips = acc.trips,
                totalKm = acc.km,
                hoursOnline = hoursByDay[day] ?: 0.0,
                earningsPerTrip = perTrip(acc.net(marginalCostPerKm), acc.trips),
                earningsPerKm = perKm(acc.net(marginalCostPerKm), acc.km),
                earningsPerHour = perHour(acc.net(marginalCostPerKm), hoursByDay[day] ?: 0.0),
                tripsPerHour = tripsPerHour(acc.trips, hoursByDay[day] ?: 0.0),
                acceptanceRate = counts?.acceptanceRate(),
                computedAt = computedAt,
            )
        }
    }

    private fun weeklyAggregates(
        trips: List<TripEntity>,
        shifts: List<ShiftEntity>,
        offers: List<OfferEntity>,
        computedAt: Long,
    ): List<WeeklyAggregateEntity> {
        val byBucket = mutableMapOf<Pair<String, String>, Accumulator>()
        for (trip in trips.filter { it.endedAt != null }) {
            val key = trip.platform to weekStartIso(trip.startedAt)
            byBucket.getOrPut(key) { Accumulator() }.addTrip(trip)
        }
        val offerCounts = offerCountsByBucket(offers) { weekStartIso(it) }
        val platformsByWeek = byBucket.keys.groupBy({ it.second }, { it.first })
        val hoursByWeek = platformsByWeek.keys.associateWith { weekStart ->
            val start = LocalDate.parse(weekStart, ISO_DATE).atStartOfDay(zone).toInstant()
            hoursForBucket(shifts, start, start.plusSeconds(WEEK_SECONDS))
        }

        return byBucket.map { (key, acc) ->
            val (platform, weekStart) = key
            val counts = offerCounts[platform to weekStart]
            val hours = hoursByWeek[weekStart] ?: 0.0
            WeeklyAggregateEntity(
                platform = platform,
                weekStart = weekStart,
                source = AggregateSource.CAPTURED.name,
                netEarningsMxn = acc.net(marginalCostPerKm),
                grossEarningsMxn = acc.gross,
                totalTrips = acc.trips,
                totalKm = acc.km,
                hoursOnline = hours,
                earningsPerTrip = perTrip(acc.net(marginalCostPerKm), acc.trips),
                earningsPerKm = perKm(acc.net(marginalCostPerKm), acc.km),
                earningsPerHour = perHour(acc.net(marginalCostPerKm), hours),
                tripsPerHour = tripsPerHour(acc.trips, hours),
                acceptanceRate = counts?.acceptanceRate(),
                computedAt = computedAt,
            )
        }
    }

    /** ISO date (yyyy-MM-dd) of the Monday opening the week that [epochMs] falls in (local zone). */
    fun weekStartIso(epochMs: Long): String =
        localDate(epochMs)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .format(ISO_DATE)

    /** ISO date (yyyy-MM-dd) of the local day [epochMs] falls in. */
    fun dayIso(epochMs: Long): String = localDate(epochMs).format(ISO_DATE)

    private fun localDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun dayStart(dayIso: String): Instant =
        LocalDate.parse(dayIso, ISO_DATE).atStartOfDay(zone).toInstant()

    private fun Instant.plusDays(): Instant =
        atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

    /**
     * Online hours: sum each shift's overlap with `[from, until)`, in hours. An open shift (endedAt
     * null) is clipped at [until] — we only count time up to the bucket's end, never the future.
     */
    private fun hoursForBucket(shifts: List<ShiftEntity>, from: Instant, until: Instant): Double {
        val fromMs = from.toEpochMilli()
        val untilMs = until.toEpochMilli()
        var totalMs = 0L
        for (shift in shifts) {
            val end = shift.endedAt ?: untilMs
            val overlapStart = maxOf(shift.startedAt, fromMs)
            val overlapEnd = minOf(end, untilMs)
            if (overlapEnd > overlapStart) totalMs += overlapEnd - overlapStart
        }
        return totalMs / MILLIS_PER_HOUR
    }

    private inline fun offerCountsByBucket(
        offers: List<OfferEntity>,
        bucketOf: (Long) -> String,
    ): Map<Pair<String, String>, OfferCounts> {
        val counts = mutableMapOf<Pair<String, String>, OfferCounts>()
        for (offer in offers) {
            val outcome = runCatching { OfferOutcome.valueOf(offer.outcome) }.getOrDefault(OfferOutcome.PENDING)
            if (outcome == OfferOutcome.PENDING) continue
            val key = offer.platform to bucketOf(offer.seenAt)
            val c = counts.getOrPut(key) { OfferCounts() }
            if (outcome == OfferOutcome.ACCEPTED) c.accepted++ else c.rejected++
        }
        return counts
    }

    private class Accumulator {
        var gross = 0.0
        var km = 0.0
        var trips = 0

        fun addTrip(trip: TripEntity) {
            gross += trip.grossMxn
            km += trip.distanceKm
            trips++
        }

        fun net(marginalCostPerKm: Double): Double = gross - km * marginalCostPerKm
    }

    private class OfferCounts {
        var accepted = 0
        var rejected = 0

        fun acceptanceRate(): Double? {
            val total = accepted + rejected
            return if (total == 0) null else accepted.toDouble() / total
        }
    }

    private companion object {
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        const val MILLIS_PER_HOUR = 3_600_000.0
        const val WEEK_SECONDS = 7L * 24L * 3600L

        fun perTrip(net: Double, trips: Int): Double? = if (trips == 0) null else net / trips
        fun perKm(net: Double, km: Double): Double? = if (km <= 0.0) null else net / km
        fun perHour(net: Double, hours: Double): Double? = if (hours <= 0.0) null else net / hours
        fun tripsPerHour(trips: Int, hours: Double): Double? =
            if (hours <= 0.0) null else trips / hours
    }
}

/** The full output of one [RollupCalculator.rollup] pass. */
data class RollupResult(
    val daily: List<DailyAggregateEntity>,
    val weekly: List<WeeklyAggregateEntity>,
)
