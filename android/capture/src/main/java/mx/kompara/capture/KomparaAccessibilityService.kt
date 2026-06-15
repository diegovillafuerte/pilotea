package mx.kompara.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mx.kompara.data.service.ScreenReaderState
import javax.inject.Inject

/**
 * Read-only [AccessibilityService] bound to the Uber Driver and DiDi Conductor apps.
 *
 * It is intentionally thin: it forwards each qualifying [AccessibilityEvent] into the injectable
 * [EventPipeline] (which coalesces bursts and reads snapshots off the main thread) and reports its
 * connected/disconnected state to [ServiceStateRepository]. All capture logic lives in those
 * collaborators so it stays unit-testable without an emulator.
 *
 * READ-ONLY (legal requirement): this class never calls `performAction` on any node or event. It
 * only ever reads the active-window tree via [WindowSnapshotSource].
 */
@AndroidEntryPoint
class KomparaAccessibilityService : AccessibilityService() {

    @Inject lateinit var pipeline: EventPipeline

    @Inject lateinit var offerPipeline: OfferEventPipeline

    @Inject lateinit var serviceState: ServiceStateRepository

    @Inject lateinit var snapshotSource: WindowSnapshotSource

    @Inject lateinit var overlayPresenter: OverlayPresenter

    private val scope = pipelineScope(kotlinx.coroutines.Dispatchers.Default)

    /**
     * Live "the driver is in an OCR-owned app right now" signal feeding the reader-down banner
     * (B-078). RISE edge: armed instantly by accessibility events (and only when the banner could
     * actually show — reader previously ran this session but is down now). FALL edge:
     * [pollToClearForeground] — the packageNames filter means no event ever fires for non-target
     * apps, so exit is invisible to the event stream and must be polled, but ONLY while armed.
     */
    private val foregroundOcrOwnedApp = MutableStateFlow(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Feed the pipeline from ALL current windows (read-only). Offer cards often live in a
        // non-focused window, so we cannot rely on rootInActiveWindow alone; getWindows() (enabled
        // by flagRetrieveInteractiveWindows) exposes the offer popup too. Fall back to the active
        // window if the window list is momentarily empty.
        snapshotSource.attach(
            WindowSnapshotSource.RootProvider {
                val roots = windows.orEmpty().mapNotNull { it.root }
                roots.ifEmpty { listOfNotNull(rootInActiveWindow) }
            },
        )
        pipeline.start(scope)
        // Keep the active spec set current as the OTA layer applies new bundles / kill switches
        // (B-033). The pipeline starts on the bundled baseline; this upgrades it as the
        // SpecConfigRepository emits remote-cached/verified specs.
        offerPipeline.trackActiveSpecs(scope)
        // Run each coalesced snapshot through the spec engine and publish OfferEvents to the shared
        // OfferEventBus, which the OCR path (design §7) also publishes to. (The trip log + telemetry
        // still collect offerPipeline.offers directly.)
        //
        // ONE WRITER PER PLATFORM: the node path is structurally blind wherever the host renders its
        // offer to a SurfaceView/Compose surface with no accessibility text, so for those packages
        // every event it produces is a spurious NoCard — and a single one racing the OCR path's
        // Parsed on the bus hides a live verdict (seen on-device 2026-06-12: a DiDi window event
        // killed the chip ~0.6 s after it appeared). As of 2026-06-15 that is ALL THREE target apps —
        // Uber moved to a UberComposeView/TextureView offer card — so OCR_OWNED_PACKAGES now covers
        // every target and the OCR service is the sole bus writer. The node path keeps running for
        // telemetry/trip-log; this filter just keeps its (now always-NoCard) verdicts off the bus.
        offerPipeline.offers
            .filterNot { it.packageName in OCR_OWNED_PACKAGES }
            .onEach { OfferEventBus.tryEmit(it) }
            .launchIn(scope)
        // Drive the verdict overlay from the unified bus. The service is the only place allowed to
        // attach the TYPE_ACCESSIBILITY_OVERLAY window; the presenter (an :overlay OverlayController,
        // injected as an interface to avoid a :capture -> :overlay cycle) does the window plumbing.
        overlayPresenter.start(scope, OfferEventBus.events, this, foregroundOcrOwnedApp)
        pollToClearForeground()
        serviceState.setConnected(true)
    }

