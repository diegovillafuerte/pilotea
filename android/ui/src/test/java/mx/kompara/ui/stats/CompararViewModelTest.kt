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
import mx.kompara.billing.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.imports.ImportStatementEntity
import mx.kompara.data.model.City
import mx.kompara.data.settings.*
import mx.kompara.metrics.*
import mx.kompara.metrics.compare.ComparisonMetric
import mx.kompara.sync.aggregate.*
import mx.kompara.sync.api.*
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
class CompararViewModelTest {
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
        val store = ViewModelStore()
        var id = 0
        val jobs = mutableListOf<Job>()
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { throw java.io.IOException("offline") })
        val benchmarks = BenchmarksRepository(db.populationStatDao(), ApiClient(http, "https://test.invalid", { null }, { "test" }))
        val gate = TierGatekeeper(EntitlementRepository(FakeBillingClient(), SubscriptionBackend.NONE,
            object : EntitlementCache { override suspend fun read() = Entitlement.Free; override suspend fun write(entitlement: Entitlement) {} }, BillingLogger.NOOP),
            DebugPremiumSource { flowOf(false) }, PaywallConfigSource { flowOf(true) }, VerificationSource { flowOf(true) })
        suspend fun seed() {
            running.saveRunningCosts(fixture.fixture.schedule.running.single().inputs, 0, 0)
            car.save("1500".toBigDecimal(), day.date.minusWeeks(3), 0)
            day.snapshot.offers.forEach { db.offerDao().insert(it) }
            day.snapshot.trips.forEach { db.tripDao().insert(it) }
            day.snapshot.shifts.forEach { db.shiftDao().insert(it) }
        }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = CompararViewModel(handle, repo, benchmarks, prefs, gate,
            selected, clock, fixture.fixture.zone, AggregateSyncScheduler(context)).also { store.put("${id++}", it); jobs += it.viewModelScope.coroutineContext[Job]!! }
        override fun close() { store.clear(); runBlocking { jobs.forEach { it.join() } }; http.close(); db.close() }
    }
    private suspend fun CompararViewModel.await(predicate: (CompareUiState) -> Boolean = { true }) =
        withTimeout(15_000) { uiState.first { !it.loading && it.comparison != null && predicate(it) } }

    @Test fun `offline own data survives cancellation and midnight no session verified ranking`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm()
            val job = launch { vm.uiState.collect() }
            try {
                val first = vm.await()
                assertEquals(CompareScope.CITY, first.choice.scope)
                assertEquals("UBER", first.comparison!!.singleApp)
                assertEquals("Contamos 6 viajes", first.comparison.qualifier)
                assertEquals("$163/viaje", CompareUiText.rate(first.comparison.values.first().value!!, ComparisonMetric.PER_TRIP))
                assertNull(first.comparison.rank); assertFalse(first.comparison.locked)
                val id = h.day.rows.first { h.day.clock(it) == "11:20" }.offerId
                h.db.tripDao().cancel(id, h.now)
                assertEquals("Contamos 5 viajes", vm.await { it.comparison!!.qualifier == "Contamos 5 viajes" }.comparison!!.qualifier)
                h.now = h.day.date.plusDays(1).atTime(0, 1).atZone(h.fixture.fixture.zone).toInstant().toEpochMilli()
                vm.retry()
                assertEquals(h.day.date.plusDays(1), vm.await { it.today == h.day.date.plusDays(1) }.today)
            } finally { job.cancel() }
        }
    }
    @Test fun `period context sheet and contribution restore without changing limits or requiring consent`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val handle = SavedStateHandle()
            val vm = h.vm(handle)
            val job = launch { vm.uiState.collect() }
            try {
                val original = vm.await()
                assertFalse(original.contributing)
                val limits = h.prefs.settings.first().threshold
                vm.selectCity(City.MONTERREY)
                vm.await { it.choice.city == City.MONTERREY }
                assertEquals(limits, h.prefs.settings.first().threshold)
                vm.setContribution(true)
                assertFalse(vm.uiState.value.contributionAvailable)
                assertFalse(h.prefs.settings.first().shareAggregates)
                // Preserve a prior saved preference without claiming that upload is available.
                h.prefs.setShareAggregates(true)
                val opted = vm.await { it.contributing }
                assertEquals(original.comparison!!.values, opted.comparison!!.values)
                assertFalse(StatementContributionContract.canUpload(opted.contributing))
                vm.step(1); assertNull(h.selected.week.first())
                vm.selectApp("DIDI")
                vm.selectWeek(LocalDate.parse("2026-10-12"))
                vm.await { it.weekStart == LocalDate.parse("2026-10-12") && it.choice.app == "DIDI" }
                vm.openSheet("context")
                val restored = h.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
                assertEquals("context", restored.sheet.value)
                assertEquals("DIDI", restored.await().choice.app)
                assertEquals("2026-10-12", h.selected.week.first())
                assertTrue(restored.await().contributing)
            } finally { job.cancel() }
        }
    }

    @Test fun `stored confirmed statement updates own city row without inventing a cohort or rank`() = runBlocking {
        Harness().use { h ->
            h.seed()
            h.selected.select(LocalDate.parse("2026-10-12"))
            val vm = h.vm(SavedStateHandle(mapOf("compareScope" to CompareScope.CITY.name)))
            val job = launch { vm.uiState.collect() }
            try {
                assertEquals(ComparisonStatus.EMPTY, vm.await().comparison!!.status)
                val statement = ImportStatementEntity("confirmed", "UBER", "2026-10-12", "2026-10-18",
                    "5062", 34, null, "24", "SERVER_CONFIRMED_STATEMENT", "pdf", h.now,
                    "server-proof", "cdmx", "PAYMENT,TRIPS,HOURS")
                h.db.importDao().putStatement(statement)
                val confirmed = vm.await { it.comparison!!.status == ComparisonStatus.NO_COHORT }.comparison!!
                assertEquals("Según tu resumen de Uber", confirmed.qualifier)
                assertEquals("Aún juntamos conductores de Uber para compararte", confirmed.finding)
                assertEquals("$149/viaje", CompareUiText.rate(confirmed.values.single().value!!, ComparisonMetric.PER_TRIP))
                assertNull(confirmed.rank); assertFalse(confirmed.locked)
                // Removing certification must invalidate the displayed proof, even with the same money.
                h.db.importDao().putStatement(statement.copy(source = "USER_CORRECTED_IMPORT", certificationId = null))
                val unconfirmed = vm.await { it.comparison!!.status != ComparisonStatus.NO_COHORT }.comparison!!
                assertNull(unconfirmed.rank)
                assertFalse(unconfirmed.qualifier.startsWith("Según tu resumen"))
                assertTrue(unconfirmed.finding.isNotBlank())
            } finally { job.cancel() }
        }
    }
}
