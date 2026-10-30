package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.OfferInstanceEvidence
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
 * An observed card lifetime owns one UUID. Matching numeric legs never establish that lifetime:
 * host/variant/product/action changes or displaced independent card anchors (including stack switches)
 * end it immediately. Missing tokens/legs and a single numeric misread keep the durable ID. Two
 * consecutive complete changed routes can corroborate replacement when other tokens stay alike.
 * The pipeline resets on a signature-free gap (N=1) or capture end before a new card can inherit it.
 */
internal class OcrOfferSessionTracker {
    private var previous: OfferCard? = null
    private var route: OcrOfferIdentity? = null
    private var candidateRoute: OcrOfferIdentity? = null
    private var candidateAt = 0L
    private var id: String? = null
    private var startedAt = 0L

    /** A disappearing secondary card is ambiguous: lose money confidence, not the durable ID. */
    fun stackChanged(card: OfferCard): Boolean {
        val oldCount = previous?.instanceEvidence?.visibleCards ?: return false
        val newCount = card.instanceEvidence?.visibleCards ?: return false
        return oldCount > 1 && newCount < oldCount
    }

    /** Observe reliable replacement evidence even if the numeric outlier guard rejects this frame. */
    fun observe(card: OfferCard): Boolean {
        val old = previous
        val replaced = old != null && replacement(old, card)
        if (replaced) reset()
        val base = previous
        val evidence = card.instanceEvidence
        val prior = base?.instanceEvidence
        previous = card.copy(variant = card.variant ?: base?.variant,
            instanceEvidence = OfferInstanceEvidence(
                evidence?.product ?: prior?.product, evidence?.action ?: prior?.action,
                prior?.headingBounds ?: evidence?.headingBounds,
                prior?.actionBounds ?: evidence?.actionBounds,
                evidence?.visibleCards ?: prior?.visibleCards),
            hostGeometry = base?.hostGeometry?.takeIf {
                it.source == HostGeometrySource.HOST_LAYOUT || it.ocrEvidence?.independentlyMeasuredCard == true
            } ?: card.hostGeometry ?: base?.hostGeometry)
        return replaced
    }

    fun parsed(card: OfferCard, atMs: Long, nowMs: Long = atMs): OfferCard {
        observe(card)
        val next = OcrOfferIdentity.from(card)
        if (route != null && next != null && next != route) {
            if (candidateRoute == next && nowMs - candidateAt in 0..FareStabilizer.WINDOW_MS) {
                reset()
                observe(card)
                route = next
            } else {
                candidateRoute = next
                candidateAt = nowMs
            }
        } else {
            candidateRoute = null
            if (route == null) route = next
        }
        if (id == null) {
            id = UUID.randomUUID().toString()
            startedAt = atMs
        }
        return card.copy(sessionId = id, sessionStartedAt = startedAt)
    }

    fun reset() {
        previous = null
        route = null
        candidateRoute = null
        candidateAt = 0L
        id = null
        startedAt = 0L
    }

    private fun replacement(old: OfferCard, next: OfferCard): Boolean {
        fun changed(a: String?, b: String?) = a != null && b != null && a != b
        if (old.platform != next.platform || changed(old.variant, next.variant)) return true
        val a = old.instanceEvidence
        val b = next.instanceEvidence
        if (changed(a?.product, b?.product) || changed(a?.action, b?.action)) return true
        // Text widths vary with recognition. Compare anchor origins, not fare/content unions whose
        // top changes whenever the fare disappears. Tolerance is 2.5% of the viewport, at least 24px.
        val tolerance = maxOf(24, ((next.hostGeometry?.displayBounds?.width ?: 0) * .025).toInt())
        fun moved(a: HostRect?, b: HostRect?) = a != null && b != null &&
            (abs(a.top - b.top) > tolerance || abs(a.left - b.left) > tolerance)
        if (moved(a?.headingBounds, b?.headingBounds) || moved(a?.actionBounds, b?.actionBounds)) return true
        fun measured(card: OfferCard): HostRect? = card.hostGeometry?.takeIf {
            it.source == HostGeometrySource.HOST_LAYOUT || it.ocrEvidence?.independentlyMeasuredCard == true
        }?.offerCard
        val oldBounds = measured(old)
        val newBounds = measured(next)
        return oldBounds != null && newBounds != null &&
            (moved(oldBounds, newBounds) || abs(oldBounds.right - newBounds.right) > tolerance ||
                abs(oldBounds.bottom - newBounds.bottom) > tolerance)
    }
}
