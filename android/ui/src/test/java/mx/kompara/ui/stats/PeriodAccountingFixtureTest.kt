package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.accounting.*
import mx.kompara.ui.fixtures.V3Fixture
import mx.kompara.ui.format.Formatters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodAccountingFixtureTest {
    private val fixture = V3Fixture(ApplicationProvider.getApplicationContext())
    private fun whole(value: BigDecimal?) = Formatters.formatMxnWhole(requireNotNull(value))
    @Test fun `B1 B5 B6 B8 D3 D3a E8 all use one money definition`() {
        assertEquals("$548", whole(fixture.todayMoney.periodResult))
        assertEquals("$434", whole(fixture.pausedMoney.periodResult))
        assertEquals("$166", whole(fixture.partialSupportMoney.periodResult))
        assertEquals("−$100", whole(fixture.negativeDayMoney.periodResult))
        assertEquals("$1,026", whole(fixture.closedDayMoney.periodResult))
        assertEquals(6, fixture.todayMoney.trips)
        assertEquals(1, fixture.todayMoney.inProgress)
        assertEquals(9, fixture.closedDayMoney.trips)
        assertEquals(0, fixture.closedDayMoney.inProgress)
        assertEquals(listOf("$979", "$217", "$214"), listOf(fixture.todayMoney.payment, fixture.todayMoney.runningExpense, fixture.todayMoney.carCost).map(::whole))
        assertEquals(listOf("$1,590", "$350", "$214"), listOf(fixture.closedDayMoney.payment, fixture.closedDayMoney.runningExpense, fixture.closedDayMoney.carCost).map(::whole))
        val openWeek = fixture.period(fixture.date, fixture.date.plusDays(6), fixture.todayRecords)
        assertEquals(fixture.todayMoney.periodResult, openWeek.periodResult)
        assertEquals(1, openWeek.carCharges.size)
        assertEquals("ya restamos gasolina, desgaste y coche", openWeek.deductionBasis())
        val withoutCar = PeriodStats.calculate(openWeek.window, fixture.todayRecords, CostSchedule(fixture.schedule.running))
        assertEquals("ya restamos gasolina y desgaste", withoutCar.deductionBasis())
    }
    @Test fun `E1b E1c E1d rounding residual no-trip Sunday and per app rate`() {
        assertEquals("$4,980", whole(fixture.weekMoney.periodResult))
        assertEquals(listOf("$8,400", "$1,920", "$1,500"), listOf(fixture.weekMoney.payment, fixture.weekMoney.runningExpense, fixture.weekMoney.carCost).map(::whole))
        assertEquals(1, fixture.weekMoney.roundingResidual(fixture.dayMoney)!!.toInt())
        assertEquals(4979, fixture.dayMoney.sumOf { it.periodResult!!.setScale(0, java.math.RoundingMode.HALF_UP).toInt() })
        assertEquals("$4,481", whole(fixture.noSundayMoney.periodResult))
        val sunday = fixture.period(fixture.weekStart.plusDays(6), fixture.weekStart.plusDays(6), emptyList())
        assertEquals("−$214", whole(sunday.periodResult))
        assertEquals("$1,166", whole(fixture.dayMoney[4].periodResult))
        assertEquals("$9.13/km", Formatters.formatPerKm(fixture.rate("uber")).replace('\u00a0', ' '))
        assertEquals("$10.34/km", Formatters.formatPerKm(fixture.rate("didi")).replace('\u00a0', ' '))
        assertEquals("$1.21", Formatters.formatMxn(fixture.rate("didi").subtract(fixture.rate("uber"))))
    }
    @Test fun `D3b E3 October and week rows agree without false rounding note`() {
        val month = fixture.monthMoney
        assertEquals("$11,692", whole(month.periodResult))
        assertEquals(listOf("$20,429", "$4,666", "$4,071"), listOf(month.payment, month.runningExpense, month.carCost).map(::whole))
        assertEquals(listOf("$1,417", "$4,747", "$4,980", "$548"), fixture.monthRows.map { whole(it.periodResult) })
        assertEquals(0, month.roundingResidual(fixture.monthRows)!!.signum())
        assertEquals(155, month.trips)
        assertEquals(1, month.inProgress)
        assertEquals(19, month.carCharges.size)
        assertEquals("$12,487", whole(month.byApp.getValue("UBER").payment))
        assertEquals("$7,942", whole(month.byApp.getValue("DIDI").payment))
    }
    @Test fun `E3c mixed September excludes undated payment from its net and bars`() {
        val start = java.time.LocalDate.parse("2026-09-01")
        val inputs = fixture.raw.getJSONObject("alt_scenarios").getJSONObject("E3c_sept_mixed").getJSONObject("weeks")
        val records = listOf(fixture.record("sep21", inputs.getJSONObject("2026-09-21"), start.withDayOfMonth(21), start.withDayOfMonth(27)),
            fixture.record("sep28", inputs.getJSONObject("2026-09-28_partial"), start.withDayOfMonth(28), start.withDayOfMonth(30)),
            IncomeRecord("undated", "UBER", Coverage(start.withDayOfMonth(14), start.withDayOfMonth(20), false), MoneySource.PARSED_STATEMENT,
                BigDecimal(fixture.import.get("paid").toString()), null, fixture.import.getInt("trips")))
        val month = fixture.period(start, start.withDayOfMonth(30), records)
        assertEquals("$7,170", whole(month.periodResult))
        assertEquals("$5,340", whole(month.paymentOnly.single().record.payment))
        assertEquals(75, month.trips)
        assertEquals(3, month.carCharges.size)
    }

    @Test fun `offer rates and last finished trip never include car`() {
        assertEquals(listOf("$9.49/km", "$7.16/km", "$2.20/km", "−$1.05/km"),
            listOf("verde", "amarillo", "rojo", "rojo_negativo").map { fixture.chipRate(it).replace('\u00a0', ' ') })
        val input = fixture.raw.getJSONObject("today_morning_trips").getJSONObject("11:20")
        val last = PeriodAccounting.contribution(fixture.record("11:20", input, fixture.date), fixture.schedule, fixture.zone)
        assertEquals("$133", whole(last.tripContribution))
        assertEquals("$308", whole(last.ratePerHour))
        assertEquals("$9.81/km", Formatters.formatPerKm(last.ratePerKm!!).replace('\u00a0', ' '))
    }
    @Test fun `E1e payment reconciliation keeps captured total until explicit selection`() {
        val uber = fixture.weekRecords.first()
        val statement = uber.copy(id = "pdf", source = MoneySource.PARSED_STATEMENT,
            payment = BigDecimal(fixture.week.getJSONObject("statement_uber").get("paid").toString()), km = null)
        val default = fixture.period(fixture.weekStart, fixture.weekStart.plusDays(6), fixture.weekRecords + statement)
        assertEquals(fixture.weekMoney.periodResult, default.periodResult)
        assertEquals("$34", whole(statement.payment!!.subtract(uber.payment)))
        assertEquals("$149", whole(statement.payment!!.per(BigDecimal(statement.trips))))
        val selected = PeriodStats.calculate(default.window, fixture.weekRecords + statement, fixture.schedule,
            decisions = listOf(Reconciliation(uber.id, statement.id, setOf(MoneyField.PAYMENT))))
        assertEquals("$5,014", whole(selected.periodResult))
        assertEquals(62, selected.trips)
    }
}
