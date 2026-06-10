package mx.kompara.parsers.engine

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.normalize.Normalizer
import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.spec.CardDetector
import mx.kompara.parsers.spec.ExtractStrategy
import mx.kompara.parsers.spec.FieldExtractor
import mx.kompara.parsers.spec.FieldNames
import mx.kompara.parsers.spec.ListMode
import mx.kompara.parsers.spec.ParserSpec
import mx.kompara.parsers.spec.TextPattern
import mx.kompara.parsers.spec.VariantRule

/**
 * Evaluates a declarative [ParserSpec] against a [ParserSnapshot] to produce a typed [OfferCard]
 * (android-technical-design.md §1). Stateless and exception-safe: any malformed regex or odd input
 * degrades to "field not found" rather than throwing, and a snapshot that isn't an offer card
 * returns `null`.
 *
 * Signal priority follows the task's extraction rules — text anchors first, viewId second,
 * geometry only as a tiebreaker. There is no absolute-child-index or raw-coordinate matching as a
 * primary signal anywhere in here.
 */
class SpecEngine {

    /**
     * @return a partially-or-fully populated [OfferCard] when [spec] recognizes the snapshot as an
     *   offer card, or `null` when the card detector does not match (or the spec targets a
     *   different package/version).
     *
     * For a list-mode spec (inDrive's bid list, [ParserSpec.listMode] set) this returns the FIRST
     * (top) bid card so the single-verdict pipeline/overlay keeps working unchanged; the count of
     * other simultaneous bids is recorded in the card's `raw["additional_bids"]`. Single-card specs
     * (Uber/DiDi) are entirely unaffected.
     */
    fun evaluate(snapshot: ParserSnapshot, spec: ParserSpec): OfferCard? {
        if (!recognizes(snapshot, spec)) return null
        // Variant is a snapshot-level property (e.g. the list's "bid_list" header sits above the
        // first card) so it's resolved over the whole snapshot, then shared by every card.
        val variant = resolveVariant(spec, snapshotTexts(snapshot))

        // List-mode: segment, return the top bid, stamp the count of the rest into raw.
        if (spec.listMode != null) {
            val segments = segment(snapshot, spec.listMode)
            if (segments.isEmpty()) return null
            val additional = segments.size - 1
            return buildCard(segments.first(), spec, variant, additionalBids = if (additional > 0) additional else null)
        }

        return buildCard(snapshot.nodes, spec, variant, additionalBids = null)
    }

    /**
     * List-mode evaluation: produce one [OfferCard] per bid card in a multi-bid snapshot (inDrive's
     * passenger-bid list). The primary [evaluate] stays single-verdict for pipeline compatibility;
     * this is the opt-in entry point for surfaces that want every bid (a richer multi-offer UX is
     * tracked as tech debt — the shipping overlay still shows a single verdict).
     *
     * Segmentation is driven by [ParserSpec.listMode]; a single-card spec (no [ParserSpec.listMode])
     * yields exactly the one card [evaluate] would, so callers can use [evaluateAll] uniformly. A
     * snapshot that isn't an offer card yields an empty list (never null, never throws).
     *
     * @return cards in reading order (top bid first), or an empty list when the spec doesn't match.
     */
    fun evaluateAll(snapshot: ParserSnapshot, spec: ParserSpec): List<OfferCard> {
        if (!recognizes(snapshot, spec)) return emptyList()
        val variant = resolveVariant(spec, snapshotTexts(snapshot))
        val listMode = spec.listMode
            ?: return listOf(buildCard(snapshot.nodes, spec, variant, additionalBids = null))
        return segment(snapshot, listMode).map { buildCard(it, spec, variant, additionalBids = null) }
    }

    /** Package/version/detector gate shared by [evaluate] and [evaluateAll]. */
    private fun recognizes(snapshot: ParserSnapshot, spec: ParserSpec): Boolean {
        if (spec.targetPackage != snapshot.packageName) return false
        if (!spec.versionCodeRange.contains(snapshot.versionCode)) return false
        return detects(spec.cardDetector, snapshotTexts(snapshot))
    }

    private fun snapshotTexts(snapshot: ParserSnapshot): List<String> =
        snapshot.nodes.mapNotNull { it.text?.takeIf(String::isNotBlank) }

    /**
     * Slice a snapshot's nodes into one list per bid card. Every node whose text matches
     * [ListMode.cardAnchor] starts a new card; a card owns every node from its anchor (inclusive) up
     * to the next anchor (exclusive). Reading order (depth/index) defines the sequence, mirroring the
     * extractor anchors — no absolute child index or raw coordinate is used to find boundaries.
     *
     * When the anchor matches once, this returns a single slice (the single-bid variant). When it
     * never matches, it returns the whole node list as one slice so a list-mode spec still extracts a
     * card from a degenerate snapshot rather than dropping it.
     */
    private fun segment(snapshot: ParserSnapshot, listMode: ListMode): List<List<ParserNode>> {
        val ordered = orderedNodes(snapshot)
        val anchorIdxs = ordered.indices.filter {
            ordered[it].text?.let(listMode.cardAnchor::matches) == true
        }
        if (anchorIdxs.size <= 1) return listOf(ordered)
        val bounds = anchorIdxs + ordered.size
        return bounds.zipWithNext { start, end -> ordered.subList(start, end).toList() }
    }

