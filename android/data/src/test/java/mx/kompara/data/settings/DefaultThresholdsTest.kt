package mx.kompara.data.settings

import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultThresholdsTest {

    @Test
    fun `cdmx uber matches the seed p50`() {
        val t = DefaultThresholds.forCity("cdmx", Platform.UBER)
        assertEquals(8.05, t.minPerKmMxn, 1e-9)
        assertEquals(161.0, t.minPerHourMxn, 1e-9)
    }

    @Test
    fun `cdmx didi is lower than uber`() {
        val didi = DefaultThresholds.forCity("cdmx", Platform.DIDI)
        val uber = DefaultThresholds.forCity("cdmx", Platform.UBER)
        assertEquals(7.41, didi.minPerKmMxn, 1e-9)
        assertEquals(148.12, didi.minPerHourMxn, 1e-9)
        assertEquals(true, didi.minPerKmMxn < uber.minPerKmMxn)
    }

    @Test
    fun `cancun has the highest km floor (tourism premium)`() {
        val cancun = DefaultThresholds.forCity("cancun", Platform.UBER)
        assertEquals(8.4, cancun.minPerKmMxn, 1e-9)
        assertEquals(168.0, cancun.minPerHourMxn, 1e-9)
    }

    @Test
    fun `lookup is case and whitespace insensitive`() {
        val a = DefaultThresholds.forCity("  CDMX  ", Platform.UBER)
        val b = DefaultThresholds.forCity("cdmx", Platform.UBER)
        assertEquals(b, a)
    }

    @Test
    fun `unknown city falls back to national`() {
        val national = DefaultThresholds.forCity("national", Platform.UBER)
        val unknown = DefaultThresholds.forCity("oaxaca", Platform.UBER)
        assertEquals(national, unknown)
        assertEquals(7.0, national.minPerKmMxn, 1e-9)
        assertEquals(140.0, national.minPerHourMxn, 1e-9)
    }

    @Test
    fun `indrive falls back to uber row for the same city`() {
        val indrive = DefaultThresholds.forCity("monterrey", Platform.INDRIVE)
        val uber = DefaultThresholds.forCity("monterrey", Platform.UBER)
        assertEquals(uber, indrive)
    }

    @Test
    fun `unknown platform in unknown city falls back to national uber`() {
        val t = DefaultThresholds.forCity("nowhere", Platform.UNKNOWN)
        val nationalUber = DefaultThresholds.forCity("national", Platform.UBER)
        assertEquals(nationalUber, t)
    }

    @Test
    fun `every seeded city resolves for uber and didi`() {
        val cities = listOf(
            "cdmx", "monterrey", "guadalajara", "puebla", "toluca",
            "tijuana", "leon", "queretaro", "merida", "cancun", "national",
        )
        for (city in cities) {
            for (platform in listOf(Platform.UBER, Platform.DIDI)) {
                val t = DefaultThresholds.forCity(city, platform)
                assertEquals(true, t.minPerKmMxn > 0.0)
                assertEquals(true, t.minPerHourMxn > 0.0)
            }
        }
    }
}
