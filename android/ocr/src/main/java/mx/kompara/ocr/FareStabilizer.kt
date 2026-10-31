package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard

/**
 * Payment confirmation for a single offer instance, independent of its durable ledger identity.
 *
 * Policy (0.2.14 round 4): the FIRST REVEAL of an offer or a replacement is one frame — the seed or
 * the first confident frame paints and mints the ledger row (the June device data shows that first
 * reading wrong in about one offer in six: the accepted price of latency). Every REVISION of the
 * offer's economics — the fare and the pickup leg here; another trip leg is another offer, a
 * session replacement (two frames, [OcrOfferSessionTracker]) — needs TWO consecutive agreeing
 * observations within [WINDOW_MS]. A single frame never revises, whatever the outlier guard said
 * about it: the guard is blind with fewer than two priors (an offer's second frame, the first frame
 * after a replacement), so its "accepted" is vacuous there. The published card carries only these
 * confirmed economics, which is all the ledger row and the chip ever take from a payment-bearing
 * card (OfferRecorder, OcrLifecycleClassifier).
 *
 * The pickup's first reveal is the instance's first confident admitted frame (the one that mints
 * the row), even when its fare is unread — on a replacement, the admitted seed the pipeline feeds
 * first (the pending frame's accepted economics, fare-less included); the fare's is its first
 * confident admitted payment. Payment-less cards carry the confirmed pickup too, so a published
 * card's legs are always the confirmed legs and a fare-less card differs from the row only by its
 * missing fare (round 5). Only a frame of unresolved identity or of another trip leg keeps its own
 * legs: it is not a reading of this instance, and it never carries payment.
 * Unreadable, invalid or disagreeing frames break agreement. Candidate expiry never changes the
 * confirmed values; only confirmed payment survives unread OCR.
 *
 * The session owns identity and marks uncertain observations; these break candidate agreement while
 * preserving confirmed payment, unless the frame reads a different trip leg (then payment is
 * withheld). A frame the outlier guard rejected is not evidence ([paymentAdmissible] false): its
 * fare and pickup can neither be a first reveal nor a revision candidate; the confirmed ones stay.
 * Only an ended/replaced session resets payment. Geometry stays current-frame.
 * Pure, single-threaded (the OCR frame mutex serializes calls) and JVM-testable.
 */
class FareStabilizer(private val windowMs: Long = WINDOW_MS) {

    private data class Sample(val fare: Double, val atMs: Long, val paymentBasis: String, val rawFare: String?)
    private data class Pickup(val km: Double, val min: Double, val atMs: Long)

    private var instanceId: String? = null
    private var route: OcrOfferIdentity? = null
    private var confirmed: Sample? = null
    private var candidate: Sample? = null
    private var firstRevealUsed = false
    private var confirmedPickup: Pickup? = null
    private var pickupCandidate: Pickup? = null

