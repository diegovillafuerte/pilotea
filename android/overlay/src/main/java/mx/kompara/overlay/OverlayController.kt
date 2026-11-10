package mx.kompara.overlay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
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
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostAppearance
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaModel
import mx.kompara.ui.components.ReglaMotion
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
    /** One absolute watchdog: soft-hides [FRAME_SILENCE_MS] after the last processed host frame,
     *  detaches [PARSE_CEILING_MS] after the last parse (see [heartbeat]). */
    private var watchdog: Job? = null
    @Volatile private var lastParsedAtMs = 0L
    @Volatile private var lastProcessedAtMs = 0L
    @Volatile private var watchdogWakeAtMs = Long.MAX_VALUE
    private var softHidden = false
    /** TEMP DIAG: the last on-screen rect verified by getLocationOnScreen at reveal. */
    private var revealedRect: HostRect? = null
    /** TEMP DIAG: decline rects latched for the current offer (count only). */
    private var latchedDeclines = 0
    /**
     * WP-AA: a reveal is the window's state, not the screen's: every chip state reaches the screen only through
     * this window's traversals. [presence] and [composedState] are what the window's composition last applied;
     * [drawnState] is the chip a revealed traversal of this window was seen drawing in full; [drawCheck] is the
     * pending deadline for the window to show what the controller holds (see [armDrawCheck]). WP-AB: a new window
     * waits for the verdict and its plate is present from its first composition, so the traversal that reveals it
     * draws the final chip and meets the deadline itself; no later frame is needed for the driver to see it.
     */
    private var presence: MutableTransitionState<Boolean>? = null
    private var composedState: ReglaModel? = null
    private var drawnState: ReglaModel? = null
    private var drawCheck: Runnable? = null
    /** The pending deadline was armed at attach and only the window's first reveal, drawn, meets it. */
    private var drawCheckAwaitsReveal = false
    private var preDrawsSinceArm = 0
    /** TEMP DIAG (debuggable builds only): the attached window's life, logged at detach. */
    private var windowLife: WindowLife? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var event: OfferEvent.Parsed? = null
    private var evaluated = false
    private var chipState by mutableStateOf<ReglaModel?>(null)
    private var chipVisible by mutableStateOf(false)
    private var geometry by mutableStateOf<HostGeometry?>(null)
    private val placementEvidence = PlacementEvidence()
    private var viewport by mutableStateOf<OverlayViewport?>(null)
    private var savedPosition by mutableStateOf<OverlayPosition?>(null)
    private var savedDocked by mutableStateOf(false)
    private var dockRequested by mutableStateOf(false)
    private val savedPositions = mutableMapOf<OverlayPositionKey, OverlayPosition?>()
    private var placement: OverlayPlacement.Displayed? = null
    private var windowOrigin = OverlayPosition(0, 0)
    @Volatile private var settingsSnapshot: mx.kompara.data.settings.Settings? = null
    private var configurationCallbacks: ComponentCallbacks? = null
    private var generation = 0
    private val diagnostics = OverlayDiagnosticThrottle()
    /** The service's foreground host, null outside a host visit; Main only. */
    private var foregroundHost: String? = null
    private var foregroundChangedAtMs = 0L
    /**
     * Whether the frame in [geometry] may vouch for touches: captured during this foreground visit of
     * its own host, and of a settled offer identity. Only a fresh frame sets it; every switch clears it.
     */
    private var touchVouched = false

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
                event?.let { show(it) }
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
                            is OfferEvent.Parsed -> heartbeat(overlayScope, parsed = true, processed = true)
                            is OfferEvent.LayoutUncertain -> heartbeat(overlayScope, parsed = false,
                                processed = it.processedFrame)
                        }
                    })
                    .onStart { emit(OverlayVisibility.Hidden) }
                else flowOf(OverlayVisibility.Hidden)
            }.onEach { render(overlayScope, it) }.launchIn(overlayScope)
        }
        // Between captures only this can revoke touch: another host's controls may already lie under the plate.
        overlayScope.launch(Dispatchers.Main.immediate) {
            foregroundOcrOwnedApp.distinctUntilChanged().collect(::onForegroundChanged)
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

    /** The ledger row's evaluation, shared with the replays (see [RowBackedVerdict]). */
    private suspend fun evaluate(event: OfferEvent.Parsed): VerdictChipState =
        RowBackedVerdict.evaluate(recorder, event).state

    private fun sessionId(event: OfferEvent.Parsed) = event.card.sessionId ?: "${event.packageName}:${event.timestampMs}"

    private suspend fun render(scope: CoroutineScope, visibility: OverlayVisibility) = withContext(Dispatchers.Main.immediate) {
        if (!readiness.state.value.canRead || !readiness.state.value.captureRunning) {
            diagnostic("READINESS", readinessDetails())
            event = null; chipVisible = false; chipState = null; detach(); return@withContext
        }
        when (visibility) {
            is OverlayVisibility.Reading -> {
                // A new session swaps content in place: the window and a still-valid position
                // stay (no blink between back-to-back offers); only this offer's placement
                // evidence (ceiling, anchors) starts over.
                evaluated = false; softHidden = false
                placementEvidence.reset()
                // This offer's frames are captured under the old plate: not touchable until seen clear.
                forcePassThrough()
                chipState = VerdictChipState.reading(settingsSnapshot?.preferredMetric ?: PreferredMetric.DEFAULT)
                chipVisible = true
                show(visibility.event)
            }
            is OverlayVisibility.Showing -> {
                // Content is swapped atomically. Its measured footprint decides whether a move is
                // needed; changing the verdict alone must not blank an already verified window.
                chipState = visibility.state.regla; evaluated = true; chipVisible = true
                val reveal = softHidden
                softHidden = false
                show(visibility.event)
                if (reveal) composeView?.invalidate() // the next pre-draw re-verifies and reveals
            }
            OverlayVisibility.SoftHidden -> {
                // The frame showed no offer: invisible and untouchable now (v3's ~1 s dismissal),
                // keeping window, verdict, mask and position until absence is corroborated.
                softHidden = true
                hideUntilSettled("SIGNATURE_ABSENT")
            }
            OverlayVisibility.Exiting -> { diagnostic("OFFER_ABSENT"); chipVisible = false }
            OverlayVisibility.Hidden -> {
                if (event != null) diagnostic("OFFER_HIDDEN")
                event = null; chipVisible = false; chipState = null; softHidden = false; detach("HIDDEN")
                placementEvidence.reset()
            }
        }
    }

    private fun onForegroundChanged(host: String?) {
        foregroundHost = host
        foregroundChangedAtMs = System.currentTimeMillis()
        touchVouched = false
        forcePassThrough()
    }

    private fun show(next: OfferEvent.Parsed) {
        if (!readiness.canRead(next.packageName) || !readiness.state.value.captureRunning) {
            diagnostic("READINESS", readinessDetails()); detach(); return
        }
        composeView?.let { view -> windowLife?.let { watchFrames(view, it, next.card.hostGeometry?.capturedAtMs) } }
        event = next
        // Set the flag, not merely refrain from clearing it: the window may be touchable right now.
        touchVouched = !next.identityUnresolved && next.packageName == foregroundHost &&
            next.timestampMs >= foregroundChangedAtMs
        if (!touchVouched) forcePassThrough()
        val v = readViewport()
        val rawGeometry = next.card.hostGeometry
        // Age never detaches a live chip; presence and the no-frame watchdog bound it.
        val problem = OverlayPositioning.geometryProblem(rawGeometry, next.packageName, v,
            System.currentTimeMillis(), checkAge = false)
        if (problem != null) {
            diagnostic(problem, geometryDetails(rawGeometry, v))
            detach(problem)
            record(OverlayPlacement.Unsupported(problem))
            return
        }
        // A changed frame may still leave the current footprint safe and in the same slot.
        // Keep it visible until measurement proves a move/resize is required. The ceiling only
        // rises within an offer, so a safe chip never moves down; an unsafe one moves up.
        val current = placement
        val evidence = placementEvidence.observe(rawGeometry!!, v, next.identityUnresolved)
        val g = evidence.geometry
        latchedDeclines = evidence.latched
        val currentIsSafe = current != null && !(evidence.dock && !current.docked) &&
            OverlayPositioning.valid(current.position, current.size, g, v, current.overMarker, current.docked, kept = true)
        if (current != null && !currentIsSafe)
            hideUntilSettled()
        geometry = g; viewport = v
        dockRequested = evidence.dock
        savedDocked = currentIsSafe && current.docked
        // While this card's upper boundary is unobserved beneath our own mask, the driver's slot
        // cannot be vouched for either (0.2.9's savedPositions.remove, without the detach).
        savedPosition = when {
            currentIsSafe -> current.position
            evidence.dock && rawGeometry.ocrEvidence?.overlayMask != null -> null
            else -> savedPositions[OverlayPositionKey(next.packageName, v.orientation)]
        }
        if (debugDiag) Log.d(TAG, "TEMP DIAG show at=${rawGeometry.capturedAtMs} rawCardTop=${rawGeometry.offerCard.top} " +
            "fare=${rawGeometry.fareBounds?.diag()} upper=${rawGeometry.ocrEvidence?.upperAnchors?.map { it.diag() }} " +
            "routes=${rawGeometry.ocrEvidence?.routeAnchors?.map { it.diag() }} controls=${rawGeometry.protectedControls.map { it.diag() }} " +
            "soft=${rawGeometry.softObstacles.map { it.diag() }} mask=${rawGeometry.ocrEvidence?.overlayMask?.diag()} " +
            "ceiling=${evidence.ceiling} dock=${evidence.dock} current=${current?.position?.bounds(current.size)?.diag()} " +
            "currentIsSafe=$currentIsSafe saved=$savedPosition savedDocked=$savedDocked " +
            "decline=${g.declineControls.diagList()} latched=${evidence.latched} " +
            "touch=${current?.let { currentIsSafe && touchable(it, g) }}")
        // WP-AB: a new window waits for the verdict, so its first composition is the chip it keeps. Attached at Reading,
        // it measured "Leyendo oferta" first (571 px on the S25) and needed later frames for the verdict; one window
        // stopped there. A window already up still swaps to Leyendo in place, so an old verdict never stays over a new
        // offer while it is evaluated.
        if (evaluated) attach()
        // A visible window that has not drawn this chip yet (a new verdict, a back-to-back offer) must draw it in time.
        if (composeView?.alpha == 1f && chipState != drawnState) armDrawCheck()
    }

    /**
     * Absolute deadlines: a Parsed refreshes both, a held host frame only the soft hide, and an
     * unknown-visibility beat (IDLE ticks with a null root, dropped screenshots: no frame) neither.
     * ONE watchdog on Main wakes at min(soft hide, ceiling), re-checks both and re-arms. Deadlines
     * only move later, so it is (re)launched only when absent or asleep past a newer soft-hide
     * deadline (after a soft hide it sleeps to the ceiling): at most once per hide, never per beat.
     */
    private fun heartbeat(scope: CoroutineScope, parsed: Boolean, processed: Boolean) {
        if (!parsed && !processed) return
        val now = SystemClock.elapsedRealtime()
        if (parsed) lastParsedAtMs = now
        lastProcessedAtMs = now
        if (watchdog?.isActive == true && watchdogWakeAtMs <= now + FRAME_SILENCE_MS) return
        watchdog?.cancel()
        watchdogWakeAtMs = now // the new loop re-checks at once; beats meanwhile need no relaunch
        watchdog = scope.launch(Dispatchers.Main) {
            while (true) {
                val at = SystemClock.elapsedRealtime()
                val ceilingAt = lastParsedAtMs + PARSE_CEILING_MS
                if (at >= ceilingAt) {
                    if (composeView != null) {
                        diagnostic("PARSE_CEILING")
                        detach("PARSE_CEILING")
                    }
                    placementEvidence.reset() // also after a C11 detach: a later Showing starts over
                    // As on confirmed absence: nothing stale is left for a configuration change to
                    // re-show; the next Parsed repopulates it.
                    event = null; chipVisible = false; chipState = null; softHidden = false
                    watchdogWakeAtMs = Long.MAX_VALUE
                    // A parse that raced this exit sees the wake reset and relaunches, or we see it.
                    if (SystemClock.elapsedRealtime() >= lastParsedAtMs + PARSE_CEILING_MS) return@launch
                    continue
                }
                val silenceAt = lastProcessedAtMs + FRAME_SILENCE_MS
                if (at >= silenceAt && composeView != null && !softHidden) {
                    softHidden = true; hideUntilSettled("FRAME_SILENCE")
                }
                watchdogWakeAtMs = if (at < silenceAt) minOf(silenceAt, ceilingAt) else ceilingAt
                // Beats run on the collector's thread: one that landed while this iteration acted saw
                // the old wake time and did not relaunch us. Recompute rather than sleep on a stale read.
                if (lastProcessedAtMs + FRAME_SILENCE_MS != silenceAt || lastParsedAtMs + PARSE_CEILING_MS != ceilingAt) continue
                delay(watchdogWakeAtMs - at)
            }
        }
    }

    private fun attach() {
        if (composeView != null) return
        val context = requireNotNull(overlayContext)
        val owner = OverlayLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner
        val life = if (debugDiag) WindowLife(SystemClock.uptimeMillis(), event?.card?.hostGeometry?.capturedAtMs) else null
        windowLife = life
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
                    SideEffect { if (composeView === this) composedState = state; life?.composed(compositionContext as? Recomposer) }
                    MeasuredVerdictChip(state, g, v, g.capturedAtMs, savedPosition, chipVisible, reduced,
                        g.appearance == HostAppearance.DARK, VerdictChipCallbacks(::onDrag) { onDragEnd() },
                        dock = dockRequested, savedDocked = savedDocked,
                        onPresence = { if (composeView === this) presence = it }) { decision ->
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
            life?.preDrawn()
            measuredPlacement?.also { measuredPlacement = null; it() }
            confirmDrawn(view, settled = settleWindow())
            true
        }
        owner.onResume()
        runCatching { windowManager.addView(view, params) }.onFailure {
            diagnostic("WINDOW_UNAVAILABLE", it.javaClass.simpleName)
            detach(); record(OverlayPlacement.Unsupported("WINDOW_UNAVAILABLE"))
        }.onSuccess {
            // WP-AB: the same deadline from attach, so a window that stops before its first reveal does not sit
            // attached and transparent for the whole offer. A window attached with the chip leaving has no reveal due.
            if (chipVisible) armDrawCheck(awaitReveal = true)
        }
    }

    private fun applyPlacement(decision: OverlayPlacement) {
        if (debugDiag) Log.d(TAG, "TEMP DIAG place at=${geometry?.capturedAtMs} " + when (decision) {
            is OverlayPlacement.Displayed -> "rule=${decision.rule} format=${decision.format} " +
                "rect=${decision.position.bounds(decision.size).diag()}"
            is OverlayPlacement.Suppressed -> "rule=C11 cause=${decision.cause}"
            is OverlayPlacement.Unsupported -> "rule=UNSUPPORTED cause=${decision.cause}"
        })
        windowLife?.let { it.placed++ }
        if (decision !is OverlayPlacement.Displayed) {
            // Only if the next measurement places without the reservation is it waived.
            val trial = if (decision is OverlayPlacement.Suppressed) placementEvidence.withoutReservation() else null
            if (trial != null) { geometry = trial; return }
            record(decision); detach("PLACEMENT_${decision.javaClass.simpleName}"); return
        }
        val g = geometry ?: run { diagnostic("UNVALIDATED_GEOMETRY"); return }
        val v = viewport ?: run { diagnostic("VIEWPORT_MISSING"); return }
        if (!readiness.canRead(g.packageName) || !readiness.state.value.captureRunning) {
            diagnostic("READINESS", readinessDetails()); detach(); return
        }
        val problem = OverlayPositioning.geometryProblem(g, event?.packageName.orEmpty(), v,
            System.currentTimeMillis(), checkAge = false)
        if (problem != null) {
            diagnostic(problem, geometryDetails(g, v))
            detach(problem); return
        }
        val view = composeView ?: return
        val params = layoutParams ?: return
        placementEvidence.commitWaiver()
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

    /**
     * Actual location/size must agree before visibility, touch or displayed history is published. True when this
     * traversal verified the visible window: laid out with no layout pending, at the placed size and position, valid.
     */
    private fun settleWindow(): Boolean {
        val view = composeView ?: return false
        val params = layoutParams ?: return false
        val shown = placement ?: return false
        // Layout-change listeners run before the root's screen transform is final. Only pre-draw
        // after a completed layout may read getLocationOnScreen or reveal the chip.
        if (!view.isLaidOut || view.isLayoutRequested) return false
        if (view.width != shown.size.width || view.height != shown.size.height) {
            val sizes = "actual=${view.width}x${view.height} expected=${shown.size.width}x${shown.size.height}"
            val p = presence
            if (p != null && !p.currentState && !p.targetState) {
                // No plate: composed hidden, or its exit has ended, so it is not laid out (MeasuredVerdictChip). The
                // window holds no chip: hidden and pass-through, with no reader mask (the exit already took its pixels
                // off the screen), until the plate returns at the placed size or the window detaches.
                hideUntilSettled("PLATE_ABSENT", sizes)
                ScreenReaderState.clearOverlayChipRect()
                return false
            }
            // A larger phrase/limit must not paint once at the old position.
            hideUntilSettled("SIZE_CHANGED", sizes)
            return false
        }
        val g = geometry ?: return false
        val v = viewport ?: return false
        val problem = OverlayPositioning.geometryProblem(g, event?.packageName.orEmpty(), v,
            System.currentTimeMillis(), checkAge = false)
        if (problem != null) {
            diagnostic(problem, geometryDetails(g, v))
            detach(problem); return false
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
            return false
        }
        if (!OverlayPositioning.valid(actual, shown.size, g, v, shown.overMarker, shown.docked, kept = true)) {
            hideUntilSettled("SUPPRESSED_OCCLUSION", geometryDetails(g, v)); return false
        }
        // A soft-hidden chip stays settled but invisible until the next parsed frame.
        if (softHidden) return false
        // 0.2.17 (user decision 2026-11-07, "only in a clear slot"): draggable only in a strict tier
        // over no host control or soft chrome; docked or over host chrome, taps pass through.
        // 0.2.18: never on Uber; on DiDi only on a fresh frame of the current foreground with a settled
        // offer identity, once this footprint was seen without our plate, and never on a waived offer.
        val touchable = touchable(shown, g)
        if (debugDiag && view.alpha != 1f) Log.d(TAG, "TEMP DIAG window reveal rect=${actual.bounds(shown.size).diag()} " +
            "touch=$touchable decline=${g.declineControls.diagList()} latched=$latchedDeclines")
        revealedRect = actual.bounds(shown.size)
        val revealing = view.alpha != 1f
        view.alpha = 1f
        // Revealed: an attach's deadline (same time) now asks what any reveal's does, counting pre-draws from here.
        if (revealing && drawCheckAwaitsReveal) { drawCheckAwaitsReveal = false; preDrawsSinceArm = 0 }
        if (revealing) armDrawCheck()
        val untouchable = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
        if (untouchable == touchable) {
            params.flags = if (touchable) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (debugDiag) Log.d(TAG, "TEMP DIAG window touch=$touchable rect=${revealedRect?.diag()}")
            // A flags-only update: it requests layout again, but this traversal already verified what it draws.
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure {
                diagnostic("WINDOW_UPDATE_FAILED", it.javaClass.simpleName); detach(); return false
            }
        }
        val rect = actual.bounds(shown.size)
        // The mask goes up with the reveal: the plate may be on screen from this frame on. It comes down with the
        // window if this frame never draws it (armDrawCheck). Displayed history waits for the drawn plate.
        ScreenReaderState.setOverlayChipRect(ScreenRect(rect.left, rect.top, rect.right, rect.bottom))
        if (drawCheck == null && drawnState == chipState) record(shown)
        return true
    }

    /**
     * Arms the deadline for this window to show what the controller now holds: the plate fully present with the
     * current chip after a reveal or a content change, or no plate after a hide. Only a later pre-draw of this
     * window can meet it ([confirmDrawn]); a window that draws no further frame is taken down when it lapses, so
     * the frame line, the reader's mask and displayed history never outlive the ink, and never keep an old verdict
     * over a new offer. The next frame attaches a fresh window. One deadline at a time: later frames never extend it.
     * [awaitReveal] (WP-AB, from attach): only the window's revealed, drawn chip meets it, not a hidden pre-draw, so a
     * window that stops before its first reveal is replaced too; its line says `revealed=false`.
     */
    private fun armDrawCheck(awaitReveal: Boolean = false) {
        val view = composeView ?: return
        if (drawCheck != null) return
        preDrawsSinceArm = 0
        drawCheckAwaitsReveal = awaitReveal
        val armed = generation
        val check = Runnable {
            if (composeView !== view || generation != armed) return@Runnable
            drawCheck = null
            drawCheckAwaitsReveal = false
            val p = presence
            val details = "revealed=${revealedRect != null} alpha=${view.alpha} presence=${p?.currentState}/${p?.isIdle} " +
                "composed=${composedState == chipState} preDraws=$preDrawsSinceArm recomposer=${recomposerState(view)}"
            diagnostic("UNDRAWN", details)
            if (debugDiag) Log.d(TAG, "TEMP DIAG window undrawn rect=${revealedRect?.diag()} $details ${windowState(view)}")
            detach("UNDRAWN")
        }
        drawCheck = check
        mainHandler.postDelayed(check, drawConfirmMs())
    }

    /**
     * Pre-draw: this traversal draws the window as the controller holds it. A visible window counts only when
     * [settled] (settleWindow verified it this traversal), not by a pending-layout check: settleWindow's own
     * flags-only touch update requests layout after verifying, and a DiDi drag repeats it on every frame.
     */
    private fun confirmDrawn(view: ComposeView, settled: Boolean) {
        if (drawCheck == null || composeView !== view) return
        preDrawsSinceArm++
        val visible = view.alpha == 1f
        if (visible) {
            val p = presence
            if (!settled || p == null || !p.isIdle || !p.currentState || composedState != chipState) return
            drawnState = chipState
        } else if (drawCheckAwaitsReveal) return
        disarmDrawCheck()
        if (!visible) return
        if (debugDiag) Log.d(TAG, "TEMP DIAG window drawn rect=${revealedRect?.diag()} preDraws=$preDrawsSinceArm")
        placement?.let(::record)
    }

    private fun disarmDrawCheck() {
        drawCheck?.let(mainHandler::removeCallbacks)
        drawCheck = null
        drawCheckAwaitsReveal = false
    }

    /** The plate's own entrance ([ReglaMotion.enterMs] at the system's animator scale) plus a margin under one capture. */
    private fun drawConfirmMs(): Long {
        val scale = overlayContext?.let { android.provider.Settings.Global.getFloat(it.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) } ?: 1f
        return DRAW_CONFIRM_MS + (ReglaMotion.enterMs(scale == 0f) * scale).toLong()
    }

    /**
     * TEMP DIAG (WP-AB): every captured frame writes new geometry, so a revealed window that still runs re-measures,
     * and pre-draws, before the next one. A captured frame that finds no pre-draw since the previous one suspects a
     * stop and probes this window, once. Reading and Showing of one capture are one frame.
     */
    private fun watchFrames(view: ComposeView, life: WindowLife, capturedAtMs: Long?) {
        if (capturedAtMs == null || capturedAtMs == life.frameAt) return
        val stopped = life.frameAt != null && view.alpha == 1f && life.preDraws == life.preDrawsAtFrame
        life.frameAt = capturedAtMs; life.preDrawsAtFrame = life.preDraws
        if (stopped && !life.probed) probeStall(view, life)
    }

    /**
     * TEMP DIAG (WP-AB): which of three things still work for a window suspected of having stopped, reported once,
     * [STALL_PROBE_MS] later. A plain main-looper message; one Choreographer frame; and a pre-draw of this window after
     * one invalidate. The probe observes: nothing re-posts, its callbacks are removed at the report, and the window's
     * state in the line is read when the stop is suspected. The invalidate is part of the measurement: it is the one
     * traversal request whose pre-draw the probe waits for, and the only thing it does to the window.
     */
    private fun probeStall(view: ComposeView, life: WindowLife) {
        life.probed = true
        val rect = revealedRect?.diag()
        val state = "recomposer=${recomposerState(view)} ${windowState(view)}"
        val preDraws = life.preDraws
        val compositions = life.compositions
        var handler = false
        var choreographer = false
        // A plain message, as most main-thread work is: a traversal barrier that is never lifted holds it back.
        val message = Runnable { handler = true }
        val frame = Choreographer.FrameCallback { choreographer = true }
        mainHandler.post(message)
        Choreographer.getInstance().postFrameCallback(frame)
        view.invalidate()
        val report = Runnable {
            mainHandler.removeCallbacks(message)
            Choreographer.getInstance().removeFrameCallback(frame)
            Log.d(TAG, "TEMP DIAG window stall rect=$rect handler=$handler choreographer=$choreographer " +
                "invalidatePreDraw=${life.preDraws > preDraws} recomposed=${life.compositions > compositions} $state " +
                "kept=${composeView === view}")
        }
        // Asynchronous, so a barrier that holds back the plain message cannot also hold back the report.
        mainHandler.sendMessageDelayed(Message.obtain(mainHandler, report).apply { isAsynchronous = true }, STALL_PROBE_MS)
    }

    /** The window's recomposer state; in debuggable builds also after Compose unset a recomposer that shut down. */
    private fun recomposerState(view: ComposeView) =
        ((view.compositionContext as? Recomposer) ?: windowLife?.recomposer)?.currentState?.value

    /** TEMP DIAG: what Android says of this window (its lifecycle owner is the attached window's). */
    private fun windowState(view: ComposeView) = "lifecycle=${lifecycleOwner?.lifecycle?.currentState} " +
        "attached=${view.isAttachedToWindow} windowVisibility=${view.windowVisibility} shown=${view.isShown}"

    private fun touchable(shown: OverlayPlacement.Displayed, g: HostGeometry) =
        OverlayPositioning.touchable(shown, g, mayTakeTouches(shown.position.bounds(shown.size)))

    private fun mayTakeTouches(footprint: HostRect) =
        touchVouched && !placementEvidence.waived && placementEvidence.seenClear(footprint)

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
        val visible = composeView?.alpha == 1f
        if (debugDiag && visible) Log.d(TAG, "TEMP DIAG window hide reason=$reason rect=${revealedRect?.diag()}")
        composeView?.alpha = 0f
        // A hide is a claim too: only a later traversal takes the plate off the screen.
        if (visible) armDrawCheck()
        forcePassThrough()
        // Keep the last visible footprint masked until the window settles or detaches. The
        // compositor may still contain those pixels while the transparent update is in flight.
    }

    private fun forcePassThrough() {
        val params = layoutParams
        val view = composeView
        if (params != null && view != null && params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(view, params) }.onFailure {
                diagnostic("WINDOW_UPDATE_FAILED", it.javaClass.simpleName)
            }
        }
    }

    private fun detach(reason: String = "") {
        generation++
        if (debugDiag && composeView != null) {
            Log.d(TAG, "TEMP DIAG window detach reason=$reason rect=${revealedRect?.diag()}")
            windowLife?.let { Log.d(TAG, "TEMP DIAG window life ${it.line(SystemClock.uptimeMillis())}") }
        }
        windowLife = null
        revealedRect = null
        composeView?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner?.onDestroy()
        composeView = null; lifecycleOwner = null; layoutParams = null; placement = null
        disarmDrawCheck(); presence = null; composedState = null; drawnState = null
        ScreenReaderState.clearOverlayChipRect()
    }

    private fun onDrag(dx: Float, dy: Float) {
        val current = placement ?: return
        val g = geometry ?: return
        val v = viewport ?: return
        val proposed = OverlayPosition(current.position.x + dx.toInt(), current.position.y + dy.toInt())
        // A footprint never seen clear would turn the window pass-through under the driver's finger.
        val resolved = OverlayPositioning.drag(current, proposed, g, v)
            .takeIf { mayTakeTouches(it.bounds(current.size)) } ?: current.position
        savedPosition = resolved // holds through remeasure, persisted only on release
        applyPlacement(current.copy(position = resolved))
    }

    private fun onDragEnd() {
        val current = placement ?: return
        val g = geometry ?: return
        val v = viewport ?: return
        val resolved = OverlayPositioning.snapToEdge(current, g, v)
        // Only a drag that ends in a valid clear slot becomes the driver's saved slot.
        if (current.docked || !OverlayPositioning.clearSlot(resolved, current.size, g, v, current.overMarker) ||
            !mayTakeTouches(resolved.bounds(current.size))) return
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
        // No age check: stoppedTabGeometry bounded every source's age, and stoppedTabWindowDecision places by g.capturedAtMs.
        if (OverlayPositioning.geometryProblem(g, host, v, System.currentTimeMillis(), checkAge = false) != null) return

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
        watchdog?.cancel(); watchdog = null; softHidden = false
        configurationCallbacks?.let { overlayContext?.unregisterComponentCallbacks(it) }
        configurationCallbacks = null
        detach(); detachBanner()
        event = null; chipState = null; chipVisible = false; geometry = null; viewport = null
        evaluated = false; lastRecord = null; savedPositions.clear(); savedPosition = null
        savedDocked = false; dockRequested = false
        placementEvidence.reset(); foregroundHost = null; touchVouched = false
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

    /** TEMP DIAG lines (geometry only, never screen text) are written in debuggable builds. */
    private val debugDiag: Boolean get() = overlayContext?.applicationInfo?.let {
        it.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 } == true

    private fun HostRect.diag() = "$left,$top,$right,$bottom"
    /** Count and rects only, e.g. `1:[479,156,993,212]`. */
    private fun List<HostRect>.diagList() = "$size:${map { it.diag() }}"

    /** Fixed reason keys keep changing frame coordinates from defeating the log limit. */
    private fun diagnostic(reason: String, details: String = "") {
        if (diagnostics.shouldLog(reason, SystemClock.elapsedRealtime()))
            Log.d(TAG, "chip hidden reason=$reason $details")
    }

    companion object {
        private const val TAG = "OverlayController"
        /** Soft-hide when no processed host frame (parsed or held) arrives for this long. */
        const val FRAME_SILENCE_MS = 3_000L
        /** Main's ceiling without a successful parse: the window detaches. */
        const val PARSE_CEILING_MS = 15_000L
        /**
         * WP-AA: after the plate's entrance, how long a revealed, re-verdicted or hidden window has to draw that
         * state before it is taken down. Below the S25's ~0.9 s capture gap less the ~70 ms to a reveal, so the next
         * frame line never claims a chip this window did not draw.
         */
        const val DRAW_CONFIRM_MS = 400L
        /** TEMP DIAG (WP-AB): how long a stall probe waits for its three checks. */
        const val STALL_PROBE_MS = 500L
    }

    /**
     * TEMP DIAG (WP-AB): one chip window's life on the uptime clock, logged at its detach, so a window that stopped
     * shows it even while its chip was visible. [frameAt] is the last captured frame shown on it and [preDrawsAtFrame]
     * its pre-draw count then ([watchFrames]).
     */
    private class WindowLife(private val attachedAtMs: Long, var frameAt: Long?) {
        var preDraws = 0
        private var lastPreDrawAtMs = -1L
        var compositions = 0
        private var lastCompositionAtMs = -1L
        var placed = 0
        var preDrawsAtFrame = 0
        var probed = false
        /** The window's recomposer, kept: Compose unsets it from the window once it shuts down. */
        var recomposer: Recomposer? = null

        fun preDrawn() { preDraws++; lastPreDrawAtMs = SystemClock.uptimeMillis() }
        fun composed(by: Recomposer?) {
            compositions++; lastCompositionAtMs = SystemClock.uptimeMillis()
            if (by != null) recomposer = by
        }

        /** `-1` for a pre-draw or composition the window never had. */
        fun line(nowMs: Long) = "ms=${nowMs - attachedAtMs} preDraws=$preDraws compositions=$compositions " +
            "lastPreDrawMsAgo=${ago(lastPreDrawAtMs, nowMs)} lastCompositionMsAgo=${ago(lastCompositionAtMs, nowMs)} placed=$placed"

        private fun ago(atMs: Long, nowMs: Long) = if (atMs < 0) -1 else nowMs - atMs
    }
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
