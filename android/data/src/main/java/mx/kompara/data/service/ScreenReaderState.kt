package mx.kompara.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live state of the screen-capture (OCR) reader, shared across module boundaries (B-075):
 * `:ocr`'s capture service writes it and the Lector tab observes it. A plain process-wide object —
 * the same pattern as `:capture`'s OfferEventBus — because `:ocr` has no Hilt graph and everything
 * runs in the single app process.
 */
object ScreenReaderState {

    /**
     * Intent action (package-internal — always send with `setPackage`) that opens the screen-capture
     * consent flow. The Lector tab's start button and the reader-stopped notification both use it;
     * `:app`'s manifest binds it to OcrConsentActivity (the literal there must match this).
     */
    const val ACTION_START = "mx.kompara.action.START_SCREEN_READER"

    private val _running = MutableStateFlow(false)

    /** True while the OCR capture service holds a live MediaProjection. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(running: Boolean) {
        _running.value = running
    }
}
