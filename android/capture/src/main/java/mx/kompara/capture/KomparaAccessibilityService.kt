package mx.kompara.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
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

    @Inject lateinit var readiness: mx.kompara.data.service.ReaderReadinessRepository

    @Inject lateinit var screenReaderLane: ScreenReaderLane

    private val scope = pipelineScope(kotlinx.coroutines.Dispatchers.Default)

    /** Host identity makes C6 once per foreground visit, including direct Uber → DiDi switches. */
    private val foregroundOcrOwnedApp = MutableStateFlow<String?>(null)

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
            displayProvider = ::readHostDisplay,
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
        // Primary capture lane on API 30+ (B-091): silent AccessibilityService.takeScreenshot → OCR,
        // no MediaProjection consent/indicator, survives screen lock. Returns false below API 30,
        // where the MediaProjection OcrCaptureService stays the only lane (started from the Lector tab).
        serviceState.setConnected(true)
        screenReaderLane.start(scope, this)
        pollToClearForeground()
    }

    /** Keep host identity and its measured bounds fresh even on a static map. Node geometry also
     * supplies C6 while capture is stopped; no OCR or offer evaluation is started for that tab. */
    private fun pollToClearForeground() {
        scope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val root = rootInActiveWindow
                val current = try { root?.packageName?.toString() } finally {
                    if (android.os.Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") root?.recycle()
                }
                val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive != false
                if (interactive && current == null) mx.kompara.data.service.DrivingActivity.unknown()
                else mx.kompara.data.service.DrivingActivity.foreground(current.takeIf { interactive })
                foregroundOcrOwnedApp.value = current.takeIf { interactive && it in OCR_OWNED_PACKAGES }
                val host = foregroundOcrOwnedApp.value
                if (host != null && (readiness.canRead(host) || readiness.state.value.canShowStoppedTab)) {
                    val snapshot = snapshotSource.read(host, System.currentTimeMillis())
                    if (snapshot?.hostWindow == null) HostGeometryEvidence.publish(null)
                } else HostGeometryEvidence.publish(null)
                delay(750)
            }
        }
    }

    private fun readHostDisplay(): HostDisplayFrame {
        val manager = getSystemService(android.view.WindowManager::class.java)
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) manager.currentWindowMetrics.bounds else {
            @Suppress("DEPRECATION")
            val size = android.graphics.Point().also { manager.defaultDisplay.getRealSize(it) }
            android.graphics.Rect(0, 0, size.x, size.y)
        }
        val top = if (android.os.Build.VERSION.SDK_INT >= 30) {
            manager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout()).top
        } else {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id != 0) resources.getDimensionPixelSize(id) else 0
        }
        val display = mx.kompara.parsers.model.HostRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        return HostDisplayFrame(display, resources.displayMetrics.density,
            display.copy(bottom = (display.top + top.coerceAtLeast(1)).coerceAtMost(display.bottom)))
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

        mx.kompara.data.service.DrivingActivity.foreground(pkg)
        foregroundOcrOwnedApp.value = pkg.takeIf { it in OCR_OWNED_PACKAGES }
        if (!readiness.canRead(pkg)) return

        // Wall-clock, NOT event.eventTime: this timestamp flows through ScreenSnapshot into the B-039
        // ledger (OfferEntity.seenAt, TripEntity.startedAt) and is merged with the OCR path's signals
        // — which stamp System.currentTimeMillis() — into the single TripLifecycleTracker stream. Using
        // event.eventTime (SystemClock.uptimeMillis) here mixed two clock domains: it persisted trips
        // dated to 1970 and made cross-path shift/accept-window math nonsensical. The event was just
        // delivered, so submit-time wall-clock == the event's wall-clock within a millisecond.
        pipeline.submit(CaptureEvent(packageName = pkg, timestampMs = System.currentTimeMillis()))
    }

    override fun onInterrupt() {
        // No-op: nothing to interrupt in a read-only pipeline.
    }

    override fun onDestroy() {
        mx.kompara.data.service.DrivingActivity.unknown()
        serviceState.setConnected(false, ReaderEnvironment.permissionGranted(this))
        snapshotSource.detach()
        overlayPresenter.stop()
        screenReaderLane.stop()
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

    }
}
