package mx.kompara.data.settings

/**
 * The acceptable economics for a platform, used by the metrics engine to colour the traffic-light
 * verdict. Two floors per metric, expressed in MXN:
 *
 *  - at or above the green floor ([minPerKmMxn]/[minPerHourMxn]) the metric reads GREEN;
 *  - below the red floor ([redPerKmMxn]/[redPerHourMxn]) it reads RED;
 *  - in between it reads YELLOW (the "depends on your shift" band).
 *
 * Red floors default to [DEFAULT_RED_FRACTION] of their green floor so existing persisted settings
 * and the city seed table get a sensible band without migration. Invariant: red ≤ green — consumers
 * clamp ([mx.kompara.metrics.NetProfitEngine] does) rather than trust every producer.
 */
data class PlatformThreshold(
    /** Green floor: earnings per km (MXN/km) at or above which the km metric is GREEN. */
    val minPerKmMxn: Double,
    /** Green floor: earnings per hour (MXN/hr) at or above which the hour metric is GREEN. */
    val minPerHourMxn: Double,
    /** Red floor: below this many MXN/km the km metric is RED. */
    val redPerKmMxn: Double = minPerKmMxn * DEFAULT_RED_FRACTION,
    /** Red floor: below this many MXN/hr the hour metric is RED. */
    val redPerHourMxn: Double = minPerHourMxn * DEFAULT_RED_FRACTION,
) {
    companion object {
        /** Red floor as a fraction of the green floor when the driver hasn't tuned it. */
        const val DEFAULT_RED_FRACTION: Double = 0.75

        /** Conservative starting floors until the driver tunes them. */
        val DEFAULT = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0)
    }
}
