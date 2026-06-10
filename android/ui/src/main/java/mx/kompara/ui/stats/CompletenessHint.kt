package mx.kompara.ui.stats

/**
 * Why a period's numbers are provisional, so the dashboard can be honest about what it inferred.
 *
 * On the captured path the driver never types a number: km/time come from the offer card's
 * *estimate* ([mx.kompara.data.db.entity.TripEntity.estimated]) and online hours are reconstructed
 * from auto-inferred shift wall-clock — never read from the platform. The hint surfaces that so a
 * rate like "$/hora" isn't mistaken for a settled figure.
 */
enum class CompletenessHint {
    /** Nothing to flag — either no data, or (future) an imported/realized week. No hint shown. */
    NONE,

    /** Hours came from inferred shifts but the period has trips and earnings — "horas estimadas por turnos". */
    HOURS_INFERRED,

    /** Trips exist but no shift overlapped, so $/hora and viajes/hora can't be shown — hours missing. */
    HOURS_MISSING,
}

/**
 * Decide the completeness hint for a captured period.
 *
 * @param hasTrips whether the period has at least one completed trip.
 * @param hoursOnline online hours attributed to the period (from inferred shifts).
 * @param tripsEstimated whether the trips' km/time/earnings are offer estimates (true on the captured
 *   path). When false (a realized/imported period) we don't flag inference at all.
 */
object CompletenessHints {
    fun hintFor(hasTrips: Boolean, hoursOnline: Double, tripsEstimated: Boolean): CompletenessHint =
        when {
            !hasTrips || !tripsEstimated -> CompletenessHint.NONE
            hoursOnline <= 0.0 -> CompletenessHint.HOURS_MISSING
            else -> CompletenessHint.HOURS_INFERRED
        }
}
