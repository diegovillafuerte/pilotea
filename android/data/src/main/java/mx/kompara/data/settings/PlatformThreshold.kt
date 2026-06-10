package mx.kompara.data.settings

/**
 * The minimum acceptable economics for a platform, used by the metrics engine to colour the
 * traffic-light verdict. Floors are expressed in MXN.
 */
data class PlatformThreshold(
    /** Minimum acceptable earnings per km (MXN/km). */
    val minPerKmMxn: Double,
    /** Minimum acceptable earnings per hour (MXN/hr). */
    val minPerHourMxn: Double,
) {
    companion object {
        /** Conservative starting floors until the driver tunes them. */
        val DEFAULT = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0)
    }
}
