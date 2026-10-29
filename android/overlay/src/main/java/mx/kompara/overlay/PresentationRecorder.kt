package mx.kompara.overlay

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.db.entity.OfferPresentationEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Persists against the recorder/ledger's session, without restamping duplicate OCR frames. */
@Singleton
class PresentationRecorder @Inject constructor(private val dao: OfferPresentationDao) {
    private val mutex = Mutex()

    suspend fun record(sessionId: String, placement: OverlayPlacement, rated: Boolean, atMs: Long) = mutex.withLock {
        val record = when (placement) {
            is OverlayPlacement.Displayed -> OfferPresentationEntity(sessionId, placement.format.name, atMs,
                if (placement.overMarker) "MARKER_FALLBACK" else null)
            is OverlayPlacement.Suppressed -> if (rated) OfferPresentationEntity(sessionId, "SUPPRESSED", atMs, placement.cause)
                else OfferPresentationEntity(sessionId, "UNSUPPORTED", atMs, "UNREAD_NO_PRESENTATION")
            is OverlayPlacement.Unsupported -> OfferPresentationEntity(sessionId, "UNSUPPORTED", atMs, placement.cause)
        }
        val prior = dao.forSession(sessionId)
        // Once seen, the log must not imply it was never shown. A later successful placement can
        // replace a no-room outcome; a stale/unknown frame cannot erase a measured outcome.
        if (prior?.outcome in setOf("FULL", "COMPACT") && record.outcome !in setOf("FULL", "COMPACT")) return@withLock
        if (prior?.outcome == "SUPPRESSED" && record.outcome == "UNSUPPORTED") return@withLock
        if (prior?.outcome != record.outcome || prior.cause != record.cause) dao.record(record)
    }
}
