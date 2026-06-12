package mx.kompara.capture

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single stream of [OfferEvent]s the overlay consumes, regardless of how the offer was captured.
 *
 * Two capture sources feed it: the accessibility node-tree path (Uber, which exposes text) and the
 * MediaProjection + OCR path (DiDi/inDrive, which render to a SurfaceView — design doc §7). The
 * accessibility service owns the verdict overlay window (only it has the accessibility window token),
 * so OCR-derived events route here and the service-hosted overlay collects them.
 *
 * A plain object (not Hilt) so the non-injected OCR foreground service can publish to it directly.
 */
object OfferEventBus {
    private val _events = MutableSharedFlow<OfferEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<OfferEvent> = _events.asSharedFlow()

    fun tryEmit(event: OfferEvent) {
        _events.tryEmit(event)
    }
}
