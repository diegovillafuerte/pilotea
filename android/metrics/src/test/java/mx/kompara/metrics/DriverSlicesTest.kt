package mx.kompara.metrics

import mx.kompara.data.accounting.CostConfirmation
import mx.kompara.data.accounting.CostSchedule
import mx.kompara.data.accounting.PeriodAccounting
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.data.accounting.RunningCostVersion
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.data.rollup.capturedIncomeRecords
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Comparar · Tú on a small ledger run through the real taken-trip and contribution rules. Cost $2 per km. */
class DriverSlicesTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val today = LocalDate.of(2026, 10, 19) // Monday; the window is Mon 21 Sep … Sun 18 Oct
    private val asOf = today.atTime(14, 40).atZone(zone).toInstant()
    private val costs = CostSchedule(listOf(RunningCostVersion(1, 0, 0,
        RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal("2")), CostConfirmation.CONFIRMED)))
    private var nextId = 1L

    private fun trip(app: String, local: String, fare: Double, km: Double, minutes: Double? = 20.0, taken: Boolean = true): Pair<TripEntity, OfferEntity> {
        val id = nextId++
        val at = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
        return TripEntity(id = id, offerId = id, startedAt = at, endedAt = at + 600_000, platform = app, grossMxn = fare,
            distanceKm = km, durationMin = minutes) to OfferEntity(id = id, seenAt = at - 60_000, platform = app, fareMxn = fare,
            distanceKm = km, durationMin = minutes, outcome = if (taken) "ACCEPTED" else "NOT_TAKEN")
    }

    private fun slices(rows: List<Pair<TripEntity, OfferEntity>>, schedule: CostSchedule = costs): DriverSlices {
        val range = DriverSlices.window(today)
        val records = capturedIncomeRecords(rows.map { it.first }, rows.map { it.second }, asOf.toEpochMilli(), zone)
        val money = PeriodAccounting.calculate(PeriodWindow(range.start, range.endInclusive, asOf, zone), records, schedule)
        return DriverSlices.of(range.start, range.endInclusive, money.contributions, zone)
    }

    private fun BigDecimal?.cents() = this!!.setScale(2, RoundingMode.HALF_UP)

    @Test fun `a group's rate is a ratio of sums in every unit and basis`() {
        val s = slices(listOf(trip("UBER", "2026-10-14T09:00", 100.0, 10.0, 20.0), trip("UBER", "2026-10-15T09:00", 300.0, 20.0, 40.0)))
        val uber = s.byApp.getValue("UBER")
        assertEquals(2, uber.trips)
        // gross 400 / 30 km = 13.33 (not the mean of 10 and 15); net (400 − 60) / 30 = 11.33
        assertEquals(BigDecimal("13.33"), uber.rate(PreferredMetric.IPK, RateBasis.GROSS).cents())
        assertEquals(BigDecimal("11.33"), uber.rate(PreferredMetric.IPK, RateBasis.NET).cents())
        // per hour over 60 min: gross 400, net 340
        assertEquals(BigDecimal("400.00"), uber.rate(PreferredMetric.IPH, RateBasis.GROSS).cents())
        assertEquals(BigDecimal("340.00"), uber.rate(PreferredMetric.IPH, RateBasis.NET).cents())
        assertEquals(BigDecimal("400.0"), uber.fare); assertEquals(BigDecimal("30.0"), uber.km); assertEquals(BigDecimal("60.0"), uber.minutes)
    }

    @Test fun `a trip without a duration counts per km but stays out of the hour rate`() {
        val s = slices(listOf(trip("DIDI", "2026-10-14T09:00", 100.0, 10.0, 30.0), trip("DIDI", "2026-10-15T09:00", 200.0, 10.0, null)))
        val didi = s.byApp.getValue("DIDI")
        assertEquals(BigDecimal("15.00"), didi.rate(PreferredMetric.IPK, RateBasis.GROSS).cents()) // 300 / 20
        assertEquals(BigDecimal("200.00"), didi.rate(PreferredMetric.IPH, RateBasis.GROSS).cents()) // 100 × 60 / 30
        assertEquals(BigDecimal("160.00"), didi.rate(PreferredMetric.IPH, RateBasis.NET).cents()) // (100 − 20) × 60 / 30
    }

    @Test fun `weekday and hour block follow the local start time at the boundaries`() {
        val s = slices(listOf(
            trip("UBER", "2026-10-17T05:59", 100.0, 10.0), // Saturday, 22–6
            trip("UBER", "2026-10-17T06:00", 100.0, 10.0), // Saturday, 6–10
            trip("UBER", "2026-10-16T22:00", 100.0, 10.0), // Friday, 22–6
            trip("UBER", "2026-10-16T21:59", 100.0, 10.0), // Friday, 18–22
            trip("UBER", "2026-10-12T10:00", 100.0, 10.0), // Monday, 10–14
            trip("UBER", "2026-10-18T13:59", 100.0, 10.0), // Sunday, 10–14
        ))
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), s.byWeekday.keys.toList())
        assertEquals(2, s.byWeekday.getValue(DayOfWeek.SATURDAY).trips); assertEquals(2, s.byWeekday.getValue(DayOfWeek.FRIDAY).trips)
        assertEquals(listOf(HourBlock.H6_10, HourBlock.H10_14, HourBlock.H18_22, HourBlock.H22_6), s.byHourBlock.keys.toList())
        assertEquals(2, s.byHourBlock.getValue(HourBlock.H22_6).trips)
        assertEquals(1, s.byHourBlock.getValue(HourBlock.H6_10).trips)
        assertEquals(2, s.byHourBlock.getValue(HourBlock.H10_14).trips)
        assertEquals(HourBlock.H22_6, HourBlock.of(0)); assertEquals(HourBlock.H14_18, HourBlock.of(17)); assertEquals(HourBlock.H18_22, HourBlock.of(18))
    }

    @Test fun `only taken trips of the 28 days before today count`() {
        val s = slices(listOf(
            trip("UBER", "2026-09-21T00:00", 100.0, 10.0), // first day of the window
            trip("UBER", "2026-10-18T23:59", 100.0, 10.0), // last day
            trip("UBER", "2026-09-20T23:59", 100.0, 10.0), // 29 days ago: out
            trip("UBER", "2026-10-19T08:00", 100.0, 10.0), // today: out until it is complete
            trip("DIDI", "2026-10-10T12:00", 100.0, 10.0, taken = false), // left without being taken: out
        ))
        assertEquals(LocalDate.of(2026, 9, 21), s.from); assertEquals(LocalDate.of(2026, 10, 18), s.to)
        assertEquals(2, s.trips); assertEquals(setOf("UBER"), s.byApp.keys)
    }

    @Test fun `a group needs 3 trips and the comparison needs 30`() {
        val few = slices(listOf(trip("UBER", "2026-10-14T09:00", 100.0, 10.0), trip("UBER", "2026-10-14T10:00", 100.0, 10.0),
            trip("DIDI", "2026-10-14T11:00", 100.0, 10.0), trip("DIDI", "2026-10-14T12:00", 100.0, 10.0), trip("DIDI", "2026-10-14T13:00", 100.0, 10.0)))
        assertFalse(few.byApp.getValue("UBER").drawable); assertTrue(few.byApp.getValue("DIDI").drawable)
        assertFalse(few.ready)
        fun many(count: Int) = slices((0 until count).map { trip("UBER", LocalDate.of(2026, 9, 21).plusDays(it % 28L).atTime(9, it / 28).toString(), 100.0, 10.0) })
        assertEquals(29, many(29).trips); assertFalse(many(29).ready)
        assertEquals(30, many(30).trips); assertTrue(many(30).ready)
        assertEquals(30, DriverSlices.MIN_TRIPS_TO_COMPARE); assertEquals(3, DriverSlices.MIN_TRIPS_PER_GROUP)
    }

    @Test fun `an unknown cost leaves the net rate unknown and the gross rate intact`() {
        val later = CostSchedule(listOf(costs.running.single().copy(effectiveFrom = asOf.toEpochMilli())))
        val uber = slices(listOf(trip("UBER", "2026-10-14T09:00", 100.0, 10.0)), later).byApp.getValue("UBER")
        assertNull(uber.net); assertNull(uber.rate(PreferredMetric.IPK, RateBasis.NET))
        assertEquals(BigDecimal("10.00"), uber.rate(PreferredMetric.IPK, RateBasis.GROSS).cents())
    }
}
