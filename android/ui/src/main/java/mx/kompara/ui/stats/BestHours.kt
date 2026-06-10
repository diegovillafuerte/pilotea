package mx.kompara.ui.stats

import mx.kompara.data.db.entity.TripEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Net earnings bucketed by hour-of-day, for the day-detail "mejores horas" blocks (B-040 req 2).
 *
 * Simple aggregation: each completed trip is attributed to the local hour (0..23) of its
 * [TripEntity.startedAt], and its net (gross − km × marginal cost) is summed into that hour. The
 * screen highlights the top few. Pure and zone-parametric so it's unit-testable against
 * hand-computed buckets.
 */
class BestHours(private val zone: ZoneId, private val marginalCostPerKm: Double) {

    /**
     * Hour buckets for [trips], sorted by net descending (ties broken by earlier hour). Only hours
     * that actually had a trip appear. Open trips (no [TripEntity.endedAt]) are ignored, matching the
     * rollup's "completed trips only" rule.
     */
    fun blocks(trips: List<TripEntity>): List<HourBlock> {
        val byHour = sortedMapOf<Int, Accumulator>()
        for (trip in trips) {
            if (trip.endedAt == null) continue
            val hour = Instant.ofEpochMilli(trip.startedAt).atZone(zone).hour
            byHour.getOrPut(hour) { Accumulator() }.add(trip)
        }
        return byHour.map { (hour, acc) ->
            HourBlock(
                hour = hour,
                trips = acc.trips,
                netMxn = acc.gross - acc.km * marginalCostPerKm,
            )
        }.sortedWith(compareByDescending<HourBlock> { it.netMxn }.thenBy { it.hour })
    }

    private class Accumulator {
        var gross = 0.0
        var km = 0.0
        var trips = 0
        fun add(trip: TripEntity) {
            gross += trip.grossMxn
            km += trip.distanceKm
            trips++
        }
    }
}

/**
 * One hour-of-day block. [hour] is 0..23 local; the screen renders it as a range ("18:00–19:00").
 */
data class HourBlock(
    val hour: Int,
    val trips: Int,
    val netMxn: Double,
)
