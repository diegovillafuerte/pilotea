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

    private val _silentLaneActive = MutableStateFlow(false)

    /**
     * True while the silent-screenshot capture lane (AccessibilityService.takeScreenshot, API 30+,
     * B-091) owns capture. On 30+ the reader runs with no MediaProjection consent and no cast
     * indicator, so the MediaProjection consent flow ([OcrConsentActivity]) must no-op while this is
     * true — otherwise tapping "Iniciar lector" would start a redundant second capture of the same
     * screen. False on API <30 (MediaProjection is the only lane) or if the lane self-disabled after
     * repeated on-device takeScreenshot failures (then the MediaProjection path is the fallback).
     */
    val silentLaneActive: StateFlow<Boolean> = _silentLaneActive.asStateFlow()

    fun setSilentLaneActive(active: Boolean) {
        _silentLaneActive.value = active
    }

    /** An immutable (package, seen-at) pair, published atomically — see [foregroundHost]. */
    private data class ForegroundHost(val packageName: String, val seenAtMs: Long)

    // Which target host app (Uber/DiDi/inDrive) the accessibility service last observed foreground,
    // and the elapsedRealtime it was seen. The OCR ledger path (B-039) reads this to GATE its
    // trip/idle signals: capture sees the WHOLE screen, so without this a non-host app (WhatsApp, the
    // notification shade) would feed bogus state changes into the trip ledger and close trips /
    // resolve offers from unrelated screens. The accessibility service only receives events for
    // target packages, so a fresh timestamp ≈ "a target app is foreground"; staleness means the
    // driver switched away (or the host screen went static).
    //
    // Published as ONE @Volatile immutable snapshot (not two separate @Volatile fields): the writer
    // (a11y service thread) and reader (OCR capture thread) are different threads, and two separate
    // volatiles could be torn — a reader seeing a new package paired with the previous timestamp for
    // one frame. A single reference store makes the pair atomic.
    @Volatile
    private var foregroundHost: ForegroundHost? = null

    /** Called by the accessibility service on every target-app event. */
    fun setForegroundHost(packageName: String, seenAtMs: Long) {
        foregroundHost = ForegroundHost(packageName, seenAtMs)
    }

    /**
     * The foreground host package if a target app was seen within [freshnessMs] of [nowMs], else null
     * (driver switched away / screen went static — OCR frames must NOT be attributed to the ledger).
     * Reads the snapshot ONCE so the package and its timestamp are always consistent. Pure so the OCR
     * gate is unit-testable.
     */
    fun freshForegroundHost(nowMs: Long, freshnessMs: Long): String? =
        foregroundHost?.takeIf { nowMs - it.seenAtMs < freshnessMs }?.packageName

    // The overlay verdict chip's current on-screen rect in display pixels (top-left origin), or null
    // when the chip is hidden. :overlay's OverlayController publishes it on attach/drag; :ocr's
    // capture service reads it to MASK the chip's own pixels out of the OCR frame. MediaProjection
    // mirrors the WHOLE screen including our chip, so without this the chip's own "MXN…"/"$…" text is
    // OCR'd and misparsed as the offer fare — a self-capture feedback loop that collapsed the live
    // verdict to $0. Lives here (not in :overlay) because :ocr can't depend on :overlay and this
    // object is already the overlay↔capture bridge.
    @Volatile
    var overlayChipRect: ScreenRect? = null
        private set

    /** Publish the chip's current display-pixel rect (called by the overlay on attach/layout/drag). */
    fun setOverlayChipRect(rect: ScreenRect) {
        overlayChipRect = rect
    }

    /** The chip is hidden/detached — stop masking its (now-absent) region. */
    fun clearOverlayChipRect() {
        overlayChipRect = null
    }
}

/**
 * A rectangle in display pixels (top-left origin). Bridges the overlay chip's on-screen bounds from
 * :overlay to :ocr without either touching android.graphics.Rect, so the masking logic stays pure.
 */
data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
