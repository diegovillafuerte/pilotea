package mx.kompara.ui.components

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ReglaModelTest {
    private fun model(rate: String = "9.4916666666667", goal: String = "8", minimum: String = "6", unit: ReglaUnit = ReglaUnit.KM) =
        ReglaModel(ReglaBand.Rated(ReglaPosition.ABOVE, BigDecimal(rate)), BigDecimal(goal), BigDecimal(minimum), unit)

    @Test fun `limits share cents only when either limit needs them`() {
        assertEquals("tu meta $8", model().goalLabel)
        assertEquals("tu mínimo $6", model(goal = "8.00").minimumLabel)
        assertEquals("tu meta $8.00", model(minimum = "6.50").goalLabel)
        assertEquals("tu mínimo $6.00", model(goal = "9.50").minimumLabel)
        assertEquals("tu meta $1,200.00/h", model(goal = "1200", minimum = "150.25", unit = ReglaUnit.HOUR).goalLabel)
    }

    @Test fun `spoken phrase precedes the rate and retains negative sign and chosen unit`() {
        assertEquals("Arriba de tu meta: 9.49 pesos netos por km", model().spokenBand)
        val negative = model("-1.053048780487805").copy(band = ReglaBand.Rated(ReglaPosition.BELOW, BigDecimal("-1.053048780487805")))
        assertEquals("−$1.05", negative.rateText)
        assertEquals("Abajo de tu mínimo: menos 1.05 pesos netos por km", negative.spokenBand)
        assertEquals("Arriba de tu meta: 285 pesos netos por hora", model("284.75", unit = ReglaUnit.HOUR).spokenBand)
    }

    @Test fun `every unknown band has input and reason but no trip rate`() {
        ReglaUnavailable.entries.forEach { input ->
            val state = model().copy(band = ReglaBand.Unavailable(input))
            assertNull(state.rateText)
            assertFalse(state.spokenBand.contains('$'))
            assertFalse(state.spokenBand.contains("pesos"))
            assertEquals("${input.headline}: ${input.reason}", state.spokenBand)
            assertEquals("tu mínimo $6", state.minimumLabel)
        }
        assertEquals("Sin lectura: falta la tarifa", model().copy(band = ReglaBand.Unavailable(ReglaUnavailable.FARE)).spokenBand)
    }

    @Test fun `reduced motion only fades and all entry durations meet the target`() {
        assertEquals(120, ReglaMotion.enterMs(false)); assertEquals(16, ReglaMotion.riseDp(false))
        assertEquals(100, ReglaMotion.enterMs(true)); assertEquals(0, ReglaMotion.riseDp(true))
        assertTrue(ReglaMotion.enterMs(false) <= 150); assertEquals(160, ReglaMotion.EXIT_MS)
    }
}
