package mx.kompara.capture.lifecycle

/**
 * The tunable knobs of the trip-lifecycle inference (B-039), gathered in one place so they read as a
 * spec and can be swapped wholesale once real device data calibrates them.
 *
 * **ALL VALUES HERE NEED ON-DEVICE CALIBRATION.** They are deliberate, documented guesses built and
 * tested only against synthetic event sequences (no device in the build env) — see techdebt.md. The
 * acceptance/decline split in particular is a timing heuristic standing in for a tap we cannot read.
 *
 * @property acceptWindowMs after an offer card disappears, a transition to a trip-like state within
 *   this window counts as an **acceptance**. Longer than this and we no longer attribute the trip to
 *   that offer (the driver likely declined and got a fresh request later).
 * @property declineMaxMs if the card disappears and we see an idle/offer-capable state within this
 *   window (i.e. no trip), it's classed **DECLINED** (looks like a fast tap / quick auto-timeout).
 *   Beyond it, with still no trip, the offer is **EXPIRED** (we couldn't attribute a decision).
 * @property minTripDurationMs a "trip" shorter than this between accept and end is treated as noise
 *   (a misfire / immediate cancel), not a real completed trip. Guards against UI flicker spawning a
 *   zero-length trip.
 */
data class TripStateHeuristics(
    val acceptWindowMs: Long = DEFAULT_ACCEPT_WINDOW_MS,
    val declineMaxMs: Long = DEFAULT_DECLINE_MAX_MS,
    val minTripDurationMs: Long = DEFAULT_MIN_TRIP_DURATION_MS,
) {
    companion object {
        /** Default accept window: 12s from card-gone to trip-state. NEEDS CALIBRATION. */
        const val DEFAULT_ACCEPT_WINDOW_MS: Long = 12_000L

        /** Default decline window: idle within 6s of card-gone ⇒ declined. NEEDS CALIBRATION. */
        const val DEFAULT_DECLINE_MAX_MS: Long = 6_000L

        /** Default minimum real-trip duration: 30s. NEEDS CALIBRATION. */
        const val DEFAULT_MIN_TRIP_DURATION_MS: Long = 30_000L

        val DEFAULT = TripStateHeuristics()
    }
}

/**
 * Data-driven markers used by [OfferEventLifecycleMapper] to classify a *non-offer* host screen as
 * trip-like vs idle. On device these would be matched against view-ids / text in the live window;
 * here they're a per-package list of substrings, kept as configurable constants so the on-device
 * calibration is a data edit, not a code change.
 *
 * **NEEDS ON-DEVICE CALIBRATION** — the marker strings are best-guess Uber/DiDi resource-id fragments
 * (e.g. a navigation/active-trip container). See techdebt.md.
 */
data class TripStateMarkers(
    /** Per-package substrings that, when present on a non-offer screen, mean "trip in progress". */
    val tripMarkersByPackage: Map<String, List<String>> = DEFAULT_TRIP_MARKERS,
) {
    /** Whether [hints] (view-ids/text from the current window) look trip-like for [packageName]. */
    fun isTripLike(packageName: String, hints: Collection<String>): Boolean {
        val markers = tripMarkersByPackage[packageName] ?: return false
        return hints.any { hint -> markers.any { hint.contains(it, ignoreCase = true) } }
    }

    companion object {
        const val UBER_PACKAGE = "com.ubercab.driver"
        const val DIDI_PACKAGE = "com.didiglobal.driver"

        /** Best-guess trip-screen markers per platform. NEEDS CALIBRATION against real captures. */
        val DEFAULT_TRIP_MARKERS: Map<String, List<String>> = mapOf(
            UBER_PACKAGE to listOf("navigation", "active_trip", "trip_in_progress", "en_route", "dropoff"),
            DIDI_PACKAGE to listOf("navi", "ongoing", "in_trip", "serving", "trip_panel"),
        )

        val DEFAULT = TripStateMarkers()
    }
}
