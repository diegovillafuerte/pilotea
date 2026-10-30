package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import java.util.UUID

/** Exact route evidence, independent of the amount, for retaining one OCR offer's payment. */
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

/** OCR fare corrections stay in one session; unknown or changed route evidence starts afresh. */
internal class OcrOfferSessionTracker {
    private var identity: OcrOfferIdentity? = null
    private var id: String? = null
    private var startedAt = 0L

    fun parsed(card: OfferCard, atMs: Long): OfferCard {
        val next = OcrOfferIdentity.from(card)
        if (id == null || next == null || identity != next) {
            id = UUID.randomUUID().toString()
            startedAt = atMs
        }
        identity = next
        return card.copy(sessionId = id, sessionStartedAt = startedAt)
    }

    fun reset() {
        identity = null
        id = null
        startedAt = 0L
    }
}
