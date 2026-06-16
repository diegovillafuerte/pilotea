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
    /**
     * Screen-pixel union (top-left origin) of the offer's fare + leg blocks, when the card was read
     * via OCR — otherwise null (node-path / simulator cards, or a frame whose blocks had no bounds).
     * The overlay uses it to keep the chip from covering the fare: a chip parked over the fare
     * occludes it from the MediaProjection screen-capture, breaking the read and making the chip
     * blink. Same full-display pixel space as the OCR capture, so it maps 1:1 onto the chip's window.
     */
    val contentBounds: OfferContentBounds? = null,
)

/**
 * A screen-pixel rectangle (top-left origin). Plain ints mirroring the OCR block bounds, so
 * `:parsers` needs no `android.graphics` type or `:ocr` dependency. Serializable so fixtures can pin
 * it.
 */
@Serializable
data class OfferContentBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
