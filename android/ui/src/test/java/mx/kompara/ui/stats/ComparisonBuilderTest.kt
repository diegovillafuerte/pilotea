package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.accounting.*
import mx.kompara.metrics.WeekMoneySnapshot
import mx.kompara.metrics.compare.*
import mx.kompara.ui.fixtures.CompareFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComparisonBuilderTest {
    private val fixture get() = CompareFixture(ApplicationProvider.getApplicationContext())
    @Test fun `apps use raw running costs not car or blended hours`() {
        val f = fixture
        val c = f.comparison()
        assertEquals("DiDi te dejó $1.21 más por km", c.finding)
        assertEquals(listOf("$10.34/km", "$9.13/km"), c.values.map { CompareUiText.rate(it.value!!, c.choice.metric) })
        assertEquals(listOf("sin el coche", "sin el coche"), c.values.map { it.qualifier })
        assertNull(c.rank)
        val hour = ComparisonBuilder.build(f.current, f.previous, CompareChoice(appsMetric = ComparisonMetric.PER_HOUR))
        assertEquals(ComparisonStatus.UNAVAILABLE, hour.status)
        assertTrue(hour.values.all { it.value == null })
        val changedCar = f.current.copy(money = f.current.money.copy(carCost = BigDecimal("99999"), hoursOnline = BigDecimal("777")))
        assertEquals(c.values, ComparisonBuilder.build(changedCar, f.previous, CompareChoice()).values)
    }
    @Test fun `city uses statement payment and same cohort rank free retains own value`() {
        val c = fixture.comparison("F2")
        assertEquals(78, c.rank)
        assertEquals(listOf("$149/viaje", "$131/viaje"), c.values.map { CompareUiText.rate(it.value!!, c.choice.metric) })
        assertEquals("Según los resúmenes de 1,240 conductores", c.qualifier)
        assertEquals("Antes de gastos", c.basis)
        val free = fixture.comparison("F3")
        assertEquals(c.rank, free.rank); assertEquals(c.values[0], free.values[0])
        assertTrue(free.values[1].locked); assertNull(free.values[1].value)
        assertFalse(free.details.any { it.contains("131") })
    }
    @Test fun `no cohort compares own matching captured weeks and displayed rounded difference`() {
        val c = fixture.comparison("F4")
        assertEquals(ComparisonStatus.NO_COHORT, c.status)
        assertEquals(listOf("$120/viaje", "$107/viaje"), c.values.map { CompareUiText.rate(it.value!!, c.choice.metric) })
        assertEquals("+12 %", c.difference); assertNull(c.rank); assertFalse(c.locked)
        assertEquals("Tú · 5 – 11 oct", c.values[1].label)
    }
    @Test fun `unconfirmed week never ranks or sells missing data even to premium`() {
        val f = fixture
        val c = f.comparison("F5")
        assertEquals(ComparisonStatus.UNCONFIRMED, c.status)
        assertEquals("$161/viaje", CompareUiText.rate(c.values[0].value!!, c.choice.metric))
        assertEquals("Contamos 31 viajes", c.qualifier)
        assertFalse(c.showBars); assertFalse(c.locked); assertNull(c.rank)
        val imported = f.statement.copy(record = f.statement.record.copy(source = MoneySource.PARSED_STATEMENT))
        val result = ComparisonBuilder.build(f.current, null, CompareChoice(scope = CompareScope.CITY), listOf(imported), listOf(f.cohort), true)
        assertEquals(ComparisonStatus.UNCONFIRMED, result.status); assertNull(result.rank); assertFalse(result.locked)
    }
    @Test fun `synthetic wrong city app week source basis coverage or ambiguous cohort cannot rank`() {
        val f = fixture
        val c = f.cohort
        listOf(c.copy(synthetic = true), c.copy(city = "national"), c.copy(app = "all"), c.copy(week = c.week.minusWeeks(1)),
            c.copy(source = MoneySource.CAPTURED_ESTIMATE), c.copy(basis = ComparisonBasis.RUNNING_COST_CONTRIBUTION),
            c.copy(completeCoverage = false), c.copy(certificationId = ""), c.copy(drivers = 19)).forEach {
            val result = ComparisonBuilder.build(f.current, f.previous, CompareChoice(scope = CompareScope.CITY), listOf(f.statement), listOf(it))
            assertNull(result.rank); assertFalse(result.locked)
        }
        assertNull(ComparisonBuilder.build(f.current, null, CompareChoice(scope = CompareScope.CITY), listOf(f.statement), listOf(c, c)).rank)
    }
    @Test fun `single app and empty do not invent a second app or change selected metric`() {
        val f = fixture
        val money = f.fixture.period(f.fixture.weekStart, f.fixture.weekStart.plusDays(6), f.fixture.weekRecords.filter { it.app == "UBER" })
        val c = ComparisonBuilder.build(WeekMoneySnapshot(money, emptyList()), null, CompareChoice())
        assertEquals(CompareScope.CITY, c.choice.scope); assertEquals("UBER", c.singleApp)
        assertFalse(c.values.any { it.label == "DiDi" })
        val empty = ComparisonBuilder.build(WeekMoneySnapshot(f.fixture.period(f.fixture.weekStart, f.fixture.weekStart.plusDays(6), emptyList()), emptyList()), null,
            CompareChoice(appsMetric = ComparisonMetric.PER_TRIP))
        assertEquals(ComparisonStatus.EMPTY, empty.status); assertEquals(ComparisonMetric.PER_TRIP, empty.choice.metric)
    }
    @Test fun `missing values are not zero and negative running contributions retain sign`() {
        val f = fixture
        fun build(records: List<IncomeRecord>) = ComparisonBuilder.build(WeekMoneySnapshot(f.fixture.period(f.fixture.weekStart, f.fixture.weekStart.plusDays(6), records), emptyList()), null, CompareChoice())
        val zero = build(f.fixture.weekRecords.map { it.copy(payment = BigDecimal.ZERO) })
        assertTrue(zero.values.all { it.value!!.signum() < 0 }); assertTrue(zero.values.all { CompareUiText.rate(it.value!!, zero.choice.metric).startsWith("−$") })
        val missing = build(f.fixture.weekRecords.map { it.copy(payment = null) })
        assertEquals(ComparisonStatus.UNAVAILABLE, missing.status); assertTrue(missing.values.all { it.value == null })
        val noDistance = build(f.fixture.weekRecords.map { it.copy(km = null) })
        assertEquals(ComparisonStatus.UNAVAILABLE, noDistance.status)
    }
    @Test fun `per app hours need two complete statements and costs on that same work`() {
        val f = fixture
        val records = f.fixture.weekRecords.map { it.copy(source = MoneySource.SERVER_CONFIRMED_STATEMENT) }
        val proofs = records.map { ConfirmedStatement(it, "cdmx", "proof-${it.app}", MoneyField.entries.toSet(), "1200".toBigDecimal()) }
        val snapshot = WeekMoneySnapshot(f.fixture.period(f.fixture.weekStart, f.fixture.weekStart.plusDays(6), records), emptyList(), statements = records)
        val selected = CompareChoice(appsMetric = ComparisonMetric.PER_HOUR)
        val complete = ComparisonBuilder.build(snapshot, null, selected, proofs)
        assertEquals(ComparisonStatus.OWN_APPS, complete.status)
        assertTrue(complete.values.all { it.value != null })
        val partial = ComparisonBuilder.build(snapshot, null, selected, proofs.take(1))
        assertEquals(ComparisonStatus.UNAVAILABLE, partial.status)
        assertEquals("falta tu resumen de DiDi", partial.finding)
        val noHours = ComparisonBuilder.build(snapshot, null, selected, proofs.map { it.copy(onlineMinutes = null) })
        assertEquals(ComparisonStatus.UNAVAILABLE, noHours.status)
    }
    @Test fun `open weeks cannot acquire a statement rank even with a fabricated early proof`() {
        val f = fixture
        val open = f.current.copy(money = f.current.money.copy(window = f.current.money.window.copy(asOf = f.fixture.weekStart.atStartOfDay(f.fixture.zone).toInstant())))
        assertNull(ComparisonBuilder.build(open, null, CompareChoice(scope = CompareScope.CITY), listOf(f.statement), listOf(f.cohort), true).rank)
    }

    @Test fun `statement numerator never borrows partial reader distance and captured time never enables hours`() {
        val f = fixture
        val half = f.statement.copy(record = f.statement.record.copy(km = BigDecimal("10"), fieldSources = f.statement.record.fieldSources + (MoneyField.DISTANCE to "reader-subset")),
            confirmedFields = MoneyField.entries.toSet())
        assertNull(half.value(f.fixture.weekStart, "UBER", ComparisonMetric.PER_KM))
        assertNull(paymentRate(f.fixture.weekRecords.first().copy(minutes = BigDecimal("600")), ComparisonMetric.PER_HOUR))
        val result = ComparisonBuilder.build(f.current, f.previous.copy(money = f.previous.money.copy(window = f.previous.money.window.copy(start = f.previous.money.window.start.minusWeeks(1)))), CompareChoice(scope = CompareScope.CITY, app = "DIDI"))
        assertNull(result.difference)
    }
}
