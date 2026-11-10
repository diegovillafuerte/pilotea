package mx.kompara.ui.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.OfferPresentationEntity
import mx.kompara.data.settings.*
import mx.kompara.metrics.*
import mx.kompara.metrics.rollup.RollupRecomputer
import mx.kompara.ui.fixtures.DayFixture
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayDetailViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
    }
    private class Harness : AutoCloseable {
        val fixture = DayFixture(ApplicationProvider.getApplicationContext())
        val initial = fixture.day()
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        val running = CostProfileRepository(db.costProfileDao())
        val car = CarCostRepository(db.carCostDao())
        val repository = PeriodMoneyRepository(db, running, car)
        val preferences = SettingsRepository(MemoryPreferences())
        var now = initial.money.window.asOf.toEpochMilli()
        val clock = AppClock { now }
        val snapshots = TodaySnapshots(clock, repository, initial.money.window.zone)
        val rollup = RollupRecomputer(db, db.offerDao(), db.tripDao(), db.shiftDao(), db.aggregateDao(), running,
            Clock.fixed(initial.money.window.asOf, initial.money.window.zone))
        val corrections = OfferCorrections(db, rollup, initial.money.window.zone)
        private val store = ViewModelStore()
        private var count = 0
        suspend fun seed() {
            running.saveRunningCosts(fixture.fixture.schedule.running.single().inputs, 0, 0)
            car.save("1500".toBigDecimal(), initial.date, 0)
            initial.snapshot.offers.forEach { db.offerDao().insert(it) }
            initial.snapshot.trips.forEach { db.tripDao().insert(it) }
            initial.snapshot.shifts.forEach { db.shiftDao().insert(it) }
        }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = DayDetailViewModel(handle, snapshots, repository, preferences, corrections, clock).also { store.put("${count++}", it) }
        fun id(time: String) = initial.rows.first { initial.clock(it) == time }.offerId
        override fun close() { store.clear(); db.close() }
    }
    private suspend fun DayDetailViewModel.await(predicate: (DayPresentation) -> Boolean = { true }): DayPresentation =
        withTimeout(15_000) { uiState.filterIsInstance<DayDetailUiState.Loaded>().map { it.day }.first(predicate) }

    @Test fun `room events atomically refresh open and historical day with current limits only as context`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm(SavedStateHandle(mapOf("day" to "2026-10-19")))
            val collecting = launch { vm.uiState.collect() }
            try {
                assertEquals("$762", vm.await().amount)
                h.db.offerPresentationDao().record(OfferPresentationEntity("fixture-12:10", "SUPPRESSED", h.now, "NAVIGATION"))
                assertNotNull(vm.await { it.offer(h.id("12:10"))?.suppression != null }.offer(h.id("12:10"))!!.suppression)
                h.preferences.setThreshold(h.initial.settings.effectiveThreshold.copy(redPerKmMxn = 6.5))
                val changed = vm.await { it.offer(h.id("12:10"))!!.why.endsWith("a esa hora") }
                assertEquals("$762", changed.amount)
                assertFalse(changed.offer(h.id("11:20"))!!.why.contains("a esa hora"))
                h.now = h.initial.date.plusDays(2).atTime(8, 0).atZone(h.initial.money.window.zone).toInstant().toEpochMilli()
                h.snapshots.retry()
                val past = vm.await { it.today == h.initial.date.plusDays(2) }
                assertEquals(h.initial.date, past.date)
                assertEquals("Ganaste", past.label); assertEquals("Lunes 19 oct", past.title)
                h.db.tripDao().cancel(h.id("11:20"), h.now)
                val cancelled = vm.await { it.rows.first { row -> row.offerId == h.id("11:20") }.outcomeLabel == "Se canceló" }
                assertEquals(5, cancelled.money.trips)
                assertEquals(10, cancelled.rows.size)
            } finally { collecting.cancel() }
        }
    }

    @Test fun `selected row and sheet survive recreation and a route can open the calculation`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val handle = SavedStateHandle(mapOf("day" to "2026-10-19", "offerId" to h.id("11:20")))
            val vm = h.vm(handle)
            vm.await()
            assertEquals(h.id("11:20"), vm.selectedOffer.value)
            vm.showLedger(true)
            val restored = h.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            assertTrue(restored.ledger.value)
            assertEquals(h.id("11:20"), restored.selectedOffer.value)
            restored.showLedger(false)
            assertEquals(h.id("11:20"), restored.selectedOffer.value)
            restored.closeOffer(); assertNull(restored.selectedOffer.value)
            val yesterday = h.vm(SavedStateHandle(mapOf("day" to h.initial.date.minusDays(1).toString())))
            assertEquals(h.initial.date.minusDays(1), yesterday.await().date)
            val fromHero = h.vm(SavedStateHandle(mapOf("day" to "2026-10-19", "ledger" to true)))
            assertTrue(fromHero.ledger.value)
        }
    }

    @Test fun `explicit cancellation removes only chosen trip updates rollups and guarded undo restores it once`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val id = h.id("11:20")
            val handle = SavedStateHandle(mapOf("day" to "2026-10-19", "offerId" to id))
            val vm = h.vm(handle)
            val collecting = launch { vm.uiState.collect() }
            try {
                vm.await()
                vm.cancelOffer()
                val receipt = withTimeout(15_000) { vm.undoReceipt.filterNotNull().first() }
                val restored = h.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
                assertEquals(receipt, restored.undoReceipt.value)
                val cancelled = vm.await { it.money.trips == 5 }
                assertEquals(10, cancelled.rows.size)
                assertEquals(1, cancelled.money.inProgress)
                assertEquals("$629", cancelled.amount)
                assertEquals("Se canceló", cancelled.rows.first { it.offerId == id }.outcomeLabel)
                assertEquals("172.0", h.db.offerDao().findById(id)!!.fareMxn.toString())
                assertEquals(h.initial.snapshot.offers.first { it.id == id }.evaluation, h.db.offerDao().findById(id)!!.evaluation)
                assertEquals(5, h.db.aggregateDao().capturedDay("2026-10-19").single().totalTrips)
                assertNull(h.corrections.cancel(id, h.now))
                assertNull(h.corrections.cancel(h.id("12:10"), h.now))
                assertNull(h.corrections.cancel(h.id("11:48"), h.now))
                assertTrue(h.corrections.undo(receipt))
                assertFalse(h.corrections.undo(receipt))
                assertEquals("$762", vm.await { it.money.trips == 6 }.amount)
                assertEquals(6, h.db.aggregateDao().capturedDay("2026-10-19").single().totalTrips)
                val second = h.corrections.cancel(id, h.now + 1)!!
                val trip = h.db.tripDao().findById(id)!!
                h.db.tripDao().update(trip.copy(lastObservedAt = h.now + 2, cancellationSource = "HOST"))
                assertFalse(h.corrections.undo(second))
            } finally { collecting.cancel() }
        }
    }

    @Test fun `default today follows midnight and future route is clamped without fabricating a day`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm()
            val collecting = launch { vm.uiState.collect() }
            try {
                assertEquals(h.initial.date, vm.await().date)
                h.now = h.initial.date.plusDays(1).atStartOfDay(h.initial.money.window.zone).toInstant().toEpochMilli()
                h.snapshots.retry()
                val next = vm.await { it.date == h.initial.date.plusDays(1) }
                assertEquals("$0", next.amount); assertEquals("Aún no hay viajes hoy", next.quietLine); assertTrue(next.rows.isEmpty())
                val future = h.vm(SavedStateHandle(mapOf("day" to "2099-01-01")))
                assertEquals(next.date, future.await().date)
            } finally { collecting.cancel() }
        }
    }
}
