package mx.kompara.ui.components

import mx.kompara.data.settings.RateBasis
import mx.kompara.metrics.CounterOffers
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ReglaModelTest {
    private fun model(position: ReglaPosition = ReglaPosition.ABOVE, rate: String = "9.4916666666667",
        unit: ReglaUnit = ReglaUnit.KM, basis: RateBasis = RateBasis.NET, counters: CounterOffers? = null) =
        ReglaModel(ReglaBand.Rated(position, BigDecimal(rate)), unit, basis, counters)

    @Test fun `the factory is a rated pill without counter-offers`() {
        assertEquals(model(), ReglaModel.rated(ReglaPosition.ABOVE, BigDecimal("9.4916666666667"), ReglaUnit.KM, RateBasis.NET))
        assertEquals(model(basis = RateBasis.GROSS, unit = ReglaUnit.HOUR),
            ReglaModel.rated(ReglaPosition.ABOVE, BigDecimal("9.4916666666667"), ReglaUnit.HOUR, RateBasis.GROSS))
    }

    @Test fun `one accessible name says the position, the rate, the unit and the basis`() {
        assertEquals("Arriba de tu meta: 9.49 pesos por kilómetro, neto", model().spokenBand)
        assertEquals("Entre tu mínimo y tu meta: 7.16 pesos por kilómetro, neto", model(ReglaPosition.MEETS, "7.158333").spokenBand)
        assertEquals("Abajo de tu mínimo: 2.20 pesos por kilómetro, neto", model(ReglaPosition.BELOW, "2.199").spokenBand)
        assertEquals("Arriba de tu meta: 12.33 pesos por kilómetro, bruto", model(rate = "12.3333", basis = RateBasis.GROSS).spokenBand)
        assertEquals("Arriba de tu meta: 285 pesos por hora, neto", model(rate = "284.75", unit = ReglaUnit.HOUR).spokenBand)
    }

    @Test fun `the rate keeps its sign and the unit's precision`() {
        val negative = model(ReglaPosition.BELOW, "-1.053048780487805")
        assertEquals("−$1.05", negative.rateText)
        assertEquals("Abajo de tu mínimo: menos 1.05 pesos por kilómetro, neto", negative.spokenBand)
        assertEquals("$285", model(rate = "284.75", unit = ReglaUnit.HOUR).rateText)
        assertEquals("$12,346", model(rate = "12345.56", unit = ReglaUnit.HOUR).rateText)
        assertEquals("neto", model().basisWord)
        assertEquals("bruto", model(basis = RateBasis.GROSS).basisWord)
    }

    @Test fun `every unread pill says its words and carries no rate or counter-offer`() {
        ReglaUnavailable.entries.forEach { input ->
            val state = ReglaModel(ReglaBand.Unavailable(input), counterOffers = CounterOffers(203, 249))
            assertNull(state.rateText)
            assertFalse(state.hasTray)
            assertFalse(state.spokenBand.contains('$'))
            assertFalse(state.spokenBand.contains("pesos"))
        }
        for (input in listOf(ReglaUnavailable.FARE, ReglaUnavailable.TIME, ReglaUnavailable.DISTANCE)) {
            assertEquals("Sin lectura", input.headline)
            assertEquals("Sin lectura: no pudimos leer esta oferta", ReglaModel(ReglaBand.Unavailable(input)).spokenBand)
        }
        assertEquals("Sin confirmar", ReglaModel(ReglaBand.Unavailable(ReglaUnavailable.UNCONFIRMED)).spokenBand)
        assertEquals("Leyendo oferta", ReglaModel(ReglaBand.Unavailable(ReglaUnavailable.READING)).spokenBand)
    }

    @Test fun `the verdict decides which counter-offers are drawn`() {
        val both = CounterOffers(203, 249)
        model(ReglaPosition.BELOW, "1.09", counters = both).let {
            assertEquals(203, it.trayMinimum); assertEquals(249, it.trayGoal); assertTrue(it.hasTray)
        }
        // Between the limits the mínimo is met: a rounding edge that still returns it is not drawn.
        model(ReglaPosition.MEETS, "6.39", counters = both).let {
            assertNull(it.trayMinimum); assertEquals(249, it.trayGoal); assertTrue(it.hasTray)
        }
        // At or above the meta both are met: the plain pill.
        model(counters = both).let { assertNull(it.trayMinimum); assertNull(it.trayGoal); assertFalse(it.hasTray) }
        model(ReglaPosition.BELOW, "1.09", counters = CounterOffers(null, null)).let { assertFalse(it.hasTray) }
        assertFalse(model(ReglaPosition.BELOW, "1.09").hasTray)
    }

    @Test fun `the accessible name adds the counter-offers that are drawn`() {
        assertEquals("Abajo de tu mínimo: 1.09 pesos por kilómetro, neto. Desde 203 pesos alcanza tu mínimo; desde 249, tu meta.",
            model(ReglaPosition.BELOW, "1.09", counters = CounterOffers(203, 249)).spokenBand)
        assertEquals("Entre tu mínimo y tu meta: 6.39 pesos por kilómetro, neto. Desde 113 pesos alcanza tu meta.",
            model(ReglaPosition.MEETS, "6.389", counters = CounterOffers(92, 113)).spokenBand)
        assertEquals("Abajo de tu mínimo: 1.09 pesos por kilómetro, neto. Desde 1,203 pesos alcanza tu mínimo.",
            model(ReglaPosition.BELOW, "1.09", counters = CounterOffers(1203, null)).spokenBand)
        assertEquals("Arriba de tu meta: 9.49 pesos por kilómetro, neto", model(counters = CounterOffers(90, 100)).spokenBand)
    }

    @Test fun `reduced motion only fades and all entry durations meet the target`() {
        assertEquals(120, ReglaMotion.enterMs(false)); assertEquals(16, ReglaMotion.riseDp(false))
        assertEquals(100, ReglaMotion.enterMs(true)); assertEquals(0, ReglaMotion.riseDp(true))
        assertTrue(ReglaMotion.enterMs(false) <= 150); assertEquals(160, ReglaMotion.EXIT_MS)
    }
}
