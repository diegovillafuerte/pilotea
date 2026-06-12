package mx.kompara.ui.stats

import mx.kompara.data.settings.PlatformThreshold
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Ajustes editor's slider math: clamping, stepping, and the red ≤ green invariant. */
class ThresholdEditorTest {

    private val base = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0)

    @Test
    fun `per-hour values clamp into range and snap to five-peso steps`() {
        assertEquals(ThresholdEditor.MIN_PER_HOUR, ThresholdEditor.clampPerHour(0.0), 1e-9)
        assertEquals(ThresholdEditor.MAX_PER_HOUR, ThresholdEditor.clampPerHour(999.0), 1e-9)
        assertEquals(145.0, ThresholdEditor.clampPerHour(143.0), 1e-9)
        assertEquals(140.0, ThresholdEditor.clampPerHour(141.0), 1e-9)
    }

    @Test
    fun `withGreenPerHour drags the red floor down when they would cross`() {
        val tuned = base.copy(minPerHourMxn = 150.0, redPerHourMxn = 120.0)
        val updated = ThresholdEditor.withGreenPerHour(tuned, 100.0)
        assertEquals(100.0, updated.minPerHourMxn, 1e-9)
        assertEquals(100.0, updated.redPerHourMxn, 1e-9)
    }

    @Test
    fun `withRedPerHour caps at the green floor and keeps km floors`() {
        val updated = ThresholdEditor.withRedPerHour(base, 200.0)
        assertEquals(90.0, updated.redPerHourMxn, 1e-9) // capped at green
        assertEquals(8.0, updated.minPerKmMxn, 1e-9)    // untouched
    }

    @Test
    fun `km helpers mirror the overlay quick sheet semantics`() {
        val updated = ThresholdEditor.withGreenPerKm(base, 10.2)
        assertEquals(10.0, updated.minPerKmMxn, 1e-9)
        assertEquals(base.redPerKmMxn, updated.redPerKmMxn, 1e-9)

        val red = ThresholdEditor.withRedPerKm(base, 9.0)
        assertEquals(8.0, red.redPerKmMxn, 1e-9) // capped at green
    }

    @Test
    fun `step counts span both ranges`() {
        assertEquals(33, ThresholdEditor.kmStepCount)   // (20-3)/0.5 - 1
        assertEquals(51, ThresholdEditor.hourStepCount) // (300-40)/5 - 1
    }
}
