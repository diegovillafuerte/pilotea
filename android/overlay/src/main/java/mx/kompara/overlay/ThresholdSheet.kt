package mx.kompara.overlay

import mx.kompara.data.settings.PlatformThreshold

/**
 * Pure logic backing the long-press "quick threshold" sheet: the $/km floor slider's range and the
 * value clamping, plus how an edited floor folds back into a [PlatformThreshold] (the per-hour
 * floor is left untouched — the inline sheet only exposes the single most-used knob, $/km).
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
     * Fold an edited $/km floor into [current], preserving its per-hour floor. The value is clamped
     * and snapped first, so the caller can pass a raw slider position.
     */
    fun withPerKm(current: PlatformThreshold, newPerKm: Double): PlatformThreshold =
        current.copy(minPerKmMxn = clampPerKm(newPerKm))
}
