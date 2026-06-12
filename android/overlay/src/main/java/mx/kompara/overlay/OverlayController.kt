package mx.kompara.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OverlayPresenter
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.CostProfile
import mx.kompara.metrics.CostProfileMapper
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferMetrics
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.ui.theme.KomparaTheme
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single `TYPE_ACCESSIBILITY_OVERLAY` window that hosts the Compose verdict chip, and
 * drives it from the [OfferEvent] stream the accessibility service collects.
 *
 * The service is the *only* place allowed to attach this window (it has the accessibility window
 * token), so the service calls [start] from its scope and [stop] on destroy; the controller never
 * touches the window on its own.
 *
 * ## Lifecycle & leaks
 * Attach/detach are idempotent and always run on the main thread. A fresh [OverlayLifecycleOwner] is
 * built per attach so a service restart can never resurrect a DESTROYED lifecycle, and detach moves
 * it to DESTROYED + removes the view so the composition disposes cleanly (acceptance criterion: no
 * leaks on service restart).
 *
 * ## Positioning
 * Window position is `Gravity.TOP or START` with explicit (x, y) so our pure
 * [OverlayPositioning] math owns clamping and snap-to-edge; the bottom safe zone keeps the chip off
 * the host's Accept button. Dragging updates the LayoutParams live; release snaps to the nearer
 * edge and persists via [OverlayPrefs].
 */
