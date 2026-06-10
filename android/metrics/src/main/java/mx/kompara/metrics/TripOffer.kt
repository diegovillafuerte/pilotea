package mx.kompara.metrics

/**
 * A single ride-hail offer as the engine sees it — the platform and the four economic legs the
 * driver cares about. This is the engine's *own* input type (no dependency on `:parsers`): later
 * tasks (B-031/B-039) map a parsed `OfferCard` onto this shape.
 *
 * Every numeric field is nullable because real captures are lossy: the surge banner may hide the
 * pickup ETA, a screen may scroll before the trip distance renders, etc. The engine degrades
 * gracefully on partial data rather than refusing to judge (see [NetProfitEngine]).
 *
 * Field names are load-bearing — keep them exactly as-is so the `OfferCard → TripOffer` mapping
 * stays mechanical.
 *
 * @property platform platform key, e.g. "uber" / "didi" (free-form string; mapping layer owns the
 *   canonical casing)
 * @property fareMxn gross fare the platform advertises, in MXN
 * @property pickupKm distance of the pickup (dead) leg, in km
 * @property pickupMin estimated time to reach the pickup, in minutes
 * @property tripKm distance of the paid trip leg, in km
 * @property tripMin estimated duration of the paid trip, in minutes
 */
data class TripOffer(
    val platform: String,
    val fareMxn: Double?,
    val pickupKm: Double?,
    val pickupMin: Double?,
    val tripKm: Double?,
    val tripMin: Double?,
)