    /** Only confirmed payment is published to the shared overlay/ledger path. */
    fun onParsed(card: OfferCard, nowMs: Long, confident: Boolean = true,
        identityUnresolved: Boolean = false, paymentAdmissible: Boolean = true): OfferCard {
        val nextRoute = OcrOfferIdentity.from(card)
        if (card.sessionId != null) {
            if (instanceId != card.sessionId) {
                reset()
                instanceId = card.sessionId
            }
        } else if (nextRoute == null || nextRoute != route) {
            // Standalone callers lack the pipeline's durable instance ID. Never carry money across
            // unknown/changed routes; the production path always supplies the assigned session ID.
            reset()
        }
        if (identityUnresolved) {
            breakCandidateAgreement()
            return card.withoutPayment()
        }
        // A readable trip leg that differs from the confirmed one is another card on screen: it
        // can never borrow this payment. Pickup alone may change live; it never withholds.
        val confirmedRoute = route
        if (card.sessionId != null && confirmedRoute != null && nextRoute != null &&
            nextRoute.tripKey != confirmedRoute.tripKey) {
            breakCandidateAgreement()
            return card.withoutPayment()
        }
        val uncertain = !confident || nextRoute == null
        if (uncertain) {
            breakCandidateAgreement()
            return confirmed?.let { card.withPayment(it) } ?: card.withoutPayment().withConfirmedPickup()
        }
        route = nextRoute
        observePickup(nextRoute, nowMs, paymentAdmissible)

        val fare = card.fare?.takeIf { paymentAdmissible }
        if (fare == null || !fare.isFinite() || fare <= 0.0) {
            candidate = null
            return confirmed?.let { card.withPayment(it) }
                ?: (if (fare == null) card.withoutPayment() else card).withConfirmedPickup()
        }
        val reading = Sample(fare, nowMs, card.paymentBasis, card.raw["fare"])
        if (!firstRevealUsed) {
            confirm(reading)
        } else if (confirmed?.fare == fare && confirmed?.paymentBasis == card.paymentBasis) {
            candidate = null
        } else {
            val prior = candidate
            if (prior != null && prior.fare == fare && prior.paymentBasis == card.paymentBasis &&
                nowMs - prior.atMs in 0..windowMs) {
                confirm(reading)
            } else {
                candidate = reading
            }
        }
        return confirmed?.let { card.withPayment(it) } ?: card.withoutPayment().withConfirmedPickup()
    }

    /** The pickup leg follows the fare's rule: first reveal at once, revisions on two agreeing readings. */
    private fun observePickup(route: OcrOfferIdentity, nowMs: Long, admissible: Boolean) {
        if (!admissible) {
            pickupCandidate = null
            return
        }
        val reading = Pickup(route.pickupKm, route.pickupMin, nowMs)
        val prior = pickupCandidate
        when {
            confirmedPickup == null -> confirmedPickup = reading
            confirmedPickup?.let { it.km == reading.km && it.min == reading.min } == true -> pickupCandidate = null
            prior != null && prior.km == reading.km && prior.min == reading.min && nowMs - prior.atMs in 0..windowMs -> {
                confirmedPickup = reading
                pickupCandidate = null
            }
            else -> pickupCandidate = reading
        }
    }

    /** The payment currently shown for this instance, for contradiction checks only. */
    internal fun confirmedFare(): Double? = confirmed?.fare

    /** Clear payment only when reset ends or replaces the owning session. */
    private fun invalidatePayment() {
        confirmed = null
        candidate = null
        confirmedPickup = null
        pickupCandidate = null
    }

    /** A dropped/unparsed frame cannot connect two otherwise nonconsecutive candidate readings. */
    fun breakCandidateAgreement() {
        candidate = null
        pickupCandidate = null
    }

    /** The instance left the screen: the next instance gets its own immediate first reveal. */
    fun reset() {
        instanceId = null
        route = null
        invalidatePayment()
        firstRevealUsed = false
    }

    private fun confirm(sample: Sample) {
        confirmed = sample
        firstRevealUsed = true
        candidate = null
    }

    /** Confirmed economics only: the confirmed fare and pickup leg (the trip leg is the identity). */
    private fun OfferCard.withPayment(sample: Sample) = copy(fare = sample.fare,
        paymentBasis = sample.paymentBasis,
        raw = (raw - "fare") + (sample.rawFare?.let { mapOf("fare" to it) } ?: emptyMap())).withConfirmedPickup()

    /** The published legs are the confirmed legs, with or without payment (round 5, B1). */
    private fun OfferCard.withConfirmedPickup() = copy(pickupDistanceKm = confirmedPickup?.km ?: pickupDistanceKm,
        pickupEtaMin = confirmedPickup?.min ?: pickupEtaMin)

    private fun OfferCard.withoutPayment() = copy(fare = null, paymentBasis = "UNCONFIRMED",
        raw = raw - "fare")

    companion object {
        /** Maximum gap for consecutive readings: 700 ms post-processing wait plus up to 800 ms capture/OCR. */
        const val WINDOW_MS = 1500L
    }
}
