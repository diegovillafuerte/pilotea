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

    /**
     * The host package is recognized but its parser was DISABLED by a remote kill switch (B-033):
     * the active OTA bundle flipped this package off because its parser is known-broken. Distinct
     * from [NoCard] so the overlay can show "actualizando soporte para Uber/DiDi…" instead of
     * silently doing nothing. The overlay copy/wiring is owned by `:overlay` (a sibling task); this
     * just surfaces the state.
     */
    data class SpecDisabled(
        override val packageName: String,
        override val timestampMs: Long,
    ) : OfferEvent

    enum class Reason {
        /** No bundled/active spec targets this package + version code. */
        NO_SPEC,

        /** A spec matched the package, but its detector said "this isn't an offer card". */
        NOT_AN_OFFER,
    }
}
