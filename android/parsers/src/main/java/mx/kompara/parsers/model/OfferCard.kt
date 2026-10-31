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
    /** OCR fare/leg union used with heading/action bounds to derive the card envelope. */
    val contentBounds: OfferContentBounds? = null,
    /** The VISIBLE headline fare block (never a button echo); placement keeps the chip above it. */
    val fareBounds: OfferContentBounds? = null,
    /** Recognized offer variants declare estimated driver payment; unknown shapes stay unconfirmed. */
    val paymentBasis: String = "UNCONFIRMED",
    val captureSource: String = "ACCESSIBILITY",
    val sessionId: String? = null,
    val sessionStartedAt: Long? = null,
    /** Measured/derived host bounds for this frame; null gates the overlay only. */
    val hostGeometry: HostGeometry? = null,
    /** Card-local replacement evidence; missing OCR tokens are unknown, never proof of continuity. */
    val instanceEvidence: OfferInstanceEvidence? = null,
)

@Serializable
data class OfferInstanceEvidence(
    val product: String? = null,
    val action: String? = null,
    val headingBounds: HostRect? = null,
    val actionBounds: HostRect? = null,
    /** Count visible action controls independently of recognized headings, including stacked cards. */
    val visibleCards: Int? = null,
    /** Independent positive labels; an absent OCR token never negates a previously observed label. */
    val labels: Set<String> = emptySet(),
    /** Complete visible heading/action bands, in screen order, for corroborating stacked selection. */
    val cardBands: List<HostRect> = emptyList(),
    /** Recovery may extract numbers from malformed lines without establishing confident reading. */
    val routeWellFormed: Boolean = true,
    /** A preceding action without its heading leaves the active stacked card unidentified. */
    val selectionCertain: Boolean = true,
)

/**
 * A screen-pixel rectangle (top-left origin). Plain ints mirroring the OCR block bounds, so
 * `:parsers` needs no `android.graphics` type or `:ocr` dependency. Serializable so fixtures can pin
 * it.
 */
@Serializable
data class OfferContentBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
