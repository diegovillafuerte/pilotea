package mx.kompara.metrics.rollup

import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.rollup.RollupCalculator
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.metrics.CostProfileMapper
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a rollup recompute (B-039): reads the captured offers/trips/shifts and the active cost
 * profile, runs the pure [RollupCalculator], and writes the resulting CAPTURED daily/weekly rows.
 *
 * Lives in `:metrics` (not `:data`) because it needs [CostProfileMapper] to turn the persisted cost
 * profile into a marginal $/km for the net math — and `:metrics` may depend on `:data`, never the
 * reverse. The actual math is the pure `:data` [RollupCalculator]; this is just the IO shell, so it's
 * thin and the interesting logic stays unit-tested without Android.
 *
 * **Recompute window:** by default it rebuilds the trailing [WINDOW_DAYS] days of captured aggregates
 * (covers the on-trip-close incremental case and the daily worker). It deletes the captured rows in
 * the recomputed buckets before re-inserting, so a recompute is idempotent and never double-counts.
 * IMPORTED rows are untouched (see [mx.kompara.data.db.entity.AggregateSource]).
 */
@Singleton
class RollupRecomputer @Inject constructor(
    private val offerDao: OfferDao,
    private val tripDao: TripDao,
    private val shiftDao: ShiftDao,
    private val aggregateDao: AggregateDao,
    private val costProfileRepository: CostProfileRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** Recompute the trailing [windowDays] days of captured daily + weekly aggregates. */
    suspend fun recompute(windowDays: Long = WINDOW_DAYS) {
        val zone: ZoneId = clock.zone
        val now = clock.millis()
        // Widen the read window to the Monday of the oldest day we recompute, so weekly buckets are
        // complete even when the window starts mid-week.
        val calc = RollupCalculator(
            zone = zone,
            marginalCostPerKm = CostProfileMapper
                .toCostProfileOrZero(costProfileRepository.get())
                .marginalCostPerKm,
        )
        val from = clock.instant()
            .atZone(zone)
            .toLocalDate()
            .minusDays(windowDays)
            .with(java.time.DayOfWeek.MONDAY.let { java.time.temporal.TemporalAdjusters.previousOrSame(it) })
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val until = clock.instant()
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val trips = tripDao.completedStartedBetween(from, until)
        val shifts = shiftDao.overlapping(from, until)
        val offers = offerDao.seenBetween(from, until)

        val result = calc.rollup(trips = trips, shifts = shifts, offers = offers, computedAt = now)

        // Clear the captured rows in every recomputed bucket, then write the fresh ones, so a bucket
        // that went to zero (all trips deleted) doesn't leave a stale row behind.
        result.daily.map { it.day }.toSet().forEach { aggregateDao.deleteCapturedDay(it) }
        result.weekly.map { it.weekStart }.toSet().forEach { aggregateDao.deleteCapturedWeek(it) }
        aggregateDao.upsertDaily(result.daily)
        aggregateDao.upsertWeekly(result.weekly)
    }

    companion object {
        /** Trailing window recomputed by default: 14 days (two ISO weeks of incremental freshness). */
        const val WINDOW_DAYS: Long = 14L
    }
}
