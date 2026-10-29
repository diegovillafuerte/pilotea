package mx.kompara.ui.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.billing.*
import mx.kompara.data.accounting.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.service.*
import mx.kompara.data.settings.*
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.sync.api.ApiClient
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesViewModelTest {
    @Before fun before() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun after() = Dispatchers.resetMain()
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    @Test fun `settings observes saved costs car limits theme and real readiness without reopening`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { throw java.io.IOException("offline") })
        val store = ViewModelStore()
        var collecting: Job? = null
        var vmJob: Job? = null
        try {
            val zone = ZoneId.of("America/Mexico_City")
            var now = LocalDate.parse("2026-10-19").atTime(7, 30).atZone(zone).toInstant()
            val running = CostProfileRepository(db.costProfileDao()); val car = CarCostRepository(db.carCostDao())
            running.saveRunningCosts(RunningCostInputs.TYPICAL, now.toEpochMilli(), now.toEpochMilli(), CostConfirmation.TYPICAL)
            val prefs = SettingsRepository(Memory())
            val auth = AuthRepository(Memory(), ApiClient(http, "https://test.invalid", { null }, { "test" }), kotlinx.serialization.json.Json, devBypassEnabled = false)
            val reader = ReaderReadinessRepository(object : ReaderMemoryStore {
                override fun load() = ReaderMemory()
                override fun save(memory: ReaderMemory) {}
            }, Clock.fixed(now, zone))
            reader.observeEnvironment(false, false)
            val entitlement = EntitlementRepository(FakeBillingClient(), SubscriptionBackend.NONE,
                object : EntitlementCache { override suspend fun read() = Entitlement.Free; override suspend fun write(entitlement: Entitlement) {} }, BillingLogger.NOOP)
            val vm = AjustesViewModel(prefs, running, car, reader, auth, entitlement, AppClock { now.toEpochMilli() }, zone)
            store.put("vm", vm); vmJob = vm.viewModelScope.coroutineContext[Job]
            collecting = launch { vm.state.collect() }
            suspend fun await(predicate: (AjustesState) -> Boolean) = withTimeout(10000) { vm.state.filterNotNull().first(predicate) }
            val first = await { it.firstPermission }
            assertEquals("Entrar con WhatsApp", first.account); assertNull(first.accountDetail)
            assertEquals("Plan gratis", first.plan)
            assertNull(first.planDetail); assertEquals("Sin agregar", first.car)
            assertTrue(first.costs.endsWith("típicos, sin confirmar"))
            running.saveRunningCosts(RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal("3.75")), now.toEpochMilli(), now.toEpochMilli())
            assertEquals("$3.75 por km · confirmados hoy", await { it.costs.startsWith("$3.75") }.costs)
            car.save(BigDecimal("1500"), now.atZone(zone).toLocalDate(), now.toEpochMilli())
            assertEquals("$1,500 a la semana", await { it.car.startsWith("$1,500") }.car)
            prefs.saveLimits(PlatformThreshold.DEFAULT.copy(redPerKmMxn = 6.5), PreferredMetric.IPK)
            assertTrue(await { it.limits.contains("6.50") }.limits.contains("8.00"))
            vm.setThemePreference(ThemePreference.DARK); await { it.theme == ThemePreference.DARK }
            reader.observeEnvironment(true, false)
            assertEquals("Preparado · permiso listo", await { it.reader.startsWith("Preparado") }.reader)
            now = now.plusSeconds(86400)
            assertTrue(await { it.costs.endsWith("confirmados ayer") }.costs.contains("3.75"))
        } finally { collecting?.cancelAndJoin(); store.clear(); vmJob?.join(); http.close(); db.close() }
    }
}
