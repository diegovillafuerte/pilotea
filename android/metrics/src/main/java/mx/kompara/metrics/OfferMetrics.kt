package mx.kompara.metrics

/**
 * The full per-offer economics the engine computes, gross and net side by side, plus the
 * [Verdict]. All money in MXN; rate fields are null when the underlying input was missing so the
 * UI can show "—" instead of a misleading 0.
 *
 * Distance is the **total** the driver actually drives: pickup leg + trip leg. Drivers (and Uber's
 * own badge) reason this way — the dead km to the rider are real fuel and real time, so folding
 * them into the denominators is what makes the net rate honest. Time is likewise pickup ETA + trip
 * duration.
 *
 * @property grossMxn the advertised fare (null if unknown)
 * @property netMxn fare minus marginal cost over total distance (null if fare unknown)
 * @property totalKm pickup km + trip km (null if neither leg known)
 * @property totalMin pickup min + trip min (null if neither leg known)
 * @property grossPerKm grossMxn / totalKm (null if either unknown)
 * @property grossPerMin grossMxn / totalMin (null if either unknown)
 * @property netPerKm netMxn / totalKm (null if either unknown)
 * @property netPerMin netMxn / totalMin (null if either unknown)
 * @property netPerHour netMxn per hour of total time (null if total time unknown)
 * @property verdict the traffic-light decision and the missing-input list
 */
data class OfferMetrics(
    val grossMxn: Double?,
    val netMxn: Double?,
    val totalKm: Double?,
    val totalMin: Double?,
    val grossPerKm: Double?,
    val grossPerMin: Double?,
    val netPerKm: Double?,
    val netPerMin: Double?,
    val netPerHour: Double?,
    val verdict: Verdict,
)
