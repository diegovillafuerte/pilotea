package mx.kompara.parsers.spec

import kotlinx.serialization.Serializable
import mx.kompara.parsers.normalize.Normalizer

/**
 * A declarative, data-only description of how to recognize and extract an offer card for one host
 * app and version range (android-technical-design.md §1). The whole point of B-028: when a host
 * app reshuffles its UI, we ship a new spec (JSON) rather than a new Kotlin release.
 *
 * A spec is selected when [targetPackage] matches the snapshot's package and the snapshot's
 * version code falls inside [versionCodeRange]. The [cardDetector] then decides whether the
 * snapshot is actually an offer card (and which variant), and [extractors] pull typed fields.
 */
@Serializable
data class ParserSpec(
    /** Host package this spec handles, e.g. `com.ubercab.driver`. */
    val targetPackage: String,
    /** Host versionCode window this spec is valid for; open-ended when bounds are null. */
    val versionCodeRange: VersionRange = VersionRange(),
    /** Monotonic spec revision, surfaced in telemetry so we know which spec produced a card. */
    val specVersion: Int = 1,
    /** Anchor rules that decide "is this an offer card?" and tag the base variant. */
    val cardDetector: CardDetector,
    /** Per-field extraction chains. */
    val extractors: List<FieldExtractor> = emptyList(),
    /** Optional variant-override rules layered on top of the detector's base tag. */
    val variants: List<VariantRule> = emptyList(),
    /**
     * Optional list-mode config. inDrive (and any future bid-list host) renders MANY offer cards in
     * a single scrollable list; [listMode] tells the engine how to segment one snapshot into N
     * per-card sub-snapshots so each bid gets its own [mx.kompara.parsers.model.OfferCard]. When
     * null the spec is single-card: the engine extracts exactly one card per snapshot (the existing
     * Uber/DiDi behavior, untouched). See [mx.kompara.parsers.engine.SpecEngine.evaluateAll].
     */
    val listMode: ListMode? = null,
)

/**
 * How to segment a multi-card snapshot into per-card slices. The engine finds every node whose text
 * matches [cardAnchor] (the repeating "start of card" marker — e.g. inDrive's per-card bid amount or
 * accept button), orders them in reading order, and slices the node list at each anchor: card *i* is
 * every node from anchor *i* (inclusive) up to anchor *i+1* (exclusive). Each slice is evaluated as
 * its own sub-snapshot through the normal extractor chains, so list mode reuses 100% of the
 * single-card extraction logic — it only changes *which nodes* each extraction sees.
 *
 * This is text-anchor segmentation, consistent with the engine's anchors-first rule: it never slices
 * by absolute child index or raw coordinates. A snapshot with a single matching anchor degrades to a
 * one-element list (a single bid card), so a list-mode spec also handles the single-card variant.
 */
@Serializable
data class ListMode(
    /** The repeating per-card anchor that marks the start of each card in the list. */
    val cardAnchor: TextPattern,
)

/** Inclusive version-code window. `null` bound means unbounded on that side. */
@Serializable
data class VersionRange(
    val min: Long? = null,
    val max: Long? = null,
) {
    fun contains(versionCode: Long?): Boolean {
        if (versionCode == null) return true // unknown version: don't exclude
        if (min != null && versionCode < min) return false
        if (max != null && versionCode > max) return false
        return true
    }
}

/**
 * Decides whether a snapshot is an offer card. [allOf] patterns must ALL match some node's text;
 * [anyOf] requires at least one. [noneOf] is a negative guard (e.g. exclude the trip-in-progress
 * screen). Matching is on node *text* — anchors, never coordinates.
 */
@Serializable
data class CardDetector(
    val allOf: List<TextPattern> = emptyList(),
    val anyOf: List<TextPattern> = emptyList(),
    val noneOf: List<TextPattern> = emptyList(),
    /** Base variant tag applied when the detector matches and no [VariantRule] overrides it. */
    val baseVariant: String? = null,
)

