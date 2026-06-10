package mx.kompara.ui.stats

import mx.kompara.ui.format.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

/** The "null rate ⇒ dash" mapping for [MetricCardValues] (B-040 req 1). */
class MetricCardValuesTest {

    @Test
    fun `present rates format, absent rates show the dash`() {
        val stats = PeriodStats(
            netEarningsMxn = 1000.0,
            grossEarningsMxn = 1200.0,
            totalTrips = 10,
            totalKm = 100.0,
            hoursOnline = 0.0, // no hours → per-hour rates null
            earningsPerTrip = 100.0,
            earningsPerKm = 10.0,
            earningsPerHour = null,
            tripsPerHour = null,
            acceptanceRate = 0.5,
        )
        val cards = MetricCardValues.of(stats)
        assertEquals(5, cards.size)
        assertEquals(Formatters.formatMxn(100.0), cards[0].value) // $/viaje
        assertEquals(Formatters.formatPerKm(10.0), cards[1].value) // $/km
        assertEquals(Formatters.DASH, cards[2].value) // $/hora (null)
        assertEquals(Formatters.DASH, cards[3].value) // viajes/hora (null)
        assertEquals("50 %", cards[4].value) // acceptance
    }

    @Test
    fun `fully empty period is all dashes except where defined`() {
        val cards = MetricCardValues.of(PeriodStats.EMPTY)
        assertEquals(Formatters.DASH, cards[0].value)
        assertEquals(Formatters.DASH, cards[4].value)
    }
}
