package mx.kompara.metrics.percentile

import mx.kompara.data.model.PopulationStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fallback, inversion, sample-size and metadata logic for [PercentileCalculator.percentileFor]
 * (B-046) — the per-result wrapper around the raw [PercentileCalculator.interpolate].
 */
class PercentileCalculatorLogicTest {

    private fun stat(
        city: String,
        metric: String,
        sampleSize: Int,
        synthetic: Boolean = true,
        platform: String = "uber",
        p10: Double = 27.0,
        p25: Double = 36.0,
        p50: Double = 45.0,
        p75: Double = 56.25,
        p90: Double = 67.5,
    ) = PopulationStat(
        city = city,
        platform = platform,
        metric = metric,
        period = "current",
        sampleSize = sampleSize,
        p10 = p10, p25 = p25, p50 = p50, p75 = p75, p90 = p90,
        mean = p50 * 1.05,
        isSynthetic = synthetic,
    )

    @Test
    fun `uses the city cell when its sample size is at least 20`() {
        val city = listOf(stat("cdmx", "earnings_per_trip", sampleSize = 20, synthetic = false))
        val national = listOf(stat("national", "earnings_per_trip", sampleSize = 5000))

        val r = PercentileCalculator.percentileFor("earnings_per_trip", 45.0, city, national)!!

        assertEquals(50, r.percentile)
        assertFalse(r.isNationalFallback)
        assertEquals(20, r.sampleSize)
        assertFalse(r.isSynthetic) // the chosen (city) cell's flag flows through
    }

    @Test
    fun `falls back to national when the city sample size is below 20`() {
        val city = listOf(stat("cdmx", "earnings_per_trip", sampleSize = 19))
        val national = listOf(stat("national", "earnings_per_trip", sampleSize = 5000, synthetic = true))

        val r = PercentileCalculator.percentileFor("earnings_per_trip", 45.0, city, national)!!

        assertEquals(50, r.percentile)
        assertTrue(r.isNationalFallback)
        assertEquals(5000, r.sampleSize)
        assertTrue(r.isSynthetic)
    }

    @Test
    fun `falls back to national when the city has no cell for the metric`() {
        val national = listOf(stat("national", "earnings_per_trip", sampleSize = 5000))

        val r = PercentileCalculator.percentileFor("earnings_per_trip", 45.0, emptyList(), national)!!

        assertTrue(r.isNationalFallback)
    }

    @Test
    fun `returns null when neither city nor national has a cell`() {
        val r = PercentileCalculator.percentileFor("earnings_per_trip", 45.0, emptyList(), emptyList())
        assertNull(r)
    }

    @Test
    fun `inverts the display percentile for commission (lower is better)`() {
        // commission breakpoints; v at p50 -> raw 50 -> displayPercentile inverted to 50.
        val national = listOf(
            stat(
                "national", "platform_commission_pct", sampleSize = 5000, platform = "uber",
                p10 = 14.0, p25 = 17.0, p50 = 20.0, p75 = 23.6, p90 = 27.0,
            ),
        )
        val atP50 = PercentileCalculator.percentileFor("platform_commission_pct", 20.0, emptyList(), national)!!
        assertEquals(50, atP50.percentile)
        assertEquals(50, atP50.displayPercentile)

        // A LOW commission (good) -> low raw percentile -> HIGH display percentile.
        val lowCommission = PercentileCalculator.percentileFor("platform_commission_pct", 14.0, emptyList(), national)!!
        assertEquals(10, lowCommission.percentile) // v == p10 -> 10
        assertEquals(90, lowCommission.displayPercentile) // 100 - 10
        assertEquals(10, lowCommission.topPercent) // top 10% (lowest commission)
    }

    @Test
    fun `does not invert the display percentile for higher-is-better metrics`() {
        val national = listOf(stat("national", "earnings_per_hour", sampleSize = 5000, p10 = 70.0, p25 = 100.8, p50 = 140.0, p75 = 189.0, p90 = 238.0))
        val r = PercentileCalculator.percentileFor("earnings_per_hour", 189.0, emptyList(), national)!!
        assertEquals(75, r.percentile)
        assertEquals(75, r.displayPercentile)
        assertEquals(25, r.topPercent) // top 25%
    }

    @Test
    fun `topPercent never renders zero even at the very top`() {
        val national = listOf(stat("national", "earnings_per_trip", sampleSize = 5000))
        val r = PercentileCalculator.percentileFor("earnings_per_trip", 10_000.0, emptyList(), national)!!
        assertEquals(99, r.displayPercentile)
        assertEquals(1, r.topPercent) // 100 - 99 = 1, not 0
    }
}
