package mx.kompara.ui.stats

import androidx.lifecycle.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.data.service.*
import mx.kompara.ui.fixtures.DayFixture
import mx.kompara.ui.screens.LectorViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LectorViewModelTest {
    @Before fun before() { Dispatchers.setMain(Dispatchers.Unconfined); ScreenReaderState.setSilentLaneFailed(false); ScreenReaderState.setRunning(false) }
    @After fun after() { Dispatchers.resetMain(); ScreenReaderState.setSilentLaneFailed(false); ScreenReaderState.setRunning(false) }
    @Test fun `live readiness and the capture fallback share real sources and a stored pause turns back on`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = DayFixture(context); val day = f.day()
        fun at(time: String) = day.date.atTime(LocalTime.parse(time)).atZone(f.fixture.zone).toInstant().toEpochMilli()
        val reader = ReaderReadinessRepository(object : ReaderMemoryStore {
            private var value = ReaderMemory()
            override fun load() = value
            override fun save(memory: ReaderMemory) { value = memory }
        }, Clock.fixed(day.money.window.asOf, f.fixture.zone))
        reader.observeEnvironment(true, false); reader.supportedApps(setOf(ReaderReadinessReducer.UBER, ReaderReadinessReducer.DIDI)); reader.bound(); reader.captureRunning(true)
        ScreenReaderState.setRunning(true)
        reader.readOffer(ReaderReadinessReducer.UBER, "live", at("14:32"), reader.captureToken())
        val vm = LectorViewModel(reader, WeekClock(f.fixture.zone))
        val store = ViewModelStore(); store.put("reader", vm)
        val collect = launch { vm.state.collect() }
        suspend fun await(predicate: (SemaforoState) -> Boolean = { true }) = withTimeout(10_000) { vm.state.filterNotNull().first(predicate) }
        try {
            assertEquals("Semáforo funcionando", await().title)
            assertNull(await().primary)
            assertEquals("Leyó tu última oferta · 14:32", await().apps.first().detail)
            // A manual retry follows the actual owner even before a silent failure is reported.
            ScreenReaderState.setRunning(false)
            assertFalse(ScreenReaderState.silentLaneFailed.value)
            assertEquals(SemaforoAction.CAPTURE, await { it.primary == SemaforoAction.CAPTURE }.primary)
            ScreenReaderState.setRunning(true)
            assertNull(await { it.primary == null }.primary)
            ScreenReaderState.setRunning(false); ScreenReaderState.setSilentLaneFailed(true)
            assertEquals(SemaforoAction.CAPTURE, await { it.primary == SemaforoAction.CAPTURE }.primary)
            reader.observeEnvironment(false, false)
            assertEquals("Sin permiso, no leemos ofertas", await { it.primary == SemaforoAction.PERMISSION }.apps.first().detail)
            // The screen-capture fallback's own stop stores a pause; "Encender el semáforo" clears it.
            reader.observeEnvironment(true, false); reader.pauseUntilTomorrow()
            assertEquals("Detuviste el semáforo", await { it.primary == SemaforoAction.RESUME }.detail)
            vm.resume()
            assertNotEquals(SemaforoAction.RESUME, await { it.primary != SemaforoAction.RESUME }.primary)
            assertFalse(reader.state.value.paused)
        } finally { collect.cancelAndJoin(); val job = vm.viewModelScope.coroutineContext[Job]!!; store.clear(); job.join() }
    }
}
