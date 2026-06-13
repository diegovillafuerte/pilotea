package mx.kompara.overlay

import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric

/**
 * Pure logic backing the long-press "quick threshold" sheet: slider ranges and value clamping for
 * the *two* floors (green = "conviene desde", red = "no conviene debajo de") of whichever metric
 * decides the driver's semáforo (B-079), plus how an edited floor folds back into a
 * [PlatformThreshold]. The sheet edits ONLY the preferred metric's floors — the other metric's
 * floors pass through every helper untouched. Ranges mirror the Ajustes editor
 * (`mx.kompara.ui.stats.ThresholdEditor`) — keep them in sync so the two surfaces never disagree
 * about what's selectable.
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

    /** Lowest selectable $/hr floor (MXN). */
    const val MIN_PER_HOUR: Double = 40.0

    /** Highest selectable $/hr floor (MXN). */
    const val MAX_PER_HOUR: Double = 300.0

    /** $/hr slider step (MXN) — coarser than km, matching the metric's scale. */
    const val STEP_PER_HOUR: Double = 5.0

    /** Number of discrete steps between [MIN_PER_KM] and [MAX_PER_KM] (for the Slider's `steps`). */
    val stepCount: Int = ((MAX_PER_KM - MIN_PER_KM) / STEP_PER_KM).toInt() - 1

    /** Number of discrete steps between [MIN_PER_HOUR] and [MAX_PER_HOUR]. */
    val hourStepCount: Int = ((MAX_PER_HOUR - MIN_PER_HOUR) / STEP_PER_HOUR).toInt() - 1

    /** Clamp a raw slider value into range and snap it to the nearest [STEP_PER_KM]. */
    fun clampPerKm(value: Double): Double = clamp(value, MIN_PER_KM, MAX_PER_KM, STEP_PER_KM)

    /** Clamp a raw slider value into range and snap it to the nearest [STEP_PER_HOUR]. */
    fun clampPerHour(value: Double): Double = clamp(value, MIN_PER_HOUR, MAX_PER_HOUR, STEP_PER_HOUR)

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
     * Fold an edited green $/hr floor into [current], preserving the per-km floors. If the new
     * green floor falls below the red floor, the red floor follows it down (red ≤ green).
     */
    fun withGreenPerHour(current: PlatformThreshold, newPerHour: Double): PlatformThreshold {
        val green = clampPerHour(newPerHour)
        return current.copy(
            minPerHourMxn = green,
            redPerHourMxn = minOf(current.redPerHourMxn, green),
        )
    }

    /**
     * Fold an edited red $/hr floor into [current], preserving the per-km floors. The red floor
     * is capped at the green floor (red ≤ green).
     */
    fun withRedPerHour(current: PlatformThreshold, newPerHour: Double): PlatformThreshold =
        current.copy(redPerHourMxn = minOf(clampPerHour(newPerHour), current.minPerHourMxn))

    /**
     * Snap a persisted threshold onto the slider grid of the [metric] the sheet is editing (both
     * of that metric's floors clamped + stepped, red ≤ green) so the sliders open exactly on
     * their thumb positions. The other metric's floors are deliberately left untouched — the
     * sheet must never silently rewrite floors it doesn't show.
     */
    fun snapped(threshold: PlatformThreshold, metric: PreferredMetric): PlatformThreshold =
        when (metric) {
            PreferredMetric.IPK ->
                withRedPerKm(withGreenPerKm(threshold, threshold.minPerKmMxn), threshold.redPerKmMxn)
            PreferredMetric.IPH ->
                withRedPerHour(withGreenPerHour(threshold, threshold.minPerHourMxn), threshold.redPerHourMxn)
        }

    private fun clamp(value: Double, min: Double, max: Double, step: Double): Double {
        val coerced = value.coerceIn(min, max)
        val steps = Math.round((coerced - min) / step)
        return min + steps * step
    }
}
