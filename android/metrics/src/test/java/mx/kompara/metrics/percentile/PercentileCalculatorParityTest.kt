package mx.kompara.metrics.percentile

import mx.kompara.data.model.PopulationStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parity suite for [PercentileCalculator] (B-046) — the acceptance criterion "same inputs → same
 * percentiles as web/backend implementation".
 *
 * These mirror the backend parity test (`backend/src/db/percentile.test.ts`) EXACTLY: the same
 * seeded breakpoints, the same 7 representative inputs spanning every interpolation branch plus the
 * national-fallback path, and the same expected integers (derived by hand in the backend test from
 * the §5.2 formula). Same numbers in, same percentiles out.
 *
 * Seeded breakpoints used (city/platform/metric -> p10,p25,p50,p75,p90):
 *   national/uber/earnings_per_trip       -> 27,    36,    45,   56.25, 67.5
 *   national/uber/earnings_per_hour       -> 70,   100.8, 140,  189,   238
 *   national/didi/earnings_per_km         -> 3.86,  5.15,  6.44,  8.05,  9.66
 *   national/indrive/platform_commission  -> 14,    17,    20,   23.6,  27
 *   cdmx/uber/trips_per_hour              -> 1.9,   2.31,  2.72,  3.21,  3.67
 */
class PercentileCalculatorParityTest {

    private fun stat(
        city: String,
        platform: String,
        metric: String,
        p10: Double,
        p25: Double,
        p50: Double,
        p75: Double,
        p90: Double,
        sampleSize: Int = 5000,
    ) = PopulationStat(
        city = city,
        platform = platform,
        metric = metric,
        period = "current",
        sampleSize = sampleSize,
        p10 = p10, p25 = p25, p50 = p50, p75 = p75, p90 = p90,
        mean = p50 * 1.05,
        isSynthetic = true,
    )

    // ── national rows used by the parity inputs (sample 5000, well above the 20 floor) ──
    private val natUberEarningsPerTrip =
        stat("national", "uber", "earnings_per_trip", 27.0, 36.0, 45.0, 56.25, 67.5)
    private val natUberEarningsPerHour =
        stat("national", "uber", "earnings_per_hour", 70.0, 100.8, 140.0, 189.0, 238.0)
    private val natDidiEarningsPerKm =
        stat("national", "didi", "earnings_per_km", 3.86, 5.15, 6.44, 8.05, 9.66)
    private val natIndriveCommission =
        stat("national", "indrive", "platform_commission_pct", 14.0, 17.0, 20.0, 23.6, 27.0)

    private fun raw(value: Double, stats: PopulationStat): Int? =
        PercentileCalculator.interpolate(value, stats)

    @Test
    fun `returns 50 at an exact p50 boundary (national_uber_earnings_per_trip v=45)`() {
        assertEquals(50, raw(45.0, natUberEarningsPerTrip))
    }

    @Test
    fun `interpolates within the p25-p50 branch (national_uber_earnings_per_trip v=40,8)`() {
        // 25 + ROUND(((40.8-36)/(45-36))*25) = 25 + ROUND(13.333) = 38
        assertEquals(38, raw(40.8, natUberEarningsPerTrip))
    }

    @Test
    fun `returns 75 at an exact p75 boundary (national_uber_earnings_per_hour v=189)`() {
        assertEquals(75, raw(189.0, natUberEarningsPerHour))
    }

    @Test
    fun `interpolates within the lowest branch (national_didi_earnings_per_km v=3,0)`() {
        // ROUND((3.0/3.86)*10) = ROUND(7.772) = 8
        assertEquals(8, raw(3.0, natDidiEarningsPerKm))
    }

    @Test
    fun `interpolates in the top tail above p90 (national_indrive_commission v=30)`() {
        // 90 + LEAST(9, ROUND(((30-27)/(27*0.5))*10)) = 90 + LEAST(9, ROUND(2.222)) = 92
        assertEquals(92, raw(30.0, natIndriveCommission))
    }

    @Test
    fun `interpolates within the p25-p50 branch for a city row (cdmx_uber_trips_per_hour v=2,5)`() {
        val cdmxTrips = stat("cdmx", "uber", "trips_per_hour", 1.9, 2.31, 2.72, 3.21, 3.67, sampleSize = 1500)
        // 25 + ROUND(((2.5-2.31)/(2.72-2.31))*25) = 25 + ROUND(11.585) = 37
        assertEquals(37, raw(2.5, cdmxTrips))
    }

    @Test
    fun `falls back to national when the city has no row (atlantis to national, v=45 yields 50)`() {
        // 'atlantis' is unseeded -> percentileFor uses the national cell -> v=45 == p50 -> 50.
        val result = PercentileCalculator.percentileFor(
            metric = "earnings_per_trip",
            value = 45.0,
            cityStats = emptyList(),
            nationalStats = listOf(natUberEarningsPerTrip),
        )
        assertEquals(50, result?.percentile)
        assertEquals(true, result?.isNationalFallback)
    }

    @Test
    fun `clamps below the floor to 1 and above the ceiling to 99`() {
        // value = 0 -> ROUND(0)=0 -> clamped to 1.
        assertEquals(1, raw(0.0, natUberEarningsPerTrip))
        // value far above p90 -> top-tail caps at 90+9 = 99.
        assertEquals(99, raw(10_000.0, natUberEarningsPerTrip))
    }

    @Test
    fun `returns null for a degenerate all-zero cell (SQL NULLIF yields NULL)`() {
        val zeroCell = stat("national", "uber", "earnings_per_trip", 0.0, 0.0, 0.0, 0.0, 0.0)
        assertNull(raw(5.0, zeroCell))
    }
}
