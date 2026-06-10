package mx.kompara.parsers.normalize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizersTest {

    // --- parseNumberEsMx ------------------------------------------------------------------------

    @Test
    fun `es-MX currency with comma grouping and dot decimal`() {
        assertEquals(1234.56, parseNumberEsMx("$1,234.56")!!, 1e-9)
        assertEquals(1250.75, parseNumberEsMx("Ganancia $ 1,250.75 MXN")!!, 1e-9)
    }

    @Test
    fun `european style with dot grouping and comma decimal`() {
        assertEquals(1234.56, parseNumberEsMx("1.234,56")!!, 1e-9)
        assertEquals(1234.5, parseNumberEsMx("$ 1.234,50")!!, 1e-9)
    }

    @Test
    fun `lone dot followed by three digits is grouping not decimal`() {
        assertEquals(1234.0, parseNumberEsMx("$1.234")!!, 1e-9)
    }

    @Test
    fun `lone comma followed by three digits is grouping`() {
        assertEquals(1234.0, parseNumberEsMx("1,234")!!, 1e-9)
    }

    @Test
    fun `lone separator with two trailing digits is a decimal`() {
        assertEquals(12.5, parseNumberEsMx("12.5")!!, 1e-9)
        assertEquals(2.5, parseNumberEsMx("2,5 km")!!, 1e-9)
    }

    @Test
    fun `multiple grouping separators collapse`() {
        assertEquals(1234567.0, parseNumberEsMx("1,234,567")!!, 1e-9)
        assertEquals(1234567.89, parseNumberEsMx("1,234,567.89")!!, 1e-9)
    }

    @Test
    fun `plain integers and decimals`() {
        assertEquals(85.0, parseNumberEsMx("85")!!, 1e-9)
        assertEquals(0.5, parseNumberEsMx("0.5")!!, 1e-9)
        assertEquals(210.0, parseNumberEsMx("$ 210.00")!!, 1e-9)
    }

    @Test
    fun `no number present returns null`() {
        assertNull(parseNumberEsMx("Efectivo"))
        assertNull(parseNumberEsMx(""))
        assertNull(parseNumberEsMx("$ -"))
    }

    @Test
    fun `negative numbers parse with sign`() {
        assertEquals(-15.0, parseNumberEsMx("-15")!!, 1e-9)
    }

    // --- parseDistanceKm ------------------------------------------------------------------------

    @Test
    fun `km stays km`() {
        assertEquals(3.2, parseDistanceKm("3.2 km")!!, 1e-9)
        assertEquals(12.0, parseDistanceKm("12 km")!!, 1e-9)
    }

    @Test
    fun `meters convert to km`() {
        assertEquals(0.85, parseDistanceKm("850 m")!!, 1e-9)
        assertEquals(0.5, parseDistanceKm("500 mts")!!, 1e-9)
    }

    @Test
    fun `unitless distance assumed km`() {
        assertEquals(4.0, parseDistanceKm("4")!!, 1e-9)
    }

    @Test
    fun `distance with no number is null`() {
        assertNull(parseDistanceKm("cerca"))
    }

    // --- parseDurationMin -----------------------------------------------------------------------

    @Test
    fun `minutes only`() {
        assertEquals(12.0, parseDurationMin("12 min")!!, 1e-9)
        assertEquals(1.0, parseDurationMin("1 minuto")!!, 1e-9)
    }

    @Test
    fun `hours plus minutes sum`() {
        assertEquals(65.0, parseDurationMin("1 h 5 min")!!, 1e-9)
        assertEquals(130.0, parseDurationMin("2 h 10 min")!!, 1e-9)
        assertEquals(120.0, parseDurationMin("2 h")!!, 1e-9)
    }

    @Test
    fun `seconds convert to fractional minutes`() {
        assertEquals(1.5, parseDurationMin("90 s")!!, 1e-9)
    }

    @Test
    fun `bare number treated as minutes`() {
        assertEquals(22.0, parseDurationMin("22")!!, 1e-9)
    }

    @Test
    fun `duration with no number is null`() {
        assertNull(parseDurationMin("pronto"))
    }

    // --- Normalizer.applyNumeric dispatch -------------------------------------------------------

    @Test
    fun `applyNumeric dispatches by kind`() {
        assertEquals(85.5, Normalizer.applyNumeric(Normalizer.CURRENCY, "$85.50")!!, 1e-9)
        assertEquals(0.85, Normalizer.applyNumeric(Normalizer.DISTANCE_KM, "850 m")!!, 1e-9)
        assertEquals(65.0, Normalizer.applyNumeric(Normalizer.DURATION_MIN, "1 h 5 min")!!, 1e-9)
        assertNull(Normalizer.applyNumeric(Normalizer.NONE, "Efectivo"))
    }
}
