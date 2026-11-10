package mx.kompara.ui.stats

import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric.*
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.components.*
import org.junit.Assert.*
import org.junit.Test

/** Replaces retired slider/quick-sheet assertions with the same boundary scenarios under v3. */
class ThresholdEditorTest {
    private val base = LimitDraft.from(PlatformThreshold(8.0, 200.0, 6.0, 150.0), IPK)
    @Test fun `decimal entry accepts local paste without grid rounding`() {
        listOf("6.5", "6,50", "$6.50", "  $6,50 ").forEach { assertEquals(0, ThresholdEditor.parse(it)!!.compareTo("6.50".toBigDecimal())) }
        listOf("", ".", "-2", "NaN", "1.234", "1,2.3", "1e3", "6/2").forEach { assertNull(it, ThresholdEditor.parse(it)) }
    }
    @Test fun `off grid steps follow direction in both units and clamp only to actual bounds`() {
        assertEquals("6.50", ThresholdEditor.step("6.30", IPK, true))
        assertEquals("6.00", ThresholdEditor.step("6.30", IPK, false))
        assertEquals("7.00", ThresholdEditor.step("6.50", IPK, true))
        assertEquals("6.00", ThresholdEditor.step("6.50", IPK, false))
        assertEquals("99.99", ThresholdEditor.step("99.90", IPK, true))
        assertEquals("99.50", ThresholdEditor.step("99.99", IPK, false))
        assertEquals("0.00", ThresholdEditor.step("0", IPK, false))
        assertEquals("160.00", ThresholdEditor.step("153.25", IPH, true))
        assertEquals("150.00", ThresholdEditor.step("153.25", IPH, false))
        assertEquals("1010.00", ThresholdEditor.step("1000", IPH, true))
    }
    @Test fun `equal crossing and out of range fail without moving other limit`() {
        val equal = base.edit(LimitField.MINIMUM, "8.00")
        assertEquals("8.00", equal.pair.goal)
        assertEquals("Tu mínimo debe ser menor que tu meta", ThresholdEditor.error(equal, LimitField.MINIMUM))
        assertEquals("Tu meta debe ser mayor que tu mínimo", ThresholdEditor.error(base.edit(LimitField.GOAL, "5.99"), LimitField.GOAL))
        assertFalse(ThresholdEditor.valid(LimitPair("100", "6"), IPK))
        assertTrue(ThresholdEditor.valid(LimitPair("9999", "99.99"), IPH))
        assertTrue(ThresholdEditor.valid(LimitPair("99.99", "0"), IPK))
    }
    @Test fun `draft unit switching preserves both pairs and formatting-only entry is clean`() {
        assertFalse(ThresholdEditor.dirty(base.edit(LimitField.MINIMUM, "$6,00"), base))
        val draft = base.edit(LimitField.MINIMUM, "6.30").copy(metric = IPH).edit(LimitField.GOAL, "315.25")
        assertEquals("6.30", draft.km.minimum); assertEquals("150.00", draft.hour.minimum)
        assertEquals(draft, LimitDraft.decode(draft.encode()))
        assertEquals("$315.25/h", ThresholdEditor.limit(draft.hour.goal, ReglaUnit.HOUR))
    }
    @Test fun `example uses real decimal money and all three verdict phrases`() {
        fun preview(draft: LimitDraft) = ThresholdEditor.preview(draft, RunningCostInputs.TYPICAL.costPerKm)
        assertEquals("$9.49", preview(base).rateText)
        assertEquals(ReglaPosition.ABOVE, (preview(base).band as ReglaBand.Rated).position)
        assertEquals(ReglaPosition.MEETS, (preview(base.edit(LimitField.GOAL, "9.50")).band as ReglaBand.Rated).position)
        val red = base.copy(km = LimitPair("11", "10"))
        assertEquals(ReglaPosition.BELOW, (preview(red).band as ReglaBand.Rated).position)
        assertEquals("$285", preview(base.copy(metric = IPH)).rateText)
        assertTrue(preview(base.copy(metric = IPH)).spokenBand.contains("por hora"))
    }
    @Test fun `untouched precision survives unit and other-limit edits`() {
        val original = PlatformThreshold(8.05, 400.1234, 6.0375, 150.5678)
        val saved = LimitDraft.from(original, IPK)
        val result = saved.edit(LimitField.GOAL, "9.50").copy(metric = IPH).thresholdPreserving(saved, original)
        assertEquals(original.copy(minPerKmMxn = 9.50), result)
    }
    @Test fun `save names changed limit reset or both and back closes IME first`() {
        assertEquals("Guardado: tu mínimo ahora es $6.50/km", ThresholdEditor.savedMessage(base.edit(LimitField.MINIMUM, "6.50"), base, false))
        assertEquals("Guardado: tu meta ahora es $9.50/km", ThresholdEditor.savedMessage(base.edit(LimitField.GOAL, "9.50"), base, false))
        assertEquals("Guardado: tu meta $8.50/km · tu mínimo $6.50/km", ThresholdEditor.savedMessage(base.copy(km = LimitPair("8.50", "6.50")), base, false))
        assertEquals("Guardado: volviste a $8.00/km y $6.00/km", ThresholdEditor.savedMessage(base, base.copy(km = LimitPair("9", "7")), true))
        assertEquals(LimitsBack.HIDE_KEYBOARD, limitsBack(true, true))
        assertEquals(LimitsBack.CONFIRM, limitsBack(false, true))
        assertEquals(LimitsBack.CLOSE, limitsBack(false, false))
    }
    @Test fun `conversion goes both ways in both units and rounds to each editor's step`() {
        val cost = RunningCostInputs.TYPICAL.costPerKm!!
        val net = LimitDraft.from(PlatformThreshold(8.0, 200.0, 6.0, 150.0), IPK)
        val gross = ThresholdEditor.convert(net, RateBasis.GROSS, cost)
        assertEquals(LimitPair("11.00", "9.00"), gross.km); assertEquals(LimitPair("280.00", "230.00"), gross.hour)
        assertEquals(RateBasis.GROSS, gross.basis); assertEquals(IPK, gross.metric)
        val back = ThresholdEditor.convert(gross, RateBasis.NET, cost)
        assertEquals(net.km, back.km); assertEquals(net.hour, back.hour); assertEquals(RateBasis.NET, back.basis)
        assertEquals(gross, LimitDraft.decode(gross.encode()))
        assertEquals(RateBasis.NET, LimitDraft.decode(net.encode().take(5)).basis) // A 0.2.x saved draft has no basis.
    }
    @Test fun `bruto judges and shows the fare rate and every line says its basis`() {
        val cost = RunningCostInputs.TYPICAL.costPerKm!!
        val gross = ThresholdEditor.convert(base, RateBasis.GROSS, cost)
        assertEquals("$12.33", ThresholdEditor.preview(gross, cost).rateText)
        assertEquals(ReglaPosition.ABOVE, (ThresholdEditor.preview(gross, cost).band as ReglaBand.Rated).position)
        assertEquals("$12.33", ThresholdEditor.preview(gross, null).rateText) // Bruto needs no cost.
        assertEquals("$370", ThresholdEditor.preview(gross.copy(metric = IPH), cost).rateText)
        assertEquals("Neto: ya restamos tu costo de $2.84 por km.", ThresholdEditor.basisHelper(RateBasis.NET, IPK, cost))
        assertEquals("Bruto: la tarifa entre los km, sin restar tu costo.", ThresholdEditor.basisHelper(RateBasis.GROSS, IPK, cost))
        assertEquals("Bruto: la tarifa por hora, sin restar tu costo.", ThresholdEditor.basisHelper(RateBasis.GROSS, IPH, cost))
        assertEquals("Son tus $8.00 y $6.00 neto más tu costo de $2.84, redondeados.", ThresholdEditor.explanation(base, cost))
        assertEquals("Son tus $11.00 y $9.00 bruto menos tu costo de $2.84, redondeados.", ThresholdEditor.explanation(gross, cost))
        assertEquals("Son tus $200 y $150 neto, convertidos con tu costo de $2.84 por km y redondeados.", ThresholdEditor.explanation(base.copy(metric = IPH), cost))
        assertTrue(ThresholdEditor.dirty(gross, base))
        assertEquals("Guardado: tu meta $11.00/km · tu mínimo $9.00/km", ThresholdEditor.savedMessage(gross, base, false))
        assertTrue(ThresholdEditor.changes(gross, base).endsWith("Muestra: Bruto"))
    }
}
