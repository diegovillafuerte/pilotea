package mx.kompara.capture.lifecycle

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Cross-module bus for OCR-derived [LifecycleSignal]s (B-039 OCR ledger path), mirroring
 * `:capture`'s `OfferEventBus`.
 *
 * The trip ledger ([TripLifecycleTracker]) is fed by [OfferEventLifecycleMapper], which reads the
 * **accessibility node** stream. But no target app exposes its offer card to accessibility
 * (DiDi/inDrive render to a SurfaceView; Uber's card moved to a Compose/TextureView — design §7.1),
 * so the node path never produces an `OfferSeen` for a real offer. The MediaProjection + OCR service
 * (`:ocr`, which has no Hilt graph) classifies each captured frame into a [LifecycleSignal] and
 * publishes it here; `:app` merges this stream into the tracker so OCR-captured offers/trips reach
 * the driver's automatic ledger.
 *
 * A plain process-wide object (same rationale as `OfferEventBus` / `ScreenReaderState`): the single
 * app process hosts both the OCR foreground service and the tracker collector.
 */
object OcrLifecycleBus {
    private val _signals = MutableSharedFlow<LifecycleSignal>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val signals: SharedFlow<LifecycleSignal> = _signals.asSharedFlow()

    fun tryEmit(signal: LifecycleSignal) {
        _signals.tryEmit(signal)
    }
}
