package mx.kompara.ui.screens

import androidx.activity.ComponentActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import mx.kompara.billing.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.settings.*
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.metrics.rollup.RollupRecomputer
import mx.kompara.ui.fixtures.DayFixture
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class WeekCarNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(15_000) {
        // Room resumes the real ViewModels on Android's Handler; Compose advances its own clock.
        shadowOf(android.os.Looper.getMainLooper()).idle()
        condition()
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(data.value).also { data.value = it }
    }

    @Test fun `open week car row opens real G5 directly and dismisses back to the same week`() {
        val context = compose.activity
        val fixture = DayFixture(context)
        val day = fixture.day()
        val zone = day.money.window.zone
        val now = day.money.window.asOf
        val clock = AppClock { now.toEpochMilli() }
        val database = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val costs = CostProfileRepository(database.costProfileDao())
            val car = CarCostRepository(database.carCostDao())
            runBlocking {
                costs.saveRunningCosts(fixture.fixture.schedule.running.single().inputs, 0, 0)
                car.save("1500".toBigDecimal(), day.date.minusWeeks(3), 0)
                day.snapshot.offers.forEach { database.offerDao().insert(it) }
                day.snapshot.trips.forEach { database.tripDao().insert(it) }
                day.snapshot.shifts.forEach { database.shiftDao().insert(it) }
            }
            val repository = PeriodMoneyRepository(database, costs, car)
            val gate = TierGatekeeper(EntitlementRepository(FakeBillingClient(), SubscriptionBackend.NONE,
                object : EntitlementCache {
                    override suspend fun read() = Entitlement.Free
                    override suspend fun write(entitlement: Entitlement) {}
                }, BillingLogger.NOOP), DebugPremiumSource { flowOf(false) }, PaywallConfigSource { flowOf(true) })
            val weekModel = WeekSummaryViewModel(SavedStateHandle(), SelectedPeriod(SettingsRepository(MemoryPreferences())),
                TodaySnapshots(clock, repository, zone), repository, gate, clock, zone, EarningsConnectivity(context))
                .also { store.put("week", it) }
            val rollup = RollupRecomputer(database, database.offerDao(), database.tripDao(), database.shiftDao(),
                database.aggregateDao(), costs, Clock.fixed(now, zone))
            val carModel = CarCostViewModel(car, costs, rollup, clock, zone, SavedStateHandle())
                .also { store.put("car", it) }
            compose.setContent { KomparaTheme {
                WeekSummaryScreen(onImport = {}, onDay = {}, onMonth = {}, onUpgrade = {},
                    viewModel = weekModel, carModel = carModel)
            } }
            awaitState { weekModel.uiState.value.week != null || weekModel.uiState.value.error }
            assertFalse("Week must load from its dated Room evidence", weekModel.uiState.value.error)
            compose.onNodeWithText("Cada día se restan $214 del coche").performScrollTo().performClick()
            awaitState { carModel.state.value.open }
            compose.onNodeWithText("El coche").assertIsDisplayed()
            compose.onNodeWithText("Cada semana").assertIsDisplayed()
            compose.onNodeWithText("Renta, crédito o seguro").assertIsDisplayed()
            assertEquals("1500", carModel.state.value.amount)
            assertEquals(day.date.minusWeeks(3), carModel.state.value.from)
            assertEquals(day.date, weekModel.uiState.value.weekStart)
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
                .performSemanticsAction(SemanticsActions.Dismiss) { it() }
            awaitState { !carModel.state.value.open }
            compose.onNodeWithText("El coche").assertDoesNotExist()
            compose.onNodeWithText("Cada día se restan $214 del coche").assertIsDisplayed()
            assertEquals("$548", weekModel.uiState.value.week!!.amount)
            assertEquals(day.date, weekModel.uiState.value.weekStart)
        } finally {
            compose.runOnIdle { store.clear() }
            database.close()
        }
    }
}
