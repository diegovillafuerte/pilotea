package mx.kompara.parsers

import mx.kompara.data.model.Platform

/**
 * Extracts a [ParsedOffer] from a flattened representation of a host app's accessibility node
 * tree. Concrete parsers are driven by declarative, versioned specs (per
 * android-technical-design.md §1) — this interface is the contract the capture pipeline calls.
 */
interface OfferParser {
    /** Platform this parser handles. */
    val platform: Platform

    /**
     * Attempt to parse an offer from the given lines of visible text (the flattened node tree).
     * Returns null when the screen is not a recognizable offer card.
     */
    fun parse(textLines: List<String>): ParsedOffer?
}
