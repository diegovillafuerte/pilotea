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
class FiscalViewModelTest {
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
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { error("Month values must never request the network") })
        val config = mx.kompara.sync.fiscal.FiscalConfigRepository(db.fiscalConfigDao(), mx.kompara.sync.api.ApiClient(http,
            "https://unused.invalid", mx.kompara.sync.api.TokenProvider { null }, mx.kompara.sync.api.DeviceIdProvider { "test" }))
        val exporter = mx.kompara.ui.fiscal.FiscalReportExporter(context, mx.kompara.ui.fiscal.FiscalPdfWriter(context, mx.kompara.ui.export.ExportHarness().cache),
            mx.kompara.ui.fiscal.FiscalPdfSharer(context, prefs))
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = FiscalViewModel(handle, selected, snapshots, repo, gate, config, clock,
            fixture.fixture.zone, exporter, EarningsConnectivity(context)).also { store.put("${id++}", it) }
        override fun close() { store.clear(); http.close(); db.close() }
    }
    private suspend fun FiscalViewModel.await(predicate: (MonthPresentation) -> Boolean = { true }) =
        withTimeout(15_000) { uiState.mapNotNull { it.period }.first(predicate) }

    @Test fun `month observes atomic local money cancellation and midnight without fiscal network`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm()
            val job = launch { vm.uiState.collect() }
            try {
                val month = vm.await()
                assertEquals(19, month.money.carCharges.size)
                assertEquals(6, month.money.trips)
                assertEquals(h.day.money.window.asOf, month.money.window.asOf)
                assertEquals("$979", mx.kompara.ui.format.Formatters.formatMxnWhole(vm.uiState.value.payments.single().amount!!))
                val id = h.day.rows.first { h.day.clock(it) == "11:20" }.offerId
                h.db.tripDao().cancel(id, h.now)
                assertEquals(5, vm.await { it.money.trips == 5 }.money.trips)
                h.now = h.day.date.plusDays(1).atTime(0, 1).atZone(h.fixture.fixture.zone).toInstant().toEpochMilli()
                h.snapshots.retry()
                assertEquals(20, vm.await { it.today == h.day.date.plusDays(1) }.money.carCharges.size)
                assertFalse(vm.regulatoryEnabled)
            } finally { job.cancel() }
        }
    }
    @Test fun `shared month persists while sheets restore and comparison receives last closed week`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val handle = SavedStateHandle()
            val vm = h.vm(handle)
            vm.await()
            vm.step(1)
            assertNull(h.selected.month.first())
            vm.selectComparisonWeek()
            assertEquals("2026-10-12", h.selected.week.first())
            vm.selectMonth(java.time.YearMonth.parse("2026-09"))
            vm.await { it.month.toString() == "2026-09" }
            assertEquals("2026-09-21", h.selected.week.first())
            vm.openSheet("calculation")
            val restored = h.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            assertEquals("calculation", restored.sheet.value)
            assertEquals("2026-09", restored.await().month.toString())
            restored.closeSheet()
            assertEquals("2026-09", h.selected.month.first())
        }
    }
    @Test fun `Room legacy import is unconfirmed payment only and crossing scope never enters IMSS month`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val row = WeeklyAggregateEntity("UBER", "2026-09-14", "IMPORTED", 99999.0, 5340.0, 29, 0.0, 26.0, null, null, null, null, null, h.now)
            h.db.aggregateDao().upsertWeekly(listOf(row))
            val vm = h.vm()
            vm.await()
            vm.selectMonth(java.time.YearMonth.parse("2026-09"))
            val imported = vm.await { it.importOnly }
            assertEquals("$5,340", imported.amount)
            assertNull(imported.money.periodResult)
            assertTrue(vm.uiState.value.payments.single().unconfirmed)
            h.db.aggregateDao().upsertWeekly(listOf(row.copy(platform = "DIDI", weekStart = "2026-09-28")))
            val crossing = vm.await { it.snapshot.unallocated.isNotEmpty() }
            assertEquals(listOf("UBER"), ImssTracker.payments(crossing.money).map { it.app })
            assertEquals("2026-09-28", crossing.snapshot.unallocated.single().coverage.start.toString())
        }
    }
    @Test fun `selected month and its comparison week survive disk store reopen together`() = runBlocking {
        val file = java.io.File.createTempFile("s10-period", ".preferences_pb").also { it.delete() }
        fun store(scope: CoroutineScope) = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            SelectedPeriod(SettingsRepository(store(firstScope))).selectMonth(java.time.YearMonth.parse("2026-09"), LocalDate.parse("2026-10-19"))
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val restored = SelectedPeriod(SettingsRepository(store(secondScope)))
                assertEquals("2026-09", restored.month.first())
                assertEquals("2026-09-21", restored.week.first())
            } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
        } finally { firstScope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
