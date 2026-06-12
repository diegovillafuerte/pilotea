package mx.kompara.overlay

import mx.kompara.data.settings.PlatformThreshold
import org.junit.Assert.assertEquals
import org.junit.Test

/** The quick-threshold slider math: range clamping, step snapping, red ≤ green, per-hour preserved. */
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
    fun `withGreenPerKm replaces the green km floor but keeps the per-hour floors`() {
        val current = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 161.0)
        val updated = ThresholdSheet.withGreenPerKm(current, 10.2)
        assertEquals(10.0, updated.minPerKmMxn, 1e-9) // snapped
        assertEquals(161.0, updated.minPerHourMxn, 1e-9) // untouched
        assertEquals(current.redPerKmMxn, updated.redPerKmMxn, 1e-9) // untouched (below green)
    }

    @Test
    fun `lowering green below red drags red down with it`() {
        val current = PlatformThreshold(minPerKmMxn = 10.0, minPerHourMxn = 161.0, redPerKmMxn = 8.0)
        val updated = ThresholdSheet.withGreenPerKm(current, 6.0)
        assertEquals(6.0, updated.minPerKmMxn, 1e-9)
        assertEquals(6.0, updated.redPerKmMxn, 1e-9) // followed green down
    }

    @Test
    fun `withRedPerKm caps the red floor at the green floor`() {
        val current = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 161.0)
        val updated = ThresholdSheet.withRedPerKm(current, 12.0)
        assertEquals(8.0, updated.redPerKmMxn, 1e-9) // capped at green
        assertEquals(8.0, updated.minPerKmMxn, 1e-9) // green untouched
    }

    @Test
    fun `withRedPerKm snaps and keeps a valid band`() {
        val current = PlatformThreshold(minPerKmMxn = 10.0, minPerHourMxn = 161.0)
        val updated = ThresholdSheet.withRedPerKm(current, 6.3)
        assertEquals(6.5, updated.redPerKmMxn, 1e-9) // snapped to the half-peso grid
        assertEquals(161.0, updated.minPerHourMxn, 1e-9)
    }

    @Test
    fun `snapped puts both floors on the slider grid with red at most green`() {
        val raw = PlatformThreshold(
            minPerKmMxn = 8.05, // city-seeded, off the 0.5 grid
            minPerHourMxn = 161.0,
            redPerKmMxn = 9.2, // misconfigured above green
        )
        val snapped = ThresholdSheet.snapped(raw)
        assertEquals(8.0, snapped.minPerKmMxn, 1e-9)
        assertEquals(8.0, snapped.redPerKmMxn, 1e-9) // capped at green after snapping
    }

    @Test
    fun `step count spans the range`() {
        // (20 - 3) / 0.5 = 34 intervals -> 33 interior steps for the Slider.
        assertEquals(33, ThresholdSheet.stepCount)
    }
}
