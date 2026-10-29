package mx.kompara.parsers.model

import java.util.UUID

/** Session identity is independent of OCR distance jitter and shared by every downstream consumer. */
class OfferSessionTracker {
    private var host: String? = null
    private var fare: Double? = null
    private var id: String? = null
    private var startedAt = 0L
    private var missingAt: Long? = null

    @Synchronized fun parsed(card: OfferCard, at: Long): OfferCard {
        val newSession = id == null || host != card.platform ||
            (fare != null && card.fare != null && fare != card.fare) ||
            (missingAt?.let { at - it > 500 } == true)
        if (newSession) {
            id = UUID.randomUUID().toString()
            startedAt = at
            host = card.platform
            fare = card.fare
        }
        missingAt = null
        return card.copy(sessionId = id, sessionStartedAt = startedAt)
    }
    @Synchronized fun missing(at: Long) { if (missingAt == null) missingAt = at }
    @Synchronized fun reset() { id = null; host = null; fare = null; missingAt = null }
}
