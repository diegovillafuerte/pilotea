package mx.kompara.metrics.imss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * The founder's rule on the kit's October (M1, M2): 9,582.47 ÷ ((1 − 0.48) × (1 − 0.16)) = 21,937.89 → $21,938.
 * Uber trips 12,487 + incentives 1,800; DiDi trips 7,942, no incentives; today 19 Oct of 31 days.
 */
class ImssMonthTest {
    private val october = YearMonth.of(2026, 10)
    private val day19 = LocalDate.of(2026, 10, 19)
    private fun pesos(value: Int) = BigDecimal(value)
    private fun BigDecimal.whole() = setScale(0, RoundingMode.HALF_UP)
    private fun BigDecimal.hundreds() = divide(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).multiply(BigDecimal(100))

    @Test fun `the need is 21,938`() {
        assertEquals(BigDecimal("21938"), ImssRule.Y2026.need)
        assertEquals(2026, ImssRule.Y2026.year)
    }

    @Test fun `M1 — Uber reaches with its incentives, DiDi falls short, together they are there`() {
        val m = ImssMonth.of(october, day19, mapOf("UBER" to pesos(12_487), "DIDI" to pesos(7_942)), mapOf("UBER" to 1_800))
        assertEquals(12, m.daysLeft) // "quedan 12 días"
        assertEquals(listOf("UBER", "DIDI"), m.apps.map { it.platform })
        val (uber, didi) = m.apps
        assertEquals(pesos(14_287), uber.counted); assertEquals(1_800, uber.incentives)
        // pace = 12,487 ÷ 19 × 31 + 1,800 = 22,173.5 → "unos $22,200"
        assertEquals(BigDecimal("22174"), uber.pace!!.whole()); assertEquals(BigDecimal("22200"), uber.pace!!.hundreds())
        assertEquals(ImssState.WILL_REACH, uber.state); assertNull(uber.shortBy)
        // without its incentives Uber would not make it: 20,373.5 < 21,938
        assertTrue(uber.pace!!.subtract(pesos(1_800)) < m.need)
        assertNull(didi.incentives); assertEquals(pesos(7_942), didi.counted)
        // pace = 7,942 ÷ 19 × 31 = 12,958 → "unos $13,000"; short by 21,938 − 12,958 = 8,980 → "Te faltarían $9,000"
        assertEquals(BigDecimal("12958"), didi.pace!!.whole()); assertEquals(BigDecimal("13000"), didi.pace!!.hundreds())
        assertEquals(ImssState.WILL_FALL_SHORT, didi.state)
        assertEquals(BigDecimal("8980"), didi.shortBy!!.whole()); assertEquals(BigDecimal("9000"), didi.shortBy!!.hundreds())
        // 14,287 + 7,942 = 22,229 ≥ 21,938 with no single app there
        assertEquals(pesos(22_229), m.combined); assertTrue(m.combinedReaches); assertTrue(m.hasTrips)
    }

    @Test fun `M2 — 20,610 plus 1,800 is over`() {
        val m = ImssMonth.of(october, LocalDate.of(2026, 10, 27), mapOf("UBER" to pesos(20_610)), mapOf("UBER" to 1_800))
        assertEquals(4, m.daysLeft)
        val uber = m.apps.single()
        assertEquals(pesos(22_410), uber.counted); assertEquals(ImssState.OVER, uber.state); assertNull(uber.shortBy)
        assertFalse(m.combinedReaches) // one app only
    }

    @Test fun `an app already over keeps the combined line off`() {
        val m = ImssMonth.of(october, day19, mapOf("UBER" to pesos(22_000), "DIDI" to pesos(500)), emptyMap())
        assertEquals(ImssState.OVER, m.apps.first().state); assertFalse(m.combinedReaches)
    }

    @Test fun `no pace before the 7th, then from the 7th`() {
        val early = ImssMonth.of(october, LocalDate.of(2026, 10, 6), mapOf("DIDI" to pesos(3_000)), emptyMap()).apps.single()
        assertNull(early.pace); assertEquals(ImssState.NO_PACE_YET, early.state); assertNull(early.shortBy)
        // day 7: 3,000 ÷ 7 × 31 = 13,285.7
        val seventh = ImssMonth.of(october, LocalDate.of(2026, 10, 7), mapOf("DIDI" to pesos(3_000)), emptyMap()).apps.single()
        assertEquals(BigDecimal("13286"), seventh.pace!!.whole()); assertEquals(ImssState.WILL_FALL_SHORT, seventh.state)
    }

    @Test fun `a past month has no pace and says what was missing`() {
        val m = ImssMonth.of(YearMonth.of(2026, 9), day19, mapOf("DIDI" to pesos(12_287)), emptyMap())
        assertNull(m.daysLeft)
        val didi = m.apps.single()
        assertNull(didi.pace); assertEquals(ImssState.FELL_SHORT, didi.state)
        assertEquals(pesos(9_651), didi.shortBy) // "Te faltaron $9,651": 21,938 − 12,287
        val over = ImssMonth.of(YearMonth.of(2026, 9), day19, mapOf("UBER" to pesos(21_938)), emptyMap()).apps.single()
        assertEquals(ImssState.OVER, over.state) // exactly the need counts
    }

    @Test fun `the last day, incentives without trips, and an empty month`() {
        val last = ImssMonth.of(october, LocalDate.of(2026, 10, 31), mapOf("UBER" to pesos(1_000)), emptyMap())
        assertEquals(0, last.daysLeft) // "último día"
        val typedOnly = ImssMonth.of(october, day19, emptyMap(), mapOf("DIDI" to 500))
        assertEquals(listOf("DIDI"), typedOnly.apps.map { it.platform }); assertFalse(typedOnly.hasTrips)
        assertEquals(BigDecimal.ZERO, typedOnly.apps.single().trips)
        val empty = ImssMonth.of(october, day19, emptyMap(), emptyMap())
        assertTrue(empty.apps.isEmpty()); assertFalse(empty.hasTrips) // "Aún no hay viajes en octubre."
    }
}
