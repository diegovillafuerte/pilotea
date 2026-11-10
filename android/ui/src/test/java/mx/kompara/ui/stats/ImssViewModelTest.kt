package mx.kompara.ui.stats

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
import mx.kompara.data.settings.*
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.metrics.imss.ImssMonthRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** IMSS wiring on a real Room ledger: the month's blocks, month navigation, the rule sheet and the typed incentives. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImssViewModelTest {
    @Before fun before() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun after() = Dispatchers.resetMain()
    private class Harness : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zone: ZoneId = ZoneId.of("America/Mexico_City")
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        val costs = CostProfileRepository(db.costProfileDao())
        val months = ImssMonthRepository(db, PeriodMoneyRepository(db, costs, CarCostRepository(db.carCostDao())))
        val incentives = MonthlyIncentiveRepository(db.monthlyIncentiveDao())
        val clock = AppClock { at("2026-10-19T14:40") }
        val store = ViewModelStore()
        private var id = 1L
        private var vms = 0
        fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
        suspend fun taken(app: String, local: String, fare: Double, km: Double) {
            val n = id++
            db.offerDao().insert(OfferEntity(id = n, seenAt = at(local) - 60_000, platform = app, fareMxn = fare, distanceKm = km,
                durationMin = 20.0, sessionId = "s$n", outcome = "ACCEPTED"))
            db.tripDao().insert(TripEntity(id = n, offerId = n, startedAt = at(local), endedAt = at(local) + 600_000, platform = app,
                grossMxn = fare, distanceKm = km, durationMin = 20.0))
        }
        /** M1's month: Uber $12,487 with $1,800 typed, DiDi $7,942; September closed short with Uber. */
        suspend fun seed() {
            costs.saveRunningCosts(RunningCostInputs.TYPICAL, effectiveFrom = 0, recordedAt = 0)
            taken("UBER", "2026-10-02T09:00", 12_000.0, 900.0)
            taken("UBER", "2026-10-19T08:00", 487.0, 30.0)
            taken("DIDI", "2026-10-05T18:00", 7_942.0, 600.0)
            taken("UBER", "2026-09-12T10:00", 12_287.0, 900.0)
            incentives.set("UBER", YearMonth.of(2026, 10), 1_800, 0)
        }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = ImssViewModel(handle, months, incentives, clock, zone).also { store.put("${vms++}", it) }
        override fun close() { store.clear(); db.close() }
    }
    private suspend fun ImssViewModel.await(predicate: (ImssUiState) -> Boolean) = withTimeout(15_000) { uiState.first { it.view != null && predicate(it) } }

    @Test fun `the month shows M1's blocks and the forward arrow stops at the current month`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val handle = SavedStateHandle()
            val vm = h.vm(handle)
            val october = vm.await { true }
            assertEquals("Octubre · quedan 12 días", october.title); assertFalse(october.canNext)
            assertEquals(listOf("A este ritmo cierras en unos \$22,200. Lo alcanzas.", "A este ritmo cierras en unos \$13,000. Te faltarían \$9,000."),
                october.view!!.blocks.map { it.said!!.text })
            assertEquals("Entre las dos llevas \$22,229. En una sola app ya lo habrías alcanzado.", october.view!!.combined!!.text)
            vm.step(1)
            assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)
            vm.step(-1)
            val september = vm.await { it.month == YearMonth.of(2026, 9) }
            assertEquals("Septiembre", september.title); assertTrue(september.canNext)
            assertEquals("Te faltaron \$9,651.", september.view!!.blocks.single().said!!.text)
            assertEquals("2026-09", handle["imssMonth"])
            vm.step(1)
            vm.await { it.month == YearMonth.of(2026, 10) }
            assertNull(handle.get<String>("imssMonth")) // the current month follows the calendar
        }
    }

    @Test fun `incentives are typed in whole pesos saved and removed per app and month`() = runBlocking {
        Harness().use { h ->
            h.seed()
            val vm = h.vm()
            val collector = launch { vm.incentiveSheet.collect() }
            vm.await { true }
            vm.openIncentives("DIDI")
            val empty = withTimeout(15_000) { vm.incentiveSheet.filterNotNull().first() }
            assertEquals("", empty.draft); assertNull(empty.saved); assertNull(empty.amount); assertTrue(empty.current)
            vm.editDraft("1,2a00")
            assertEquals("1200", withTimeout(15_000) { vm.incentiveSheet.filterNotNull().first { it.draft.isNotEmpty() } }.draft)
            vm.saveIncentives()
            val saved = vm.await { it.view!!.blocks[1].parts.endsWith("\$1,200") }
            assertEquals("Viajes \$7,942 · Incentivos \$1,200", saved.view!!.blocks[1].parts)
            assertEquals(mapOf("UBER" to 1_800, "DIDI" to 1_200), h.incentives.observe(YearMonth.of(2026, 10)).first())
            assertNull(vm.sheet.value)
            vm.openIncentives("DIDI")
            val reopened = withTimeout(15_000) { vm.incentiveSheet.filterNotNull().first() }
            assertEquals("1200", reopened.draft); assertEquals(1_200, reopened.saved)
            vm.removeIncentives()
            vm.await { it.view!!.blocks[1].parts.endsWith("agregar") }
            assertEquals(mapOf("UBER" to 1_800), h.incentives.observe(YearMonth.of(2026, 10)).first())
            collector.cancel()
        }
    }

    @Test fun `the rule sheet opens and closes`() = runBlocking {
        Harness().use { h ->
            val vm = h.vm()
            vm.openRule(); assertEquals(ImssViewModel.RULE, vm.sheet.value)
            vm.closeSheet(); assertNull(vm.sheet.value)
            assertEquals("Aún no hay viajes en octubre.", vm.await { true }.view!!.empty)
        }
    }
}
