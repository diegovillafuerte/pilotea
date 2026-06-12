package mx.kompara.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultThresholdsTest {

    @Test
    fun `cdmx matches the seed p50 base row`() {
        val t = DefaultThresholds.forCity("cdmx")
        assertEquals(8.05, t.minPerKmMxn, 1e-9)
        assertEquals(161.0, t.minPerHourMxn, 1e-9)
    }

    @Test
    fun `cancun has the highest km floor (tourism premium)`() {
        val cancun = DefaultThresholds.forCity("cancun")
        assertEquals(8.4, cancun.minPerKmMxn, 1e-9)
        assertEquals(168.0, cancun.minPerHourMxn, 1e-9)
    }

    @Test
    fun `lookup is case and whitespace insensitive`() {
        val a = DefaultThresholds.forCity("  CDMX  ")
        val b = DefaultThresholds.forCity("cdmx")
        assertEquals(b, a)
    }

    @Test
    fun `unknown city falls back to national`() {
        val national = DefaultThresholds.forCity("national")
        val unknown = DefaultThresholds.forCity("oaxaca")
        assertEquals(national, unknown)
        assertEquals(7.0, national.minPerKmMxn, 1e-9)
        assertEquals(140.0, national.minPerHourMxn, 1e-9)
    }

    @Test
    fun `every seeded city resolves`() {
        val cities = listOf(
            "cdmx", "monterrey", "guadalajara", "puebla", "toluca",
            "tijuana", "leon", "queretaro", "merida", "cancun", "national",
        )
        for (city in cities) {
            val t = DefaultThresholds.forCity(city)
            assertEquals(true, t.minPerKmMxn > 0.0)
            assertEquals(true, t.minPerHourMxn > 0.0)
        }
    }
}
