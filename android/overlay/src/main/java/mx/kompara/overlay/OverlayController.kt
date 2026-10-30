package mx.kompara.overlay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.HostGeometryEvidence
import mx.kompara.capture.OverlayPresenter
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.recordedMetrics
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostAppearance
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaModel
import mx.kompara.ui.theme.KomparaTheme
import javax.inject.Inject
import javax.inject.Singleton

/** One measured accessibility window. All decisions use frame-derived display pixels;
 * only WindowManager sees window-relative coordinates. */
@Singleton
class OverlayController @Inject constructor(
    private val recorder: mx.kompara.metrics.OfferRecorder,
    private val settings: SettingsRepository,
    private val prefs: OverlayPrefs,
    private val presentations: PresentationRecorder,
    private val readiness: mx.kompara.data.service.ReaderReadinessRepository,
) : OverlayPresenter {
    private var overlayContext: Context? = null
    private val windowManager get() = requireNotNull(overlayContext).getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var serviceScope: CoroutineScope? = null
    private var geometryExpiry: Job? = null
    private var event: OfferEvent.Parsed? = null
    private var evaluated = false
    private var chipState by mutableStateOf<ReglaModel?>(null)
    private var chipVisible by mutableStateOf(false)
    private var geometry by mutableStateOf<HostGeometry?>(null)
    private val placementEvidence = PlacementEvidence()
    private var viewport by mutableStateOf<OverlayViewport?>(null)
    private var savedPosition by mutableStateOf<OverlayPosition?>(null)
    private val savedPositions = mutableMapOf<OverlayPositionKey, OverlayPosition?>()
    private var placement: OverlayPlacement.Displayed? = null
    private var windowOrigin = OverlayPosition(0, 0)
    @Volatile private var settingsSnapshot: mx.kompara.data.settings.Settings? = null
    private var configurationCallbacks: ComponentCallbacks? = null
    private var generation = 0
    private val diagnostics = OverlayDiagnosticThrottle()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun start(scope: CoroutineScope, events: Flow<OfferEvent>, overlayContext: Context,
        foregroundOcrOwnedApp: Flow<String?>) {
        val job = SupervisorJob(scope.coroutineContext[Job])
        val failures = CoroutineExceptionHandler { _, error ->
            // A failed collector/persistence job must never reach Android's uncaught handler.
            // Log only the type: exception messages can contain parsed offer content.
            Log.e(TAG, "overlay stopped after ${error.javaClass.simpleName}")
            job.cancel()
            Handler(Looper.getMainLooper()).post {
                // A replacement service session may already have started while this was queued.
                if (serviceScope?.coroutineContext?.get(Job) === job) {
                    runCatching { stop() }.onFailure { Log.e(TAG, "overlay cleanup failed: ${it.javaClass.simpleName}") }
                }
            }
        }
        val overlayScope = CoroutineScope(scope.coroutineContext + job + CoroutineName("overlay") + failures)
        this.overlayContext = overlayContext
        serviceScope = overlayScope
        configurationCallbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                // Remeasure on font/inset/orientation changes, including while no window is attached.
                detach(); detachBanner()
                event?.let { show(overlayScope, it) }
            }
            override fun onLowMemory() = Unit
        }.also { overlayContext.registerComponentCallbacks(it) }
        overlayScope.launch {
            settingsSnapshot = settings.settings.first()
            settings.settings.onEach { settingsSnapshot = it }.launchIn(overlayScope)
            // Tiny scoped preference set; never block a parse on DataStore IO.
            for (host in mx.kompara.parsers.model.HostGeometryAdapter.SUPPORTED_HOSTS)
                for (orientation in HostOrientation.entries) {
                    val key = OverlayPositionKey(host, orientation)
                    savedPositions[key] = prefs.getPosition(key)
                }
            readiness.state.map { reader ->
                (reader.canRead && reader.captureRunning) to reader.apps.filterValues { it.supported }.keys
            }.distinctUntilChanged().flatMapLatest { (allowed, _) ->
                if (allowed) OverlayStateMachine(evaluate = ::evaluate)
                    .visibility(events.filter {
                        val canRead = readiness.canRead(it.packageName)
                        if (!canRead && it is OfferEvent.Parsed) diagnostic("READINESS", readinessDetails())
                        canRead
                    }.onEach {
                        when (it) {
                            is OfferEvent.NoCard -> diagnostic("NO_CARD_${it.reason}")
                            is OfferEvent.SpecDisabled -> diagnostic("SPEC_DISABLED")
                            is OfferEvent.Parsed, is OfferEvent.LayoutUncertain -> Unit
                        }
                    })
                    .onStart { emit(OverlayVisibility.Hidden) }
                else flowOf(OverlayVisibility.Hidden)
            }.onEach { render(overlayScope, it) }.launchIn(overlayScope)
        }
        val tabGate = ReaderStoppedTabGate()
        overlayScope.launch {
            combine(readiness.state, foregroundOcrOwnedApp, HostGeometryEvidence.state) { reader, host, evidence ->
                Triple(host, reader.canShowStoppedTab && reader.apps[host]?.supported == true, evidence)
            }.distinctUntilChanged().collectLatest { (host, eligible, _) ->
                val now = android.os.SystemClock.elapsedRealtime()
                val show = tabGate.visible(host, eligible, now)
                withContext(Dispatchers.Main) { if (show) attachBanner(host!!) else detachBanner() }
                if (show) {
                    // Fresh host bounds can relocate C6; they must never extend its five seconds.
                    delay(tabGate.remainingMs(android.os.SystemClock.elapsedRealtime()))
                    withContext(Dispatchers.Main) { detachBanner() }
                }
            }
        }
    }

    private suspend fun evaluate(event: OfferEvent.Parsed): VerdictChipState {
        val row = recorder.record(sessionId(event), event.card.sessionStartedAt ?: event.timestampMs,
            OfferMapping.toTripOffer(event.card), event.card.captureSource, event.card.surge, event.timestampMs)
        val evaluation = requireNotNull(row.evaluation)
        val threshold = if (evaluation.unit == PreferredMetric.IPK.name)
            thresholdSnapshot().copy(minPerKmMxn = evaluation.goal.toDouble(), redPerKmMxn = evaluation.minimum.toDouble())
        else thresholdSnapshot().copy(minPerHourMxn = evaluation.goal.toDouble(), redPerHourMxn = evaluation.minimum.toDouble())
        // Durable history keeps its last confirmed payment when current OCR confidence is lost.
        // It must not rehydrate that payment into a live frame whose instance confidence withheld it.
        if (event.card.captureSource == "OCR" && event.card.fare == null) {
            return VerdictChipState(VerdictChipState.reading(threshold, PreferredMetric.valueOf(evaluation.unit))
                .copy(band = ReglaBand.Unavailable(mx.kompara.ui.components.ReglaUnavailable.FARE)), "—")
        }
        return VerdictChipState.from(row.recordedMetrics(), threshold)
    }

    private fun sessionId(event: OfferEvent.Parsed) = event.card.sessionId ?: "${event.packageName}:${event.timestampMs}"
    private fun thresholdSnapshot() = settingsSnapshot?.effectiveThreshold ?: PlatformThreshold.DEFAULT

    private suspend fun render(scope: CoroutineScope, visibility: OverlayVisibility) = withContext(Dispatchers.Main.immediate) {
        if (!readiness.state.value.canRead || !readiness.state.value.captureRunning) {
            diagnostic("READINESS", readinessDetails())
            event = null; chipVisible = false; chipState = null; detach(); return@withContext
        }
        when (visibility) {
            is OverlayVisibility.Reading -> {
                detach(); evaluated = false
                // Reading begins a new visible offer session. The preceding card's measured
                // upper band cannot establish where this card's slide-in controls will settle.
                placementEvidence.reset(); geometry = null
                chipState = VerdictChipState.reading(thresholdSnapshot(), settingsSnapshot?.preferredMetric ?: PreferredMetric.DEFAULT)
                chipVisible = true
                show(scope, visibility.event)
            }
            is OverlayVisibility.Showing -> {
                // Content is swapped atomically. Its measured footprint decides whether a move is
                // needed; changing the verdict alone must not blank an already verified window.
                chipState = visibility.state.regla; evaluated = true; chipVisible = true
                show(scope, visibility.event)
            }
            OverlayVisibility.AwaitingUnobscuredCapture -> {
                detach()
                record(OverlayPlacement.Suppressed("UNOBSCURED_CAPTURE"))
            }
            OverlayVisibility.Exiting -> { diagnostic("OFFER_ABSENT"); chipVisible = false }
            OverlayVisibility.Hidden -> {
                if (event != null) diagnostic("OFFER_HIDDEN")
                event = null; chipVisible = false; chipState = null; detach(); placementEvidence.reset()
            }
        }
    }

    private fun show(scope: CoroutineScope, next: OfferEvent.Parsed) {
        if (!readiness.canRead(next.packageName) || !readiness.state.value.captureRunning) {
            diagnostic("READINESS", readinessDetails()); detach(); return
        }
        event = next
        val v = readViewport()
        val rawGeometry = next.card.hostGeometry
        geometryExpiry?.cancel()
        val problem = OverlayPositioning.geometryProblem(rawGeometry, next.packageName, v, System.currentTimeMillis())
        if (problem != null) {
            diagnostic(problem, geometryDetails(rawGeometry, v))
            detach()
            record(OverlayPlacement.Unsupported(problem))
            return
        }
        // A changed frame may still leave the current footprint safe and in the same slot.
        // Keep it visible until measurement proves a move/resize is required.
        val current = placement
        val evidence = placementEvidence.observe(rawGeometry!!, v, geometry)
        val g = evidence.geometry
        val needsUnobscuredCapture = evidence.needsUnobscuredCapture
        val currentIsSafe = current != null && !needsUnobscuredCapture &&
            OverlayPositioning.valid(current.position, current.size, g, v, current.overMarker)
        if (current != null && !currentIsSafe)
            hideUntilSettled()
        geometry = g; viewport = v
        // Current-frame geometry still gates safety. Small OCR envelope changes must not pull an
        // already safe same-offer chip toward the headline on each measurement. New offers detach
        // in Reading, so they cannot inherit this transient position.
        // Clear both transient and persisted preferences when the chip concealed moving anchors.
        // The regular placement path moves its mask away, exposing the host on the next capture.
        savedPosition = when {
            needsUnobscuredCapture -> {
                savedPositions.remove(OverlayPositionKey(next.packageName, v.orientation))
                null
            }
            currentIsSafe -> current!!.position
            else -> savedPositions[OverlayPositionKey(next.packageName, v.orientation)]
        }
        if (needsUnobscuredCapture) {
            detach()
            record(OverlayPlacement.Suppressed("UNOBSCURED_CAPTURE"))
            return
        }
        attach()
        geometryExpiry = scope.launch(Dispatchers.Main) {
            delay((g.capturedAtMs + OverlayPositioning.MAX_GEOMETRY_AGE_MS - System.currentTimeMillis() + 1).coerceAtLeast(1))
            detach()
            record(OverlayPlacement.Unsupported("STALE_GEOMETRY"))
        }
    }

    private fun attach() {
        if (composeView != null) return
        val context = requireNotNull(overlayContext)
        val owner = OverlayLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner
        var measuredPlacement: (() -> Unit)? = null
        val view = ComposeView(context).apply {
            alpha = 0f // measure invisibly; never flash at an unsafe provisional position
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner); setViewTreeViewModelStoreOwner(owner)
            setContent {
                KomparaTheme(darkTheme = geometry?.appearance == HostAppearance.DARK) {
                    val state = chipState ?: return@KomparaTheme
                    val g = geometry ?: return@KomparaTheme
                    val v = viewport ?: return@KomparaTheme
                    val reduced = android.provider.Settings.Global.getFloat(context.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    MeasuredVerdictChip(state, g, v, g.capturedAtMs, savedPosition, chipVisible, reduced,
                        g.appearance == HostAppearance.DARK, VerdictChipCallbacks(::onDrag) { onDragEnd() }) { decision ->
                        // Consume this measurement at pre-draw in the SAME traversal, after layout.
                        // No window/Compose mutation during measurement and no extra posted frame.
                        val frameGeneration = generation
                        measuredPlacement = {
                            if (composeView === this && frameGeneration == generation && geometry == g && chipState == state)
                                applyPlacement(decision)
                        }
                    }
                }
            }
        }
        composeView = view
        windowOrigin = OverlayPosition(0, 0)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            val v = requireNotNull(viewport)
            val g = requireNotNull(geometry)
            val saved = savedPosition
            if (saved != null) {
                gravity = Gravity.TOP or Gravity.LEFT
                x = saved.x; y = saved.y
            } else {
                // Android anchors WRAP_CONTENT after measuring it: the normal right-foot slot can
                // be verified on the first layout, without first attaching visibly at (0, 0).
                gravity = Gravity.BOTTOM or Gravity.RIGHT
                x = v.bounds.right - minOf(v.safeBounds.right, v.bounds.right - v.dp(12))
                y = v.bounds.bottom - minOf(g.offerCard.top, v.safeBounds.bottom) + v.dp(16)
            }
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        layoutParams = params
        view.setOnApplyWindowInsetsListener { _, insets ->
            val current = readViewport(insets)
            if (viewport != current) { hideUntilSettled(); viewport = current }
            insets
        }
        view.viewTreeObserver.addOnPreDrawListener {
            measuredPlacement?.also { measuredPlacement = null; it() }
            settleWindow()
            true
        }
        owner.onResume()
        runCatching { windowManager.addView(view, params) }.onFailure {
            diagnostic("WINDOW_UNAVAILABLE", it.javaClass.simpleName)
            detach(); record(OverlayPlacement.Unsupported("WINDOW_UNAVAILABLE"))
        }
    }

    private fun applyPlacement(decision: OverlayPlacement) {
        if (decision !is OverlayPlacement.Displayed) {
            record(decision); detach(); return
        }
        val g = geometry ?: run { diagnostic("UNVALIDATED_GEOMETRY"); return }
        val v = viewport ?: run { diagnostic("VIEWPORT_MISSING"); return }
        if (!readiness.canRead(g.packageName) || !readiness.state.value.captureRunning) {
            diagnostic("READINESS", readinessDetails()); detach(); return
        }
        val problem = OverlayPositioning.geometryProblem(g, event?.packageName.orEmpty(), v, System.currentTimeMillis())
        if (problem != null) {
            diagnostic(problem, geometryDetails(g, v))
            detach(); return
        }
        val view = composeView ?: return
        val params = layoutParams ?: return
        val target = windowPosition(decision.position, decision.size, params, v)
        placement = decision
        if (params.x != target.x || params.y != target.y) {
            hideUntilSettled()
            params.x = target.x; params.y = target.y
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure {
                diagnostic("WINDOW_UPDATE_FAILED", it.javaClass.simpleName); detach()
            }
            return // wait for WindowManager's traversal; immediate location reads are stale
        }
        // Verification belongs to pre-draw, never the measurement or updateViewLayout call stack.
    }

    private fun windowPosition(screen: OverlayPosition, size: ChipSize,
        params: WindowManager.LayoutParams, viewport: OverlayViewport): OverlayPosition =
        if (params.gravity == (Gravity.BOTTOM or Gravity.RIGHT))
            OverlayPosition(viewport.bounds.right - screen.x - size.width + windowOrigin.x,
                viewport.bounds.bottom - screen.y - size.height + windowOrigin.y)
        else screen.inWindow(windowOrigin)

    /** Actual location/size must agree before visibility, touch or displayed history is published. */
    private fun settleWindow() {
        val view = composeView ?: return
        val params = layoutParams ?: return
        val shown = placement ?: return
        // Layout-change listeners run before the root's screen transform is final. Only pre-draw
        // after a completed layout may read getLocationOnScreen or reveal the chip.
        if (!view.isLaidOut || view.isLayoutRequested) return
        if (view.width != shown.size.width || view.height != shown.size.height) {
            // A larger phrase/limit must not paint once at the old position.
            hideUntilSettled("SIZE_CHANGED", "actual=${view.width}x${view.height} expected=${shown.size.width}x${shown.size.height}")
            return
        }
        val g = geometry ?: return
        val v = viewport ?: return
        val problem = OverlayPositioning.geometryProblem(g, event?.packageName.orEmpty(), v, System.currentTimeMillis())
        if (problem != null) {
            diagnostic(problem, geometryDetails(g, v))
            detach(); return
        }
        val location = IntArray(2); view.getLocationOnScreen(location)
        val actual = OverlayPosition(location[0], location[1])
        if (actual != shown.position) {
            diagnostic("POSITION_PENDING", "actual=${actual.x},${actual.y} expected=${shown.position.x},${shown.position.y}")
            hideUntilSettled("POSITION_PENDING")
            windowOrigin = if (params.gravity == (Gravity.BOTTOM or Gravity.RIGHT))
                OverlayPosition(actual.x - (v.bounds.right - params.x - shown.size.width),
                    actual.y - (v.bounds.bottom - params.y - shown.size.height))
            else OverlayPosition(actual.x - params.x, actual.y - params.y)
            val target = windowPosition(shown.position, shown.size, params, v)
            params.x = target.x; params.y = target.y
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure {
                diagnostic("WINDOW_UPDATE_FAILED", it.javaClass.simpleName); detach()
            }
            return
        }
        if (!OverlayPositioning.valid(actual, shown.size, g, v, shown.overMarker)) {
            hideUntilSettled("SUPPRESSED_OCCLUSION", geometryDetails(g, v)); return
        }
        view.alpha = 1f
        if (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure {
                diagnostic("WINDOW_UPDATE_FAILED", it.javaClass.simpleName); detach(); return
            }
        }
        val rect = actual.bounds(shown.size)
        ScreenReaderState.setOverlayChipRect(ScreenRect(rect.left, rect.top, rect.right, rect.bottom))
        record(shown)
    }

    private var lastRecord: Triple<String, OverlayPlacement, Boolean>? = null
    private fun record(decision: OverlayPlacement) {
        when (decision) {
            is OverlayPlacement.Unsupported -> diagnostic(decision.cause, geometryDetails(geometry, viewport))
            is OverlayPlacement.Suppressed -> diagnostic("SUPPRESSED_${decision.cause}", geometryDetails(geometry, viewport))
            is OverlayPlacement.Displayed -> Unit
        }
        if (!evaluated) return // Leyendo is transitional, never evidence that the judged offer was seen.
        val current = event ?: return
        val rated = chipState?.band is ReglaBand.Rated
        val key = Triple(sessionId(current), decision, rated)
        if (lastRecord == key) return
        lastRecord = key
        val at = System.currentTimeMillis()
        serviceScope?.launch { presentations.record(key.first, decision, rated, at) }
    }

    private fun hideUntilSettled(reason: String = "LAYOUT_PENDING", details: String = "") {
        if (event != null) diagnostic(reason, details)
        composeView?.alpha = 0f
        val params = layoutParams
        val view = composeView
        if (params != null && view != null && params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure {
                diagnostic("WINDOW_UPDATE_FAILED", it.javaClass.simpleName)
            }
        }
        // Keep the last visible footprint masked until the window settles or detaches. The
        // compositor may still contain those pixels while the transparent update is in flight.
    }

    private fun detach() {
        generation++
        geometryExpiry?.cancel(); geometryExpiry = null
        composeView?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner?.onDestroy()
        composeView = null; lifecycleOwner = null; layoutParams = null; placement = null
        ScreenReaderState.clearOverlayChipRect()
    }

    private fun onDrag(dx: Float, dy: Float) {
        val current = placement ?: return
        val g = geometry ?: return
        val v = viewport ?: return
        val proposed = OverlayPosition(current.position.x + dx.toInt(), current.position.y + dy.toInt())
        val resolved = OverlayPositioning.drag(current, proposed, g, v)
        savedPosition = resolved // holds through remeasure, persisted only on release
        applyPlacement(current.copy(position = resolved))
    }

    private fun onDragEnd() {
        val current = placement ?: return
        val g = geometry ?: return
        val v = viewport ?: return
        val resolved = OverlayPositioning.snapToEdge(current, g, v)
        val key = OverlayPositionKey(g.packageName, v.orientation)
        savedPositions[key] = resolved; savedPosition = resolved
        applyPlacement(current.copy(position = resolved))
        serviceScope?.launch { prefs.savePosition(key, resolved) }
    }

    private fun readViewport(applied: WindowInsets? = composeView?.rootWindowInsets): OverlayViewport {
        val context = requireNotNull(overlayContext)
        val bounds = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds else {
            @Suppress("DEPRECATION")
            val size = android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }
            android.graphics.Rect(0, 0, size.x, size.y)
        }
        val insets = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.windowInsets else applied
        val system = if (Build.VERSION.SDK_INT >= 30 && insets != null) {
            insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                .let { OverlayInsets(it.left, it.top, it.right, it.bottom) }
        } else if (insets != null) {
            @Suppress("DEPRECATION")
            OverlayInsets(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        } else OverlayInsets()
        val back = if (Build.VERSION.SDK_INT >= 29 && insets != null) {
            @Suppress("DEPRECATION")
            insets.systemGestureInsets.let { OverlayInsets(left = it.left, right = it.right) }
        } else OverlayInsets()
        return OverlayViewport(HostRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
            context.resources.displayMetrics.density,
            if (bounds.width() > bounds.height()) HostOrientation.LANDSCAPE else HostOrientation.PORTRAIT, system, back)
    }
    // ─── Reader-down banner (B-078) ──────────────────────────────────────────────────────────────

    private var bannerView: ComposeView? = null
    private var bannerOwner: OverlayLifecycleOwner? = null
    private var bannerHost: String? = null
    private var bannerGeometry: HostGeometry? = null

    /** Add the reader-down banner window if not already attached. Main-thread only. Idempotent. */
    private fun attachBanner(host: String) {
        val context = overlayContext ?: return
        val v = readViewport()
        val now = System.currentTimeMillis()
        val fresh = stoppedTabGeometry(HostGeometryEvidence.current(host, now)?.idleGeometry(),
            geometry, bannerGeometry, host, v, now)
        if (bannerView != null) {
            if (bannerHost == host) {
                if (fresh == null) {
                    detachBanner(); return
                }
                bannerGeometry = fresh
                bannerView?.requestLayout()
                return
            }
            // A new foreground host must earn its own geometry; never retain the old host's tab.
            detachBanner()
        }
        val g = fresh ?: return
        if (OverlayPositioning.geometryProblem(g, host, v, System.currentTimeMillis()) != null) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        bannerOwner = owner
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                KomparaTheme(darkTheme = g.appearance == HostAppearance.DARK) {
                    ReaderStoppedTab()
                }
            }
        }

        // Measure first. C6's foot follows the same safe slot as la regla; no drag or touch region.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }

        bannerView = view
        bannerHost = host
        bannerGeometry = g
        android.util.Log.d("OverlayController", "geometry host=$host source=${g.source} idle=${g.isIdleHost}")
        view.alpha = 0f
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        var lastMoveFrom: OverlayPosition? = null
        fun settleBanner() {
            if (bannerView !== view || view.width == 0 || view.height == 0) return
            if (!readiness.state.value.canShowStoppedTab) { detachBanner(); return }
            val location = IntArray(2).also(view::getLocationOnScreen)
            val actual = OverlayPosition(location[0], location[1])
            if (lastMoveFrom != actual) lastMoveFrom = null
            val decision = stoppedTabWindowDecision(bannerGeometry ?: g, host, readViewport(view.rootWindowInsets),
                ChipSize(view.width, view.height), actual, OverlayPosition(params.x, params.y),
                view.isLayoutRequested, lastMoveFrom)
            view.alpha = if (decision == StoppedTabWindowDecision.Show) 1f else 0f
            view.importantForAccessibility = if (decision == StoppedTabWindowDecision.Show)
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            when (decision) {
                StoppedTabWindowDecision.Hide -> detachBanner()
                is StoppedTabWindowDecision.Move -> {
                    lastMoveFrom = actual
                    params.x = decision.windowPosition.x; params.y = decision.windowPosition.y
                    runCatching { windowManager.updateViewLayout(view, params) }.onFailure { detachBanner() }
                }
                else -> Unit
            }
        }
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> settleBanner() }
        view.viewTreeObserver.addOnPreDrawListener { settleBanner(); true }
        view.setOnApplyWindowInsetsListener { _, insets ->
            view.alpha = 0f
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            view.post { settleBanner() }
            insets
        }
        owner.onResume()
        // Same crash posture as the chip: an attach failure must never take the service down.
        runCatching { windowManager.addView(view, params) }.onFailure { error ->
            android.util.Log.e("OverlayController", "banner attach failed", error)
            owner.onDestroy()
            bannerView = null
            bannerOwner = null
            bannerHost = null
        }
    }

    /** Remove the banner window if attached. Main-thread only. Idempotent. */
    private fun detachBanner() {
        bannerHost = null
        bannerGeometry = null
        val view = bannerView ?: return
        runCatching { windowManager.removeView(view) }
        bannerOwner?.onDestroy()
        bannerView = null
        bannerOwner = null
    }

    override fun stop() {
        serviceScope?.cancel()
        configurationCallbacks?.let { overlayContext?.unregisterComponentCallbacks(it) }
        configurationCallbacks = null
        detach(); detachBanner()
        event = null; chipState = null; chipVisible = false; geometry = null; viewport = null
        evaluated = false; lastRecord = null; savedPositions.clear(); savedPosition = null
        placementEvidence.reset()
        serviceScope = null; overlayContext = null
    }

    private fun readinessDetails(): String = readiness.state.value.let {
        "canRead=${it.canRead} captureRunning=${it.captureRunning}"
    }

    private fun geometryDetails(g: HostGeometry?, v: OverlayViewport?): String =
        "ageMs=${g?.let { System.currentTimeMillis() - it.capturedAtMs }} maxAgeMs=${OverlayPositioning.MAX_GEOMETRY_AGE_MS}" +
            " display=${g?.displayBounds} viewport=${v?.bounds} orientation=${g?.orientation}/${v?.orientation}" +
            " card=${g?.offerCard} chrome=${g?.topChrome} controls=${g?.protectedControls}" +
            " markers=${g?.markers?.map { it.outerBounds }}"

    /** Fixed reason keys keep changing frame coordinates from defeating the log limit. */
    private fun diagnostic(reason: String, details: String = "") {
        if (diagnostics.shouldLog(reason, SystemClock.elapsedRealtime()))
            Log.d(TAG, "chip hidden reason=$reason $details")
    }

    companion object { private const val TAG = "OverlayController" }
}

internal class OverlayDiagnosticThrottle(private val intervalMs: Long = 5_000L) {
    private val lastAt = mutableMapOf<String, Long>()

    @Synchronized fun shouldLog(reason: String, nowMs: Long): Boolean {
        val previous = lastAt[reason]
        if (previous != null && nowMs - previous in 0 until intervalMs) return false
        lastAt[reason] = nowMs
        return true
    }
}
