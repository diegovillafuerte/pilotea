package mx.kompara.ui.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
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
    @Test fun `the six rows observe saved costs car limits basis account and real readiness without reopening`() = runBlocking {
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
            val vm = AjustesViewModel(prefs, running, car, reader, auth, AppClock { now.toEpochMilli() }, zone)
            store.put("vm", vm); vmJob = vm.viewModelScope.coroutineContext[Job]
            collecting = launch { vm.state.collect() }
            suspend fun await(predicate: (AjustesState) -> Boolean) = withTimeout(10000) { vm.state.filterNotNull().first(predicate) }
            val first = await { it.firstPermission }
            assertEquals("Entrar con WhatsApp", first.account)
            assertEquals("$2.84 por km", first.costs)
            assertEquals("$8.00 · $6.00 por km, neto", first.limits)
            prefs.setCity(mx.kompara.data.model.City.CDMX)
            assertEquals("Entrar con WhatsApp · CDMX", await { it.account.endsWith("CDMX") }.account)
            running.saveRunningCosts(RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal("3.75")), now.toEpochMilli(), now.toEpochMilli())
            assertEquals("$3.75 por km", await { it.costs.startsWith("$3.75") }.costs)
            car.save(BigDecimal("1500"), now.atZone(zone).toLocalDate(), now.toEpochMilli())
            assertEquals("$3.75 por km · coche $1,500 a la semana", await { it.costs.contains("coche") }.costs)
            prefs.saveLimits(PlatformThreshold.DEFAULT.copy(redPerKmMxn = 6.5), PreferredMetric.IPK)
            assertEquals("$8.00 · $6.50 por km, neto", await { it.limits.contains("6.50") }.limits)
            reader.observeEnvironment(true, false)
            assertEquals("Preparado · permiso listo", await { it.reader.startsWith("Preparado") }.reader)
            // A car amount that starts later is not in force today.
            now = now.minusSeconds(3 * 86400)
            assertEquals("$3.75 por km", await { !it.costs.contains("coche") }.costs)
        } finally { collecting?.cancelAndJoin(); store.clear(); vmJob?.join(); http.close(); db.close() }
    }

    @Test fun `the limits row says its unit and basis in every combination`() {
        val km = PlatformThreshold(8.0, 200.0, 6.0, 150.0)
        assertEquals("$8.00 · $6.00 por km, neto", ThresholdEditor.summary(LimitDraft.from(km, PreferredMetric.IPK, RateBasis.NET)))
        assertEquals("$200 · $150 por hora, neto", ThresholdEditor.summary(LimitDraft.from(km, PreferredMetric.IPH, RateBasis.NET)))
        val gross = PlatformThreshold(11.0, 280.0, 9.0, 230.0)
        assertEquals("$11.00 · $9.00 por km, bruto", ThresholdEditor.summary(LimitDraft.from(gross, PreferredMetric.IPK, RateBasis.GROSS)))
        assertEquals("$280 · $230 por hora, bruto", ThresholdEditor.summary(LimitDraft.from(gross, PreferredMetric.IPH, RateBasis.GROSS)))
        assertEquals("$315.25 · $150 por hora, neto", ThresholdEditor.summary(LimitDraft.from(km.copy(minPerHourMxn = 315.25), PreferredMetric.IPH)))
        assertEquals("$2.84 por km", costsSummary(BigDecimal("2.8417"), null))
        assertEquals("Sin confirmar · coche $1,500 a la semana", costsSummary(null, BigDecimal("1500")))
    }
}
