package mx.kompara.overlay

import mx.kompara.capture.OfferEvent
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.OfferRecorder
import mx.kompara.metrics.recordedMetrics
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaUnavailable

/**
 * What the chip displays for a published card: the ledger row's frozen evaluation (one judgement
 * for chip and ledger), never the incoming card. [paymentShown] is false when a live OCR frame
 * withheld payment: the durable row keeps its last confirmed payment, but it is not rehydrated
 * into that frame. Replays evaluate through this same function so they assert what production shows.
 */
internal class RowBackedVerdict(val row: OfferEntity, val state: VerdictChipState, val paymentShown: Boolean) {
    companion object {
        suspend fun evaluate(recorder: OfferRecorder, event: OfferEvent.Parsed, current: PlatformThreshold): RowBackedVerdict {
            val row = recorder.record(event.card.sessionId ?: "${event.packageName}:${event.timestampMs}",
                event.card.sessionStartedAt ?: event.timestampMs, OfferMapping.toTripOffer(event.card),
                event.card.captureSource, event.card.surge, event.timestampMs)
            val evaluation = requireNotNull(row.evaluation)
            val threshold = if (evaluation.unit == PreferredMetric.IPK.name)
                current.copy(minPerKmMxn = evaluation.goal.toDouble(), redPerKmMxn = evaluation.minimum.toDouble())
            else current.copy(minPerHourMxn = evaluation.goal.toDouble(), redPerHourMxn = evaluation.minimum.toDouble())
            // Durable history keeps its last confirmed payment when current OCR confidence is lost.
            // It must not rehydrate that payment into a live frame whose instance confidence withheld it.
            if (event.card.captureSource == "OCR" && event.card.fare == null) {
                return RowBackedVerdict(row, VerdictChipState(VerdictChipState.reading(threshold,
                    PreferredMetric.valueOf(evaluation.unit)).copy(band = ReglaBand.Unavailable(ReglaUnavailable.FARE)), "—"),
                    paymentShown = false)
            }
            return RowBackedVerdict(row, VerdictChipState.from(row.recordedMetrics(), threshold), paymentShown = true)
        }
    }
}