@Singleton
class OverlayController @Inject constructor(
    private val engine: NetProfitEngine,
    private val costProfiles: CostProfileRepository,
    private val settings: SettingsRepository,
    private val prefs: OverlayPrefs,
) : OverlayPresenter {
    /**
     * The accessibility service, set in [start]. A `TYPE_ACCESSIBILITY_OVERLAY` window can only be
     * added through the service's own WindowManager (it carries the accessibility window token), so
     * everything window-related resolves from here — never the application context.
     */
    private var overlayContext: Context? = null

    private val windowManager: WindowManager
        get() = requireNotNull(overlayContext) { "OverlayController.start() not called yet" }
            .getSystemService(WindowManager::class.java)

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** Live state the composition observes; updated as offers arrive. */
    private var chipState by mutableStateOf<VerdictChipState?>(null)
    private var currentThreshold by mutableStateOf(PlatformThreshold.DEFAULT)

    /** Measured once per attach so the pure positioning math has concrete pixel bounds. */
    private var chipWidthPx: Int = DEFAULT_CHIP_WIDTH_PX
    private var chipHeightPx: Int = DEFAULT_CHIP_HEIGHT_PX

    /**
     * Begin driving the overlay from [events]. Collected on [scope] (the service's scope). Each
     * `Parsed` evaluates the offer and shows the chip; `NoCard` hides it after a short grace.
     */
    override fun start(scope: CoroutineScope, events: Flow<OfferEvent>, overlayContext: Context) {
        this.overlayContext = overlayContext
        val machine = OverlayStateMachine(evaluate = ::evaluate)
        machine.visibility(events)
            .onEach { visibility -> render(scope, visibility) }
            .launchIn(scope)
    }

    /** Evaluate a parsed offer into full metrics, applying the driver's cost profile + threshold. */
    private fun evaluate(event: OfferEvent.Parsed): OfferMetrics {
        val tripOffer = OfferMapping.toTripOffer(event.card)
        val costProfile: CostProfile = CostProfileMapper.toCostProfileOrZero(costProfileSnapshot)
        currentThreshold = thresholdSnapshot()
        return engine.evaluate(tripOffer, costProfile, currentThreshold)
    }

    // Snapshots refreshed by [start]'s collectors would be ideal; for the per-offer path we read the
    // latest persisted values lazily and cache them so evaluate() stays synchronous.
    @Volatile private var costProfileSnapshot: mx.kompara.data.db.entity.CostProfileEntity? = null
    @Volatile private var settingsSnapshot: mx.kompara.data.settings.Settings? = null

    private fun thresholdSnapshot(): PlatformThreshold =
        settingsSnapshot?.effectiveThreshold ?: PlatformThreshold.DEFAULT

    private suspend fun render(scope: CoroutineScope, visibility: OverlayVisibility) {
        // Refresh persisted snapshots so the next evaluate() and the threshold sheet are current,
        // and warm the persisted drag position so the first attach lands where the driver left it.
        costProfileSnapshot = costProfiles.get()
        settingsSnapshot = settings.settings.first()
        persistedPositionSnapshot = prefs.getPosition()
        withContext(Dispatchers.Main) {
            when (visibility) {
                is OverlayVisibility.Showing -> {
                    chipState = VerdictChipState.from(visibility.metrics)
                    currentThreshold = thresholdSnapshot()
                    attach(scope)
                }
                OverlayVisibility.Hidden -> detach()
            }
        }
    }

    /** Build and add the overlay window if not already attached. Main-thread only. Idempotent. */
    private fun attach(scope: CoroutineScope) {
        if (composeView != null) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        lifecycleOwner = owner

        val view = ComposeView(requireNotNull(overlayContext)).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                KomparaTheme {
                    val s = chipState ?: return@KomparaTheme
                    VerdictChipUi(
                        state = s,
                        threshold = currentThreshold,
                        callbacks = VerdictChipCallbacks(
                            onDrag = { dx, dy -> onDrag(dx, dy) },
                            onDragEnd = { scope.launch { onDragEnd() } },
                            onThresholdChange = { updated -> scope.launch { onThresholdChange(updated) } },
                        ),
                    )
                }
            }
        }

        val params = newLayoutParams()
        layoutParams = params
        composeView = view
        owner.onResume()
        // Never let an overlay attach failure crash the accessibility service process — that would
        // take the reader and fixture recording down with it. Log, reset, and let the next offer
        // retry. (The TYPE_ACCESSIBILITY_OVERLAY token comes from the service context set in start.)
        runCatching { windowManager.addView(view, params) }.onFailure { error ->
            android.util.Log.e("OverlayController", "overlay attach failed", error)
            owner.onDestroy()
            composeView = null
            lifecycleOwner = null
            layoutParams = null
        }
    }

    /** Remove the overlay window if attached. Main-thread only. Idempotent. */
    private fun detach() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        layoutParams = null
    }

    /** Tear everything down on service destroy. Safe to call when nothing is attached. */
    override fun stop() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        layoutParams = null
        chipState = null
        overlayContext = null
    }

    private fun onDrag(dxPx: Float, dyPx: Float) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val proposed = OverlayPosition(params.x + dxPx.toInt(), params.y + dyPx.toInt())
        val clamped = OverlayPositioning.clamp(
            proposed,
            screenWidth = screenWidth(),
            screenHeight = screenHeight(),
            chipWidth = view.width.takeIf { it > 0 } ?: chipWidthPx,
            chipHeight = view.height.takeIf { it > 0 } ?: chipHeightPx,
        )
        params.x = clamped.x
        params.y = clamped.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private suspend fun onDragEnd() {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val snapped = OverlayPositioning.snapToEdge(
            OverlayPosition(params.x, params.y),
            screenWidth = screenWidth(),
            screenHeight = screenHeight(),
            chipWidth = view.width.takeIf { it > 0 } ?: chipWidthPx,
            chipHeight = view.height.takeIf { it > 0 } ?: chipHeightPx,
        )
        withContext(Dispatchers.Main) {
            params.x = snapped.x
            params.y = snapped.y
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        prefs.savePosition(snapped)
    }

    private suspend fun onThresholdChange(updated: PlatformThreshold) {
        currentThreshold = updated
        // Persist via the shared SettingsRepository so the engine + Ajustes share one floor (B-076:
        // a single semáforo for every platform).
        settings.setThreshold(updated)
    }

    private fun newLayoutParams(): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_FOCUSABLE: never steal IME/back focus from the host. The window only receives
            // touches inside its own bounds (WRAP_CONTENT sizing), so touches over the host app —
            // including the Accept button below the safe zone — pass straight through.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        val start = startPosition()
        params.x = start.x
        params.y = start.y
        return params
    }

    /** Restored position if the driver moved the chip, else the default top-right slot. */
    private fun startPosition(): OverlayPosition {
        val saved = persistedPositionSnapshot
        val w = chipWidthPx
        val h = chipHeightPx
        val base = saved ?: OverlayPositioning.defaultPosition(screenWidth(), screenHeight(), w, h)
        return OverlayPositioning.clamp(base, screenWidth(), screenHeight(), w, h)
    }

    @Volatile private var persistedPositionSnapshot: OverlayPosition? = null

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }.x
        }

    private fun screenHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }.y
        }

    companion object {
        /** Fallback chip footprint (px) used for positioning before the view has measured. */
        const val DEFAULT_CHIP_WIDTH_PX = 360
        const val DEFAULT_CHIP_HEIGHT_PX = 280
    }
}
