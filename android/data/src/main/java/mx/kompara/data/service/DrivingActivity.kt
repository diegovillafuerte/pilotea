package mx.kompara.data.service

/** Foreground identity only, never screen content. A static host remains a driving surface.
 * After process/capture loss, unknown foreground defers until the app or service observes it. */
object DrivingActivity {
    private enum class Foreground { UNKNOWN, RIDE_APP, OTHER }
    @Volatile private var foreground = Foreground.UNKNOWN
    val mayBeDriving: Boolean get() = foreground != Foreground.OTHER
    fun foreground(packageName: String?) {
        foreground = if (packageName in ReaderReadinessReducer.apps) Foreground.RIDE_APP else Foreground.OTHER
    }
    fun unknown() { foreground = Foreground.UNKNOWN }
}
