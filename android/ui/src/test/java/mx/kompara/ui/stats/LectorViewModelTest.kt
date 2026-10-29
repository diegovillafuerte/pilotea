package mx.kompara.ui.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.OfferPresentationEntity
import mx.kompara.data.service.*
import mx.kompara.data.settings.*
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
    @Before fun before() { Dispatchers.setMain(Dispatchers.Unconfined); ScreenReaderState.setSilentLaneFailed(false) }
    @After fun after() { Dispatchers.resetMain(); ScreenReaderState.setSilentLaneFailed(false) }
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    @Test fun `live settings suppression identity pause resume and fallback share real sources`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = DayFixture(context); val day = f.day()
        fun at(time: String) = day.date.atTime(LocalTime.parse(time)).atZone(f.fixture.zone).toInstant().toEpochMilli()
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        val prefs = SettingsRepository(Memory())
        val reader = ReaderReadinessRepository(object : ReaderMemoryStore {
            private var value = ReaderMemory()
            override fun load() = value
            override fun save(memory: ReaderMemory) { value = memory }
        }, Clock.fixed(day.money.window.asOf, f.fixture.zone))
        reader.observeEnvironment(true, false); reader.supportedApps(setOf(ReaderReadinessReducer.UBER, ReaderReadinessReducer.DIDI)); reader.bound(); reader.captureRunning(true)
        reader.readOffer(ReaderReadinessReducer.UBER, "live", at("14:32"), reader.captureToken())
        val vm = LectorViewModel(reader, prefs, db.offerPresentationDao(), WeekClock(f.fixture.zone))
        val store = ViewModelStore(); store.put("reader", vm)
        val collect = launch { vm.state.collect() }
        suspend fun await(predicate: (SemaforoState) -> Boolean = { true }) = withTimeout(10_000) { vm.state.filterNotNull().first(predicate) }
        try {
            assertEquals("Semáforo funcionando", await().title)
            assertNull(await().primary)
            val row = day.snapshot.offers.first { it.id == at("12:10") }
            db.offerDao().insert(row)
            db.offerPresentationDao().record(OfferPresentationEntity(row.sessionId!!, "UNSUPPORTED", row.seenAt, "UNVALIDATED_GEOMETRY"))
            assertTrue(await().suppressed.isEmpty())
            db.offerPresentationDao().record(OfferPresentationEntity(row.sessionId!!, "SUPPRESSED", row.seenAt, "NO_ROOM_NAVIGATION"))
            assertEquals(row.id, await { it.suppressed.isNotEmpty() }.suppressed.single().offerId)
            prefs.saveLimits(PlatformThreshold(8.0, 200.0, 6.5, 150.0), PreferredMetric.IPK)
            assertEquals("$8.00/km · $6.50/km", await { it.limits.contains("6.50") }.limits)
            vm.pause(); assertTrue(await { it.primary == SemaforoAction.RESUME }.suppressed.isEmpty())
            vm.resume(); assertEquals(row.id, await { it.primary == null }.suppressed.single().offerId)
            ScreenReaderState.setRunning(false); ScreenReaderState.setSilentLaneFailed(true)
            assertEquals(SemaforoAction.CAPTURE, await { it.primary == SemaforoAction.CAPTURE }.primary)
            reader.observeEnvironment(false, false)
            assertEquals("Sin permiso, no leemos ofertas", await { it.primary == SemaforoAction.PERMISSION }.apps.first().detail)
            val historical = DayPresentation(day.snapshot, day.settings.copy(threshold = prefs.settings.first().effectiveThreshold))
            assertFalse(historical.offer(at("11:20"))!!.why.contains("a esa hora"))
            assertTrue(historical.offer(at("12:10"))!!.why.endsWith("a esa hora"))
        } finally { collect.cancelAndJoin(); val job = vm.viewModelScope.coroutineContext[Job]!!; store.clear(); job.join(); db.close() }
    }
}
