package mx.kompara.overlay

import mx.kompara.capture.KomparaAccessibilityService
import mx.kompara.data.model.Platform
import mx.kompara.metrics.TripOffer
import mx.kompara.parsers.model.OfferCard

/**
 * Pure mapping from the parser's [OfferCard] onto the engine's [TripOffer], and from a host
 * `platform` string onto the canonical [Platform] enum. Kept Android-free so it is fully
 * unit-testable on the JVM — the overlay's whole "card in, verdict out" flow hinges on this being
 * mechanical and null-safe.
 *
 * The two types overlap field-for-field except for naming: [OfferCard] uses `pickupDistanceKm` /
 * `pickupEtaMin` / `tripDistanceKm` / `tripDurationMin` / `fare`, while [TripOffer] uses
 * `pickupKm` / `pickupMin` / `tripKm` / `tripMin` / `fareMxn`. Every numeric field is nullable on
 * both sides (captures are lossy), so the mapping simply forwards the optionals — the engine
 * degrades gracefully on whatever is missing.
 */
object OfferMapping {

    /**
     * Map a parsed [card] to the engine's [TripOffer].
     *
     * The [TripOffer.platform] is the canonical lowercase platform key ("uber" / "didi" / …), not
     * the raw host package, so the engine and threshold lookup speak a stable vocabulary regardless
     * of how the card was captured.
     */
    fun toTripOffer(card: OfferCard): TripOffer = TripOffer(
        platform = platformOf(card.platform).name.lowercase(),
        fareMxn = card.fare,
        pickupKm = card.pickupDistanceKm,
        pickupMin = card.pickupEtaMin,
        tripKm = card.tripDistanceKm,
        tripMin = card.tripDurationMin,
    )

    /**
     * Resolve a card's `platform` field — which is the host package on the parsed-card path
     * (e.g. `com.ubercab.driver`) — to the canonical [Platform]. Falls back to a substring match so
     * a slightly different package id ("…uber…", "…didi…") still classifies, and to
     * [Platform.UNKNOWN] when nothing matches.
     */
    fun platformOf(rawPlatform: String): Platform {
        val key = rawPlatform.trim().lowercase()
        return when {
            key == KomparaAccessibilityService.UBER_DRIVER_PACKAGE || key.contains("uber") -> Platform.UBER
            key == KomparaAccessibilityService.DIDI_DRIVER_PACKAGE || key.contains("didi") -> Platform.DIDI
            key.contains("indrive") || key.contains("indriver") -> Platform.INDRIVE
            else -> Platform.UNKNOWN
        }
    }
}
