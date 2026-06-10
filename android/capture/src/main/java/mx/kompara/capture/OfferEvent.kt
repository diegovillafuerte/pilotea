package mx.kompara.capture

import mx.kompara.parsers.model.OfferCard

/**
 * The downstream-facing result of running one captured [ScreenSnapshot] through the spec engine
 * (B-029 wiring of TD-007). Emitted on [OfferEventPipeline.offers] for the overlay (B-031) and the
 * trip log (B-039) to consume.
 *
 * Modeled as a sealed type with an explicit [NoCard] so collectors can react to a card leaving the
 * screen (the accept-countdown card disappears) — not just to a new card appearing.
 */
sealed interface OfferEvent {

    /** The host app's package the snapshot came from. */
    val packageName: String

    /** When the underlying snapshot was captured (host event time, ms). */
    val timestampMs: Long

    /** A snapshot the engine recognized as an offer card, with the extracted [card]. */
    data class Parsed(
        override val packageName: String,
        override val timestampMs: Long,
        val card: OfferCard,
    ) : OfferEvent

    /**
     * A snapshot that was NOT an offer card: either no spec targets the host package/version, or
     * the spec's card detector did not match (e.g. the offer card dismissed, a trip-in-progress
     * screen). [reason] is for telemetry/debugging only.
     */
    data class NoCard(
        override val packageName: String,
        override val timestampMs: Long,
        val reason: Reason,
    ) : OfferEvent

    enum class Reason {
        /** No bundled/active spec targets this package + version code. */
        NO_SPEC,

        /** A spec matched the package, but its detector said "this isn't an offer card". */
        NOT_AN_OFFER,
    }
}
