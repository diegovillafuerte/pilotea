package mx.kompara.metrics.rollup

import androidx.room.withTransaction
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.rollup.RollupCalculator
import mx.kompara.metrics.withTripEvidence
import mx.kompara.data.settings.CostProfileRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Transactional, affected-period rollup; imported source records remain untouched. */
@Singleton
class RollupRecomputer @Inject constructor(
    private val database: KomparaDatabase,
    private val offerDao: OfferDao,
    private val tripDao: TripDao,
    private val shiftDao: ShiftDao,
    private val aggregateDao: AggregateDao,
    private val costProfileRepository: CostProfileRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** A cost edit and its affected summaries commit together, or neither does. */
    suspend fun editCosts(effectiveDate: java.time.LocalDate, persist: suspend () -> Unit) {
        database.withTransaction {
            persist()
            recomputeFrom(effectiveDate)
        }
    }

    suspend fun recompute(windowDays: Long = WINDOW_DAYS) {
        recomputeFrom(clock.instant().atZone(clock.zone).toLocalDate().minusDays(windowDays))
    }

    /** Cost edits call this with their effective local date, never a blanket 730-day rewrite. */
    suspend fun recomputeFrom(effectiveDate: java.time.LocalDate) {
        val zone = clock.zone
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        if (effectiveDate > today) return
        val first = effectiveDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        recomputeRange(first, today, now.toEpochMilli() + 1)
    }

    /** A single corrected trip changes only its calendar week, including its daily bucket. */
    suspend fun recomputeWeek(date: java.time.LocalDate) {
        val first = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val now = clock.instant()
        val last = minOf(first.plusDays(6), now.atZone(clock.zone).toLocalDate())
        if (first > last) return
        recomputeRange(first, last, minOf(last.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli(), now.toEpochMilli() + 1))
    }

    private suspend fun recomputeRange(first: java.time.LocalDate, today: java.time.LocalDate, until: Long) {
        val zone = clock.zone
        val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
        database.withTransaction {
            val calc = RollupCalculator(zone, mx.kompara.data.accounting.CostSchedule(costProfileRepository.runningVersions()))
            val trips = tripDao.startedBetween(from, until)
            val offers = offerDao.withTripEvidence(offerDao.seenBetween(from, until), trips)
            val result = calc.rollup(trips, shiftDao.overlapping(from, until), offers, until - 1)
            // Clear every affected bucket, including one that lost its last trip. Preserve legacy
            // numeric evidence when no attributable cost exists; its known flag remains false.
            val previousDaily = aggregateDao.capturedDays(first.toString(), today.toString()).associateBy { it.platform to it.day }
            val previousWeekly = aggregateDao.capturedWeeks(first.toString(), today.toString()).associateBy { it.platform to it.weekStart }
            var day = first
            while (day <= today) {
                aggregateDao.deleteCapturedDay(day.toString())
                if (day.dayOfWeek == java.time.DayOfWeek.MONDAY) aggregateDao.deleteCapturedWeek(day.toString())
                day = day.plusDays(1)
            }
            aggregateDao.upsertDaily(result.daily.map { row ->
                if (row.runningCostKnown) row else row.copy(netEarningsMxn = previousDaily[row.platform to row.day]?.netEarningsMxn ?: row.netEarningsMxn)
            })
            aggregateDao.upsertWeekly(result.weekly.map { row ->
                if (row.runningCostKnown) row else row.copy(netEarningsMxn = previousWeekly[row.platform to row.weekStart]?.netEarningsMxn ?: row.netEarningsMxn)
            })
        }
    }

    companion object {
        /** Trailing window recomputed by default: 14 days (two ISO weeks of incremental freshness). */
        const val WINDOW_DAYS: Long = 14L
    }
}
