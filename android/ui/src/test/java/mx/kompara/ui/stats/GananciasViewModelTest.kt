package mx.kompara.ui.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.service.*
import mx.kompara.data.settings.*
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.ui.components.StatusGlyph
import mx.kompara.ui.fixtures.GananciasFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate

/** The Ganancias tab on real Room, DataStore and reader sources: the status line, Hoy's body and Semana. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GananciasViewModelTest {
    @Before fun before() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun after() = Dispatchers.resetMain()
    private class PreferencesMemory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private class Harness : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixture = GananciasFixture(context)
        val day = fixture.day()
        val zone = fixture.zone
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        val running = CostProfileRepository(db.costProfileDao())
        val car = CarCostRepository(db.carCostDao())
        val repo = PeriodMoneyRepository(db, running, car)
        val prefs = SettingsRepository(PreferencesMemory())
        val selected = SelectedPeriod(prefs)
        var now = fixture.afternoon.toEpochMilli()
        val clock = AppClock { now }
        val snapshots = TodaySnapshots(clock, repo, zone)
        val reader = ReaderReadinessRepository(object : ReaderMemoryStore {
            private var value = ReaderMemory()
            override fun load() = value
            override fun save(memory: ReaderMemory) { value = memory }
        }, Clock.fixed(fixture.afternoon, zone))
        val store = ViewModelStore()
        var id = 0
        suspend fun seed(offers: Boolean = true) {
            running.saveRunningCosts(fixture.fixture.schedule.running.single().inputs, 0, 0)
            car.save("1500".toBigDecimal(), fixture.date.minusWeeks(3), 0)
            if (!offers) return
            day.offers.forEach { db.offerDao().insert(it) }
            day.trips.forEach { db.tripDao().insert(it) }
            day.shifts.forEach { db.shiftDao().insert(it) }
        }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = GananciasViewModel(handle, selected, snapshots, reader, prefs, repo, clock, zone)
            .also { store.put("${id++}", it); jobs += it.viewModelScope.coroutineContext[Job]!! }
        private val jobs = mutableListOf<Job>()
        // Wait for the cleared ViewModels' Room work to finish before @After resets Dispatchers.Main (a concurrent
        // resume on Main while it is reset fails the test under load).
        override fun close() { store.clear(); runBlocking { jobs.joinAll() }; db.close() }
    }
    private suspend fun GananciasViewModel.await(predicate: (GananciasUiState) -> Boolean) = withTimeout(15_000) { uiState.first(predicate) }
    private suspend fun GananciasViewModel.awaitWeek(predicate: (WeekPresentation) -> Boolean = { true }) =
        withTimeout(15_000) { week.mapNotNull { it.week }.first(predicate) }

    @Test fun `the status line and Hoy's body follow the live reader and the ledger`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm()
            val job = launch { vm.uiState.collect() }
            try {
                assertEquals(HoyMode.LOADING, vm.uiState.value.mode)
                h.reader.observeEnvironment(true, false); h.reader.supportedApps(setOf(ReaderReadinessReducer.UBER, ReaderReadinessReducer.DIDI))
                h.reader.bound(); h.reader.captureRunning(true)
                // Offers in the ledger end the first day even before the reader proves itself today.
                val prepared = vm.await { it.mode == HoyMode.DAY }
                assertEquals(GananciasStatus("Semáforo preparado", "permiso listo", StatusGlyph.WAITING, true), prepared.status)
                h.reader.readOffer(ReaderReadinessReducer.UBER, "live", h.fixture.at(h.fixture.date, "14:32").toEpochMilli(), h.reader.captureToken())
                assertEquals("Semáforo funcionando", vm.await { it.status?.glyph == StatusGlyph.HEALTHY }.status!!.title)
                h.reader.observeEnvironment(false, false)
                val off = vm.await { it.mode == HoyMode.OFF }
                assertEquals(GananciasStatus("Semáforo apagado", "desde 14:40", StatusGlyph.OFF, false), off.status)
                assertEquals("Volver a dar el permiso", off.off!!.primary)
                assertEquals("Hoy llevas $762.", off.today!!.text)
            } finally { job.cancel() }
        }
    }

    @Test fun `a stored pause shows the stopped reader and turning it on clears the pause`() = runBlocking {
        Harness().use { h ->
            h.seed()
            h.reader.observeEnvironment(true, false); h.reader.supportedApps(setOf(ReaderReadinessReducer.UBER, ReaderReadinessReducer.DIDI))
            h.reader.bound(); h.reader.captureRunning(true)
            h.reader.readOffer(ReaderReadinessReducer.UBER, "live", h.fixture.at(h.fixture.date, "14:32").toEpochMilli(), h.reader.captureToken())
            val vm = h.vm()
            val job = launch { vm.uiState.collect() }
            try {
                assertEquals(HoyMode.DAY, vm.await { it.mode == HoyMode.DAY }.mode)
                // Below Android 11 the screen-capture service's own "Detener" stores a pause.
                h.reader.pauseUntilTomorrow()
                val stopped = vm.await { it.mode == HoyMode.OFF }
                assertEquals(OffAction.RESUME, stopped.off!!.action)
                assertEquals("Detuviste el semáforo", stopped.off!!.headline)
                vm.resume()
                assertEquals(HoyMode.DAY, vm.await { it.mode == HoyMode.DAY }.mode)
                assertFalse(h.reader.state.value.paused)
            } finally { job.cancel() }
        }
    }

    @Test fun `a phone that never read an offer shows the first day`() = runBlocking {
        Harness().use { h ->
            h.seed(offers = false)
            h.reader.observeEnvironment(true, false); h.reader.bound()
            val vm = h.vm()
            val job = launch { vm.uiState.collect() }
            try {
                val first = vm.await { it.mode == HoyMode.FIRST_DAY }
                assertEquals("Tu meta $8.00 · tu mínimo $6.00", first.firstDay!!.limits)
                assertEquals("Manejar te cuesta $2.84 por km", first.firstDay!!.cost)
                h.prefs.saveLimits(PlatformThreshold(9.0, 200.0, 6.5, 150.0), PreferredMetric.IPK)
                assertEquals("Por km, neto", vm.await { it.firstDay?.limits == "Tu meta $9.00 · tu mínimo $6.50" }.firstDay!!.limitsDetail)
            } finally { job.cancel() }
        }
    }

    @Test fun `week and today share as of money Room invalidations and calendar midnight`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm()
            val job = launch { vm.week.collect() }
            try {
                val week = vm.awaitWeek()
                assertTrue(week.open); assertEquals("$762", week.amount)
                assertEquals(h.fixture.afternoon, week.money.window.asOf)
                assertEquals("$762 de $1,500", week.car!!.amount)
                assertEquals(listOf(true, false, false, false, false, false, false), week.bars.map { it.elapsed })
                val trip = h.day.offers.first { mx.kompara.ui.format.Formatters.formatClock(it.seenAt, h.zone) == "11:20" }.id
                h.db.tripDao().cancel(trip, h.now)
                assertEquals(5, vm.awaitWeek { it.money.trips == 5 }.money.trips)
                h.now = h.fixture.date.plusDays(1).atTime(0, 1).atZone(h.zone).toInstant().toEpochMilli()
                h.snapshots.retry()
                val next = vm.awaitWeek { it.today == h.fixture.date.plusDays(1) }
                assertEquals("$0", next.bars[1].value); assertEquals(0f, next.bars[1].fraction)
                // Nothing is charged per day: the bill is the week's, whole.
                assertEquals("$629 de $1,500", next.car!!.amount)
            } finally { job.cancel() }
        }
    }

    @Test fun `period week and sheet survive recreation the future is blocked and a dated route opens Semana once`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val handle = SavedStateHandle()
            val vm = h.vm(handle)
            vm.awaitWeek()
            assertEquals(0, vm.period.value)
            vm.step(1)
            assertNull(h.selected.week.first())
            vm.step(-1)
            assertEquals(LocalDate.parse("2026-10-12"), vm.awaitWeek { it.start == LocalDate.parse("2026-10-12") }.start)
            vm.selectPeriod(1); vm.showWeekCalculation(true)
            val restored = h.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            assertEquals(1, restored.period.value); assertTrue(restored.weekSheet.value)
            assertEquals("Ganaste", restored.awaitWeek().label)
            restored.step(1)
            assertTrue(restored.awaitWeek { it.start == h.fixture.date }.open)
            val routed = h.vm(SavedStateHandle(mapOf(GananciasViewModel.ARG_WEEK_START to "2026-10-14")))
            assertEquals(1, routed.period.value)
            assertEquals(LocalDate.parse("2026-10-12"), routed.awaitWeek { it.start == LocalDate.parse("2026-10-12") }.start)
            assertEquals("2026-10-12", h.selected.week.first())
        }
    }

    @Test fun `legacy imports cannot replace another app or draw false bars`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val window = h.day.money.window.copy(endInclusive = h.fixture.date.plusDays(6))
            val before = h.repo.weekSnapshot(window)
            val imported = WeeklyAggregateEntity("UBER", h.fixture.date.toString(), "IMPORTED", 9000.0, 9999.0, 6, 0.0, 22.0, null, null, null, null, null, h.now)
            h.db.aggregateDao().upsertWeekly(listOf(imported, imported.copy(platform = "DIDI")))
            val after = h.repo.observeWeek(window).first()
            assertEquals(before.money.tripContribution, after.money.tripContribution)
            assertEquals(before.days, after.days)
            assertEquals(WeekPresentation(before).bars, WeekPresentation(after).bars)
            assertTrue(statementDifferences(after.money, after.statements).isEmpty())
            assertTrue(after.money.paymentOnly.isEmpty())
            assertEquals(setOf("UBER", "DIDI"), after.money.excluded.map { it.app }.toSet())
        }
    }

    @Test fun `the notification dialog is due once a first run left the reader on and never after it was shown`() = runBlocking {
        Harness().use { h ->
            val vm = h.vm()
            val job = launch { vm.askNotifications.collect() }
            try {
                suspend fun due() = withTimeout(15_000) { vm.askNotifications.first { it } }
                h.prefs.setOnboardingCompleted(true); h.reader.observeEnvironment(false, false)
                assertNull("the first run left the reader off", withTimeoutOrNull(300) { vm.askNotifications.first { it } })
                h.reader.observeEnvironment(true, false); due()
                vm.notificationsAsked()
                withTimeout(15_000) { vm.askNotifications.first { !it } }
                assertTrue(h.prefs.notificationsAsked.first())
                val again = h.vm()
                val second = launch { again.askNotifications.collect() }
                assertNull("the next visit does not ask again", withTimeoutOrNull(300) { again.askNotifications.first { it } })
                second.cancel()
            } finally { job.cancel() }
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
