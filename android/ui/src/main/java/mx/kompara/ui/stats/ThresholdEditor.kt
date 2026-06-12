package mx.kompara.ui.stats

import mx.kompara.data.settings.PlatformThreshold

/**
 * Pure logic backing the Ajustes threshold editor: slider ranges/steps for both metrics and how an
 * edited floor folds back into a [PlatformThreshold] keeping red ≤ green. The $/km range mirrors
 * the overlay quick sheet (`mx.kompara.overlay.ThresholdSheet`) — keep them in sync so the two
 * surfaces never disagree about what's selectable.
 *
 * Kept Android-free so the math is unit-testable without Compose or DataStore.
 */
object ThresholdEditor {

    const val MIN_PER_KM: Double = 3.0
    const val MAX_PER_KM: Double = 20.0
    const val STEP_PER_KM: Double = 0.5

    const val MIN_PER_HOUR: Double = 40.0
    const val MAX_PER_HOUR: Double = 300.0
    const val STEP_PER_HOUR: Double = 5.0

    /** Interior step counts for the Compose Slider. */
    val kmStepCount: Int = ((MAX_PER_KM - MIN_PER_KM) / STEP_PER_KM).toInt() - 1
    val hourStepCount: Int = ((MAX_PER_HOUR - MIN_PER_HOUR) / STEP_PER_HOUR).toInt() - 1

    fun clampPerKm(value: Double): Double = clamp(value, MIN_PER_KM, MAX_PER_KM, STEP_PER_KM)

    fun clampPerHour(value: Double): Double = clamp(value, MIN_PER_HOUR, MAX_PER_HOUR, STEP_PER_HOUR)

    fun withGreenPerKm(current: PlatformThreshold, value: Double): PlatformThreshold {
        val green = clampPerKm(value)
        return current.copy(minPerKmMxn = green, redPerKmMxn = minOf(current.redPerKmMxn, green))
    }

    fun withRedPerKm(current: PlatformThreshold, value: Double): PlatformThreshold =
        current.copy(redPerKmMxn = minOf(clampPerKm(value), current.minPerKmMxn))

    fun withGreenPerHour(current: PlatformThreshold, value: Double): PlatformThreshold {
        val green = clampPerHour(value)
        return current.copy(minPerHourMxn = green, redPerHourMxn = minOf(current.redPerHourMxn, green))
    }

    fun withRedPerHour(current: PlatformThreshold, value: Double): PlatformThreshold =
        current.copy(redPerHourMxn = minOf(clampPerHour(value), current.minPerHourMxn))

    private fun clamp(value: Double, min: Double, max: Double, step: Double): Double {
        val coerced = value.coerceIn(min, max)
        val steps = Math.round((coerced - min) / step)
        return min + steps * step
    }
}
