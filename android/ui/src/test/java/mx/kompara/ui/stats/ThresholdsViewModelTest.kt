package mx.kompara.ui.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.settings.*
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThresholdsViewModelTest {
    @Before fun before() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun after() = Dispatchers.resetMain()
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var fail = false
        val commits = mutableListOf<Preferences>()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (fail) throw java.io.IOException("disk full")
            return transform(data.value).also { commits += it; data.value = it }
        }
    }
    private class Harness : AutoCloseable {
        val memory = Memory()
        val prefs = SettingsRepository(memory)
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KomparaDatabase::class.java).build()
        val costs = CostProfileRepository(db.costProfileDao())
        val store = ViewModelStore()
        val jobs = mutableListOf<Job>()
        suspend fun seed() { costs.saveRunningCosts(RunningCostInputs.TYPICAL, 0, 0); prefs.saveLimits(PlatformThreshold(8.0, 500.0, 6.0, 153.25), PreferredMetric.IPK); memory.commits.clear() }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = ThresholdsViewModel(prefs, costs, AppClock { 1000 }, handle).also {
            store.put(jobs.size.toString(), it); jobs += it.viewModelScope.coroutineContext[Job]!!
        }
        override fun close() { store.clear(); runBlocking { jobs.forEach { it.join() } }; db.close() }
    }
    private suspend fun ThresholdsViewModel.ready() = withTimeout(10_000) { state.first { it.draft != null } }
    @Test fun `editing unit reset and recreation preserve draft without writes`() = runBlocking {
        Harness().use { h ->
            h.seed(); val handle = SavedStateHandle(); val vm = h.vm(handle); vm.ready()
            assertFalse(vm.state.value.canSave)
            vm.edit(LimitField.MINIMUM, "$6,30"); vm.select(PreferredMetric.IPH); vm.step(LimitField.MINIMUM, true)
            assertEquals("160.00", vm.state.value.draft!!.hour.minimum)
            vm.confirmExit(true)
            val restored = h.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })); restored.ready()
            assertEquals(vm.state.value.draft, restored.state.value.draft); assertTrue(restored.state.value.confirmExit)
            restored.select(PreferredMetric.IPK); restored.reset()
            assertEquals("6.00", restored.state.value.draft!!.km.minimum)
            assertEquals("160.00", restored.state.value.draft!!.hour.minimum)
            assertTrue(h.memory.commits.isEmpty()); assertNull(restored.state.value.result)
        }
    }
    @Test fun `invalid and empty pair hold last preview and report only on save`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.vm(); vm.ready(); vm.edit(LimitField.MINIMUM, "6.50")
            val preview = vm.state.value.preview
            vm.edit(LimitField.MINIMUM, "8.00")
            assertNull(vm.state.value.error); assertEquals(preview, vm.state.value.preview)
            vm.save(); assertEquals("Tu mínimo debe ser menor que tu meta", vm.state.value.error)
            vm.edit(LimitField.MINIMUM, ""); assertFalse(vm.state.value.canSave); vm.save()
            assertEquals(preview, vm.state.value.preview); assertTrue(h.memory.commits.isEmpty())
            vm.edit(LimitField.MINIMUM, "6.50"); vm.edit(LimitField.GOAL, "6.50"); vm.save()
            assertEquals("Tu meta debe ser mayor que tu mínimo", vm.state.value.error)
        }
    }
    @Test fun `successful save commits both units and selection once before result`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.vm(); vm.ready(); vm.edit(LimitField.MINIMUM, "6,50")
            vm.select(PreferredMetric.IPH); vm.step(LimitField.GOAL, true); vm.save()
            val result = withTimeout(10_000) { vm.state.first { it.result != null } }
            assertEquals(1, h.memory.commits.size)
            val saved = h.prefs.settings.first()
            assertEquals(6.5, saved.effectiveThreshold.redPerKmMxn, 0.0)
            assertEquals(510.0, saved.effectiveThreshold.minPerHourMxn, 0.0)
            assertEquals(153.25, saved.effectiveThreshold.redPerHourMxn, 0.0)
            assertEquals(PreferredMetric.IPH, saved.preferredMetric); assertFalse(result.dirty)
            assertTrue(result.result!!.contains("6.50/km")); assertTrue(result.result.contains("510/h"))
        }
    }
    @Test fun `failed persistence stays in editor with draft and no success then retries`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.vm(); vm.ready(); vm.edit(LimitField.MINIMUM, "6.50"); h.memory.fail = true; vm.save()
            withTimeout(10_000) { vm.state.first { it.error != null } }
            assertNull(vm.state.value.result); assertTrue(vm.state.value.dirty); assertFalse(vm.state.value.saving)
            assertEquals(6.0, h.prefs.settings.first().effectiveThreshold.redPerKmMxn, 0.0)
            h.memory.fail = false; vm.save()
            assertEquals("Guardado: tu mínimo ahora es $6.50/km", withTimeout(10_000) { vm.state.first { it.result != null } }.result)
        }
    }
    @Test fun `repository rejects impossible rule before any atomic write`() = runBlocking {
        Harness().use { h ->
            h.seed()
            listOf(PlatformThreshold(8.0, 200.0, 8.0, 150.0), PlatformThreshold(100.0, 200.0, 6.0, 150.0), PlatformThreshold(8.0, Double.NaN, 6.0, 150.0)).forEach {
                try { h.prefs.saveLimits(it, PreferredMetric.IPH); fail("invalid rule") } catch (_: IllegalArgumentException) { }
            }
            assertTrue(h.memory.commits.isEmpty())
        }
    }
}
