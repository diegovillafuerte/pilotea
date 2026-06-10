package mx.kompara.ui.stats

import mx.kompara.metrics.percentile.PercentileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [MetricPercentiles] glue (B-046): the metric→value map fed to the percentile engine, card→metric
 * index alignment, and the top-X% label math via [PercentileResult.topPercent].
 */
class MetricPercentilesTest {

    private fun stats(
        perTrip: Double? = 90.0,
        perKm: Double? = 8.4,
        perHour: Double? = 153.0,
        tripsPerHour: Double? = 1.7,
        acceptance: Double? = 0.62,
    ) = PeriodStats(
        netEarningsMxn = 3450.0,
        grossEarningsMxn = 4200.0,
        totalTrips = 38,
        totalKm = 410.0,
        hoursOnline = 22.5,
        earningsPerTrip = perTrip,
        earningsPerKm = perKm,
        earningsPerHour = perHour,
        tripsPerHour = tripsPerHour,
        acceptanceRate = acceptance,
    )

    private fun result(metric: String, displayPercentile: Int) = PercentileResult(
        metric = metric,
        value = 1.0,
        percentile = displayPercentile,
        displayPercentile = displayPercentile,
        sampleSize = 1500,
        isNationalFallback = false,
        isSynthetic = false,
    )

    @Test
    fun `metricValues carries the four benchmarked efficiency metrics and not acceptance`() {
        val values = MetricPercentiles.metricValues(stats())
        assertEquals(setOf("earnings_per_trip", "earnings_per_km", "earnings_per_hour", "trips_per_hour"), values.keys)
        assertEquals(90.0, values["earnings_per_trip"])
        assertEquals(1.7, values["trips_per_hour"])
    }

    @Test
    fun `metricValues carries nulls through so the repository skips them`() {
        val values = MetricPercentiles.metricValues(stats(perKm = null))
        assertNull(values["earnings_per_km"])
        assertEquals(90.0, values["earnings_per_trip"])
    }

    @Test
    fun `forCard maps card index to the right metric standing`() {
        val byMetric = MetricPercentiles.byMetric(
            listOf(
                result("earnings_per_trip", 78),
                result("earnings_per_hour", 60),
            ),
        )
        // Card order: per_trip(0), per_km(1), per_hour(2), trips_per_hour(3), acceptance(4)
        assertEquals("earnings_per_trip", MetricPercentiles.forCard(0, byMetric)?.metric)
        assertNull(MetricPercentiles.forCard(1, byMetric)) // per_km has no standing
        assertEquals("earnings_per_hour", MetricPercentiles.forCard(2, byMetric)?.metric)
        assertNull(MetricPercentiles.forCard(3, byMetric)) // trips_per_hour has no standing here
        assertNull(MetricPercentiles.forCard(4, byMetric)) // acceptance never benchmarks
    }

    @Test
    fun `forCard returns null for an out-of-range index`() {
        assertNull(MetricPercentiles.forCard(99, emptyMap()))
    }

    @Test
    fun `top X percent is 100 minus display percentile`() {
        assertEquals(22, result("earnings_per_trip", 78).topPercent)
        assertEquals(50, result("earnings_per_trip", 50).topPercent)
    }

    @Test
    fun `top X percent never renders zero at the very top`() {
        assertEquals(1, result("earnings_per_trip", 99).topPercent)
    }
}
