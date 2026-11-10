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
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.data.model.City
import mx.kompara.data.settings.*
import mx.kompara.metrics.PeriodMoneyRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** Comparar's wiring on a real Room ledger: the 30-trip gate, the driver's unit and basis, the tab and the city. */
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
        val zone: ZoneId = ZoneId.of("America/Mexico_City")
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        val costs = CostProfileRepository(db.costProfileDao())
        val repo = PeriodMoneyRepository(db, costs, CarCostRepository(db.carCostDao()))
        val settings = SettingsRepository(PreferencesMemory())
        val clock = AppClock { at("2026-10-19T14:40") }
        val store = ViewModelStore()
        private var id = 1L
        private var vms = 0
        fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
        suspend fun taken(app: String, local: String, fare: Double, km: Double, minutes: Double = 20.0) {
            val n = id++
            db.offerDao().insert(OfferEntity(id = n, seenAt = at(local) - 60_000, platform = app, fareMxn = fare, distanceKm = km,
                durationMin = minutes, sessionId = "s$n", outcome = "ACCEPTED"))
            db.tripDao().insert(TripEntity(id = n, offerId = n, startedAt = at(local), endedAt = at(local) + 600_000, platform = app,
                grossMxn = fare, distanceKm = km, durationMin = minutes))
        }
        suspend fun seed(trips: Int) {
            costs.saveRunningCosts(RunningCostInputs.TYPICAL, effectiveFrom = 0, recordedAt = 0) // $2.84 per km
            repeat(trips) { i -> taken(if (i % 2 == 0) "UBER" else "DIDI", "2026-10-%02dT%02d:00".format(5 + i % 10, 8 + i % 12), 150.0, 12.0) }
        }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = CompararViewModel(handle, repo, settings, clock, zone).also { store.put("${vms++}", it) }
        override fun close() { store.clear(); db.close() }
    }
    private suspend fun CompararViewModel.await(predicate: (CompararUiState) -> Boolean) = withTimeout(15_000) { uiState.first(predicate) }

    @Test fun `under thirty taken trips Tú gathers and counts`() = runBlocking {
        Harness().use { h ->
            h.seed(29)
            h.taken("UBER", "2026-10-19T09:00", 150.0, 12.0) // today stays out of the window
            val state = h.vm().await { it.tu != null }
            assertEquals(CompararTab.TU, state.tab)
            assertEquals(29, (state.tu as TuPresentation.Gathering).trips)
        }
    }

    @Test fun `thirty trips compare in the driver's unit and basis`() = runBlocking {
        Harness().use { h ->
            h.seed(30)
            val vm = h.vm()
            val k1 = vm.await { it.tu is TuPresentation.Ready }.tu as TuPresentation.Ready
            assertEquals("Tus viajes · últimas 4 semanas · por km, neto", k1.header)
            assertEquals(listOf("Uber", "DiDi"), k1.apps.bars.map { it.label }) // equal rates keep the platform order
            assertEquals("Uber y DiDi te dejan lo mismo por km.", k1.apps.finding)
            h.settings.saveLimits(PlatformThreshold(11.0, 280.0, 9.0, 230.0), PreferredMetric.IPH, RateBasis.GROSS)
            val hour = vm.await { (it.tu as? TuPresentation.Ready)?.header?.endsWith("bruto") == true }.tu as TuPresentation.Ready
            assertEquals("Tus viajes · últimas 4 semanas · por hora, bruto", hour.header)
            assertEquals("\$450", hour.apps.bars.first().value) // 150 × 60 / 20 min
        }
    }

    @Test fun `the tab survives and Tu ciudad names the chosen city only`() = runBlocking {
        Harness().use { h ->
            val handle = SavedStateHandle()
            val vm = h.vm(handle)
            vm.selectTab(CompararTab.CIUDAD)
            assertNull(vm.await { it.tab == CompararTab.CIUDAD }.city)
            assertEquals(CompararTab.CIUDAD, h.vm(handle).await { true }.tab)
            h.settings.setCity(City.MONTERREY)
            assertEquals("Monterrey", vm.await { it.city != null }.city)
        }
    }
}