/**
 * A variant tag applied when its [whenAnyOf]/[whenAllOf] anchors are present. First matching rule
 * wins, so order rules from most-specific to least in the spec. A rule may also flip [setsSurge].
 */
@Serializable
data class VariantRule(
    val tag: String,
    val whenAllOf: List<TextPattern> = emptyList(),
    val whenAnyOf: List<TextPattern> = emptyList(),
    val setsSurge: Boolean = false,
)

/**
 * One field's extraction chain. The engine tries [strategies] in order and takes the first that
 * yields a value, then runs [normalizer]. Field names are free-form keys; the engine maps a known
 * set ([FieldNames]) into the typed [mx.kompara.parsers.model.OfferCard] columns and keeps every
 * matched field's raw text in the card's `raw` map.
 */
@Serializable
data class FieldExtractor(
    val field: String,
    val strategies: List<ExtractStrategy> = emptyList(),
    val normalizer: Normalizer = Normalizer.NONE,
)

/**
 * A single extraction attempt. Signals are tried in priority order by the engine (text anchors
 * first, then viewId, geometry only as a tiebreaker — never absolute child index or raw coords as
 * a primary signal, per the task's extraction rules):
 *
 *  1. [regexOnSameNode] — capture group 1 (or whole match) of a regex against a node's own text.
 *  2. [viewIdEquals] / [viewIdContains] — restrict candidate nodes by accessibility view id.
 *  3. [afterAnchor] / [belowAnchor] — take the text of the node that follows / sits below a node
 *     whose text matches the anchor pattern (relative position, resolved via depth/index then
 *     geometry as a tiebreaker).
 *
 * A strategy may combine an anchor with a regex (e.g. "the node after 'Tarifa', then pull the
 * number out of it"). At least one signal must be set.
 */
@Serializable
data class ExtractStrategy(
    /** Regex applied to a node's own text; group 1 if present, else the whole match. */
    val regexOnSameNode: String? = null,
    /** Only consider nodes whose viewId exactly equals this. */
    val viewIdEquals: String? = null,
    /** Only consider nodes whose viewId contains this substring. */
    val viewIdContains: String? = null,
    /** Take the value from the node positioned just after a node matching this anchor pattern. */
    val afterAnchor: TextPattern? = null,
    /** Take the value from the node positioned just below a node matching this anchor pattern. */
    val belowAnchor: TextPattern? = null,
    /** Optional regex applied to the candidate node's text once it's selected by an anchor. */
    val extractRegex: String? = null,
)

/** A reusable text pattern: a regex with a case-sensitivity flag. */
@Serializable
data class TextPattern(
    val regex: String,
    val ignoreCase: Boolean = true,
) {
    fun compile(): Regex =
        if (ignoreCase) Regex(regex, RegexOption.IGNORE_CASE) else Regex(regex)
}

/**
 * Canonical field names the engine maps into typed [mx.kompara.parsers.model.OfferCard] columns.
 * Any other field name still flows into the card's `raw` map but not a typed column.
 */
object FieldNames {
    const val FARE = "fare"
    const val PICKUP_DISTANCE_KM = "pickupDistanceKm"
    const val PICKUP_ETA_MIN = "pickupEtaMin"
    const val TRIP_DISTANCE_KM = "tripDistanceKm"
    const val TRIP_DURATION_MIN = "tripDurationMin"
    const val PAYMENT_TYPE = "paymentType"

    /**
     * Count of other simultaneous bids on a list-mode (inDrive) snapshot, stamped by the engine into
     * the top card's `raw` when [mx.kompara.parsers.engine.SpecEngine.evaluate] runs in list mode.
     * Not a typed [mx.kompara.parsers.model.OfferCard] column — it only ever appears in `raw`.
     */
    const val ADDITIONAL_BIDS = "additional_bids"
}
