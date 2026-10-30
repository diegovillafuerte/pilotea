package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.HostRect
import kotlin.math.abs
import java.util.UUID

/** Exact route confidence for money retention, NOT durable offer-instance identity. */
internal data class OcrOfferIdentity(
    val host: String,
    val pickupKm: Double,
    val pickupMin: Double,
    val tripKm: Double,
    val tripMin: Double,
) {
    companion object {
        fun from(card: OfferCard): OcrOfferIdentity? {
            fun readable(value: Double?) = value?.takeIf { it.isFinite() && it >= 0.0 }
            return OcrOfferIdentity(card.platform,
                readable(card.pickupDistanceKm) ?: return null,
                readable(card.pickupEtaMin) ?: return null,
                readable(card.tripDistanceKm) ?: return null,
                readable(card.tripDurationMin) ?: return null)
        }
    }
}

/**
 * Durable, presence-based session. Numbers and OCR geometry are observations, never IDs.
 * A replacement needs two distinct positive changes, each repeated on consecutive frames.
 * Missing evidence breaks candidate agreement while preserving the last confirmed semantics.
 * Presence and payment use monotonic time; UUID start timestamps remain epoch milliseconds.
 */
internal class OcrOfferSessionTracker {
    enum class Confidence { CONFIDENT, UNCERTAIN }
    data class Decision(val card: OfferCard?, val confidence: Confidence, val replaced: Boolean = false)
    private val presence = CardPresenceTracker()
    private var baseline: OfferCard? = null
    private var previous: OfferCard? = null
    private var votes = emptyMap<String, Pair<Any, Int>>()
    private var id: String? = null
    private var startedAt = 0L

    fun isPresent() = presence.isPresent()
    fun obscuredBy(mask: HostRect?): Boolean {
        if (mask == null) return false
        val known = baseline ?: return false
        return listOfNotNull(known.instanceEvidence?.headingBounds, known.instanceEvidence?.actionBounds,
            known.hostGeometry?.offerCard).plus(known.instanceEvidence?.cardBands.orEmpty()).any(mask::overlaps)
    }

    /** Absence must be corroborated; filtered/occluded frames are uncertainty, not disappearance. */
    fun miss(cardLike: Boolean, nowMs: Long, observedGone: Boolean = false): Boolean {
        votes = emptyMap()
        val gone = observedGone && presence.isPresent() || presence.onMiss(cardLike, nowMs)
        if (gone) reset()
        return gone
    }

    fun stackChanged(card: OfferCard): Boolean = (previous?.instanceEvidence?.visibleCards ?: 1) >
        (card.instanceEvidence?.visibleCards ?: 1)

