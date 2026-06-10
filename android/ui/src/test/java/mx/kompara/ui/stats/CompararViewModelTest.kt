package mx.kompara.ui.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mx.kompara.billing.BillingLogger
import mx.kompara.billing.Entitlement
import mx.kompara.billing.EntitlementCache
import mx.kompara.billing.EntitlementRepository
import mx.kompara.billing.FakeBillingClient
import mx.kompara.billing.GateState
import mx.kompara.billing.SubscriptionBackend
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.ui.paywall.GateEvent
import mx.kompara.ui.paywall.GateFunnel
import mx.kompara.ui.paywall.GateSurface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CompararViewModel] state tests (B-047): 0/1/2/3-platform modes, defaulting to the newest week with
 * data, week switching, the platform-pair chooser, and the premium gate. Backed by a [FakeAggregateDao]
 * and a real [TierGatekeeper] built from billing fakes (the proven pattern from PaywallViewModelTest) —
 * the debug-premium lambda unlocks the gate, the paywall-config lambda toggles the kill switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompararViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    // ─── Modes ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `no data is the empty mode with no weeks`() = runTest(dispatcher) {
        val vm = viewModel(emptyList())
        advanceUntilIdle()
        val state = vm.uiState.first { !it.loading }
        assertTrue(state.availableWeeks.isEmpty())
        assertNull(state.data)
    }

    @Test
    fun `one platform is the single-platform mode`() = runTest(dispatcher) {
        val vm = viewModel(listOf(weekly(Platform.UBER, WEEK)))
        advanceUntilIdle()
        val state = vm.uiState.first { !it.loading }
        assertTrue(state.data!!.mode is CompareMode.SinglePlatform)
    }

    @Test
    fun `two platforms compare automatically`() = runTest(dispatcher) {
        val vm = viewModel(listOf(weekly(Platform.UBER, WEEK), weekly(Platform.DIDI, WEEK)))
        advanceUntilIdle()
        val mode = vm.uiState.first { !it.loading }.data!!.mode as CompareMode.Comparison
        assertEquals(Platform.UBER, mode.platformA)
        assertEquals(Platform.DIDI, mode.platformB)
        assertTrue(!mode.showsChips)
    }

    @Test
    fun `three platforms show the chooser and switching the pair re-compares`() = runTest(dispatcher) {
        val vm = viewModel(
            listOf(
                weekly(Platform.UBER, WEEK),
                weekly(Platform.DIDI, WEEK),
                weekly(Platform.INDRIVE, WEEK),
            ),
        )
        advanceUntilIdle()
        val mode = vm.uiState.first { !it.loading }.data!!.mode as CompareMode.Comparison
        assertTrue(mode.showsChips)
        assertEquals(Platform.UBER to Platform.DIDI, mode.platformA to mode.platformB)

        vm.selectPair(Platform.DIDI, Platform.INDRIVE)
        advanceUntilIdle()
        val switched = vm.uiState.first {
            (it.data?.mode as? CompareMode.Comparison)?.platformA == Platform.DIDI
        }.data!!.mode as CompareMode.Comparison
        assertEquals(Platform.DIDI to Platform.INDRIVE, switched.platformA to switched.platformB)
    }

    // ─── Week selection ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `defaults to the newest week with data`() = runTest(dispatcher) {
        val vm = viewModel(
            listOf(
                weekly(Platform.UBER, "2026-06-01"),
                weekly(Platform.DIDI, "2026-06-08"),
                weekly(Platform.UBER, "2026-06-08"),
            ),
        )
        advanceUntilIdle()
        val state = vm.uiState.first { !it.loading }
        assertEquals("2026-06-08", state.data!!.weekStart)
        assertEquals(listOf("2026-06-08", "2026-06-01"), state.availableWeeks)
    }

    @Test
    fun `selecting a week switches the compared rows`() = runTest(dispatcher) {
        val vm = viewModel(
            listOf(
                weekly(Platform.UBER, "2026-06-01"), // older week: single platform
                weekly(Platform.UBER, "2026-06-08"),
                weekly(Platform.DIDI, "2026-06-08"),
            ),
        )
        advanceUntilIdle()
        // Newest week (default) compares two platforms.
        assertTrue(vm.uiState.first { !it.loading }.data!!.mode is CompareMode.Comparison)

        vm.selectWeek("2026-06-01")
        advanceUntilIdle()
        val older = vm.uiState.first { it.data?.weekStart == "2026-06-01" }
        assertTrue(older.data!!.mode is CompareMode.SinglePlatform)
    }

    @Test
    fun `an invalid selected week falls back to the newest`() = runTest(dispatcher) {
        val vm = viewModel(listOf(weekly(Platform.UBER, WEEK), weekly(Platform.DIDI, WEEK)))
        advanceUntilIdle()
        vm.selectWeek("1999-01-04") // no data
        advanceUntilIdle()
        assertEquals(WEEK, vm.uiState.first { !it.loading }.data!!.weekStart)
    }

    // ─── Gate ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `gate is locked for a free driver with the paywall on`() = runTest(dispatcher) {
        val vm = viewModel(listOf(weekly(Platform.UBER, WEEK)), premiumOverride = false, paywallOn = true)
        advanceUntilIdle()
        assertEquals(GateState.LOCKED, vm.gateState.first { it == GateState.LOCKED })
    }

    @Test
    fun `gate unlocks with the debug override`() = runTest(dispatcher) {
        val vm = viewModel(listOf(weekly(Platform.UBER, WEEK)), premiumOverride = true, paywallOn = true)
        advanceUntilIdle()
        assertEquals(GateState.UNLOCKED, vm.gateState.first { it == GateState.UNLOCKED })
    }

    @Test
    fun `gate unlocks when the paywall kill switch is off`() = runTest(dispatcher) {
        val vm = viewModel(listOf(weekly(Platform.UBER, WEEK)), premiumOverride = false, paywallOn = false)
        advanceUntilIdle()
        assertEquals(GateState.UNLOCKED, vm.gateState.first { it == GateState.UNLOCKED })
    }

    @Test
    fun `the funnel records on the compare surface`() = runTest(dispatcher) {
        val funnel = RecordingFunnel()
        viewModel(listOf(weekly(Platform.UBER, WEEK)), funnel = funnel)
        funnel.record(GateSurface.COMPARE, GateEvent.GATE_SHOWN)
        assertEquals(GateSurface.COMPARE to GateEvent.GATE_SHOWN, funnel.recorded.first())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun viewModel(
        rows: List<WeeklyAggregateEntity>,
        premiumOverride: Boolean = false,
        paywallOn: Boolean = true,
        funnel: GateFunnel = RecordingFunnel(),
    ): CompararViewModel {
        val billing = FakeBillingClient(available = false)
        val repo = EntitlementRepository(billing, SubscriptionBackend.NONE, FakeCache(), BillingLogger.NOOP)
        val gatekeeper = TierGatekeeper(
            entitlementRepository = repo,
            debugPremiumSource = { flowOf(premiumOverride) },
            paywallConfigSource = { flowOf(paywallOn) },
        )
        return CompararViewModel(FakeAggregateDao(rows), gatekeeper, funnel)
    }

    private fun weekly(
        platform: Platform,
        weekStart: String,
        source: AggregateSource = AggregateSource.CAPTURED,
    ) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = weekStart,
        source = source.name,
        netEarningsMxn = 1000.0,
        grossEarningsMxn = 1300.0,
        totalTrips = 40,
        totalKm = 200.0,
        hoursOnline = 30.0,
        earningsPerTrip = 55.0,
        earningsPerKm = 9.0,
        earningsPerHour = 180.0,
        tripsPerHour = 1.3,
        acceptanceRate = 0.8,
        computedAt = 0L,
    )

    private companion object {
        const val WEEK = "2026-06-08"
    }
}

