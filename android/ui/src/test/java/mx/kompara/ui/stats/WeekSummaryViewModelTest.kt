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
import mx.kompara.billing.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.*
import mx.kompara.data.settings.*
import mx.kompara.metrics.*
import mx.kompara.ui.fixtures.DayFixture
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekSummaryViewModelTest {
    @Before fun before() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun after() = Dispatchers.resetMain()
    private class PreferencesMemory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private class Harness : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixture = DayFixture(context)
        val day = fixture.day()
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        val running = CostProfileRepository(db.costProfileDao())
        val car = CarCostRepository(db.carCostDao())
        val repo = PeriodMoneyRepository(db, running, car)
        val prefs = SettingsRepository(PreferencesMemory())
        val selected = SelectedPeriod(prefs)
        var now = day.money.window.asOf.toEpochMilli()
        val clock = AppClock { now }
        val snapshots = TodaySnapshots(clock, repo, fixture.fixture.zone)
        val store = ViewModelStore()
        var id = 0
        val gate = TierGatekeeper(EntitlementRepository(FakeBillingClient(), SubscriptionBackend.NONE,
            object : EntitlementCache { override suspend fun read() = Entitlement.Free; override suspend fun write(entitlement: Entitlement) {} }, BillingLogger.NOOP),
            DebugPremiumSource { flowOf(false) }, PaywallConfigSource { flowOf(true) })
        suspend fun seed() {
            running.saveRunningCosts(fixture.fixture.schedule.running.single().inputs, 0, 0)
            car.save("1500".toBigDecimal(), day.date.minusWeeks(3), 0)
            day.snapshot.offers.forEach { db.offerDao().insert(it) }
            day.snapshot.trips.forEach { db.tripDao().insert(it) }
            day.snapshot.shifts.forEach { db.shiftDao().insert(it) }
        }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = WeekSummaryViewModel(handle, selected, snapshots, repo, gate, clock,
            fixture.fixture.zone, EarningsConnectivity(context)).also { store.put("${id++}", it) }
        override fun close() { store.clear(); db.close() }
    }
    private suspend fun WeekSummaryViewModel.await(predicate: (WeekPresentation) -> Boolean = { true }) =
        withTimeout(15_000) { uiState.mapNotNull { it.week }.first(predicate) }

    @Test fun `week and today share as of money Room invalidations and calendar midnight`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm()
            val job = launch { vm.uiState.collect() }
            try {
                val week = vm.await()
                assertEquals("$548", week.amount)
                assertEquals(h.day.money.window.asOf, week.money.window.asOf)
                assertEquals(week.money.periodResult, week.days.first()!!.periodResult)
                assertEquals(6, week.days.count { it == null })
                val id = h.day.rows.first { h.day.clock(it) == "11:20" }.offerId
                h.db.tripDao().cancel(id, h.now)
                assertEquals(5, vm.await { it.money.trips == 5 }.money.trips)
                h.now = h.day.date.plusDays(1).atTime(0, 1).atZone(h.fixture.fixture.zone).toInstant().toEpochMilli()
                h.snapshots.retry()
                val nextDay = vm.await { it.today == h.day.date.plusDays(1) }
                assertEquals(2, nextDay.money.carCharges.size)
                assertEquals("−$214", nextDay.bars[1].display)
            } finally { job.cancel() }
        }
    }
    @Test fun `legacy imports cannot replace another app or create false comparison or bars`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val window = h.day.money.window.copy(endInclusive = h.day.date.plusDays(6))
            val before = h.repo.weekSnapshot(window)
            val imported = WeeklyAggregateEntity("UBER", h.day.date.toString(), "IMPORTED", 9000.0, 9999.0, 6, 0.0, 22.0, null, null, null, null, null, h.now)
            h.db.aggregateDao().upsertWeekly(listOf(imported, imported.copy(platform = "DIDI")))
            val after = h.repo.observeWeek(window).first()
            assertEquals(before.money.periodResult, after.money.periodResult)
            assertEquals(before.days, after.days)
            assertTrue(WeekPresentation(after).differences.isEmpty())
            // A full undated statement cannot enter an unfinished week.
            assertTrue(after.money.paymentOnly.isEmpty())
            assertEquals(setOf("UBER", "DIDI"), after.money.excluded.map { it.app }.toSet())
            assertEquals(1, after.money.carCharges.size)
            val closed = h.repo.weekSnapshot(window.copy(asOf = h.day.date.plusWeeks(1).atStartOfDay(h.fixture.fixture.zone).toInstant()))
            assertEquals(listOf("DIDI"), closed.money.paymentOnly.map { it.record.app })
            assertEquals(listOf("UBER"), closed.money.excluded.map { it.app })
            assertEquals(7, closed.money.carCharges.size)
        }
    }
    @Test fun `shared week and active sheet persist recreation and future step is blocked`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val handle = SavedStateHandle()
            val vm = h.vm(handle)
            vm.await()
            vm.step(1)
            assertNull(h.selected.week.first())
            vm.selectWeek(LocalDate.parse("2026-10-12"))
            vm.await { it.start == LocalDate.parse("2026-10-12") }
            vm.openSheet("calculation")
            val selectedAfterProcessRestore = SelectedPeriod(h.prefs)
            assertEquals("2026-10-12", selectedAfterProcessRestore.week.first())
            val restored = h.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            assertEquals("calculation", restored.sheet.value)
            assertEquals(LocalDate.parse("2026-10-12"), restored.await().start)
            restored.closeSheet()
            assertEquals("2026-10-12", h.selected.week.first())
            h.selected.followCurrentWeek()
            assertEquals(h.day.date, restored.await { it.start == h.day.date }.start)
        }
    }
    @Test fun `selected week survives closing and reopening the disk preferences store`() = runBlocking {
        val file = java.io.File.createTempFile("s9-period", ".preferences_pb").also { it.delete() }
        fun dataStore(scope: CoroutineScope) = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = SelectedPeriod(SettingsRepository(dataStore(firstScope)))
            first.select(LocalDate.parse("2026-10-15"))
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val second = SelectedPeriod(SettingsRepository(dataStore(secondScope)))
                assertEquals("2026-10-12", second.week.first())
                second.followCurrentWeek()
                assertNull(second.week.first())
            } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
        } finally { firstScope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }

}
