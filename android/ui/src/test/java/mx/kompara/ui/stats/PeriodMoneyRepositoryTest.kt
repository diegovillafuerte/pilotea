package mx.kompara.ui.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import mx.kompara.data.accounting.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.*
import mx.kompara.data.settings.CarCostRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.metrics.rollup.RollupRecomputer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodMoneyRepositoryTest {
    @Test fun `reactive day snapshot pairs live offers trips and money and midnight rebinds bounds`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        try {
            val day = LocalDate.parse("2026-10-19")
            val zone = ZoneId.of("America/Mexico_City")
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val now = day.atTime(14, 40).atZone(zone).toInstant()
            val running = CostProfileRepository(db.costProfileDao())
            val car = CarCostRepository(db.carCostDao())
            running.saveRunningCosts(RunningCostInputs.TYPICAL, start, start)
            car.save(BigDecimal("1500"), day, start)
            val repository = PeriodMoneyRepository(db, running, car)
            val flow = repository.observeToday(now, zone)
            val empty = flow.first()
            assertEquals(0, empty.today.money.trips)
            assertEquals(1, empty.today.money.carCharges.size)
            assertFalse(empty.hasHistory)
            val awaiting = async(start = CoroutineStart.UNDISPATCHED) { flow.first { it.today.money.trips == 1 } }
            val offer = db.offerDao().insert(OfferEntity(seenAt = now.toEpochMilli() - 60_000,
                platform = "UBER", fareMxn = 148.0, distanceKm = 12.0, durationMin = 24.0, sessionId = "live"))
            val trip = db.tripDao().accept(TripEntity(offerId = offer, startedAt = now.toEpochMilli() - 59_000,
                platform = "UBER", grossMxn = 148.0, distanceKm = 12.0, durationMin = 24.0))
            val accepted = awaiting.await()
            assertEquals("ACCEPTED", accepted.today.offers.single().outcome)
            assertEquals(1, accepted.today.money.inProgress)
            assertEquals("−$100", mx.kompara.ui.format.Formatters.formatMxnWhole(accepted.today.money.periodResult!!))
            assertEquals(now, accepted.today.money.window.asOf)
            val suppression = async(start = CoroutineStart.UNDISPATCHED) { flow.first { it.today.presentations.isNotEmpty() } }
            db.offerPresentationDao().record(OfferPresentationEntity("live", "SUPPRESSED", now.toEpochMilli(), "NO_ROOM"))
            assertEquals("SUPPRESSED", suppression.await().today.presentations.single().outcome)
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { flow.first { it.today.trips.singleOrNull()?.lifecycle == "CANCELLED" } }
            db.tripDao().cancel(trip, now.toEpochMilli())
            assertEquals(0, cancelled.await().today.money.trips)
            val nextMorning = repository.observeToday(day.plusDays(1).atTime(8, 0).atZone(zone).toInstant(), zone).first()
            assertEquals(day.plusDays(1), nextMorning.today.money.window.start)
            assertTrue(nextMorning.today.offers.isEmpty())
            assertEquals(day, nextMorning.yesterday.money.window.start)
            assertEquals(offer, nextMorning.yesterday.offers.single().id)
            assertTrue(nextMorning.hasHistory)
        } finally { db.close() }
    }

    @Test fun `database snapshot includes accepted in progress once and one calendar car charge`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        try {
            val day = LocalDate.parse("2026-10-19")
            val zone = ZoneId.of("America/Mexico_City")
            val start = day.atTime(0, 0).atZone(zone).toInstant().toEpochMilli()
            val now = day.atTime(14, 40).atZone(zone).toInstant()
            val running = CostProfileRepository(db.costProfileDao())
            val car = CarCostRepository(db.carCostDao())
            running.saveRunningCosts(RunningCostInputs.TYPICAL, start, start)
            car.save(BigDecimal("1500"), day, start)
            db.shiftDao().insert(ShiftEntity(startedAt = start))
            val offer = db.offerDao().insert(OfferEntity(seenAt = start - 1_000, platform = "UBER", fareMxn = 148.0,
                distanceKm = 12.0, durationMin = 24.0, outcome = "ACCEPTED"))
            val trip = db.tripDao().insert(TripEntity(offerId = offer, startedAt = start, platform = "UBER",
                grossMxn = 148.0, distanceKm = 12.0, durationMin = 24.0))
            val repo = PeriodMoneyRepository(db, running, car)
            val today = repo.snapshot(PeriodWindow(day, day, now, zone))
            val week = repo.snapshot(PeriodWindow(day, day.plusDays(6), now, zone))
            assertEquals(today.periodResult, week.periodResult)
            assertEquals(1, today.inProgress)
            assertEquals(1, week.carCharges.size)
            assertEquals(14.666666666666667, today.hoursOnline.toDouble(), 1e-12)
            db.tripDao().update(db.tripDao().findById(trip)!!.copy(endedAt = start + 1_440_000))
            val finished = repo.snapshot(today.window)
            assertEquals(today.periodResult, finished.periodResult)
            assertEquals(0, finished.inProgress)
        } finally { db.close() }
    }

    @Test fun `recompute clears empty captured buckets preserves imports and leaves older dates alone`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        try {
            val day = LocalDate.parse("2026-10-19")
            val zone = ZoneId.of("America/Mexico_City")
            val now = day.atTime(14, 40).atZone(zone).toInstant()
            val running = CostProfileRepository(db.costProfileDao())
            val seed = DailyAggregateEntity("UBER", day.toString(), "CAPTURED", 99.0, 100.0, 1, 1.0, 1.0,
                99.0, 99.0, 99.0, 1.0, null, 0)
            db.aggregateDao().upsertDaily(listOf(seed, seed.copy(source = "IMPORTED"), seed.copy(day = day.minusWeeks(1).toString())))
            val recomputer = RollupRecomputer(db, db.offerDao(), db.tripDao(), db.shiftDao(), db.aggregateDao(), running, Clock.fixed(now, zone))
            recomputer.recomputeFrom(day)
            assertTrue(db.aggregateDao().capturedDay(day.toString()).isEmpty())
            assertEquals(99.0, db.aggregateDao().capturedDay(day.minusWeeks(1).toString()).single().netEarningsMxn, 0.0)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM daily_aggregates WHERE source = 'IMPORTED'").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
            recomputer.recomputeFrom(day)
            assertTrue(db.aggregateDao().capturedDay(day.toString()).isEmpty())
        } finally { db.close() }
    }
}