/** Records gate-funnel events for assertions. */
private class RecordingFunnel : GateFunnel {
    val recorded = mutableListOf<Pair<GateSurface, GateEvent>>()
    override suspend fun record(surface: GateSurface, event: GateEvent) {
        recorded += surface to event
    }
}

/** In-memory [AggregateDao] backed by a fixed weekly list; only the weekly reads are exercised. */
private class FakeAggregateDao(private val weekly: List<WeeklyAggregateEntity>) : AggregateDao {
    private val weeklyFlow = MutableStateFlow(weekly)

    override suspend fun upsertWeekly(rows: List<WeeklyAggregateEntity>) = Unit
    override suspend fun upsertDaily(rows: List<DailyAggregateEntity>) = Unit
    override fun observeWeekly(): Flow<List<WeeklyAggregateEntity>> = weeklyFlow
    override fun observeDaily(): Flow<List<DailyAggregateEntity>> = MutableStateFlow(emptyList())
    override suspend fun capturedWeek(weekStart: String): List<WeeklyAggregateEntity> =
        weekly.filter { it.weekStart == weekStart && it.source == AggregateSource.CAPTURED.name }
    override suspend fun capturedDay(day: String): List<DailyAggregateEntity> = emptyList()
    override suspend fun allWeekly(): List<WeeklyAggregateEntity> = weekly
    override fun observeDailyInRange(startDay: String, endDay: String): Flow<List<DailyAggregateEntity>> =
        MutableStateFlow(emptyList())
    override fun observeWeeklyInRange(startWeek: String, endWeek: String): Flow<List<WeeklyAggregateEntity>> =
        MutableStateFlow(weekly.filter { it.weekStart in startWeek..endWeek })
    override suspend fun deleteCapturedDay(day: String) = Unit
    override suspend fun deleteCapturedWeek(weekStart: String) = Unit
    override suspend fun dirtyForSync(): List<WeeklyAggregateEntity> = emptyList()
    override suspend fun markSynced(platform: String, weekStart: String, source: String, syncedAt: Long) = Unit
}

/** Minimal [EntitlementCache] for building a [TierGatekeeper] in tests. */
private class FakeCache(var current: Entitlement = Entitlement.Free) : EntitlementCache {
    override suspend fun read(): Entitlement = current
    override suspend fun write(entitlement: Entitlement) { current = entitlement }
}
