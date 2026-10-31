package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.HostRect
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale
import java.util.UUID

/** Exact route confidence for money retention, NOT durable offer-instance identity. */
internal data class OcrOfferIdentity(
    val host: String,
    val pickupKm: Double,
    val pickupMin: Double,
    val tripKm: Double,
    val tripMin: Double,
) {
    /** Main's device-validated offer key: the trip leg is OCR-stable; pickup may change live. */
    val tripKey: String get() = "$host|${String.format(Locale.US, "%.1f", tripKm)}|${tripMin.roundToInt()}"

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
 * A new offer is a different TRIP leg (main's device-validated key; pickup is corroboration only,
 * it may change live) read identically on two consecutive complete, well-formed frames. The trip
 * vote comes from the frame's own visible legs, so our chip mask can never erase it. Two distinct
 * non-route signals (product, action, presentation, stack), each repeated, still replace an
 * identical-leg card. Every replacement mints a FRESH session, even when an earlier card's trip leg
 * returns (A -> B -> A): its OfferSeen then retires the departing card's row, keeping exactly one
 * PENDING ledger row = the card on screen (an A -> B -> A re-order costs one extra row for A).
 * (A first frame is never re-based in place: its ledger row keeps its legs, and evaluation reads
 * that row, so a re-based ID would show the first frame's verdict.) Missing evidence breaks
 * candidate agreement while preserving the last confirmed semantics; evidence that only our own
 * overlay hid is not missing when the frame reads the same trip leg. A non-route vote is void only
 * when the block it rests on lies under our own mask (the action block for the action and its card
 * class, the heading for the product, the card bands for a stack pop); our mask touching a card's
 * padded envelope voids no visible vote. Presence and payment use monotonic time; UUID start
 * timestamps remain epoch milliseconds.
 *
 * Identity confirmation is separate from payment admission: [Decision.seed] is the pending frame's
 * fare only if the outlier guard accepted it, or if the confirming frame reads the identical fare
 * AND legs (two identical readings of the new trip leg are their own consensus; the old offer's
 * window cannot judge the new offer). The pipeline admits the confirming frame's own fare only if
 * the guard accepted it, so a guard-rejected fare never becomes a session's first reveal.
 *
 * The economic inputs (fare AND legs) that reach the published card and the ledger row come from
 * ONE admitted frame, never an accepted fare with rejected legs: a guard-rejected confirming frame
 * keeps its own geometry and evidence but takes the accepted pending frame's legs
 * ([Decision.admitted]), and the held baseline adopts legs only from guard-accepted frames (held
 * route-incomplete frames republish them with the confirmed fare). Both replacement paths (another
 * trip leg, or two non-route keys on identical legs) take them the same way from the voting frame
 * before the confirming one. That first reveal is one frame; every later revision of the economics
 * needs two consecutive agreeing observations ([FareStabilizer]).
 *
 * The Radar pill is not identity, but the card's own action button is: its card class (Aceptar vs
 * the Radar Me interesa / Coincidir) is also a presentation label, so a sustained action change
 * is two keys and an identical-leg Exclusivo -> Radar card of the same product replaces.
 */
internal class OcrOfferSessionTracker {
    enum class Confidence { CONFIDENT, UNCERTAIN }
    data class Decision(val card: OfferCard?, val confidence: Confidence, val replaced: Boolean = false,
        val identityUnresolved: Boolean = false, val rejection: OcrPublicationRejectionReason? = null,
        /** The corroborating pending frame's clean payment, stamped with the confirmed identity. */
        val seed: OfferCard? = null,
        /** The guard-accepted pending frame whose economics a guard-rejected confirming frame takes. */
        val admitted: OfferCard? = null,
        /** Diagnostics only: consecutive votes per replacement key. */
        val votes: Map<String, Int> = emptyMap())
    /** A frame voting for a replacement (another trip leg, or non-route keys on identical legs),
     *  kept with its trip key and guard verdict until the next frame confirms or breaks it. */
    private data class PendingTrip(val key: String, val card: OfferCard, val guardAccepted: Boolean)
    private val presence = CardPresenceTracker()
    private var baseline: OfferCard? = null
    private var previous: OfferCard? = null
    private var votes = emptyMap<String, Pair<Any, Int>>()
    private var pendingTrip: PendingTrip? = null
    private var pendingVote: PendingTrip? = null
    private var id: String? = null
    private var startedAt = 0L
    private var replacementPending = false

    fun isPresent() = presence.isPresent()
    fun obscuredBy(mask: HostRect?): Boolean {
        if (mask == null) return false
        val known = baseline ?: return false
        return listOfNotNull(known.instanceEvidence?.headingBounds, known.instanceEvidence?.actionBounds,
            known.hostGeometry?.offerCard).plus(known.instanceEvidence?.cardBands.orEmpty()).any(mask::overlaps)
    }

    /**
     * A frame with no parsed card. A signature (garble, mask) holds; three signature-free frames
     * spanning 1.8 s, the 15 s no-parse ceiling or positive absence retire the session.
     */
    fun miss(cardLike: Boolean, nowMs: Long, observedGone: Boolean = false): Boolean {
        votes = emptyMap()
        pendingTrip = null
        pendingVote = null
        if (!presence.isPresent()) return false
        val gone = observedGone || presence.onMiss(cardLike, nowMs)
        if (gone) reset()
        return gone
    }

    fun suspendObservation() { votes = emptyMap(); pendingTrip = null; pendingVote = null }

    /** A parsed frame without a complete route is card-like, not a parse: the 15 s ceiling still runs. */
    fun parseCeilingExpired(nowMs: Long): Boolean {
        if (!presence.isPresent() || !presence.onMiss(cardLike = true, nowMs = nowMs)) return false
        reset()
        return true
    }

    fun stackChanged(card: OfferCard): Boolean = (previous?.instanceEvidence?.visibleCards ?: 1) >
        (card.instanceEvidence?.visibleCards ?: 1)

    /** [guardAccepted] false: an outlier frame may vote toward consensus; its fare seeds nothing alone. */
    fun update(card: OfferCard, atMs: Long, nowMs: Long = atMs, guardAccepted: Boolean = true): Decision {
        val old = baseline
        val route = OcrOfferIdentity.from(card)
        val extractionCertain = card.instanceEvidence?.routeWellFormed != false &&
            card.instanceEvidence?.selectionCertain != false
        // From this frame's own visible, well-formed legs, before any mask clause.
        val trip = route?.takeIf { extractionCertain }?.tripKey
        if (old == null) {
            // An unread/partial first frame cannot mint a ledger ID or spend first-payment allowance.
            // A missing heading is not masked evidence: real DiDi cards print none.
            val mask = card.hostGeometry?.ocrEvidence?.overlayMask
            val maskedEvidence = mask != null && (card.hostGeometry?.offerCard?.overlaps(mask) == true ||
                card.instanceEvidence?.cardBands.orEmpty().any(mask::overlaps))
            val rejection = when {
                route == null -> OcrPublicationRejectionReason.PARSED_ROUTE_INCOMPLETE
                maskedEvidence -> OcrPublicationRejectionReason.PARSED_CARD_MASKED
                !extractionCertain -> OcrPublicationRejectionReason.PARSED_EXTRACTION_UNCERTAIN
                else -> null
            }
            if (rejection != null) return Decision(null, Confidence.UNCERTAIN, rejection = rejection)
            start(card, atMs, nowMs)
            return Decision(stamped(card), Confidence.CONFIDENT)
        }
        // Platform transitions are OS/capture boundaries, handled before parsing in the pipeline.
        if (old.platform != card.platform) {
            reset()
            return update(card, atMs, nowMs, guardAccepted)
        }
        if (route != null) presence.onParsed(nowMs)
        val oldTrip = OcrOfferIdentity.from(old)?.tripKey
        if (trip != null && oldTrip != null && trip != oldTrip) {
            votes = emptyMap()
            pendingVote = null
            previous = card
            val prior = pendingTrip?.takeIf { it.key == trip }
            if (prior != null) return confirmReplacement(prior, card, guardAccepted, atMs, nowMs)
            pendingTrip = PendingTrip(trip, card, guardAccepted)
            replacementPending = true
            // Another card is on screen: show its own legs, never the held offer's payment.
            return Decision(stamped(card.withoutPayment()), Confidence.UNCERTAIN, identityUnresolved = true,
                votes = mapOf("trip" to 1))
        }
        pendingTrip = null
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
        val oldBands = a?.cardBands.orEmpty()
        val selected = b?.cardBands?.firstOrNull()
        val secondary = oldBands.drop(1).indexOfFirst { sameBand(it, selected) }
        val mask = card.hostGeometry?.ocrEvidence?.overlayMask
        // Only evidence our chip can actually hide counts as masked; a card that never had a
        // heading (DiDi) is not masked by its absence.
        val maskedCriticalEvidence = mask != null && (obscuredBy(mask) ||
            card.hostGeometry?.offerCard?.overlaps(mask) == true ||
            b?.cardBands.orEmpty().any(mask::overlaps))
        // The frame reads the held trip leg itself: what OUR overlay hides (masked evidence, and
        // tokens whose baseline bounds lie under our mask) is not uncertainty. Labels carry no
        // bounds and the baseline merges them, so label loss counts as ours when our mask covers
        // the baseline card evidence.
        val ownMask = mask?.takeIf { trip != null && trip == oldTrip }
        fun hiddenByUs(bounds: HostRect?) = ownMask != null && bounds?.overlaps(ownMask) == true
        // A vote is void only when the block it rests on lies under our own mask (round 4, A1):
        // the action and its card class rest on the action block, the product on the heading, a
        // stack pop on the card bands (a button our chip hides lowers the count). Our mask touching
        // a card's padded envelope is never grounds to void a vote whose evidence is visible.
        fun underMask(bounds: HostRect?) = mask != null && bounds?.overlaps(mask) == true
        if (underMask(b?.actionBounds)) { changes -= "action"; changes -= "presentation" }
        if (underMask(b?.headingBounds)) changes -= "product"
        val stackMasked = (oldBands + listOfNotNull(a?.actionBounds) + b?.cardBands.orEmpty()).any(::underMask)
        if (!stackMasked && (a?.visibleCards ?: 1) > (b?.visibleCards ?: 1) && secondary >= 0) {
            changes["stack-count"] = b!!.visibleCards!!
            changes["known-secondary"] = secondary
        }
        if (!extractionCertain) changes.clear()
        if (changes.isNotEmpty()) replacementPending = true
        votes = changes.mapValues { (key, value) ->
            val prior = votes[key]
            value to if (prior?.first == value) prior.second + 1 else 1
        }
        val priorVote = pendingVote?.takeIf { it.key == trip }
        pendingVote = trip?.takeIf { changes.isNotEmpty() }?.let { PendingTrip(it, card, guardAccepted) }
        if (votes.values.count { it.second >= 2 } >= 2) {
            // Corroborated identical-leg replacement can reveal a missing fare, but needs a route.
            if (route == null) {
                retire()
                return Decision(null, Confidence.UNCERTAIN, true,
                    rejection = OcrPublicationRejectionReason.PARSED_ROUTE_INCOMPLETE)
            }
            // Like a trip-leg replacement (B6): economics from the accepted voting frame before.
            return confirmReplacement(priorVote, card, guardAccepted, atMs, nowMs)
        }
        val movement = moved(previous?.instanceEvidence?.headingBounds, b?.headingBounds) ||
            moved(previous?.instanceEvidence?.actionBounds, b?.actionBounds)
        val missingTokens = a?.action != null && b?.action == null && !hiddenByUs(a.actionBounds) ||
            a?.product != null && b?.product == null && !hiddenByUs(a.headingBounds) ||
            (priorLabels.isNotEmpty() && newLabels.isEmpty() || (a?.labels.orEmpty() - b?.labels.orEmpty()).isNotEmpty()) &&
                !(ownMask != null && obscuredBy(ownMask))
        previous = card // Follow animation continuously; never compare displacement to a frozen origin.
        val uncertain = route == null || changes.isNotEmpty() || movement || missingTokens ||
            maskedCriticalEvidence && ownMask == null || !extractionCertain
        if (uncertain) {
            // Same trip leg: publish this frame's legs. Unreadable/malformed legs keep the held ones.
            val shown = if (trip != null) card else old.copy(hostGeometry = card.hostGeometry,
                contentBounds = card.contentBounds, fareBounds = card.fareBounds)
            return Decision(stamped(shown.withoutPayment()), Confidence.UNCERTAIN,
                identityUnresolved = replacementPending, votes = votes.mapValues { it.value.second })
        }
        replacementPending = false
        // A guard-rejected frame of the same offer is held: it refreshes evidence, never the legs.
        baseline = (if (guardAccepted) card else card.withLegsOf(old))
            .copy(instanceEvidence = b?.copy(labels = a?.labels.orEmpty() + b.labels))
        return Decision(stamped(card), Confidence.CONFIDENT)
    }

    /** Convenience for pure replay callers; production consumes the confidence explicitly. */
    fun parsed(card: OfferCard, atMs: Long, nowMs: Long = atMs): OfferCard =
        update(card, atMs, nowMs).card ?: card.copy(fare = null, paymentBasis = "UNCONFIRMED")

    /** "trip_radar" is not identity: it comes only from the floating Radar pill, host chrome that
     *  also shows on the idle screen and that our own dock may cover (HostGeometryDeriver agrees).
     *  The card's own action button is card-intrinsic: its class (Aceptar vs Radar) is a label. */
    private fun labels(card: OfferCard): Set<String> =
        (card.instanceEvidence?.labels.orEmpty() + listOfNotNull(card.variant))
            .intersect(setOf("exclusive", "reservation")) + setOfNotNull(card.instanceEvidence?.action?.let {
                if (it in RADAR_ACTIONS) "radar_card" else "accept_card" })

    private fun sameEconomics(a: OfferCard, b: OfferCard) = a.fare == b.fare &&
        a.pickupDistanceKm == b.pickupDistanceKm && a.pickupEtaMin == b.pickupEtaMin &&
        a.tripDistanceKm == b.tripDistanceKm && a.tripDurationMin == b.tripDurationMin

    private fun OfferCard.withLegsOf(other: OfferCard) = copy(pickupDistanceKm = other.pickupDistanceKm,
        pickupEtaMin = other.pickupEtaMin, tripDistanceKm = other.tripDistanceKm, tripDurationMin = other.tripDurationMin)

    private fun moved(a: HostRect?, b: HostRect?) = a != null && b != null &&
        (abs(a.top - b.top) > 24 || abs(a.left - b.left) > 24)

    private fun sameBand(a: HostRect, b: HostRect?) = b != null &&
        abs(a.top - b.top) <= 24 && abs(a.bottom - b.bottom) <= 24 && abs(a.left - b.left) <= 24

    private fun OfferCard.withoutPayment() = copy(fare = null, raw = raw - "fare", paymentBasis = "UNCONFIRMED")

    private fun stamped(card: OfferCard) = card.copy(sessionId = id, sessionStartedAt = startedAt)
    private fun start(card: OfferCard, atMs: Long, nowMs: Long) {
        id = UUID.randomUUID().toString()
        startedAt = atMs
        baseline = card
        previous = card
        presence.onParsed(nowMs)
    }

    /** Identity is confirmed by [prior] and [card]; payment admission is separate (class KDoc). */
    private fun confirmReplacement(prior: PendingTrip?, card: OfferCard, guardAccepted: Boolean, atMs: Long,
        nowMs: Long): Decision {
        val seed = prior?.card?.takeIf { it.fare != null && (prior.guardAccepted || sameEconomics(it, card)) }
        val admitted = prior?.card?.takeIf { !guardAccepted && prior.guardAccepted }
        return replace(admitted?.let { card.withLegsOf(it) } ?: card, atMs, nowMs, seed, admitted)
    }

    /** Always a fresh session: resuming a retired ID could not reopen its ledger row (A2). */
    private fun replace(card: OfferCard, atMs: Long, nowMs: Long, seed: OfferCard?, admitted: OfferCard? = null): Decision {
        retire()
        start(card, atMs, nowMs)
        return Decision(stamped(card), Confidence.CONFIDENT, true, seed = seed?.let(::stamped),
            admitted = admitted?.let(::stamped))
    }

    /** End the current instance by replacement; presence continues. */
    private fun retire() {
        baseline = null
        previous = null
        votes = emptyMap()
        pendingTrip = null
        pendingVote = null
        id = null
        startedAt = 0L
        replacementPending = false
    }

    /** Disappearance or a host boundary: nothing survives. */
    fun reset() {
        retire()
        presence.reset()
    }

    private companion object {
        /** The Radar card class's action labels (OfferActionLabel); Aceptar is the other class. */
        val RADAR_ACTIONS = setOf("ME INTERESA", "COINCIDIR")
    }
}
