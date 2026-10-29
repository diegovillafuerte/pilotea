package mx.kompara.ui.share

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.accounting.*
import mx.kompara.data.model.City
import mx.kompara.metrics.compare.*
import mx.kompara.ui.fixtures.ShareFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareCardComposerTest {
    private val fixture get() = ShareFixture(ApplicationProvider.getApplicationContext())
    @Test fun `H3 uses only its certified statement count standing hours and payment basis`() {
        val hidden = fixture.card()
        assertTrue(hidden.finding.contains("78\u00a0%")); assertEquals("34", hidden.trips)
        assertEquals("Antes de gastos", hidden.basis); assertTrue(hidden.confirmed)
        assertTrue(hidden.source.contains("1,240")); assertEquals("12 – 18 oct", hidden.periodLabel)
        assertNull(hidden.netEarnings); assertNull(hidden.hours); assertTrue(hidden.bars.isEmpty())
        val shown = fixture.card(hide = false)
        assertEquals(listOf("$149/viaje", "$131/viaje"), shown.bars.map { it.amount })
        assertEquals("24\u00a0h", shown.hours)
        assertEquals(1f, shown.bars.first().fraction, .00001f)
        assertEquals(131f / (5062f / 34), shown.bars.last().fraction, .00001f)
    }
    @Test fun `redaction removes every amount hour and proportional bar from all outgoing content`() {
        for (frame in listOf("H3", "week", "month", "historical")) {
            val hidden = fixture.card(frame)
            assertFalse(hidden.description.contains('$')); assertFalse(hidden.caption.contains('$'))
            assertFalse(hidden.caption.contains("Horas")); assertFalse(hidden.description.contains("Tiempo"))
            assertFalse(hidden.caption.contains("24 h")); assertTrue(hidden.bars.isEmpty())
            assertNull(hidden.hours); assertNull(hidden.netEarnings)
        }
    }
    @Test fun `wrong week app city source field coverage and synthetic cohorts cannot grant a rank`() {
        val f = fixture
        val p = f.proof; val c = f.comparison.cohort
        val invalid = listOf(p.copy(certificationId = ""), p.copy(city = "monterrey"),
            p.copy(record = p.record.copy(app = "DIDI")),
            p.copy(record = p.record.copy(source = MoneySource.PARSED_STATEMENT)),
            p.copy(record = p.record.copy(paymentBasis = PaymentBasis.UNCONFIRMED)),
            p.copy(record = p.record.copy(coverage = p.record.coverage.copy(start = p.record.coverage.start.minusWeeks(1)))),
            p.copy(confirmedFields = setOf(MoneyField.PAYMENT)),
            p.copy(record = p.record.copy(fieldSources = emptyMap())))
        invalid.forEach { proof ->
            val card = ShareCardComposer.compose(f.comparison.current.money, SharePeriodKind.WEEK, statements = listOf(proof), cohorts = listOf(c))
            assertFalse(card.confirmed); assertFalse(card.finding.contains('%')); assertEquals("62", card.trips)
        }
        listOf(c.copy(synthetic = true), c.copy(week = c.week.minusWeeks(1)), c.copy(app = "DIDI"), c.copy(city = "monterrey"),
            c.copy(basis = ComparisonBasis.RUNNING_COST_CONTRIBUTION), c.copy(completeCoverage = false)).forEach { cohort ->
            assertFalse(ShareCardComposer.compose(f.comparison.current.money, SharePeriodKind.WEEK, statements = listOf(p), cohorts = listOf(cohort)).confirmed)
        }
    }
    @Test fun `no standing has truthful own counts and rounded after-car money no streak or best app`() {
        val card = fixture.card("week", false)
        assertEquals("62 viajes esa semana", card.finding); assertEquals("$4,980", card.netEarnings)
        assertTrue(card.netLabel!!.startsWith("Te quedaron")); assertFalse(card.confirmed)
        assertFalse(card.caption.contains('%')); assertFalse(card.caption.contains("Mejor app")); assertFalse(card.caption.contains("Racha"))
        assertTrue(card.basis.contains("lo del coche")); assertTrue(card.basis.contains("Antes de impuestos"))
    }
    @Test fun `month is its own scope never a borrowed weekly rank`() {
        val card = fixture.card("month", false)
        assertEquals(SharePeriodKind.MONTH, card.periodKind); assertEquals("Octubre 2026", card.periodLabel)
        assertEquals("$11,692", card.netEarnings); assertEquals("155", card.trips)
        assertTrue(card.netLabel!!.startsWith("Llevas en octubre")); assertFalse(card.confirmed)
    }
    @Test fun `payment-only imports retain trips but never present partial captured net as the whole period`() {
        val money = fixture.comparison.current.money
        val imported = IncomeRecord("partial", "DIDI", money.window.coverage, MoneySource.PARSED_STATEMENT,
            "400".toBigDecimal(), null, 3)
        val mixed = money.copy(paymentOnly = listOf(Contribution(imported, null, null, null)))
        assertNotNull(mixed.periodResult)
        val card = ShareCardComposer.compose(mixed, SharePeriodKind.WEEK, hide = false)
        assertEquals("65 viajes esa semana", card.finding)
        assertEquals("Según tus viajes registrados y resúmenes importados", card.source)
        assertNull(card.netEarnings)
        assertEquals("Faltan datos para calcular lo que te quedó", card.basis)
        assertFalse(card.confirmed)
    }
    @Test fun `unknown imported trips never become zero or a partial whole-period count`() {
        val money = fixture.comparison.current.money
        val imported = IncomeRecord("partial", "DIDI", money.window.coverage, MoneySource.PARSED_STATEMENT,
            "400".toBigDecimal(), null, 0, tripsKnown = false)
        val unknown = Contribution(imported, null, null, null)
        for (snapshot in listOf(money.copy(paymentOnly = listOf(unknown)),
            money.copy(contributions = emptyList(), paymentOnly = listOf(unknown)))) {
            val card = ShareCardComposer.compose(snapshot, SharePeriodKind.WEEK, hide = false)
            assertEquals("Resumen de Kompara", card.finding)
            assertEquals("—", card.trips)
            assertFalse(card.caption.contains("0 viajes"))
            assertFalse(card.caption.contains("62 viajes"))
            assertNull(card.netEarnings)
            assertEquals("Faltan datos para calcular lo que te quedó", card.basis)
        }
    }
    @Test fun `historical period and negative or missing money never become current-month zero`() {
        assertEquals("5 – 11 oct", fixture.card("historical").periodLabel)
        val money = fixture.comparison.current.money
        val negative = ShareCardComposer.compose(money.copy(periodResult = "-100.49".toBigDecimal()), SharePeriodKind.WEEK, false)
        assertEquals("−$100", negative.netEarnings)
        assertNull(ShareCardComposer.compose(money.copy(periodResult = null), SharePeriodKind.WEEK, false).netEarnings)
    }
    @Test fun `same-day open week cannot claim statement confirmation and uncertified hours stay absent`() {
        val f = fixture
        val money = f.comparison.current.money
        val open = money.copy(window = money.window.copy(asOf = money.window.endInclusive.atStartOfDay(money.window.zone).toInstant()))
        assertFalse(ShareCardComposer.compose(open, SharePeriodKind.WEEK, statements = listOf(f.proof), cohorts = listOf(f.comparison.cohort)).confirmed)
        val noHours = ShareCardComposer.compose(money, SharePeriodKind.WEEK, false,
            statements = listOf(f.proof.copy(confirmedFields = setOf(MoneyField.PAYMENT, MoneyField.TRIPS))), cohorts = listOf(f.comparison.cohort))
        assertTrue(noHours.confirmed); assertNull(noHours.hours)
    }
    @Test fun `hours omit zero minutes preserve fractional time and round only at display`() {
        assertEquals("24\u00a0h", shareDuration("1440".toBigDecimal()))
        assertEquals("4\u00a0h\u00a050\u00a0min", shareDuration("290".toBigDecimal()))
        assertEquals("5\u00a0h", shareDuration("299.9".toBigDecimal()))
    }
    @Test fun `renderer input rejects a private value marked as hidden`() {
        assertThrows(IllegalArgumentException::class.java) { fixture.card().copy(hours = "24 h") }
        assertThrows(IllegalArgumentException::class.java) { fixture.card().copy(netEarnings = "$99") }
        assertThrows(IllegalArgumentException::class.java) { fixture.card().copy(bars = fixture.card(hide = false).bars) }
    }
}
