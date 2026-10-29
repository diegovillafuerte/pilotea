package mx.kompara.overlay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var viewport by mutableStateOf<OverlayViewport?>(null)
    private var savedPosition by mutableStateOf<OverlayPosition?>(null)
    private val savedPositions = mutableMapOf<OverlayPositionKey, OverlayPosition?>()
    private var placement: OverlayPlacement.Displayed? = null
    private var windowOrigin = OverlayPosition(0, 0)
    @Volatile private var settingsSnapshot: mx.kompara.data.settings.Settings? = null
    private var configurationCallbacks: ComponentCallbacks? = null
    private var generation = 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun start(scope: CoroutineScope, events: Flow<OfferEvent>, overlayContext: Context,
        foregroundOcrOwnedApp: Flow<String?>) {
        this.overlayContext = overlayContext
        serviceScope = scope
        configurationCallbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                // Remeasure on font/inset/orientation changes, including while no window is attached.
                detach(); detachBanner()
                event?.let { show(scope, it) }
            }
            override fun onLowMemory() = Unit
        }.also { overlayContext.registerComponentCallbacks(it) }
        scope.launch {
            settingsSnapshot = settings.settings.first()
            settings.settings.onEach { settingsSnapshot = it }.launchIn(scope)
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
                    .visibility(events.filter { readiness.canRead(it.packageName) })
                    .onStart { emit(OverlayVisibility.Hidden) }
                else flowOf(OverlayVisibility.Hidden)
            }.onEach { render(scope, it) }.launchIn(scope)
        }
        val tabGate = ReaderStoppedTabGate()
        scope.launch {
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
        return VerdictChipState.from(row.recordedMetrics(), threshold)
    }

    private fun sessionId(event: OfferEvent.Parsed) = event.card.sessionId ?: "${event.packageName}:${event.timestampMs}"
    private fun thresholdSnapshot() = settingsSnapshot?.effectiveThreshold ?: PlatformThreshold.DEFAULT

    private suspend fun render(scope: CoroutineScope, visibility: OverlayVisibility) = withContext(Dispatchers.Main.immediate) {
        if (!readiness.state.value.canRead || !readiness.state.value.captureRunning) {
            event = null; chipVisible = false; chipState = null; detach(); return@withContext
        }
        when (visibility) {
            is OverlayVisibility.Reading -> {
                detach(); evaluated = false
                chipState = VerdictChipState.reading(thresholdSnapshot(), settingsSnapshot?.preferredMetric ?: PreferredMetric.DEFAULT)
                chipVisible = true
                show(scope, visibility.event)
            }
            is OverlayVisibility.Showing -> {
                if (chipState != visibility.state.regla) {
                    hideUntilSettled(); placement = null
                }
                chipState = visibility.state.regla; evaluated = true; chipVisible = true
                show(scope, visibility.event)
            }
            OverlayVisibility.Exiting -> chipVisible = false
            OverlayVisibility.Hidden -> { event = null; chipVisible = false; chipState = null; detach() }
        }
    }

    private fun show(scope: CoroutineScope, next: OfferEvent.Parsed) {
        if (!readiness.canRead(next.packageName) || !readiness.state.value.captureRunning) { detach(); return }
        event = next
        val v = readViewport()
        val g = next.card.hostGeometry
        geometryExpiry?.cancel()
        val problem = OverlayPositioning.geometryProblem(g, next.packageName, v, System.currentTimeMillis())
        if (problem != null) {
            detach()
            record(OverlayPlacement.Unsupported(problem))
            return
        }
        if (!OverlayPositioning.sameLayout(geometry, g!!) || viewport != v) { hideUntilSettled(); placement = null }
        geometry = g; viewport = v
        savedPosition = savedPositions[OverlayPositionKey(next.packageName, v.orientation)]
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
        val view = ComposeView(context).apply {
            alpha = 0f // measure invisibly; never flash at an unsafe provisional position
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner); setViewTreeViewModelStoreOwner(owner)
            setContent {
                KomparaTheme {
                    val state = chipState ?: return@KomparaTheme
                    val g = geometry ?: return@KomparaTheme
                    val v = viewport ?: return@KomparaTheme
                    val reduced = android.provider.Settings.Global.getFloat(context.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                    MeasuredVerdictChip(state, g, v, g.capturedAtMs, savedPosition, chipVisible, reduced,
                        isSystemInDarkTheme(), VerdictChipCallbacks(::onDrag) { onDragEnd() }) { decision ->
                        // No IO or Compose mutation during measurement. Identity guards reject work
                        // posted by a disposed composition or an older frame of this offer.
                        val frameGeneration = generation
                        post {
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
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        layoutParams = params
        view.setOnApplyWindowInsetsListener { _, insets ->
            val current = readViewport(insets)
            if (viewport != current) { hideUntilSettled(); placement = null; viewport = current }
            insets
        }
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> settleWindow() }
        view.viewTreeObserver.addOnPreDrawListener { settleWindow(); true }
        owner.onResume()
        runCatching { windowManager.addView(view, params) }.onFailure {
            android.util.Log.e("OverlayController", "overlay attach failed", it)
            detach(); record(OverlayPlacement.Unsupported("WINDOW_UNAVAILABLE"))
        }
    }

    private fun applyPlacement(decision: OverlayPlacement) {
        if (decision !is OverlayPlacement.Displayed) {
            record(decision); detach(); return
        }
        val g = geometry ?: return
        val v = viewport ?: return
        if (!readiness.canRead(g.packageName) || !readiness.state.value.captureRunning) { detach(); return }
        if (OverlayPositioning.geometryProblem(g, event?.packageName.orEmpty(), v, System.currentTimeMillis()) != null) {
            detach(); return
        }
        val view = composeView ?: return
        val params = layoutParams ?: return
        val target = decision.position.inWindow(windowOrigin)
        if (placement != decision || params.x != target.x || params.y != target.y) {
            hideUntilSettled()
            placement = decision
            params.x = target.x; params.y = target.y
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure { detach() }
            return // wait for WindowManager's traversal; immediate location reads are stale
        }
        settleWindow()
    }

    /** Actual location/size must agree before visibility, touch or displayed history is published. */
    private fun settleWindow() {
        val view = composeView ?: return
        val params = layoutParams ?: return
        val shown = placement ?: return
        if (!view.isLaidOut) return
        if (view.width != shown.size.width || view.height != shown.size.height) {
            hideUntilSettled() // a larger phrase/limit must not paint once at the old position
            return
        }
        val g = geometry ?: return
        val v = viewport ?: return
        if (OverlayPositioning.geometryProblem(g, event?.packageName.orEmpty(), v, System.currentTimeMillis()) != null) {
            detach(); return
        }
        val location = IntArray(2); view.getLocationOnScreen(location)
        val actual = OverlayPosition(location[0], location[1])
        if (actual != shown.position) {
            // Only refine from a completed layout; getLocationOnScreen can lag updateViewLayout.
            if (view.isLayoutRequested) return
            windowOrigin = OverlayPosition(actual.x - params.x, actual.y - params.y)
            val target = shown.position.inWindow(windowOrigin)
            params.x = target.x; params.y = target.y
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure { detach() }
            return
        }
        if (!OverlayPositioning.valid(actual, shown.size, g, v, shown.overMarker)) { hideUntilSettled(); return }
        view.alpha = 1f
        if (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure { detach() }
        }
        val rect = actual.bounds(shown.size)
        ScreenReaderState.setOverlayChipRect(ScreenRect(rect.left, rect.top, rect.right, rect.bottom))
        record(shown)
    }

    private var lastRecord: Triple<String, OverlayPlacement, Boolean>? = null
    private fun record(decision: OverlayPlacement) {
        if (!evaluated) return // Leyendo is transitional, never evidence that the judged offer was seen.
        val current = event ?: return
        val rated = chipState?.band is ReglaBand.Rated
        val key = Triple(sessionId(current), decision, rated)
        if (lastRecord == key) return
        lastRecord = key
        val at = System.currentTimeMillis()
        serviceScope?.launch { presentations.record(key.first, decision, rated, at) }
    }

    private fun hideUntilSettled() {
        composeView?.alpha = 0f
        val params = layoutParams
        val view = composeView
        if (params != null && view != null && params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(view, params) }
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
                KomparaTheme {
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
        configurationCallbacks?.let { overlayContext?.unregisterComponentCallbacks(it) }
        configurationCallbacks = null
        detach(); detachBanner()
        event = null; chipState = null; chipVisible = false; geometry = null; viewport = null
        evaluated = false; lastRecord = null; savedPositions.clear(); savedPosition = null
        serviceScope = null; overlayContext = null
    }

}
