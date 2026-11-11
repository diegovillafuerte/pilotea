package mx.kompara.ui.stats

import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.metrics.VerdictLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Offer-funnel classification and shift timeline assembly for [DayDetailBuilder] (B-040 req 2). */
class DayDetailBuilderTest {

    private fun offer(id: Long, fare: Double, outcome: OfferOutcome, verdict: VerdictLevel?) = OfferEntity(
        id = id,
        seenAt = 1000L * id,
        platform = "UBER",
        fareMxn = fare,
        distanceKm = 8.0,
        durationMin = 15.0,
        outcome = outcome.name,
        verdict = verdict?.name,
    )

    @Test
    fun `funnel counts seen, taken and declined (declined folds in expired)`() {
        val offers = listOf(
            offer(1, 100.0, OfferOutcome.ACCEPTED, VerdictLevel.GREEN),
            offer(2, 50.0, OfferOutcome.DECLINED, VerdictLevel.RED),
            offer(3, 60.0, OfferOutcome.EXPIRED, VerdictLevel.YELLOW),
            offer(4, 80.0, OfferOutcome.PENDING, null),
        )
        val detail = DayDetailBuilder.build(
            dayIso = "2026-06-10",
            period = PeriodStats.EMPTY,
            shifts = emptyList(),
            trips = emptyList(),
            offers = offers,
            bestHours = emptyList(),
        )
        assertEquals(4, detail.offers.seen)
        assertEquals(1, detail.offers.taken)
        assertEquals(2, detail.offers.declined) // declined + expired
        assertEquals(1, detail.offers.pending)
        // Verdicts carried through and rows sorted by seenAt.
        assertEquals(VerdictLevel.GREEN, detail.offers.rows.last().verdict)
    }

    @Test
    fun `shift timeline sorted by start with counts and net`() {
        val shifts = listOf(
            ShiftEntity(id = 2, startedAt = 5000L, endedAt = 9000L, tripCount = 3, netMxn = 240.0),
            ShiftEntity(id = 1, startedAt = 1000L, endedAt = 4000L, tripCount = 2, netMxn = 120.0),
        )
        val detail = DayDetailBuilder.build(
            dayIso = "2026-06-10",
            period = PeriodStats.EMPTY,
            shifts = shifts,
            trips = emptyList(),
            offers = emptyList(),
            bestHours = emptyList(),
        )
        assertEquals(2, detail.shifts.size)
        assertEquals(1L, detail.shifts.first().shiftId) // earliest start first
        assertEquals(2, detail.shifts.first().tripCount)
        assertEquals(240.0, detail.shifts.last().netMxn, 0.001)
    }

    @Test
    fun `open shift falls back to counting the day's trips when its counter is zero`() {
        val shift = ShiftEntity(id = 7, startedAt = 1000L, endedAt = null, tripCount = 0, netMxn = 0.0)
        val trips = listOf(
            TripEntity(id = 1, shiftId = 7, startedAt = 1500L, endedAt = 2000L, platform = "UBER", grossMxn = 100.0, distanceKm = 8.0, durationMin = 15.0),
            TripEntity(id = 2, shiftId = 7, startedAt = 2500L, endedAt = 3000L, platform = "UBER", grossMxn = 90.0, distanceKm = 7.0, durationMin = 14.0),
        )
        val detail = DayDetailBuilder.build(
            dayIso = "2026-06-10",
            period = PeriodStats.EMPTY,
            shifts = listOf(shift),
            trips = trips,
            offers = emptyList(),
            bestHours = emptyList(),
        )
        assertEquals(2, detail.shifts.first().tripCount)
    }
    @Test fun `in progress finished unknown not taken and cancelled keep distinct tenses`() {
        val row = OfferRowModel(1, 1, "UBER", 148.0, OfferOutcome.ACCEPTED, VerdictLevel.GREEN,
            mx.kompara.data.db.entity.TripLifecycle.ACCEPTED_ESTIMATE,
            evaluation = mx.kompara.data.db.entity.OfferEvaluation(1, "IPK", "8", "6", 1, "OCR", "DRIVER_PAYMENT", "114", "9.49", "285", null))
        assertEquals("En curso", row.outcomeLabel)
        assertEquals("Te deja", row.contributionLabel)
        assertEquals("Te dejó", row.copy(lifecycle = mx.kompara.data.db.entity.TripLifecycle.FINISHED).contributionLabel)
        assertEquals("Te habría dejado", row.copy(lifecycle = null, outcome = OfferOutcome.NOT_TAKEN).contributionLabel)
        assertEquals("Sin confirmar", row.copy(lifecycle = mx.kompara.data.db.entity.TripLifecycle.UNKNOWN).outcomeLabel)
        assertEquals("Se canceló", row.copy(lifecycle = mx.kompara.data.db.entity.TripLifecycle.CANCELLED).outcomeLabel)
        org.junit.Assert.assertNull(row.copy(lifecycle = mx.kompara.data.db.entity.TripLifecycle.CANCELLED).contributionLabel)
    }

