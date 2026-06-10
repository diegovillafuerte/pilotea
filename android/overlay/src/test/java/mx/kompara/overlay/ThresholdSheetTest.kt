package mx.kompara.overlay

import mx.kompara.data.settings.PlatformThreshold
import org.junit.Assert.assertEquals
import org.junit.Test

/** The quick-threshold slider math: range clamping, step snapping, per-hour preserved. */
class ThresholdSheetTest {

    @Test
    fun `clamps below the minimum and above the maximum`() {
        assertEquals(ThresholdSheet.MIN_PER_KM, ThresholdSheet.clampPerKm(0.0), 1e-9)
        assertEquals(ThresholdSheet.MAX_PER_KM, ThresholdSheet.clampPerKm(999.0), 1e-9)
    }

    @Test
    fun `snaps to the nearest half-peso step`() {
        // 7.3 -> nearest 0.5 step from 3.0 base = 7.5
        assertEquals(7.5, ThresholdSheet.clampPerKm(7.3), 1e-9)
        // 7.1 -> 7.0
        assertEquals(7.0, ThresholdSheet.clampPerKm(7.1), 1e-9)
    }

    @Test
    fun `withPerKm replaces the km floor but keeps the per-hour floor`() {
        val current = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 161.0)
        val updated = ThresholdSheet.withPerKm(current, 10.2)
        assertEquals(10.0, updated.minPerKmMxn, 1e-9) // snapped
        assertEquals(161.0, updated.minPerHourMxn, 1e-9) // untouched
    }

    @Test
    fun `step count spans the range`() {
        // (20 - 3) / 0.5 = 34 intervals -> 33 interior steps for the Slider.
        assertEquals(33, ThresholdSheet.stepCount)
    }
}
