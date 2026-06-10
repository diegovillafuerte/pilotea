package mx.kompara.parsers.model

import kotlinx.serialization.Serializable

/**
 * Typed result of evaluating a [mx.kompara.parsers.spec.ParserSpec] against a snapshot
 * (android-technical-design.md §1). All numeric fields are nullable: extraction is best-effort,
 * so a partially-recognized card yields the fields it could find and `null` for the rest. The
 * engine never throws on weird input — a non-matching snapshot returns `null` from
 * [mx.kompara.parsers.engine.SpecEngine.evaluate] instead.
 *
 * Units are normalized: money in MXN, distance in km, duration in minutes.
 *
 * This is the spec-engine's own structured type. The legacy [mx.kompara.parsers.ParsedOffer] used
 * by the current pipeline is a flatter, non-null shape; B-029 maps [OfferCard] into it (or
 * replaces it). Kept serializable so fixtures can pin expected output as JSON.
 */
@Serializable
data class OfferCard(
    /** Host package the card was read from, e.g. `com.ubercab.driver`. */
    val platform: String,
    /** Variant tag chosen by the detector, e.g. `surge`, `multi_stop`, `reservation`, or `null`. */
    val variant: String? = null,
    val fare: Double? = null,
    val pickupDistanceKm: Double? = null,
    val pickupEtaMin: Double? = null,
    val tripDistanceKm: Double? = null,
    val tripDurationMin: Double? = null,
    val surge: Boolean = false,
    val paymentType: String? = null,
    /**
     * Every field the extractors matched, keyed by extractor field name, with the raw (pre-
     * normalization) text. Useful for debugging spec regressions in the fixture harness.
     */
    val raw: Map<String, String> = emptyMap(),
)
