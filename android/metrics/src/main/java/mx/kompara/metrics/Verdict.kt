package mx.kompara.metrics

/**
 * The engine's full judgment on an offer: the traffic-light [level] plus the numbers behind it.
 *
 * Carrying both the net rates *and* the gross $/km is deliberate — the overlay's whole pitch is
 * "Uber shows you gross, we show you what's left", so the UI can render the two side by side.
 *
 * [missingInputs] names the fields the engine had to do without (e.g. "pickupMin", "tripKm"). When
 * it is non-empty the verdict was reached on partial data and should never be GREEN — the UI can
 * surface the gaps so the driver knows the read is provisional.
 *
 * @property level traffic-light decision
 * @property netPerKm net MXN per total km (pickup + trip), after marginal costs; null if total
 *   distance was unknown
 * @property netPerHourEquivalent net MXN per hour (pickup + trip time), after marginal costs; null
 *   if total time was unknown
 * @property netProfitMxn net MXN for the whole offer (fare − marginal cost over total distance);
 *   null if the fare was missing
 * @property grossPerKm gross MXN per total km, before costs; null if total distance was unknown
 * @property missingInputs names of the inputs that were absent from the [TripOffer]
 * @property netPerKmLevel how the km metric alone classified against its two floors; null when
 *   the rate was untestable. Lets the UI explain *why* the overall light landed where it did.
 * @property netPerHourLevel same for the hour metric
 */
data class Verdict(
    val level: VerdictLevel,
    val netPerKm: Double?,
    val netPerHourEquivalent: Double?,
    val netProfitMxn: Double?,
    val grossPerKm: Double?,
    val missingInputs: List<String>,
    val netPerKmLevel: VerdictLevel? = null,
    val netPerHourLevel: VerdictLevel? = null,
)
