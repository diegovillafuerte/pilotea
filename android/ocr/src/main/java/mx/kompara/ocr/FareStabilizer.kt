package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard

/**
 * Payment confirmation for a single offer instance, independent of its durable ledger identity.
 * The parser is fail-safe, so the first confident payment of a NEW instance paints immediately.
 * Once revealed, revisions (including reacquisition after lost route confidence) need two consecutive
 * agreeing readings within [WINDOW_MS]. Unreadable, invalid or disagreeing frames break agreement.
 * Candidate expiry never changes the confirmed payment; only confirmed payment survives unread OCR.
 *
 * The pipeline assigns the instance ID before calling this class and invalidates payment confidence
 * even for a changed/incomplete route rejected by the outlier guard. Geometry remains current-frame.
 * Pure, single-threaded (the OCR frame mutex serializes calls) and JVM-testable.
 */
class FareStabilizer(private val windowMs: Long = WINDOW_MS) {

    private data class Sample(val fare: Double, val atMs: Long, val paymentBasis: String, val rawFare: String?)

    private var instanceId: String? = null
    private var route: OcrOfferIdentity? = null
    private var confirmed: Sample? = null
    private var candidate: Sample? = null
    private var firstRevealUsed = false

    /** Only confirmed payment is published to the shared overlay/ledger path. */
    fun onParsed(card: OfferCard, nowMs: Long): OfferCard {
        val nextRoute = OcrOfferIdentity.from(card)
        if (card.sessionId != null) {
            if (instanceId != card.sessionId) {
                reset()
                instanceId = card.sessionId
            } else if (nextRoute == null || nextRoute != route) {
                invalidatePayment()
            }
        } else if (nextRoute == null || nextRoute != route) {
            // Standalone callers lack the pipeline's durable instance ID. Never carry money across
            // unknown/changed routes; the production path always supplies the assigned session ID.
            reset()
        }
        route = nextRoute
        if (nextRoute == null) return card.withoutPayment()

        val fare = card.fare
        if (fare == null || !fare.isFinite() || fare <= 0.0) {
            candidate = null
            return confirmed?.let { card.withPayment(it) } ?: card
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
        return confirmed?.let { card.withPayment(it) } ?: card.withoutPayment()
    }

    /** Lose money confidence without spending another first-reveal allowance for this instance. */
    fun invalidatePayment() {
        confirmed = null
        candidate = null
    }

    /** A dropped/unparsed frame cannot connect two otherwise nonconsecutive candidate readings. */
    fun breakCandidateAgreement() {
        candidate = null
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

    private fun OfferCard.withPayment(sample: Sample) = copy(fare = sample.fare,
        paymentBasis = sample.paymentBasis,
        raw = (raw - "fare") + (sample.rawFare?.let { mapOf("fare" to it) } ?: emptyMap()))

    private fun OfferCard.withoutPayment() = copy(fare = null, paymentBasis = "UNCONFIRMED",
        raw = raw - "fare")

    companion object {
        /** Maximum gap for the two consecutive agreeing readings (about five OCR frames). */
        const val WINDOW_MS = 1500L
    }
}
