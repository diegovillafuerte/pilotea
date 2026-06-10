package mx.kompara.metrics.recommendation

import mx.kompara.data.db.entity.TripEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Finds the driver's single most lucrative (day-of-week × hour) block from a week's trips, for the
 * [RecommendationEngine] "tus mejores horas" rule (B-048).
 *
 * Distinct from `:ui`'s `BestHours` (which buckets one *day's* trips by hour-of-day for the
 * day-detail screen): this buckets a *week's* trips by the (day-of-week, hour-of-day) pair so the
 * tip can say "tu mejor bloque fue el viernes de 19:00 a 20:00". Lives in `:metrics` so the engine's
 * pure-module boundary holds (it can't reach into `:ui`). Net is gross − km × marginal cost, the same
 * convention as the rollup. Pure and zone-parametric → unit-testable against hand-built buckets.
 */
class BestHourFinder(private val zone: ZoneId, private val marginalCostPerKm: Double) {

    /**
     * The single best (day-of-week, hour) block across [trips], or null when there's nothing to name
     * (no completed trips, or every block is empty). Ties broken by higher net then earlier
     * day/hour for determinism. Open trips (no [TripEntity.endedAt]) are ignored, matching the rollup's
     * completed-only rule.
     */
    fun best(trips: List<TripEntity>): BestHourBlock? {
        val byBucket = HashMap<Pair<Int, Int>, Accumulator>()
        for (trip in trips) {
            if (trip.endedAt == null) continue
            val zoned = Instant.ofEpochMilli(trip.startedAt).atZone(zone)
            val key = zoned.dayOfWeek.value to zoned.hour
            byBucket.getOrPut(key) { Accumulator() }.add(trip)
        }
        if (byBucket.isEmpty()) return null
        return byBucket
            .map { (key, acc) ->
                BestHourBlock(
                    dayOfWeek = key.first,
                    hour = key.second,
                    netMxn = acc.gross - acc.km * marginalCostPerKm,
                    tripCount = acc.trips,
                )
            }
            .maxWithOrNull(
                compareBy<BestHourBlock> { it.netMxn }
                    .thenByDescending { it.dayOfWeek }
                    .thenByDescending { it.hour },
            )
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