    /**
     * Build the typed [OfferCard] for one card's [nodes]. Extraction runs over a sub-snapshot of
     * exactly those nodes so list-mode cards never bleed fields across each other. The [variant]
     * (tag + surge) is resolved once at snapshot scope by the caller and shared across cards, since
     * a list's variant marker (e.g. inDrive's "bid_list" header) sits above the individual cards.
     * [additionalBids], when non-null, is stamped into `raw["additional_bids"]` (used by [evaluate]
     * in list mode to report how many other bids exist).
     */
    private fun buildCard(
        nodes: List<ParserNode>,
        spec: ParserSpec,
        variant: Pair<String?, Boolean>,
        additionalBids: Int?,
    ): OfferCard {
        val (variantTag, surgeFromVariant) = variant

        val cardSnapshot = ParserSnapshot(
            packageName = spec.targetPackage,
            timestampMs = 0L,
            nodes = nodes,
        )

        val raw = LinkedHashMap<String, String>()
        for (extractor in spec.extractors) {
            val value = runCatching { extractField(extractor, cardSnapshot) }.getOrNull()
            if (value != null) raw[extractor.field] = value
        }
        if (additionalBids != null) raw[FieldNames.ADDITIONAL_BIDS] = additionalBids.toString()

        fun numeric(field: String, normalizer: Normalizer): Double? =
            raw[field]?.let { runCatching { Normalizer.applyNumeric(normalizer, it) }.getOrNull() }

        // Normalizers per typed field default to the ones declared on their extractor when present.
        val normalizerByField = spec.extractors.associate { it.field to it.normalizer }

        return OfferCard(
            platform = spec.targetPackage,
            variant = variantTag,
            fare = numeric(FieldNames.FARE, normalizerByField[FieldNames.FARE] ?: Normalizer.CURRENCY),
            pickupDistanceKm = numeric(
                FieldNames.PICKUP_DISTANCE_KM,
                normalizerByField[FieldNames.PICKUP_DISTANCE_KM] ?: Normalizer.DISTANCE_KM,
            ),
            pickupEtaMin = numeric(
                FieldNames.PICKUP_ETA_MIN,
                normalizerByField[FieldNames.PICKUP_ETA_MIN] ?: Normalizer.DURATION_MIN,
            ),
            tripDistanceKm = numeric(
                FieldNames.TRIP_DISTANCE_KM,
                normalizerByField[FieldNames.TRIP_DISTANCE_KM] ?: Normalizer.DISTANCE_KM,
            ),
            tripDurationMin = numeric(
                FieldNames.TRIP_DURATION_MIN,
                normalizerByField[FieldNames.TRIP_DURATION_MIN] ?: Normalizer.DURATION_MIN,
            ),
            surge = surgeFromVariant,
            paymentType = raw[FieldNames.PAYMENT_TYPE],
            raw = raw,
        )
    }

    // --- Detection -----------------------------------------------------------------------------

    private fun detects(detector: CardDetector, texts: List<String>): Boolean {
        val allOk = detector.allOf.all { pattern -> texts.any { pattern.matches(it) } }
        val anyOk = detector.anyOf.isEmpty() ||
            detector.anyOf.any { pattern -> texts.any { pattern.matches(it) } }
        val noneOk = detector.noneOf.none { pattern -> texts.any { pattern.matches(it) } }
        // An empty detector matches nothing — a spec must declare at least one positive anchor.
        val hasPositive = detector.allOf.isNotEmpty() || detector.anyOf.isNotEmpty()
        return hasPositive && allOk && anyOk && noneOk
    }

    /**
     * Returns (variant tag, surge). The variant *tag* is the first matching [VariantRule] (order
     * rules most-specific first), but surge is orthogonal: it is the OR of `setsSurge` across ALL
     * matched rules. This lets a structural variant (e.g. `multi_stop`) keep its tag while a
     * co-occurring surge badge still flips the surge flag — Uber renders "Tarifa dinámica" on top
     * of any card type. When no rule matches, the detector's base variant is used with surge=false.
     */
    private fun resolveVariant(spec: ParserSpec, texts: List<String>): Pair<String?, Boolean> {
        var tag: String? = null
        var surge = false
        for (rule in spec.variants) {
            if (!matchesVariant(rule, texts)) continue
            if (tag == null) tag = rule.tag // first match wins for the tag
            if (rule.setsSurge) surge = true // any matched surge rule flips the flag
        }
        return (tag ?: spec.cardDetector.baseVariant) to surge
    }

