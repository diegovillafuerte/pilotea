package mx.kompara.overlay

import mx.kompara.data.settings.PlatformThreshold

/**
 * Pure logic backing the long-press "quick threshold" sheet: the $/km slider range and value
 * clamping for the *two* floors (green = "conviene desde", red = "no conviene debajo de"), plus how
 * an edited floor folds back into a [PlatformThreshold]. The per-hour floors are left untouched —
 * the inline sheet only exposes the most-used knobs, $/km.
 *
 * Invariant kept here: the red floor never exceeds the green floor. Moving green below red drags
 * red down with it; moving red above green caps it at green.
 *
 * Kept Android-free so the slider math and the persisted-value construction are unit-testable
 * without Compose or DataStore.
 */
object ThresholdSheet {

    /** Lowest selectable $/km floor (MXN). */
    const val MIN_PER_KM: Double = 3.0

    /** Highest selectable $/km floor (MXN). */
    const val MAX_PER_KM: Double = 20.0

    /** Slider step (MXN) — drivers tune in coarse, glanceable increments while driving. */
    const val STEP_PER_KM: Double = 0.5

    /** Number of discrete steps between [MIN_PER_KM] and [MAX_PER_KM] (for the Slider's `steps`). */
    val stepCount: Int = ((MAX_PER_KM - MIN_PER_KM) / STEP_PER_KM).toInt() - 1

    /** Clamp a raw slider value into range and snap it to the nearest [STEP_PER_KM]. */
    fun clampPerKm(value: Double): Double {
        val coerced = value.coerceIn(MIN_PER_KM, MAX_PER_KM)
        val steps = Math.round((coerced - MIN_PER_KM) / STEP_PER_KM)
        return MIN_PER_KM + steps * STEP_PER_KM
    }

    /**
     * Fold an edited green $/km floor into [current], preserving the per-hour floors. If the new
     * green floor falls below the red floor, the red floor follows it down (red ≤ green).
     */
    fun withGreenPerKm(current: PlatformThreshold, newPerKm: Double): PlatformThreshold {
        val green = clampPerKm(newPerKm)
        return current.copy(
            minPerKmMxn = green,
            redPerKmMxn = minOf(current.redPerKmMxn, green),
        )
    }

    /**
     * Fold an edited red $/km floor into [current], preserving the per-hour floors. The red floor
     * is capped at the green floor (red ≤ green).
     */
    fun withRedPerKm(current: PlatformThreshold, newPerKm: Double): PlatformThreshold =
        current.copy(redPerKmMxn = minOf(clampPerKm(newPerKm), current.minPerKmMxn))

    /**
     * Snap a persisted threshold onto the sheet's slider grid (both $/km floors clamped + stepped,
     * red ≤ green) so the sliders open exactly on their thumb positions.
     */
    fun snapped(threshold: PlatformThreshold): PlatformThreshold =
        withRedPerKm(withGreenPerKm(threshold, threshold.minPerKmMxn), threshold.redPerKmMxn)
}
