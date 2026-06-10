package mx.kompara.ui.share

import mx.kompara.data.model.City
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.ui.stats.PeriodStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure share-card composition logic (B-055): hide-amounts redaction, best-percentile selection
 * including display inversion, streak/period/trip formatting, and missing-data variants.
 */
class ShareCardComposerTest {

    private val fullWeek = PeriodStats(
        netEarningsMxn = 3450.0,
        grossEarningsMxn = 4200.0,
        totalTrips = 38,
        totalKm = 410.0,
        hoursOnline = 22.5,
        earningsPerTrip = 90.8,
        earningsPerKm = 8.4,
        earningsPerHour = 153.3,
        tripsPerHour = 1.7,
        acceptanceRate = 0.62,
    )

    private fun result(
        metric: String,
        displayPercentile: Int,
        percentile: Int = displayPercentile,
    ) = PercentileResult(
        metric = metric,
        value = 100.0,
        percentile = percentile,
        displayPercentile = displayPercentile,
        sampleSize = 200,
        isNationalFallback = false,
        isSynthetic = false,
    )

    @Test
    fun `amounts visible builds a net line`() {
        val card = ShareCardComposer.compose(
            stats = fullWeek,
            periodKind = SharePeriodKind.WEEK,
            periodLabel = "Semana del 1–7 jun",
            streakWeeks = 4,
            city = City.CDMX,
            percentiles = emptyList(),
            hideAmounts = false,
        )
        assertEquals("$3,450.00", card.netEarnings)
        assertFalse(card.hideAmounts)
        assertTrue(card.hasHighlight)
    }

    @Test
    fun `hide amounts fully redacts net earnings`() {
        val card = ShareCardComposer.compose(
            stats = fullWeek,
            periodKind = SharePeriodKind.WEEK,
            periodLabel = "Semana del 1–7 jun",
            streakWeeks = 4,
            city = City.CDMX,
            percentiles = listOf(result("earnings_per_hour", displayPercentile = 78)),
            hideAmounts = true,
        )
        assertNull(card.netEarnings)
        assertTrue(card.hideAmounts)
        // Trips, hours, percentile and streak still survive a redaction.
        assertEquals("38 viajes", card.trips)
        assertEquals("22.5 h", card.hours)
        assertEquals("Top 22% en CDMX 🚀", card.percentileFlex)
        assertEquals("🔥 4 semanas seguidas", card.streakLine)
    }

    @Test
    fun `picks the best favorable percentile as the flex`() {
        // 78th display ⇒ top 22; 92nd display ⇒ top 8 — the stronger one wins.
        val flex = ShareCardComposer.bestFlex(
            listOf(
                result("earnings_per_hour", displayPercentile = 78),
                result("earnings_per_km", displayPercentile = 92),
                result("trips_per_hour", displayPercentile = 60),
            ),
            City.CDMX,
        )
        assertEquals("Top 8% en CDMX 🚀", flex)
    }

    @Test
    fun `inverted metric uses display percentile not raw`() {
        // commission %: raw is low (good driver = low commission), but displayPercentile is already
        // inverted to be high/favorable. We must read displayPercentile, so this qualifies as a flex.
        val flex = ShareCardComposer.bestFlex(
            listOf(result("platform_commission_pct", percentile = 12, displayPercentile = 88)),
            City.MONTERREY,
        )
        assertEquals("Top 12% en Monterrey 🚀", flex)
    }

    @Test
    fun `unfavorable percentiles produce no flex`() {
        // Exactly 50 is not strictly above the median ⇒ not favorable; below 50 ⇒ not favorable.
        assertNull(ShareCardComposer.bestFlex(listOf(result("earnings_per_hour", 50)), City.CDMX))
        assertNull(ShareCardComposer.bestFlex(listOf(result("earnings_per_hour", 33)), City.CDMX))
        assertNull(ShareCardComposer.bestFlex(emptyList(), City.CDMX))
    }

    @Test
    fun `top percent never renders zero`() {
        // displayPercentile 99 ⇒ topPercent coerced to >= 1.
        val flex = ShareCardComposer.bestFlex(listOf(result("earnings_per_hour", 99)), City.CDMX)
        assertEquals("Top 1% en CDMX 🚀", flex)
    }

    @Test
    fun `streak line singular vs plural vs none`() {
        assertEquals("🔥 1 semana seguida", ShareCardComposer.streakLine(1))
        assertEquals("🔥 6 semanas seguidas", ShareCardComposer.streakLine(6))
        assertNull(ShareCardComposer.streakLine(0))
        assertNull(ShareCardComposer.streakLine(-2))
    }

    @Test
    fun `trips singular vs plural`() {
        assertEquals("1 viaje", ShareCardComposer.formatTrips(1))
        assertEquals("0 viajes", ShareCardComposer.formatTrips(0))
        assertEquals("38 viajes", ShareCardComposer.formatTrips(38))
    }

    @Test
    fun `month card carries the month period kind and label`() {
        val card = ShareCardComposer.compose(
            stats = fullWeek,
            periodKind = SharePeriodKind.MONTH,
            periodLabel = "Junio 2026",
            streakWeeks = 0,
            city = City.CDMX,
            percentiles = emptyList(),
            hideAmounts = false,
        )
        assertEquals(SharePeriodKind.MONTH, card.periodKind)
        assertEquals("Junio 2026", card.periodLabel)
        assertNull(card.streakLine)
    }

    @Test
    fun `empty period with no streak and no percentile has no highlight`() {
        val card = ShareCardComposer.compose(
            stats = PeriodStats.EMPTY,
            periodKind = SharePeriodKind.WEEK,
            periodLabel = "Semana del 1–7 jun",
            streakWeeks = 0,
            city = City.CDMX,
            percentiles = emptyList(),
            hideAmounts = true,
        )
        assertNull(card.netEarnings)
        assertNull(card.hours) // 0 hours ⇒ no line.
        assertEquals("0 viajes", card.trips)
        assertFalse(card.hasHighlight)
    }

    @Test
    fun `cdmx uses the short share label not the verbose Ajustes name`() {
        assertEquals("CDMX", ShareCardComposer.shareCityLabel(City.CDMX))
        assertEquals("Puebla", ShareCardComposer.shareCityLabel(City.PUEBLA))
        assertEquals("Monterrey", ShareCardComposer.shareCityLabel(City.MONTERREY))
    }
}