    private fun matchesVariant(rule: VariantRule, texts: List<String>): Boolean {
        val allOk = rule.whenAllOf.all { pattern -> texts.any { pattern.matches(it) } }
        val anyOk = rule.whenAnyOf.isEmpty() ||
            rule.whenAnyOf.any { pattern -> texts.any { pattern.matches(it) } }
        val hasCondition = rule.whenAllOf.isNotEmpty() || rule.whenAnyOf.isNotEmpty()
        return hasCondition && allOk && anyOk
    }

    // --- Extraction ----------------------------------------------------------------------------

    private fun extractField(extractor: FieldExtractor, snapshot: ParserSnapshot): String? {
        for (strategy in extractor.strategies) {
            val value = applyStrategy(strategy, snapshot)
            if (value != null) return value
        }
        return null
    }

    private fun applyStrategy(strategy: ExtractStrategy, snapshot: ParserSnapshot): String? {
        // 1. Anchor-relative strategies (text anchors are the strongest signal).
        if (strategy.afterAnchor != null) {
            val node = nodeAfterAnchor(strategy.afterAnchor, snapshot) ?: return null
            return pullFromNode(node, strategy.extractRegex)
        }
        if (strategy.belowAnchor != null) {
            val node = nodeBelowAnchor(strategy.belowAnchor, snapshot) ?: return null
            return pullFromNode(node, strategy.extractRegex)
        }

        // 2. viewId-scoped, optionally with a same-node regex.
        if (strategy.viewIdEquals != null || strategy.viewIdContains != null) {
            val candidates = snapshot.nodes.filter { matchesViewId(it, strategy) }
            for (node in candidates) {
                val value = if (strategy.regexOnSameNode != null) {
                    node.text?.let { applyRegex(strategy.regexOnSameNode, it) }
                } else {
                    node.text?.takeIf(String::isNotBlank)
                }
                if (value != null) return value
            }
            return null
        }

        // 3. Same-node regex over all text (weakest standalone signal).
        if (strategy.regexOnSameNode != null) {
            for (node in snapshot.nodes) {
                val text = node.text ?: continue
                val value = applyRegex(strategy.regexOnSameNode, text)
                if (value != null) return value
            }
        }
        return null
    }

    private fun matchesViewId(node: ParserNode, strategy: ExtractStrategy): Boolean {
        val id = node.viewId ?: return false
        strategy.viewIdEquals?.let { if (id != it) return false }
        strategy.viewIdContains?.let { if (!id.contains(it)) return false }
        return true
    }

    private fun pullFromNode(node: ParserNode, extractRegex: String?): String? {
        val text = node.text?.takeIf(String::isNotBlank) ?: return null
        return if (extractRegex != null) applyRegex(extractRegex, text) else text
    }

    /**
     * The node that follows an anchor. Anchors are resolved by tree order (depth/index), so this
     * never relies on an absolute child index: it finds the node matching the anchor, then takes
     * the next non-blank node in reading order. Geometry breaks ties only when several nodes share
     * the anchor's reading position.
     */
    private fun nodeAfterAnchor(anchor: TextPattern, snapshot: ParserSnapshot): ParserNode? {
        val ordered = orderedNodes(snapshot)
        val anchorIdx = ordered.indexOfFirst { it.text?.let(anchor::matches) == true }
        if (anchorIdx == -1) return null
        for (i in (anchorIdx + 1) until ordered.size) {
            if (!ordered[i].text.isNullOrBlank()) return ordered[i]
        }
        return null
    }

    /**
     * The node visually below an anchor: smallest vertical gap among nodes whose top edge sits at
     * or below the anchor's bottom and whose horizontal span overlaps the anchor's. Geometry here
     * is the *primary* signal only because the strategy explicitly opted into spatial layout; it
     * still never uses absolute child indices or hardcoded coordinates.
     */
    private fun nodeBelowAnchor(anchor: TextPattern, snapshot: ParserSnapshot): ParserNode? {
        val anchorNode = snapshot.nodes.firstOrNull { it.text?.let(anchor::matches) == true }
            ?: return null
        val a = anchorNode.bounds
        return snapshot.nodes
            .asSequence()
            .filter { it !== anchorNode && !it.text.isNullOrBlank() }
            .filter { it.bounds.top >= a.bottom - 1 }
            .filter { it.bounds.left < a.right && it.bounds.right > a.left } // horizontal overlap
            .minByOrNull { (it.bounds.top - a.bottom) }
    }

    /** Reading order: depth then index, mirroring the flattened accessibility traversal. */
    private fun orderedNodes(snapshot: ParserSnapshot): List<ParserNode> =
        snapshot.nodes.sortedWith(compareBy({ it.index }, { it.depth }))

    /** Apply a regex, returning capture group 1 if defined, else the whole match; null on miss. */
    private fun applyRegex(pattern: String, text: String): String? {
        val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() ?: return null
        val match = regex.find(text) ?: return null
        return (match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: match.value)
            .takeIf { it.isNotBlank() }
    }
}

/** Convenience: does this pattern occur anywhere in [text]? */
private fun TextPattern.matches(text: String): Boolean =
    runCatching { compile().containsMatchIn(text) }.getOrDefault(false)