    fun update(card: OfferCard, atMs: Long, nowMs: Long = atMs): Decision {
        val old = baseline
        val route = OcrOfferIdentity.from(card)
        val extractionCertain = card.instanceEvidence?.routeWellFormed != false &&
            card.instanceEvidence?.selectionCertain != false
        if (old == null) {
            // An unread/partial first frame cannot mint a ledger ID or spend first-payment allowance.
            val mask = card.hostGeometry?.ocrEvidence?.overlayMask
            val maskedEvidence = mask != null && (card.hostGeometry?.offerCard?.overlaps(mask) == true ||
                card.instanceEvidence?.cardBands.orEmpty().any(mask::overlaps) ||
                card.instanceEvidence?.headingBounds == null)
            if (route == null || maskedEvidence || !extractionCertain) return Decision(null, Confidence.UNCERTAIN)
            start(card, atMs, nowMs)
            return Decision(stamped(card), Confidence.CONFIDENT)
        }
        // Platform transitions are OS/capture boundaries, handled before parsing in the pipeline.
        if (old.platform != card.platform) {
            reset()
            return update(card, atMs, nowMs)
        }
        presence.onParsed(nowMs)
        val changes = linkedMapOf<String, Any>()
        fun changed(key: String, a: String?, b: String?) {
            if (a != null && b != null && a != b) changes[key] = b
        }
        val a = old.instanceEvidence
        val b = card.instanceEvidence
        changed("action", a?.action, b?.action)
        changed("product", a?.product, b?.product)
        val priorLabels = labels(old)
        val newLabels = labels(card)
        // Surge/multi-stop are additive properties; their absence never contradicts a label.
        if (priorLabels.isNotEmpty() && newLabels.isNotEmpty() && priorLabels.intersect(newLabels).isEmpty()) {
            changes["presentation"] = newLabels
        }
        if (route != null && route != OcrOfferIdentity.from(old)) changes["route"] = route
        val oldBands = a?.cardBands.orEmpty()
        val selected = b?.cardBands?.firstOrNull()
        val secondary = oldBands.drop(1).indexOfFirst { sameBand(it, selected) }
        val mask = card.hostGeometry?.ocrEvidence?.overlayMask
        val maskedCriticalEvidence = mask != null && (obscuredBy(mask) ||
            card.hostGeometry?.offerCard?.overlaps(mask) == true ||
            b?.cardBands.orEmpty().any(mask::overlaps) || b?.headingBounds == null)
        if (!maskedCriticalEvidence && (a?.visibleCards ?: 1) > (b?.visibleCards ?: 1) && secondary >= 0) {
            changes["stack-count"] = b!!.visibleCards!!
            changes["known-secondary"] = secondary
        }
        if (maskedCriticalEvidence || !extractionCertain) changes.clear()
        votes = changes.mapValues { (key, value) ->
            val prior = votes[key]
            value to if (prior?.first == value) prior.second + 1 else 1
        }
        if (votes.values.count { it.second >= 2 } >= 2) {
            reset()
            // Corroborated replacement can reveal a missing fare, but must have a readable route.
            if (route == null) return Decision(null, Confidence.UNCERTAIN, true)
            start(card, atMs, nowMs)
            return Decision(stamped(card), Confidence.CONFIDENT, true)
        }
        val movement = moved(previous?.instanceEvidence?.headingBounds, b?.headingBounds) ||
            moved(previous?.instanceEvidence?.actionBounds, b?.actionBounds)
        val missingTokens = a?.action != null && b?.action == null || a?.product != null && b?.product == null ||
            priorLabels.isNotEmpty() && newLabels.isEmpty() ||
            (a?.labels.orEmpty() - b?.labels.orEmpty()).isNotEmpty()
        previous = card // Follow animation continuously; never compare displacement to a frozen origin.
        val uncertain = route == null || changes.isNotEmpty() || movement || missingTokens || maskedCriticalEvidence || !extractionCertain
        if (uncertain) {
            return Decision(stamped(old.copy(fare = null, raw = old.raw - "fare", paymentBasis = "UNCONFIRMED",
                hostGeometry = card.hostGeometry, contentBounds = card.contentBounds)), Confidence.UNCERTAIN)
        }
        baseline = card.copy(instanceEvidence = b?.copy(labels = a?.labels.orEmpty() + b.labels))
        return Decision(stamped(card), Confidence.CONFIDENT)
    }

    /** Convenience for pure replay callers; production consumes the confidence explicitly. */
    fun parsed(card: OfferCard, atMs: Long, nowMs: Long = atMs): OfferCard =
        update(card, atMs, nowMs).card ?: card.copy(fare = null, paymentBasis = "UNCONFIRMED")

    private fun labels(card: OfferCard): Set<String> =
        (card.instanceEvidence?.labels.orEmpty() + listOfNotNull(card.variant))
            .intersect(setOf("exclusive", "trip_radar", "reservation"))

    private fun moved(a: HostRect?, b: HostRect?) = a != null && b != null &&
        (abs(a.top - b.top) > 24 || abs(a.left - b.left) > 24)

    private fun sameBand(a: HostRect, b: HostRect?) = b != null &&
        abs(a.top - b.top) <= 24 && abs(a.bottom - b.bottom) <= 24 && abs(a.left - b.left) <= 24

    private fun stamped(card: OfferCard) = card.copy(sessionId = id, sessionStartedAt = startedAt)
    private fun start(card: OfferCard, atMs: Long, nowMs: Long) {
        id = UUID.randomUUID().toString()
        startedAt = atMs
        baseline = card
        previous = card
        presence.onParsed(nowMs)
    }

    fun reset() {
        baseline = null
        previous = null
        votes = emptyMap()
        id = null
        startedAt = 0L
        presence.reset()
    }
}