    /**
     * Maintain the FALL edge of [foregroundOcrOwnedApp] (B-078). Polling exists ONLY to notice the
     * driver leaving the host app, so it runs exclusively while the signal is armed — i.e. exactly
     * while the banner is visible — and the coroutine sits suspended in every other state
     * (off-shift, reader running, other apps foreground). Never an always-on poll: an enabled
     * accessibility service lives 24/7, and reader-down is its DEFAULT state, so gating on reader
     * state alone would mean polling forever.
     *
     * Two consecutive non-relevant reads (~6 s) clear the signal, so a transient non-app window
     * (notification shade, volume dialog) can't churn the banner window.
     */
    private fun pollToClearForeground() {
        // The reader starting makes the banner moot — clear immediately so a stale "in DiDi" can
        // never resurface the banner over a different app after the NEXT projection death.
        scope.launch {
            ScreenReaderState.running.collect { running ->
                if (running) foregroundOcrOwnedApp.value = false
            }
        }
        scope.launch {
            foregroundOcrOwnedApp.collectLatest { armed ->
                if (!armed) return@collectLatest
                var misses = 0
                while (true) {
                    delay(FOREGROUND_POLL_MS)
                    if (bannerStillRelevant()) {
                        misses = 0
                    } else if (++misses >= FOREGROUND_CLEAR_READS) {
                        foregroundOcrOwnedApp.value = false
                        return@collectLatest
                    }
                }
            }
        }
    }

    /**
     * One cheap relevance read for the armed poll: reader still down, screen interactive, and an
     * OCR-owned app still holding the active window. Reads only the root's PACKAGE — never node
     * content — and recycles the node where that still matters (< API 33).
     */
    private fun bannerStillRelevant(): Boolean {
        if (ScreenReaderState.running.value) return false
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm != null && !pm.isInteractive) return false
        val root = rootInActiveWindow ?: return false
        return try {
            root.packageName?.toString() in OCR_OWNED_PACKAGES
        } finally {
            if (android.os.Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") root.recycle()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        // The accessibility-service XML already filters packageNames, but guard again so a
        // misconfiguration can never leak non-target events into the pipeline.
        if (pkg !in TARGET_PACKAGES) return

        // Publish the foreground host for the OCR ledger gate (B-039 §7.1): MediaProjection sees the
        // whole screen, so the OCR path must only attribute trip/idle to the ledger while a target
        // app is actually foreground. We only get events for target packages, so a recent timestamp
        // is exactly that signal. Monotonic clock — must match the OCR service's freshness check.
        ScreenReaderState.setForegroundHost(pkg, android.os.SystemClock.elapsedRealtime())

        // Reader-down banner edges (B-078) — flag reads only; nothing measurable on the hot path.
        // RISE: an OCR-owned event arms the signal, but only when the banner could actually show
        // (reader ran this session — recovery affordance, not an onboarding nag — and is down now).
        // Instant FALL: an event from a non-OCR target app (Uber) means the driver switched away;
        // don't keep the banner over the wrong app waiting out the poll's two-miss grace.
        if (pkg in OCR_OWNED_PACKAGES) {
            if (ScreenReaderState.hasRunThisSession.value && !ScreenReaderState.running.value) {
                foregroundOcrOwnedApp.value = true
            }
        } else {
            foregroundOcrOwnedApp.value = false
        }

        pipeline.submit(CaptureEvent(packageName = pkg, timestampMs = event.eventTime))
    }

    override fun onInterrupt() {
        // No-op: nothing to interrupt in a read-only pipeline.
    }

    override fun onDestroy() {
        serviceState.setConnected(false)
        snapshotSource.detach()
        overlayPresenter.stop()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        // Verified on-device (Samsung S25, MX, 2026-06-10) via `adb shell pm list packages`.
        const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        const val DIDI_DRIVER_PACKAGE = "com.didiglobal.driver"
        const val INDRIVE_DRIVER_PACKAGE = "sinet.startup.inDriver"
        val TARGET_PACKAGES = setOf(
            UBER_DRIVER_PACKAGE,
            DIDI_DRIVER_PACKAGE,
            INDRIVE_DRIVER_PACKAGE,
        )

        /**
         * Platforms owned exclusively by the OCR capture path (design §7). The node path never
         * forwards bus events for these — it cannot see their offer text, so anything it says about
         * them is noise that would fight the OCR verdict.
         *
         * DiDi/inDrive render to a Flutter SurfaceView. **Uber joined this set on 2026-06-15**: its
         * current driver build renders the trip-offer card inside a `UberComposeView` /
         * `TextureView` with NO accessibility semantics (verified on-device — `getWindows()` returns
         * only map chrome, no fare/distance), so the node-tree reader is blind to Uber offers too and
         * [mx.kompara.ocr.UberOcrParser] reads them off the OCR stream instead.
         */
        val OCR_OWNED_PACKAGES = setOf(
            UBER_DRIVER_PACKAGE,
            DIDI_DRIVER_PACKAGE,
            INDRIVE_DRIVER_PACKAGE,
        )

        /** Armed-poll cadence for the reader-down banner; runs ONLY while the banner shows. */
        private const val FOREGROUND_POLL_MS = 3_000L

        /** Consecutive non-relevant reads before the banner signal clears (shade-pull immunity). */
        private const val FOREGROUND_CLEAR_READS = 2
    }
}
