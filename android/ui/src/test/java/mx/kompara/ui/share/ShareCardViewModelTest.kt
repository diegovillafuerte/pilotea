package mx.kompara.ui.share

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.settings.*
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.ui.export.ExportHarness
import mx.kompara.ui.fixtures.DayFixture
import mx.kompara.ui.stats.AppClock
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardViewModelTest {
    @Before fun before() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun after() = Dispatchers.resetMain()
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private class Harness : AutoCloseable {
        val export = ExportHarness()
        val context = export.context
        val fixture = DayFixture(context)
        val day = fixture.day()
        val zone = day.money.window.zone
        var now = day.money.window.asOf.toEpochMilli()
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        val running = CostProfileRepository(db.costProfileDao())
        val car = CarCostRepository(db.carCostDao())
        val provider = ShareCardProvider(PeriodMoneyRepository(db, running, car))
        val prefs = SettingsRepository(MemoryPreferences())
        val writer = ShareCardWriter(export.cache)
        val store = ViewModelStore()
        val jobs = mutableListOf<Job>()
        suspend fun seed() {
            prefs.setShareHideAmounts(false) // A legacy preference cannot make a new flow public.
            running.saveRunningCosts(fixture.fixture.schedule.running.single().inputs, 0, 0)
            car.save("1500".toBigDecimal(), day.date, 0)
            day.snapshot.offers.forEach { db.offerDao().insert(it) }
            day.snapshot.trips.forEach { db.tripDao().insert(it) }
            day.snapshot.shifts.forEach { db.shiftDao().insert(it) }
        }
        fun vm(saved: SavedStateHandle) = ShareCardViewModel(provider, writer, ShareCardSharer(context, prefs), ShareCardRenderer(context), saved, AppClock { now }, zone).also {
            store.put("${jobs.size}", it); jobs += it.viewModelScope.coroutineContext[Job]!!
        }
        override fun close() { store.clear(); runBlocking { jobs.forEach { it.join() } }; db.close() }
    }
    private suspend fun ShareCardViewModel.ready() = withTimeout(15_000) { state.first { !it.loading && !it.sharing } }
    @Test fun `historical week and month route survive recreation without using current month`() = runBlocking {
        Harness().use { h ->
            h.seed()
            for (args in listOf(mapOf("weekStart" to "2026-10-05"), mapOf("month" to "2026-09"))) {
                val saved = SavedStateHandle(args)
                val vm = h.vm(saved); val before = vm.ready()
                assertTrue(before.hideAmounts); assertNull(before.data!!.hours); assertNull(before.data.netEarnings)
                val period = before.data.periodLabel
                h.now = Instant.parse("2026-11-03T18:00:00Z").toEpochMilli()
                val restored = h.vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })).ready()
                assertEquals(period, restored.data!!.periodLabel)
                assertTrue(before.bitmap!!.sameAs(restored.bitmap!!))
            }
        }
    }
    @Test fun `privacy change clears old preview immediately wins rapid renders and keeps one snapshot`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.vm(SavedStateHandle(mapOf("weekStart" to "2026-10-19")))
            assertTrue(vm.ready().hideAmounts)
            vm.setHideAmounts(false)
            assertEquals("$548", vm.ready().data!!.netEarnings)
            val uri = h.writer.write(vm.state.value.bitmap!!)
            vm.setHideAmounts(true)
            assertNull(vm.state.value.bitmap); assertNull(vm.state.value.data)
            vm.share()
            vm.setHideAmounts(false); vm.setHideAmounts(true)
            val hidden = vm.ready()
            assertTrue(hidden.hideAmounts); assertNull(hidden.data!!.hours); assertFalse(hidden.data.caption.contains('$'))
            assertThrows(java.io.FileNotFoundException::class.java) { h.export.provider.openFile(uri, "r") }
            h.now += 7 * 24 * 60 * 60 * 1000L
            vm.setHideAmounts(false)
            assertEquals("$548", vm.ready().data!!.netEarnings)
        }
    }
    @Test fun `share uses preview PNG and only explicit actions launch a granted chooser`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.vm(SavedStateHandle(mapOf("month" to "2026-10")))
            val state = vm.ready()
            val shadow = Shadows.shadowOf(h.context as android.app.Application)
            assertNull(shadow.nextStartedActivity)
            vm.share(false)
            withTimeout(15_000) { vm.state.first { !it.sharing } }
            val chooser = requireNotNull(shadow.nextStartedActivity)
            assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            assertEquals(state.data!!.caption, send.getStringExtra(android.content.Intent.EXTRA_TEXT))
            val uri = send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
            val bitmap = h.context.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) }
            assertTrue(state.bitmap!!.sameAs(bitmap))
            assertEquals(uri, send.clipData!!.getItemAt(0).uri)
            vm.setHideAmounts(true); vm.ready()
            assertTrue(File(h.context.cacheDir, "share_cards").listFiles()!!.isEmpty())
        }
    }
    @Test fun `hiding during a pending file write cancels the outgoing private share`() = runBlocking {
        Harness().use { h ->
            h.seed(); val vm = h.vm(SavedStateHandle(mapOf("month" to "2026-10")))
            vm.ready(); vm.setHideAmounts(false); vm.ready()
            synchronized(h.export.cache) {
                vm.share(false)
                vm.setHideAmounts(true)
                assertNull(vm.state.value.bitmap)
            }
            vm.ready()
            vm.viewModelScope.coroutineContext[Job]!!.children.toList().forEach { it.join() }
            assertNull(Shadows.shadowOf(h.context as android.app.Application).nextStartedActivity)
            assertTrue(File(h.context.cacheDir, "share_cards").listFiles()!!.isEmpty())
        }
    }
    @Test fun `invalid period has retry state and cannot launch or retain an export`() = runBlocking {
        Harness().use { h ->
            val vm = h.vm(SavedStateHandle(mapOf("month" to "not-a-month")))
            assertNotNull(vm.ready().error); assertFalse(vm.state.value.canShare)
            vm.share(); assertNull(Shadows.shadowOf(h.context as android.app.Application).nextStartedActivity)
            vm.retry(); assertNotNull(vm.ready().error)
        }
    }
}
