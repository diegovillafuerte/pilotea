package mx.kompara.data.accounting

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class PeriodAccountingTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val day = LocalDate.parse("2026-10-19")
    private fun at(date: LocalDate = day, hour: Int = 14, minute: Int = 40) = date.atTime(hour, minute).atZone(zone).toInstant()
    private val running = RunningCostVersion(1, at(day.minusMonths(1), 0, 0).toEpochMilli(), 0,
        RunningCostInputs.TYPICAL, CostConfirmation.CONFIRMED)
    private val costs = CostSchedule(listOf(running), listOf(CarCostVersion(1, day.minusMonths(1), BigDecimal("1500"), 0)))
    private fun window(start: LocalDate = day, end: LocalDate = day, now: LocalDate = day) = PeriodWindow(start, end, at(now), zone)
    private fun record(id: String = "1", app: String = "UBER", date: LocalDate = day) = IncomeRecord(id, app,
        Coverage(date, date), MoneySource.CAPTURED_ESTIMATE, BigDecimal("148"), BigDecimal("12"), 1, at(date, 14, 32).toEpochMilli(), BigDecimal("24"))
    private fun decimal(expected: String, actual: BigDecimal?) = assertEquals(BigDecimal(expected).toDouble(), requireNotNull(actual).toDouble(), 1e-10)

    @Test fun `one car charge after combining apps and negative day stays negative`() {
        val money = PeriodAccounting.calculate(window(), listOf(record(), record("2", "DIDI")), costs)
        decimal("113.9", money.byApp.getValue("UBER").tripContribution)
        decimal("9.491666666666667", money.byApp.getValue("DIDI").ratePerKm)
        decimal("214.2857142857143", money.carCost)
        decimal("13.514285714285714", money.periodResult)
        assertEquals(1, money.carCharges.size)
        assertTrue(PeriodAccounting.calculate(window(), listOf(record()), costs).periodResult!!.signum() < 0)
    }

    @Test fun `leap day month week boundaries current day and future dates`() {
        val start = LocalDate.parse("2024-02-28")
        val car = CostSchedule(emptyList(), listOf(CarCostVersion(1, start, BigDecimal("700"), 0)))
        val result = PeriodAccounting.calculate(window(start.minusDays(4), start.plusDays(5), start.plusDays(2)), emptyList(), car)
        assertEquals(listOf(start, start.plusDays(1), start.plusDays(2)), result.carCharges.map { it.date })
        decimal("-300", result.periodResult)
        val future = PeriodAccounting.calculate(window(start.plusDays(3), start.plusDays(5), start.plusDays(2)), emptyList(), car)
        decimal("0", future.periodResult)
    }

    @Test fun `car changes removal and explicit zero retain earlier charges`() {
        val car = CostSchedule(emptyList(), listOf(
            CarCostVersion(1, day.minusDays(3), BigDecimal("700"), 0),
            CarCostVersion(2, day.minusDays(2), BigDecimal("1400"), 0),
            CarCostVersion(3, day.minusDays(1), null, 0),
            CarCostVersion(4, day, BigDecimal.ZERO, 0)))
        val result = PeriodAccounting.calculate(window(day.minusDays(3)), emptyList(), car)
        decimal("300", result.carCost)
        assertEquals(3, result.carCharges.size)
        assertTrue(result.carConfigured)
        assertFalse(PeriodAccounting.calculate(window(), emptyList(), CostSchedule(emptyList())).carConfigured)
    }

    @Test fun `cost change uses start instant and earlier unknown history stays unknown`() {
        val change = running.copy(id = 2, effectiveFrom = at(day, 14, 35).toEpochMilli(),
            inputs = RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal("5")))
        val schedule = CostSchedule(listOf(running, change))
        val before = record()
        val after = record("2").copy(startedAt = at(day, 14, 36).toEpochMilli())
        val result = PeriodAccounting.calculate(window(), listOf(before, after), schedule)
        decimal("201.9", result.tripContribution)
        assertEquals(listOf(1L, 2L), result.contributions.map { it.runningCostVersion!!.id })
        val ancient = record(date = day.minusYears(1))
        val unknown = PeriodAccounting.calculate(window(ancient.coverage.start, ancient.coverage.start), listOf(ancient), schedule)
        assertNull(unknown.periodResult)
        assertTrue(unknown.unknownHistoricalCost)
    }

    @Test fun `in progress counts once future unresolved declined and cancelled do not contribute`() {
        val live = record().copy(lifecycle = MoneyLifecycle.IN_PROGRESS, inProgress = 1)
        val ignored = listOf(MoneyLifecycle.UNRESOLVED, MoneyLifecycle.NOT_TAKEN, MoneyLifecycle.CANCELLED)
            .map { record(it.name).copy(lifecycle = it) } + record("future").copy(startedAt = at(day, 16).toEpochMilli())
        val result = PeriodAccounting.calculate(window(), listOf(live, live) + ignored, costs)
        assertEquals(1, result.trips)
        assertEquals(1, result.inProgress)
        val finished = live.copy(lifecycle = MoneyLifecycle.TAKEN_ESTIMATE, inProgress = 0)
        decimal(result.periodResult!!.toPlainString(), PeriodAccounting.calculate(window(), listOf(finished), costs).periodResult)
    }

    @Test fun `missing payment distance and basis stay unavailable while missing time allows km`() {
        for (unknown in listOf(record().copy(payment = null), record().copy(km = null), record().copy(paymentBasis = PaymentBasis.UNCONFIRMED))) {
            assertNull(PeriodAccounting.calculate(window(), listOf(unknown), costs).periodResult)
        }
        val value = PeriodAccounting.contribution(record().copy(minutes = null), costs, zone)
        assertNotNull(value.ratePerKm)
        assertNull(value.ratePerHour)
        val zero = PeriodAccounting.contribution(record().copy(payment = BigDecimal.ZERO, km = BigDecimal.ZERO), costs, zone)
        decimal("0", zero.tripContribution)
        assertNull(zero.ratePerKm)
    }

    @Test fun `shifts union across local midnight and stop at shared as of`() {
        val shifts = listOf(TimeInterval(at(day.minusDays(1), 23, 0).toEpochMilli(), null),
            TimeInterval(at(day, 0, 0).toEpochMilli(), at(day, 2, 0).toEpochMilli()))
        val now = at(day, 1, 30)
        val yesterday = PeriodWindow(day.minusDays(1), day.minusDays(1), now, zone)
        val today = PeriodWindow(day, day, now, zone)
        decimal("1", PeriodAccounting.onlineHours(yesterday, shifts))
        decimal("1.5", PeriodAccounting.onlineHours(today, shifts))
        val midnight = PeriodWindow(day, day, at(day, 0, 0), zone)
        decimal("0", PeriodAccounting.onlineHours(midnight, shifts))
        assertEquals(1, PeriodAccounting.calculate(midnight, emptyList(), costs).carCharges.size)
    }

    @Test fun `default retains captured plus another app and explicit compatible payment changes only its field`() {
        val captured = record()
        val statement = captured.copy(id = "pdf", source = MoneySource.PARSED_STATEMENT, payment = BigDecimal("182"), km = null,
            fieldSources = MoneyField.entries.associateWith { "pdf" })
        val rows = listOf(captured, statement, record("didi", "DIDI"))
        val original = PeriodAccounting.calculate(window(), rows, costs)
        assertEquals(2, original.trips)
        assertEquals(1, original.excluded.size)
        val replaced = PeriodAccounting.calculate(window(), rows, costs,
            decisions = listOf(Reconciliation("1", "pdf", setOf(MoneyField.PAYMENT))))
        decimal(original.periodResult!!.add(BigDecimal(34)).toPlainString(), replaced.periodResult)
        val merged = replaced.contributions.first { it.record.app == "UBER" }.record
        assertEquals("pdf", merged.fieldSources[MoneyField.PAYMENT])
        assertEquals("1", merged.fieldSources[MoneyField.DISTANCE])
        assertEquals(MoneyLifecycle.TAKEN_ESTIMATE, merged.lifecycle)
    }

    @Test fun `statement with extra trips never uses partial captured km`() {
        val statement = record("pdf").copy(source = MoneySource.USER_CORRECTED_IMPORT, trips = 2, km = null)
        val result = PeriodAccounting.calculate(window(), listOf(record(), statement), costs,
            decisions = listOf(Reconciliation("1", "pdf", setOf(MoneyField.PAYMENT, MoneyField.TRIPS))))
        assertNull(result.periodResult)
        assertTrue(result.byApp.isEmpty())
        assertEquals(2, result.paymentOnly.single().record.trips)
    }

    @Test fun `full statement distance cannot use partial captured payment`() {
        val statement = record("pdf").copy(source = MoneySource.PARSED_STATEMENT, trips = 2, km = BigDecimal("24"))
        val selected = AccountingSourceSelector.select(listOf(record(), statement),
            listOf(Reconciliation("1", "pdf", setOf(MoneyField.DISTANCE)))).records.single()
        assertNull(selected.payment)
        assertEquals(2, selected.trips)
        assertEquals("pdf", selected.fieldSources[MoneyField.TRIPS])
    }

    @Test fun `undated payment never invents days and boundary crossing stays outside month`() {
        val statement = record("pdf").copy(source = MoneySource.PARSED_STATEMENT, startedAt = null, km = null,
            coverage = Coverage(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"), false))
        val october = PeriodAccounting.calculate(window(day.withDayOfMonth(1), day.withDayOfMonth(31)), listOf(statement), costs)
        assertTrue(october.contributions.isEmpty())
        assertEquals(listOf(statement), october.excluded)
        val week = PeriodAccounting.calculate(window(statement.coverage.start, statement.coverage.endInclusive), listOf(statement), costs)
        assertEquals(statement.payment, week.paymentScopeTotal)
        assertNull(week.periodResult)
        val tuesday = PeriodAccounting.calculate(window(statement.coverage.start.plusDays(1), statement.coverage.start.plusDays(1)), listOf(statement), costs)
        assertTrue(tuesday.contributions.isEmpty())
    }

    @Test fun `mixed month keeps labelled payment without including it in net`() {
        val statement = record("pdf").copy(app = "DIDI", source = MoneySource.PARSED_STATEMENT,
            km = null, startedAt = null, coverage = Coverage(day.minusDays(7), day.minusDays(1), false))
        val result = PeriodAccounting.calculate(window(day.withDayOfMonth(1), day.withDayOfMonth(31)), listOf(record(), statement), costs)
        assertEquals(1, result.trips)
        assertEquals(listOf(statement), result.paymentOnly.map { it.record })
        decimal("296", result.paymentScopeTotal)
        decimal("148", result.payment)
        assertNotNull(result.periodResult)
    }

    @Test fun `gas ev direct legitimate zero and invalid inputs`() {
        decimal("2.841666666666667", RunningCostInputs.TYPICAL.costPerKm)
        decimal("1.6", RunningCostInputs(RunningCostMode.ELECTRIC, BigDecimal("0.8"), kwhPer100Km = BigDecimal("16"), pricePerKwh = BigDecimal("5")).costPerKm)
        decimal("0", RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal.ZERO).costPerKm)
        assertNull(RunningCostInputs.TYPICAL.copy(kmPerLitre = BigDecimal.ZERO).costPerKm)
        assertNull(RunningCostInputs(RunningCostMode.ELECTRIC).costPerKm)
        assertNull(RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal("-1")).costPerKm)
    }
}
