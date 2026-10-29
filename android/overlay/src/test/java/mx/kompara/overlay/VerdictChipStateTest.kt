package mx.kompara.overlay

import mx.kompara.metrics.*
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.ui.components.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/** Tests the actual shared rendering projection, including the former missing-input regressions. */
class VerdictChipStateTest {
    private val engine = NetProfitEngine()
    private val limits = PlatformThreshold(8.0, 200.0, 6.0, 150.0)
    private val full = TripOffer("uber", 100.0, 2.0, 5.0, 8.0, 15.0)
    private fun state(offer: TripOffer = full, preferred: PreferredMetric = PreferredMetric.IPK,
        cost: BigDecimal? = BigDecimal.ZERO, threshold: PlatformThreshold = limits): VerdictChipState =
        VerdictChipState.from(engine.evaluate(offer, cost, threshold, preferred), threshold)

    @Test fun `complete offer has one net rate and both evaluated limits`() {
        val state = state()
        assertEquals("$10.00/km", state.heroRate)
        assertEquals("$10.00/km", state.grossPerKm)
        assertEquals("Arriba de tu meta: 10.00 pesos netos por km", state.regla.spokenBand)
        assertEquals("tu meta $8", state.regla.goalLabel)
        assertEquals("tu mínimo $6", state.regla.minimumLabel)
    }

    @Test fun `absent or partial distance withholds a verdict in either unit`() {
        for (preferred in PreferredMetric.entries) for (offer in listOf(full.copy(pickupKm = null), full.copy(tripKm = null))) {
            val state = state(offer, preferred)
            assertEquals(ReglaBand.Unavailable(ReglaUnavailable.DISTANCE), state.regla.band)
            assertNull(state.regla.rateText)
            assertEquals(VerdictLevel.UNKNOWN, state.level)
        }
    }

    @Test fun `missing fare is grey with no trip figure`() {
        val state = state(full.copy(fareMxn = null))
        assertEquals("Sin lectura", state.heroRate)
        assertEquals("Sin lectura: falta la tarifa", state.regla.spokenBand)
        assertNull(state.regla.rateText)
    }

    @Test fun `missing unchosen time keeps per km and does not add a warning to its band`() {
        val state = state(full.copy(pickupMin = null, tripMin = null))
        assertEquals("$10.00/km", state.heroRate)
        assertEquals(VerdictLevel.GREEN, state.level)
    }

    @Test fun `same economic disagreements still follow the selected metric`() {
        // Former expanded-explainer regressions: no second figure on the new plate.
        val kmWeak = full.copy(fareMxn = 210.0, pickupKm = 5.0, tripKm = 25.0, pickupMin = 10.0, tripMin = 50.0)
        assertEquals(VerdictLevel.YELLOW, state(kmWeak).level)
        assertEquals(VerdictLevel.GREEN, state(kmWeak, PreferredMetric.IPH).level)
        val hourWeak = full.copy(fareMxn = 80.0, pickupKm = 1.0, tripKm = 4.0, pickupMin = 10.0, tripMin = 50.0)
        assertEquals(VerdictLevel.GREEN, state(hourWeak).level)
        assertEquals(VerdictLevel.RED, state(hourWeak, PreferredMetric.IPH).level)
        assertEquals(VerdictLevel.RED, state(full.copy(fareMxn = 40.0)).level)
    }

    @Test fun `chosen hour never substitutes a computable km rate`() {
        val state = state(full.copy(pickupMin = null), PreferredMetric.IPH)
        assertEquals(ReglaBand.Unavailable(ReglaUnavailable.TIME), state.regla.band)
        assertEquals("tu meta $200/h", state.regla.goalLabel)
        assertEquals("tu mínimo $150/h", state.regla.minimumLabel)
        assertNull(state.regla.rateText)
    }

    @Test fun `chosen hour rate is whole pesos and carries only hour limits`() {
        val state = state(preferred = PreferredMetric.IPH)
        assertEquals("$300/h", state.heroRate)
        assertEquals(ReglaUnit.HOUR, state.regla.unit)
        assertEquals("Arriba de tu meta: 300 pesos netos por hora", state.regla.spokenBand)
    }

    @Test fun `same rounded rate can have different cue phrase and marker before rounding`() {
        val atGoal = state(full.copy(fareMxn = 80.0))
        val justBelow = state(full.copy(fareMxn = 80.0), cost = BigDecimal("0.00001"))
        assertEquals(atGoal.heroRate, justBelow.heroRate)
        assertEquals(VerdictLevel.GREEN, atGoal.level)
        assertEquals(VerdictLevel.YELLOW, justBelow.level)
        assertEquals("Cumple tu mínimo: 8.00 pesos netos por km", justBelow.regla.spokenBand)
        assertEquals(VerdictLevel.YELLOW, state(full.copy(fareMxn = 60.0)).level)
        assertEquals(VerdictLevel.RED, state(full.copy(fareMxn = 60.0), cost = BigDecimal("0.00001")).level)
    }

    @Test fun `fixture contribution rates retain cents and negative sign`() {
        val cost = BigDecimal("24.50").divide(BigDecimal(12), mx.kompara.data.accounting.MONEY_CONTEXT).add(BigDecimal("0.80"))
        val cases = listOf(Triple(148.0, 12.0, "$9.49/km"), Triple(120.0, 12.0, "$7.16/km"),
            Triple(62.0, 12.3, "$2.20/km"), Triple(22.0, 12.3, "−$1.05/km"))
        cases.forEach { (fare, km, expected) ->
            assertEquals(expected, state(full.copy(fareMxn = fare, pickupKm = 2.0, tripKm = km - 2.0), cost = cost).heroRate)
        }
    }

    @Test fun `unconfirmed payment absent costs and invalid limits never become zero or red`() {
        listOf(state(full.copy(paymentBasis = "UNCONFIRMED")), state(cost = null), state(threshold = limits.copy(redPerKmMxn = 8.0)))
            .forEach { state ->
                assertEquals(ReglaBand.Unavailable(ReglaUnavailable.UNCONFIRMED), state.regla.band)
                assertNull(state.regla.rateText)
            }
    }

    @Test fun `reading state clears money and keeps chosen limits with cents`() {
        val reading = VerdictChipState.reading(limits.copy(minPerHourMxn = 200.50), PreferredMetric.IPH)
        assertEquals("Leyendo oferta: sin cálculo", reading.spokenBand)
        assertNull(reading.rateText)
        assertEquals("tu meta $200.50/h", reading.goalLabel)
        assertEquals("tu mínimo $150.00/h", reading.minimumLabel)
    }

    @Test fun `evaluated pair is frozen in render state and not recoloured by new settings`() {
        val state = state(threshold = limits.copy(minPerKmMxn = 9.50, redPerKmMxn = 6.50))
        val newLimits = limits.copy(minPerKmMxn = 20.0)
        assertEquals(VerdictLevel.YELLOW, state(threshold = newLimits).level)
        assertEquals(VerdictLevel.GREEN, state.level)
        assertEquals("tu meta $9.50", state.regla.goalLabel)
        assertEquals("tu mínimo $6.50", state.regla.minimumLabel)
    }
}
