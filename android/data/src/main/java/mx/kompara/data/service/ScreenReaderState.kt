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

    /** True while the capture coordinator has a live OCR owner. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(running: Boolean) {
        _running.value = running
    }

    private val _silentLaneFailed = MutableStateFlow(false)
    /** A measured OEM failure, distinct from a capable lane that has not bound yet. */
    val silentLaneFailed: StateFlow<Boolean> = _silentLaneFailed.asStateFlow()
    fun setSilentLaneFailed(failed: Boolean) { _silentLaneFailed.value = failed }

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
    private data class ForegroundHost(val observation: HostForegroundObservation, val seenAtMs: Long)

    // The accessibility service reads the OS active-window package, on events and every 750 ms.
    // OCR uses this package to choose its parser and gate ledger signals. A background target event
    // never establishes foreground identity. Unknown roots withhold parser authorization while
    // preserving the distinction from an affirmatively departed or screen-off host.
    //
    // Published as ONE @Volatile immutable snapshot (not two separate @Volatile fields): the writer
    // (a11y service thread) and reader (OCR capture thread) are different threads, and two separate
    // volatiles could be torn — a reader seeing a new package paired with the previous timestamp for
    // one frame. A single reference store makes the pair atomic.
    @Volatile
    private var foregroundHost: ForegroundHost? = null

    private class ForegroundProbe(val owner: Any, val observe: () -> HostForegroundObservation)
    @Volatile private var foregroundProbe: ForegroundProbe? = null

    /** The bound accessibility service owns the live read-only OS query used by projection. */
    @Synchronized fun attachForegroundProbe(owner: Any, observe: () -> HostForegroundObservation) {
        foregroundProbe = ForegroundProbe(owner, observe)
    }

    @Synchronized fun detachForegroundProbe(owner: Any) {
        if (foregroundProbe?.owner === owner) foregroundProbe = null
    }

    /** Never substitute cached foreground state when a current OS observation is unavailable. */
    fun liveForegroundObservation(): HostForegroundObservation {
        val probe = foregroundProbe ?: return HostForegroundObservation.Unknown
        val observation = runCatching { probe.observe() }.getOrDefault(HostForegroundObservation.Unknown)
        // An old service callback finishing after detach/rebind cannot authorize a capture.
        return if (foregroundProbe === probe) observation else HostForegroundObservation.Unknown
    }

    /** Refreshed by target-app events and live foreground polling; null clears a departed host. */
    fun setForegroundHost(packageName: String?, seenAtMs: Long) {
        setForegroundObservation(packageName?.let { HostForegroundObservation.Target(it) }
            ?: HostForegroundObservation.NonHost, seenAtMs)
    }

    fun setForegroundObservation(observation: HostForegroundObservation, seenAtMs: Long) {
        foregroundHost = ForegroundHost(observation, seenAtMs)
    }

    fun freshForegroundObservation(nowMs: Long, freshnessMs: Long): HostForegroundObservation =
        foregroundHost?.takeIf { nowMs - it.seenAtMs in 0 until freshnessMs }?.observation
            ?: HostForegroundObservation.Unknown

    /**
     * The foreground host package if a target app was seen within [freshnessMs] of [nowMs], else null
     * (driver switched away / screen went static — OCR frames must NOT be attributed to the ledger).
     * Reads the snapshot ONCE so the package and its timestamp are always consistent. Pure so the OCR
     * gate is unit-testable.
     */
    fun freshForegroundHost(nowMs: Long, freshnessMs: Long): String? =
        (freshForegroundObservation(nowMs, freshnessMs) as? HostForegroundObservation.Target)?.packageName

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

    /**
     * How far beyond [overlayChipRect] the capture treats pixels as the chip's own (anti-aliased edges, the drop
     * shadow, overlay-vs-capture skew): :ocr masks OCR text and card evidence within it; WP-AD keeps the unread
     * notice's rect, expanded by it, clear of the card it announces. One number for both sides of the bridge.
     */
    const val CHIP_MASK_MARGIN_PX = 8

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

/** Unknown OS visibility never authorizes parsing and never proves a host departed. */
sealed interface HostForegroundObservation {
    data class Target(val packageName: String) : HostForegroundObservation
    data object NonHost : HostForegroundObservation
    data object Unknown : HostForegroundObservation

    companion object {
        fun fromWindow(packageName: String?, interactive: Boolean, targets: Set<String>): HostForegroundObservation = when {
            !interactive -> NonHost
            packageName == null -> Unknown
            packageName in targets -> Target(packageName)
            else -> NonHost
        }
    }
}
