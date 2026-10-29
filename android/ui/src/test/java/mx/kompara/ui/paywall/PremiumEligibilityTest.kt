package mx.kompara.ui.paywall

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.settings.*
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.ui.fixtures.DayFixture
import mx.kompara.ui.stats.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class PremiumEligibilityTest {
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    @Test fun `real stored results gate sales current window stays free and month uses its own period`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixture = DayFixture(context); val day = fixture.day()
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val running = CostProfileRepository(db.costProfileDao()); val car = CarCostRepository(db.carCostDao())
            val periods = SelectedPeriod(SettingsRepository(Memory()))
            var now = day.money.window.asOf.toEpochMilli()
            val source = LocalPremiumSales(periods, PeriodMoneyRepository(db, running, car), AppClock { now }, fixture.fixture.zone)
            assertFalse(source.check(GateSurface.GENERIC).available)
            running.saveRunningCosts(fixture.fixture.schedule.running.single().inputs, 0, 0)
            day.snapshot.offers.forEach { db.offerDao().insert(it) }; day.snapshot.trips.forEach { db.tripDao().insert(it) }
            periods.select(day.date)
            assertFalse(source.check(GateSurface.HISTORY).available)
            assertEquals(listOf("Tu mes en PDF"), source.check(GateSurface.FISCAL).benefits)
            assertFalse(source.check(GateSurface.COMPARE).available)
            now = LocalDate.parse("2026-11-02").atStartOfDay(fixture.fixture.zone).toInstant().toEpochMilli()
            periods.selectMonth(YearMonth.of(2026, 10), LocalDate.parse("2026-11-02"))
            // October is closed, while the selected week is still inside its free calendar window.
            assertTrue(source.check(GateSurface.MONTH_HISTORY).available)
            assertFalse(source.check(GateSurface.HISTORY).available)
            now = LocalDate.parse("2026-11-16").atStartOfDay(fixture.fixture.zone).toInstant().toEpochMilli()
            periods.select(day.date)
            assertTrue(source.check(GateSurface.HISTORY).available)
            periods.select(day.date.minusWeeks(8))
            assertFalse(source.check(GateSurface.HISTORY).available)
        } finally { db.close() }
    }
}
