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
        VerdictChipState.from(engine.evaluate(offer, cost, threshold, preferred))

    /** The v4 kit's cost (24.50 ÷ 12 + 0.80 per km) and its bruto limits $11 / $9. */
    private val kitCost = BigDecimal("24.50").divide(BigDecimal(12), mx.kompara.data.accounting.MONEY_CONTEXT).add(BigDecimal("0.80"))
    private val bruto = PlatformThreshold(11.0, 280.0, 9.0, 230.0)

    @Test fun `a GROSS judgement shows its gross rate and says bruto, never a net number in a gross colour`() {
        // S10: $148 for 12 km. Bruto 148 / 12 = 12.33 is green against $11; the net 9.49 is not what was judged.
        val m = engine.evaluate(TripOffer("uber", 148.0, 2.0, 6.0, 10.0, 18.0), kitCost, bruto, PreferredMetric.IPK,
            mx.kompara.data.settings.RateBasis.GROSS)
        val state = VerdictChipState.from(m)
        assertEquals(VerdictLevel.GREEN, state.level)
        assertEquals("$12.33/km", state.heroRate)
        assertEquals(mx.kompara.data.settings.RateBasis.GROSS, state.regla.basis)
        assertEquals("Arriba de tu meta: 12.33 pesos por kilómetro, bruto", state.regla.spokenBand)
        // Per hour in bruto: 148 × 60 / 24 = 370.
        val hourly = VerdictChipState.from(engine.evaluate(TripOffer("uber", 148.0, 2.0, 6.0, 10.0, 18.0), kitCost, bruto,
            PreferredMetric.IPH, mx.kompara.data.settings.RateBasis.GROSS))
        assertEquals("$370/h", hourly.heroRate)
        assertEquals("neto", state(cost = kitCost).regla.basisWord)
    }

    @Test fun `a Pon Tu Precio card's counter-offers ride along and the verdict decides which are drawn`() {
        // S5: $90.05 for 22.9 km is 1.09 neto per km, below the mínimo: from $203 and from $249.
        val s5 = engine.evaluate(TripOffer("didi", 90.05, 2.0, 8.0, 20.9, 36.0), kitCost, limits)
        val red = VerdictChipState.from(s5, CounterOffers.of(s5, BigDecimal(8), BigDecimal(6), PreferredMetric.IPK,
            mx.kompara.data.settings.RateBasis.NET))
        assertEquals(VerdictLevel.RED, red.level)
        assertEquals(203, red.regla.trayMinimum); assertEquals(249, red.regla.trayGoal)
        // S9: $96 for 10.4 km is 6.39, between the limits: only from $113.
        val s9 = engine.evaluate(TripOffer("didi", 96.0, 1.2, 4.0, 9.2, 19.0), kitCost, limits)
        val yellow = VerdictChipState.from(s9, CounterOffers.of(s9, BigDecimal(8), BigDecimal(6), PreferredMetric.IPK,
            mx.kompara.data.settings.RateBasis.NET))
        assertNull(yellow.regla.trayMinimum); assertEquals(113, yellow.regla.trayGoal)
        // Above the meta, an amount a rounding edge might return is never drawn; without counter-offers, no tray.
        val s1 = engine.evaluate(TripOffer("didi", 148.0, 2.0, 6.0, 10.0, 18.0), kitCost, limits)
        assertFalse(VerdictChipState.from(s1, CounterOffers(140, 150)).regla.hasTray)
        assertFalse(VerdictChipState.from(s5).regla.hasTray)
    }

    @Test fun `complete offer has one net rate and both evaluated limits`() {
        val state = state()
        assertEquals("$10.00/km", state.heroRate)
        assertEquals("$10.00/km", state.grossPerKm)
        assertEquals("Arriba de tu meta: 10.00 pesos por kilómetro, neto", state.regla.spokenBand)
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
        assertEquals("Sin lectura: no pudimos leer esta oferta", state.regla.spokenBand)
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
        assertNull(state.regla.rateText)
    }

    @Test fun `chosen hour rate is whole pesos and carries only hour limits`() {
        val state = state(preferred = PreferredMetric.IPH)
        assertEquals("$300/h", state.heroRate)
        assertEquals(ReglaUnit.HOUR, state.regla.unit)
        assertEquals("Arriba de tu meta: 300 pesos por hora, neto", state.regla.spokenBand)
    }

    @Test fun `same rounded rate can have different cue phrase and marker before rounding`() {
        val atGoal = state(full.copy(fareMxn = 80.0))
        val justBelow = state(full.copy(fareMxn = 80.0), cost = BigDecimal("0.00001"))
        assertEquals(atGoal.heroRate, justBelow.heroRate)
        assertEquals(VerdictLevel.GREEN, atGoal.level)
        assertEquals(VerdictLevel.YELLOW, justBelow.level)
        assertEquals("Entre tu mínimo y tu meta: 8.00 pesos por kilómetro, neto", justBelow.regla.spokenBand)
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

    @Test fun `reading state clears money and keeps the chosen unit`() {
        val reading = VerdictChipState.reading(PreferredMetric.IPH)
        assertEquals("Leyendo oferta", reading.spokenBand)
        assertNull(reading.rateText)
        assertEquals(ReglaUnit.HOUR, reading.unit)
    }

    @Test fun `evaluated pair is frozen in render state and not recoloured by new settings`() {
        val state = state(threshold = limits.copy(minPerKmMxn = 9.50, redPerKmMxn = 6.50))
        val newLimits = limits.copy(minPerKmMxn = 20.0)
        assertEquals(VerdictLevel.YELLOW, state(threshold = newLimits).level)
        assertEquals(VerdictLevel.GREEN, state.level)
    }
}