    @Test fun `historical suffix compares only the named limit in its saved unit`() {
        val snapshot = mx.kompara.data.db.entity.OfferEvaluation(1, "IPK", "8.0", "6.00", 1, "OCR",
            "DRIVER_PAYMENT", "114", "9.49", "285", null)
        val row = OfferRowModel(1, 1, "UBER", 148.0, OfferOutcome.ACCEPTED, VerdictLevel.GREEN, evaluation = snapshot)
        val limits = mx.kompara.data.settings.PlatformThreshold(8.0, 200.0, 6.5, 150.0)
        val km = mx.kompara.data.settings.PreferredMetric.IPK
        val hour = mx.kompara.data.settings.PreferredMetric.IPH
        val net = mx.kompara.data.settings.RateBasis.NET
        assertEquals("", row.historicalLimitSuffix(limits, km, net))
        assertEquals(" a esa hora", row.copy(verdict = VerdictLevel.YELLOW).historicalLimitSuffix(limits, km, net))
        assertEquals(" a esa hora", row.historicalLimitSuffix(limits.copy(minPerKmMxn = 9.5), km, net))
        org.junit.Assert.assertNull(row.copy(evaluation = null).historicalLimitSuffix(limits, km, net))
        val hourly = row.copy(evaluation = snapshot.copy(unit = "IPH", goal = "200", minimum = "150"))
        assertEquals("", hourly.historicalLimitSuffix(limits.copy(minPerKmMxn = 99.0), hour, net))
        assertEquals(" a esa hora", hourly.historicalLimitSuffix(limits.copy(minPerHourMxn = 210.0), hour, net))
    }

    @Test fun `a esa hora appears when either limit a yellow sentence names, the unit or the basis changed`() {
        // B8: a yellow sentence names both limits; the basis and the unit count too.
        val snapshot = mx.kompara.data.db.entity.OfferEvaluation(1, "IPK", "8", "6", 1, "OCR", "DRIVER_PAYMENT", "76", "6.39", "181", null)
        val yellow = OfferRowModel(1, 1, "DIDI", 96.0, OfferOutcome.NOT_TAKEN, VerdictLevel.YELLOW, evaluation = snapshot)
        val km = mx.kompara.data.settings.PreferredMetric.IPK
        val net = mx.kompara.data.settings.RateBasis.NET
        val limits = mx.kompara.data.settings.PlatformThreshold(8.0, 200.0, 6.0, 150.0)
        assertEquals("", yellow.historicalLimitSuffix(limits, km, net))
        assertEquals(" a esa hora", yellow.historicalLimitSuffix(limits.copy(minPerKmMxn = 9.0), km, net)) // only the goal moved
        assertEquals(" a esa hora", yellow.historicalLimitSuffix(limits, km, mx.kompara.data.settings.RateBasis.GROSS))
        assertEquals(" a esa hora", yellow.historicalLimitSuffix(limits, mx.kompara.data.settings.PreferredMetric.IPH, net))
        val green = yellow.copy(verdict = VerdictLevel.GREEN)
        assertEquals(" a esa hora", green.historicalLimitSuffix(limits, km, mx.kompara.data.settings.RateBasis.GROSS))
        assertEquals("", green.historicalLimitSuffix(limits.copy(redPerKmMxn = 6.5), km, net)) // a green sentence names only the goal
    }

    @Test fun `legacy zero and stored verdict survive without invented monetary explanation`() {
        val legacy = offer(1, 0.0, OfferOutcome.ACCEPTED, VerdictLevel.RED).copy(inputProvenance = "LEGACY_UNSPECIFIED")
        val row = DayDetailBuilder.build("2026-10-19", PeriodStats.EMPTY, emptyList(), emptyList(), listOf(legacy), emptyList()).offers.rows.single()
        assertEquals(VerdictLevel.RED, row.verdict)
        org.junit.Assert.assertNull(row.fareMxn)
        org.junit.Assert.assertNull(row.evaluation)
        assertEquals("Sin confirmar", row.outcomeLabel)
    }

}
