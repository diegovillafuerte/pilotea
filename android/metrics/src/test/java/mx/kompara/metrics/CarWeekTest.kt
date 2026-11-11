package mx.kompara.metrics

import mx.kompara.data.accounting.CarCostVersion
import mx.kompara.data.accounting.PeriodWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The car as a weekly bill, on the kit's L4/L5 weeks: $1,500 a week, cost per km 2.8417. */
class CarWeekTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val cost = BigDecimal("24.5").divide(BigDecimal(12), java.math.MathContext.DECIMAL128).add(BigDecimal("0.8"))
    private fun net(fare: Int, km: String) = BigDecimal(fare).subtract(BigDecimal(km).multiply(cost))
    private fun BigDecimal.whole() = setScale(0, RoundingMode.HALF_UP)
    private val bill = BigDecimal(1500)

    @Test fun `12 – 18 oct is covered on Tuesday and the rest is yours`() {
        // Mon 1,050 − 85.0 km × 2.8417 = 808.46; Tue 1,230 − 98.4 × 2.8417 = 950.38 → 1,758.84 ≥ 1,500 on Tuesday
        val days = listOf(net(1050, "85.0"), net(1230, "98.4"), net(1180, "95.1"), net(1350, "108.6"),
            net(1900, "183.0"), net(1090, "70.0"), net(600, "35.7"))
        val week = CarWeek(bill, days)
        assertEquals(DayOfWeek.TUESDAY, week.coveredOn)
        // week = 8,400 − 675.8 × 2.8417 = 6,479.58; yours = 6,479.58 − 1,500 = 4,979.58 → "Tuyo $4,980"
        assertEquals(BigDecimal("6480"), week.earned.whole())
        assertEquals(BigDecimal("4980"), week.yours!!.whole())
        assertEquals(week.earned.subtract(bill), week.yours)
        assertNull(week.missing)
    }

    @Test fun `19 – 25 oct at 762 misses 738`() {
        val week = CarWeek(bill, listOf(BigDecimal("762.31"), null, null, null, null, null, null))
        assertNull(week.coveredOn); assertNull(week.yours)
        assertEquals(BigDecimal("738"), week.missing!!.whole()) // "$762 de $1,500 · Te faltan $738"
    }

    @Test fun `reaching the bill exactly covers that day`() {
        val week = CarWeek(bill, listOf(BigDecimal(700), BigDecimal(0), BigDecimal(800), null, null, null, null))
        assertEquals(DayOfWeek.WEDNESDAY, week.coveredOn); assertEquals(BigDecimal.ZERO, week.yours)
    }

    @Test fun `a week that falls back under the bill is not covered`() {
        // Monday 1,510 covers 1,500; Tuesday a trip costs more than it pays and the week is at 1,490.
        val under = CarWeek(bill, listOf(BigDecimal(1510), BigDecimal(-20), null, null, null, null, null))
        assertNull(under.coveredOn); assertNull(under.yours)
        assertEquals(BigDecimal(10), under.missing)
        // Back over on Wednesday: covered from Wednesday, the first day it never falls under again.
        val back = under.copy(days = listOf(BigDecimal(1510), BigDecimal(-20), BigDecimal(30), BigDecimal(-15), null, null, null))
        assertEquals(DayOfWeek.WEDNESDAY, back.coveredOn); assertEquals(BigDecimal(5), back.yours); assertNull(back.missing)
    }

    @Test fun `the amount in force on the week's last elapsed day is the week's bill, whole`() {
        val start = LocalDate.of(2026, 10, 19)
        fun window(today: LocalDate) = PeriodWindow(start, start.plusDays(6), today.atTime(14, 40).atZone(zone).toInstant(), zone)
        val days = listOf<BigDecimal?>(BigDecimal(900), BigDecimal(700), null, null, null, null, null)
        val car = listOf(CarCostVersion(1, LocalDate.of(2026, 9, 28), bill, 0), CarCostVersion(2, LocalDate.of(2026, 10, 21), BigDecimal(1800), 0))
        // On Tuesday 1,500 is in force: covered Tuesday. Set from Wednesday, 1,800 is the bill once Wednesday comes.
        assertEquals(bill, CarWeek.of(car, window(start.plusDays(1)), days).bill)
        assertEquals(DayOfWeek.TUESDAY, CarWeek.of(car, window(start.plusDays(1)), days).coveredOn)
        assertEquals(BigDecimal(1800), CarWeek.of(car, window(start.plusDays(2)), days).bill)
        assertEquals(BigDecimal(200), CarWeek.of(car, window(start.plusDays(2)), days).missing)
        // a closed week uses Sunday's amount
        assertEquals(BigDecimal(1800), CarWeek.of(car, window(start.plusDays(9)), days).bill)
    }

    @Test fun `no car, a removed car or a zero amount is no bill`() {
        val days = listOf<BigDecimal?>(BigDecimal(900), null, null, null, null, null, null)
        val window = PeriodWindow(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), Instant.parse("2026-10-20T20:00:00Z"), zone)
        for (car in listOf(emptyList(), listOf(CarCostVersion(1, LocalDate.of(2026, 9, 28), null, 0)),
                listOf(CarCostVersion(1, LocalDate.of(2026, 9, 28), BigDecimal.ZERO, 0)))) {
            val week = CarWeek.of(car, window, days)
            assertNull(week.bill); assertNull(week.coveredOn); assertNull(week.missing); assertNull(week.yours)
            assertEquals(BigDecimal(900), week.earned)
        }
    }
}
