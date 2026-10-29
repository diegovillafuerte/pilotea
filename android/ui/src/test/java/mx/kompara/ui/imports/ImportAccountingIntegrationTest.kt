package mx.kompara.ui.imports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.kompara.data.accounting.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.*
import mx.kompara.data.imports.*
import mx.kompara.data.settings.*
import mx.kompara.metrics.*
import mx.kompara.sync.imports.RoomLocalImportStore
import mx.kompara.ui.stats.statementDifferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportAccountingIntegrationTest {
    private val zone = ZoneId.of("America/Mexico_City")
    @Test fun `transactional choice updates reactive week preserves other app October days and trip lifecycle`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        try {
            val start = LocalDate.parse("2026-09-28")
            val now = Instant.parse("2026-10-29T12:00:00Z")
            val cost = CostProfileRepository(db.costProfileDao()); val car = CarCostRepository(db.carCostDao())
            cost.saveRunningCosts(RunningCostInputs.TYPICAL, start.atStartOfDay(zone).toInstant().toEpochMilli(), now.toEpochMilli())
            car.save(1500.toBigDecimal(), start, now.toEpochMilli())
            val money = PeriodMoneyRepository(db, cost, car)
            val records = (1..14).map { i ->
                val ts = start.plusDays(3 + i % 4L).atTime(12, i).atZone(zone).toInstant().toEpochMilli()
                val offer = db.offerDao().insert(OfferEntity(seenAt = ts, platform = "UBER", fareMxn = 1480.0/14, distanceKm = 150.6/14, durationMin = 20.0))
                db.tripDao().insert(TripEntity(offerId = offer, startedAt = ts, endedAt = ts+1200000, platform = "UBER", grossMxn = 1480.0/14, distanceKm = 150.6/14, durationMin = 20.0))
            }
            val ts = start.plusDays(3).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            val didiOffer = db.offerDao().insert(OfferEntity(seenAt = ts, platform = "DIDI", fareMxn = 1470.0, distanceKm = 87.2, durationMin = 30.0))
            db.tripDao().insert(TripEntity(offerId = didiOffer, startedAt = ts, endedAt = ts+1800000, platform = "DIDI", grossMxn = 1470.0, distanceKm = 87.2, durationMin = 30.0))
            val window = PeriodWindow(start, start.plusDays(6), now, zone)
            val october = window.copy(start = LocalDate.parse("2026-10-01"), endInclusive = LocalDate.parse("2026-10-31"))
            val originalMonth = money.monthSnapshot(october).money.periodResult
            val original = money.weekSnapshot(window)
            val store = RoomLocalImportStore(db)
            val draft = ImportDraftEntity("pdf", "uber", "pdf", 1, 2, "original", stage = "REVIEW")
            store.retain(draft, listOf(ImportFileEntity("pdf", 0, "resumen.pdf", "application/pdf", byteArrayOf(1))))
            val row = ImportStatementEntity("pdf", "UBER", start.toString(), start.plusDays(6).toString(), "2210", 20, null, "18", "PARSED_STATEMENT", "pdf", 3)
            val change = async(start = CoroutineStart.UNDISPATCHED) { money.observeWeek(window).first { it.money.paymentOnly.isNotEmpty() } }
            store.commit(draft, row, ImportSelectionEntity("UBER", row.start, row.id, "PAYMENT,TRIPS,HOURS"))
            val reconciled = change.await()
            assertNull(reconciled.money.periodResult)
            assertEquals(2210.toBigDecimal(), reconciled.money.paymentOnly.single().record.payment)
            assertEquals(original.money.byApp["DIDI"], reconciled.money.byApp["DIDI"])
            assertEquals(originalMonth, money.monthSnapshot(october).money.periodResult)
            assertEquals(1, money.monthSnapshot(october).unallocated.size)
            assertEquals(original.days, reconciled.days)
            val trips = db.tripDao().startedBetween(ts-1, now.toEpochMilli())
            assertTrue(trips.filter { it.id in records }.all { it.lifecycle == "FINISHED" && it.confirmedAt == null && it.cancellationSource == null && it.estimated })
            store.commit(draft, row.copy(payment = "9999"), null)
            assertEquals("2210", db.importDao().statements().single().payment)
            assertTrue(store.files("pdf").isEmpty()); assertNull(store.pending())
        } finally { db.close() }
    }
    @Test fun `E1e local statement compares exact payment and does not alter captures before explicit choice`() = runTest {
        val start = LocalDate.parse("2026-10-12")
        val window = PeriodWindow(start, start.plusDays(6), Instant.parse("2026-10-29T12:00:00Z"), zone)
        val captured = IncomeRecord("captured", "UBER", Coverage(start, start.plusDays(6)), MoneySource.CAPTURED_ESTIMATE, 5028.toBigDecimal(), 420.toBigDecimal(), 34)
        val statement = ImportStatementEntity("pdf", "UBER", start.toString(), start.plusDays(6).toString(), "5062", 34, null, null, "PARSED_STATEMENT", "pdf", 1).income()
        val money = PeriodAccounting.calculate(window, listOf(captured, statement), CostSchedule(emptyList()))
        assertEquals(listOf(statement), money.excluded)
        assertEquals("+$34", statementDifferences(money, listOf(statement)).single().display)
        assertEquals(MoneySource.PARSED_STATEMENT, statement.source) // cannot certify a city cohort
    }
    @Test fun `local erase prevents a late parse or Save from resurrecting private imports`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        try {
            val store = RoomLocalImportStore(db)
            val draft = ImportDraftEntity("late", "uber", "pdf", 1, 2)
            store.retain(draft, listOf(ImportFileEntity("late", 0, "resumen.pdf", "application/pdf", byteArrayOf(1))))
            db.localDataDao().eraseHistory()
            assertTrue(runCatching { store.update(draft.copy(previewJson = "late response", stage = "REVIEW")) }.isFailure)
            val row = ImportStatementEntity("late", "UBER", "2026-09-14", "2026-09-20", "5340", 29, null, null, "PARSED_STATEMENT", "pdf", 3)
            assertTrue(runCatching { store.commit(draft, row, null) }.isFailure)
            assertNull(store.draft("late")); assertTrue(db.importDao().statements().isEmpty())
        } finally { db.close() }
    }

}
