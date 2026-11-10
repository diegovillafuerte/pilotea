package mx.kompara.ui.stats

import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.data.accounting.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.settings.*
import mx.kompara.metrics.rollup.RollupRecomputer
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
class CostEditorsViewModelTest {
    @Before fun before() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun after() = Dispatchers.resetMain()
    private class Harness : AutoCloseable {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        var failSave = false
        val costs = CostProfileRepository(object : mx.kompara.data.db.dao.CostProfileDao by db.costProfileDao() {
            override suspend fun saveVersion(profile: mx.kompara.data.db.entity.CostProfileEntity,
                version: mx.kompara.data.db.entity.CostVersionEntity, correctEfficiency: Boolean): Long {
                if (failSave) throw java.io.IOException("disk full")
                return db.costProfileDao().saveVersion(profile, version, correctEfficiency)
            }
        })
        val car = CarCostRepository(db.carCostDao())
        val zone = ZoneId.of("America/Mexico_City")
        val now = LocalDate.parse("2026-10-19").atTime(7, 30).atZone(zone).toInstant()
        var failRollup = false
        val trips = object : mx.kompara.data.db.dao.TripDao by db.tripDao() {
            override suspend fun startedBetween(from: Long, until: Long): List<mx.kompara.data.db.entity.TripEntity> {
                if (failRollup) throw java.io.IOException("read failed")
                return db.tripDao().startedBetween(from, until)
            }
        }
        val rollup = RollupRecomputer(db, db.offerDao(), trips, db.shiftDao(), db.aggregateDao(), costs, Clock.fixed(now, zone))
        val store = ViewModelStore()
        val jobs = mutableListOf<Job>()
        fun <T : ViewModel> track(vm: T) = vm.also { store.put(jobs.size.toString(), it); jobs += it.viewModelScope.coroutineContext[Job]!! }
        val prefs = SettingsRepository(object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences) =
                transform(data.value).also { data.value = it }
        })
        fun costVm(handle: SavedStateHandle = SavedStateHandle()) = track(CostProfileViewModel(costs, rollup, AppClock { now.toEpochMilli() }, zone, handle, prefs, car))
        fun carVm(handle: SavedStateHandle = SavedStateHandle()) = track(CarCostViewModel(car, costs, rollup, AppClock { now.toEpochMilli() }, zone, handle))
        suspend fun seed() { costs.saveRunningCosts(RunningCostInputs.TYPICAL, now.minusSeconds(86400).toEpochMilli(), now.minusSeconds(86400).toEpochMilli(), CostConfirmation.TYPICAL) }
        override fun close() { store.clear(); runBlocking { jobs.forEach { it.join() } }; db.close() }
    }
    private suspend fun CostProfileViewModel.ready() = withTimeout(10000) { state.first { it.saved != null } }
    @Test fun `draft recreation direct mode and electric values survive save and reopen`() = runBlocking {
        Harness().use { h ->
            h.seed(); val handle = SavedStateHandle(); val vm = h.costVm(handle); vm.ready()
            vm.open(CostField.FUEL); vm.edit(vm.state.value.draft!!.copy(isEv = true, kwhPer100Km = "17.32", costPerKwhMxn = "4.21"))
            val restored = h.costVm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })); restored.ready()
            assertEquals(vm.state.value.draft, restored.state.value.draft)
            assertEquals(1, h.costs.runningVersions().size)
            restored.save(); withTimeout(10000) { restored.state.first { it.field == null && !it.typical } }
            val loaded = h.costVm(); loaded.ready()
            assertTrue(loaded.state.value.saved!!.isEv)
            assertEquals("17.32", loaded.state.value.saved!!.kwhPer100Km)
            loaded.open(CostField.DIRECT); loaded.edit(loaded.state.value.draft!!.copy(directPerKm = "0")); loaded.save()
            withTimeout(10000) { loaded.state.first { it.field == null } }
            val direct = h.costVm(); direct.ready()
            assertTrue(direct.state.value.saved!!.direct)
            assertEquals(BigDecimal.ZERO, CostPreview.compute(direct.state.value.saved!!)!!.perKm)
            assertEquals("17.32", direct.state.value.saved!!.kwhPer100Km)
        }
    }
    @Test fun `efficiency asks before any write and price change never corrects history`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.costVm(); vm.ready(); vm.open(CostField.EFFICIENCY)
            vm.edit(vm.state.value.draft!!.copy(rendimientoKmPerLitre = "16")); vm.save()
            assertTrue(vm.state.value.confirmHistory); assertEquals(1, h.costs.runningVersions().size)
            vm.dismissHistory(); vm.save(applyHistory = false)
            withTimeout(10000) { vm.state.first { it.field == null } }
            assertEquals(2, h.costs.runningVersions().size)
            assertTrue(h.costs.runningVersions().none { it.correctsVersionId != null })
            vm.open(CostField.PRICE); vm.edit(vm.state.value.draft!!.copy(gasPricePerLitreMxn = "30")); vm.save()
            withTimeout(10000) { vm.state.first { it.field == null } }
            assertFalse(vm.state.value.confirmHistory)
            assertTrue(h.costs.runningVersions().none { it.correctsVersionId != null })
        }
    }
    @Test fun `explicit correction keeps judgement versions and confirms only active profile`() = runBlocking {
        Harness().use { h ->
            h.seed(); val original = h.costs.runningVersions().single()
            val vm = h.costVm(); vm.ready(); vm.open(CostField.EFFICIENCY)
            vm.edit(vm.state.value.draft!!.copy(rendimientoKmPerLitre = "16")); vm.save(); vm.save(applyHistory = true)
            withTimeout(10000) { vm.state.first { it.field == null } }
            val schedule = CostSchedule(h.costs.runningVersions())
            assertEquals(original, schedule.running.single { it.id == original.id })
            assertEquals(BigDecimal("16"), schedule.accountingVersion(original.id)!!.inputs.kmPerLitre)
            assertEquals(CostConfirmation.CONFIRMED, schedule.runningAt(h.now.toEpochMilli())!!.confirmation)
            assertEquals(CostConfirmation.TYPICAL, schedule.accountingVersion(original.id)!!.confirmation)
        }
    }
    @Test fun `invalid edits and dismissal never save and A5 confirmation returns only on success`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.costVm(); vm.ready(); vm.open(CostField.EFFICIENCY)
            vm.edit(vm.state.value.draft!!.copy(rendimientoKmPerLitre = "41")); vm.save()
            assertEquals("Revisa este número", vm.state.value.error); assertFalse(vm.state.value.finished)
            vm.dismiss(); assertEquals("12", vm.state.value.draft!!.rendimientoKmPerLitre)
            assertEquals(1, h.costs.runningVersions().size)
            vm.save(finish = true)
            withTimeout(10000) { vm.state.first { it.finished } }
            assertEquals(2, h.costs.runningVersions().size); assertFalse(vm.state.value.typical)
        }
    }
    @Test fun `car drafts dates save and removal keep their actual scope`() = runBlocking {
        Harness().use { h ->
            h.seed(); val handle = SavedStateHandle(); val vm = h.carVm(handle); vm.open()
            withTimeout(10000) { vm.state.first { it.open } }
            assertFalse(vm.state.value.canRemove); assertEquals("", vm.state.value.amount)
            vm.amount("1500"); vm.date(LocalDate.parse("2026-09-28"))
            val restored = h.carVm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            withTimeout(10000) { restored.state.first { it.open } }
            assertEquals("1500", restored.state.value.amount); assertEquals(vm.state.value.from, restored.state.value.from)
            restored.save(); withTimeout(10000) { restored.state.first { !it.open } }
            assertEquals(LocalDate.parse("2026-09-28"), h.car.get().single().effectiveFrom)
            restored.open(); withTimeout(10000) { restored.state.first { it.open } }
            restored.save(remove = true); withTimeout(10000) { restored.state.first { !it.open } }
            val schedule = CostSchedule(emptyList(), h.car.get())
            assertNotNull(schedule.carAt(LocalDate.parse("2026-10-18"))!!.weeklyMxn)
            assertNull(schedule.carAt(LocalDate.parse("2026-10-19"))!!.weeklyMxn)
        }
    }
    @Test fun `failed save retains draft and offers retry without a false return`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.costVm(); vm.ready(); vm.open(CostField.PRICE)
            vm.edit(vm.state.value.draft!!.copy(gasPricePerLitreMxn = "26.25")); h.failSave = true
            vm.save(finish = true)
            withTimeout(10000) { vm.state.first { it.error != null } }
            assertFalse(vm.state.value.finished); assertFalse(vm.state.value.saving)
            assertEquals("26.25", vm.state.value.draft!!.gasPricePerLitreMxn)
            assertEquals(1, h.costs.runningVersions().size)
            h.failSave = false; vm.save(finish = true)
            withTimeout(10000) { vm.state.first { it.finished } }
            assertEquals(2, h.costs.runningVersions().size)
        }
    }

    @Test fun `legacy fixed costs are a proposed weekly amount with no assumed start charge`() = runBlocking {
        Harness().use { h ->
            h.costs.save(mx.kompara.data.db.entity.CostProfileEntity(updatedAt = h.now.minusSeconds(86400).toEpochMilli(),
                fuelPerKmMxn = 2.0, maintenancePerKmMxn = .8, insurancePerDayMxn = 50.0, rentPerDayMxn = 200.0))
            val vm = h.carVm(); vm.open(); withTimeout(10000) { vm.state.first { it.open } }
            assertTrue(vm.state.value.legacy); assertEquals("1750", vm.state.value.amount)
            assertEquals(h.now.atZone(h.zone).toLocalDate(), vm.state.value.from)
            assertTrue(h.car.get().isEmpty()); vm.dismiss(); assertTrue(h.car.get().isEmpty())
        }
    }

    @Test fun `failed summary recalculation rolls back running and car edits before retry`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.costVm(); vm.ready(); vm.open(CostField.PRICE)
            vm.edit(vm.state.value.draft!!.copy(gasPricePerLitreMxn = "26.25")); h.failRollup = true; vm.save()
            withTimeout(10000) { vm.state.first { it.error != null } }
            assertEquals(1, h.costs.runningVersions().size)
            assertEquals("24.50", h.costs.runningVersions().single().inputs.fuelPricePerLitre!!.toPlainString())
            val car = h.carVm(); car.open(); withTimeout(10000) { car.state.first { it.open } }
            car.amount("1500"); car.save(); withTimeout(10000) { car.state.first { it.error != null } }
            assertTrue(h.car.get().isEmpty())
            h.failRollup = false; vm.save(); withTimeout(10000) { vm.state.first { it.field == null } }
            car.save(); withTimeout(10000) { car.state.first { !it.open } }
            assertEquals(2, h.costs.runningVersions().size); assertEquals(1, h.car.get().size)
        }
    }

    @Test fun `J3 applies neto or bruto at once with the converted limits said under it`() = runBlocking {
        Harness().use { h ->
            h.seed(); h.prefs.saveLimits(PlatformThreshold(8.0, 200.0, 6.0, 150.0), PreferredMetric.IPK)
            val vm = h.costVm(); vm.ready()
            assertEquals(RateBasis.NET, withTimeout(10000) { vm.state.first { it.saved != null } }.basis)
            vm.switchBasis(RateBasis.GROSS)
            val gross = withTimeout(10000) { vm.state.first { it.basisResult != null } }
            assertEquals("Tu meta y tu mínimo pasan a $11.00 y $9.00.", gross.basisResult)
            assertEquals(RateBasis.GROSS, gross.basis)
            val saved = h.prefs.settings.first()
            assertEquals(RateBasis.GROSS, saved.rateBasis)
            assertEquals(PlatformThreshold(11.0, 280.0, 9.0, 230.0), saved.effectiveThreshold)
            h.prefs.saveLimits(saved.effectiveThreshold, PreferredMetric.IPH)
            vm.switchBasis(RateBasis.NET)
            assertEquals("Tu meta y tu mínimo pasan a $200 y $150.", withTimeout(10000) { vm.state.first { it.basis == RateBasis.NET && !it.switching } }.basisResult)
            assertEquals(PlatformThreshold(8.0, 200.0, 6.0, 150.0), h.prefs.settings.first().effectiveThreshold)
        }
    }
    @Test fun `J3's car row follows the weekly amount in force`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.costVm(); vm.ready()
            assertEquals("Sin agregar", vm.state.value.car)
            h.car.save(BigDecimal("1500"), LocalDate.parse("2026-09-28"), h.now.toEpochMilli())
            assertEquals("$1,500 a la semana", withTimeout(10000) { vm.state.first { it.car != "Sin agregar" } }.car)
        }
    }
    @Test fun `first permission state has no fabricated proof`() {
        val zone = ZoneId.of("America/Mexico_City")
        val off = mx.kompara.data.service.ReaderReadinessReducer.reduce(mx.kompara.data.service.ReaderMemory(), mx.kompara.data.service.ReaderSignals(permission = false), 0, zone)
        assertEquals("Apagado · falta el permiso", settingsReaderText(off))
        assertEquals("Preparado · permiso listo", settingsReaderText(off.copy(phase = mx.kompara.data.service.ReaderPhase.PREPARED, permission = true)))
    }
}
