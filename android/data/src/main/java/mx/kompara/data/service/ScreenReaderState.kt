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

    private val _hasRunThisSession = MutableStateFlow(false)

    /**
     * True once the reader has run at least once this process lifetime (B-078). The reader-down
     * banner gates on this: it is a RECOVERY affordance for a reader that died mid-shift. A driver
     * who never started the reader is onboarded through the Lector tab, never nagged by an overlay
     * (and after process death the persistent stopped-notification is the recovery path instead).
     */
    val hasRunThisSession: StateFlow<Boolean> = _hasRunThisSession.asStateFlow()

    fun setRunning(running: Boolean) {
        _running.value = running
        if (running) _hasRunThisSession.value = true
    }

    // Which target host app (Uber/DiDi/inDrive) the accessibility service last observed foreground,
    // and the elapsedRealtime it was seen. The OCR ledger path (B-039) reads these to GATE its
    // trip/idle signals: MediaProjection captures the WHOLE screen, so without this a non-host app
    // (WhatsApp, the notification shade) would feed bogus state changes into the trip ledger and
    // close trips / resolve offers from unrelated screens. The accessibility service only receives
    // events for target packages, so a fresh timestamp ≈ "a target app is foreground"; staleness
    // means the driver switched away (or the host screen went static).
    @Volatile
    var foregroundHostPackage: String? = null
        private set

    @Volatile
    var foregroundHostSeenAtMs: Long = 0L
        private set

    /** Called by the accessibility service on every target-app event. */
    fun setForegroundHost(packageName: String, seenAtMs: Long) {
        foregroundHostPackage = packageName
        foregroundHostSeenAtMs = seenAtMs
    }

    /**
     * The foreground host package if a target app was seen within [freshnessMs] of [nowMs], else null
     * (driver switched away / screen went static — OCR frames must NOT be attributed to the ledger).
     * Pure so the OCR gate is unit-testable.
     */
    fun freshForegroundHost(nowMs: Long, freshnessMs: Long): String? =
        foregroundHostPackage?.takeIf { nowMs - foregroundHostSeenAtMs < freshnessMs }
}
