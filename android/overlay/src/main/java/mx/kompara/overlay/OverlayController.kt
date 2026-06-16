package mx.kompara.overlay

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OverlayPresenter
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
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
     * The current offer's fare/leg region the chip must not cover (from `OfferCard.contentBounds`),
     * captured in [evaluate] and read once per show-session in [startPosition] so the chip is lifted
     * off the fare when the driver has parked it there — the fix for the OCR-occlusion blink. Null
     * when bounds are unavailable (node path) → positioning is unchanged. @Volatile: written on the
     * offer flow, read on the main thread at attach.
     */
    @Volatile private var currentContentRect: ContentRect? = null

    /**
     * Vertical offset (px) between the overlay window's `params.y` and the chip's ACTUAL on-screen y
     * (= the status-bar inset: the window is laid out below the status bar, but the OCR capture is
     * full-screen with y=0 at the physical top). So `screenY = params.y + chipScreenOffsetY`. The
     * fare's [currentContentRect] is in capture/screen space, so the positioning math must convert it
     * into params space (subtract this) before constraining `params.y`; without it the chip is pinned
     * `chipScreenOffsetY` px too low and lands on the fare when dragged down (on-device 2026-06-15).
     * Seeded from the status-bar height (correct before first layout), then refined from a settled
     * getLocationOnScreen. @Volatile: written on layout/drag (main), read on the offer flow.
     */
    @Volatile private var chipScreenOffsetY = 0

    /**
     * Begin driving the overlay from [events]. Collected on [scope] (the service's scope). Each
     * `Parsed` evaluates the offer and shows the chip; `NoCard` hides it after a short grace.
     */
    override fun start(
        scope: CoroutineScope,
        events: Flow<OfferEvent>,
        overlayContext: Context,
        foregroundOcrOwnedApp: Flow<Boolean>,
    ) {
        this.overlayContext = overlayContext
        // Seed the params↔screen offset from the status-bar height so the very first attach (e.g. a
        // persisted low position) already converts the fare rect correctly, before any layout pass
        // can refine it from getLocationOnScreen.
        chipScreenOffsetY = statusBarHeightPx(overlayContext)
        scope.launch {
            // Prime the snapshots BEFORE arming the offer pipeline, then keep them warm for the
            // service's lifetime. evaluate() is synchronous and runs before render()'s refresh,
            // so arming the pipeline first could grade the very first offer with default floors,
            // default metric (B-079), or zero costs (B-032 "changes reflect instantly"). The
            // offer sources re-emit continuously (a11y ticks / OCR frames), so the one-read
            // delay cannot cost more than a beat — unlike a wrong verdict, which costs trust.
            settingsSnapshot = settings.settings.first()
            costProfileSnapshot = costProfiles.get()
            settings.settings.onEach { settingsSnapshot = it }.launchIn(scope)
            costProfiles.profile.onEach { costProfileSnapshot = it }.launchIn(scope)
            val machine = OverlayStateMachine(evaluate = ::evaluate)
            machine.visibility(events)
                .onEach { visibility -> render(scope, visibility) }
                .launchIn(scope)
        }
        // Reader-down banner (B-078): visible exactly while the driver sits in an OCR-owned app
        // with the screen reader dead — the silent-miss state the stopped notification can't cover
        // (it fires at lock time, when nobody is looking). Gated on hasRunThisSession: the banner
        // is a recovery affordance, never an onboarding nag for drivers who haven't enabled the
        // reader. Tap relaunches the consent flow. Independent of the chip pipeline above, so it
        // needs none of the snapshot priming.
        combine(
            ScreenReaderState.running,
            ScreenReaderState.hasRunThisSession,
            foregroundOcrOwnedApp,
        ) { running, hasRun, inApp ->
            hasRun && !running && inApp
        }
            .distinctUntilChanged()
            .onEach { show ->
                withContext(Dispatchers.Main) { if (show) attachBanner() else detachBanner() }
            }
            .launchIn(scope)
    }

    /** Evaluate a parsed offer into full metrics, applying the driver's cost profile + threshold. */
    private fun evaluate(event: OfferEvent.Parsed): OfferMetrics {
        // Remember where the offer's fare/legs sit so attach() can lift the chip off them.
        currentContentRect = event.card.contentBounds?.let {
            ContentRect(it.left, it.top, it.right, it.bottom)
        }
        val tripOffer = OfferMapping.toTripOffer(event.card)
        val costProfile: CostProfile = CostProfileMapper.toCostProfileOrZero(costProfileSnapshot)
        currentThreshold = thresholdSnapshot()
        return engine.evaluate(tripOffer, costProfile, currentThreshold, preferredMetricSnapshot())
    }

    // Kept current by [start]'s collectors (and refreshed again in [render] as a belt-and-braces
    // re-read), so the synchronous evaluate() path always sees the latest persisted values.
    @Volatile private var costProfileSnapshot: mx.kompara.data.db.entity.CostProfileEntity? = null
    @Volatile private var settingsSnapshot: mx.kompara.data.settings.Settings? = null

    private fun thresholdSnapshot(): PlatformThreshold =
        settingsSnapshot?.effectiveThreshold ?: PlatformThreshold.DEFAULT

    private fun preferredMetricSnapshot(): PreferredMetric =
        settingsSnapshot?.preferredMetric ?: PreferredMetric.DEFAULT

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
        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                // Publish the chip's rect so :ocr masks it out of the capture (the $0 self-capture
                // fix). The view isn't measured yet at addView, so republish once it lays out and on
                // any later size change; drag handlers republish on move.
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publishChipRect() }
                publishChipRect()
            }
            .onFailure { error ->
                android.util.Log.e("OverlayController", "overlay attach failed", error)
                owner.onDestroy()
                composeView = null
                lifecycleOwner = null
                layoutParams = null
            }
    }

    /**
     * Publish the chip's current SCREEN-space rect to [ScreenReaderState] so the OCR capture service
     * masks the chip's own pixels out of each frame — without it the chip's "MXN…"/"$…" text is parsed
     * as the offer fare (the $0 self-capture loop). The window's `params.y` is offset DOWN from the
     * capture's full-screen origin by the status-bar inset, so the screen rect is `params + offset`
     * ([chipScreenOffsetY]). We also refine that offset here from a SETTLED getLocationOnScreen
     * (reliable post-layout; it reads transiently as 0 right after a move, which we ignore).
     */
    private fun publishChipRect() {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val w = view.width.takeIf { it > 0 } ?: chipWidthPx
        val h = view.height.takeIf { it > 0 } ?: chipHeightPx
        // Refine the offset from the actual on-screen location, but only a settled, sane reading (the
        // value jumps to 0 for a frame right after updateViewLayout, before the window settles).
        if (view.isLaidOut && view.width > 0) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val measured = loc[1] - params.y
            if (measured in 1..MAX_SANE_TOP_INSET_PX) chipScreenOffsetY = measured
        }
        val top = params.y + chipScreenOffsetY
        ScreenReaderState.setOverlayChipRect(ScreenRect(params.x, top, params.x + w, top + h))
    }

    /** Status-bar inset (px) — the params↔screen vertical offset, available before the first layout. */
    private fun statusBarHeightPx(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    /**
     * [currentContentRect] (the fare/leg region) is in capture/screen space, but the positioning math
     * constrains `params.y`, which is offset down by [chipScreenOffsetY]. Convert the rect into params
     * space so "keep the chip above the fare" actually keeps its ON-SCREEN footprint above the fare.
     */
    private fun contentRectInParamsSpace(): ContentRect? {
        val c = currentContentRect ?: return null
        val o = chipScreenOffsetY
        return ContentRect(c.left, c.top - o, c.right, c.bottom - o)
    }

    /** Remove the overlay window if attached. Main-thread only. Idempotent. */
    private fun detach() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        layoutParams = null
        ScreenReaderState.clearOverlayChipRect()
    }

    // ─── Reader-down banner (B-078) ──────────────────────────────────────────────────────────────

    private var bannerView: ComposeView? = null
    private var bannerOwner: OverlayLifecycleOwner? = null

    /** Add the reader-down banner window if not already attached. Main-thread only. Idempotent. */
    private fun attachBanner() {
        if (bannerView != null) return
        val context = overlayContext ?: return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        bannerOwner = owner
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                KomparaTheme {
                    ReaderDownBannerUi(onTap = ::relaunchConsent)
                }
            }
        }

        // Top-center, clear of both the chip's top-right slot and the host's bottom Aceptar zone.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (BANNER_TOP_OFFSET_DP * context.resources.displayMetrics.density).toInt()
        }

        bannerView = view
        owner.onResume()
        // Same crash posture as the chip: an attach failure must never take the service down.
        runCatching { windowManager.addView(view, params) }.onFailure { error ->
            android.util.Log.e("OverlayController", "banner attach failed", error)
            owner.onDestroy()
            bannerView = null
            bannerOwner = null
        }
    }

    /** Remove the banner window if attached. Main-thread only. Idempotent. */
    private fun detachBanner() {
        val view = bannerView ?: return
        runCatching { windowManager.removeView(view) }
        bannerOwner?.onDestroy()
        bannerView = null
        bannerOwner = null
    }

    /**
     * Relaunch the screen-capture consent flow from the banner tap. Allowed from the background:
     * the host process runs a system-bound accessibility service, which exempts it from
     * background-activity-launch restrictions.
     */
    private fun relaunchConsent() {
        val context = overlayContext ?: return
        // Same crash posture as the window ops: a launch failure must never take the service down.
        runCatching {
            context.startActivity(
                Intent(ScreenReaderState.ACTION_START)
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { android.util.Log.e("OverlayController", "consent relaunch failed", it) }
    }

    /** Tear everything down on service destroy. Safe to call when nothing is attached. */
    override fun stop() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        layoutParams = null
        chipState = null
        ScreenReaderState.clearOverlayChipRect()
        bannerView?.let { runCatching { windowManager.removeView(it) } }
        bannerOwner?.onDestroy()
        bannerView = null
        bannerOwner = null
        overlayContext = null
    }

    private fun onDrag(dxPx: Float, dyPx: Float) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val sw = screenWidth()
        val sh = screenHeight()
        val w = view.width.takeIf { it > 0 } ?: chipWidthPx
        val h = view.height.takeIf { it > 0 } ?: chipHeightPx
        val proposed = OverlayPosition(params.x + dxPx.toInt(), params.y + dyPx.toInt())
        val clamped = OverlayPositioning.clamp(proposed, sw, sh, w, h)
        // The chip only shows while an offer is on screen, so every drag happens over a live fare:
        // pin it above the offer content even mid-drag so a downward drag can't park it back on the
        // card and re-occlude the fare from the OCR (the flicker). No-op when there's no content rect.
        val resolved = OverlayPositioning.avoid(clamped, contentRectInParamsSpace(), sw, sh, w, h)
        params.x = resolved.x
        params.y = resolved.y
        runCatching { windowManager.updateViewLayout(view, params) }
        publishChipRect() // keep the OCR mask aligned with the chip as it moves
    }

    private suspend fun onDragEnd() {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val sw = screenWidth()
        val sh = screenHeight()
        val w = view.width.takeIf { it > 0 } ?: chipWidthPx
        val h = view.height.takeIf { it > 0 } ?: chipHeightPx
        val snapped = OverlayPositioning.snapToEdge(OverlayPosition(params.x, params.y), sw, sh, w, h)
        // Same guard on release: snap to the edge, then keep it above the fare before persisting, so
        // the saved slot the next offer restores is never one that sits on the card.
        val resolved = OverlayPositioning.avoid(snapped, contentRectInParamsSpace(), sw, sh, w, h)
        withContext(Dispatchers.Main) {
            params.x = resolved.x
            params.y = resolved.y
            runCatching { windowManager.updateViewLayout(view, params) }
            publishChipRect() // re-align the OCR mask to the snapped resting position
        }
        prefs.savePosition(resolved)
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

    /** Restored position if the driver moved the chip, else the default top-right slot — then nudged
     *  off the offer's fare so the chip never occludes it from the screen-capture OCR (the blink). */
    private fun startPosition(): OverlayPosition {
        val saved = persistedPositionSnapshot
        val w = chipWidthPx
        val h = chipHeightPx
        val base = OverlayPositioning.clamp(
            saved ?: OverlayPositioning.defaultPosition(screenWidth(), screenHeight(), w, h),
            screenWidth(), screenHeight(), w, h,
        )
        // avoid() is a no-op when bounds are absent or the chip already sits entirely above the fare,
        // so the collision-free default slot and any high drag are preserved; it lifts the chip above
        // the fare whenever it would otherwise overlap or sit in the card body below it (below blinks
        // — the chip occludes a leg the OCR must re-read). Computed once per attach (idempotent) ⇒ no
        // per-frame churn, and NOT persisted ⇒ a later above-the-fare offer keeps the driver's slot.
        return OverlayPositioning.avoid(base, contentRectInParamsSpace(), screenWidth(), screenHeight(), w, h)
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
        // Fallback chip footprint (px) used for positioning before the view has measured. Slight
        // OVER-estimates of the real measured chip (~530x340 with the brand strip, S25) so the
        // pre-measure default stays on-screen and avoid()'s lift clears the fare even before layout.
        const val DEFAULT_CHIP_WIDTH_PX = 540
        const val DEFAULT_CHIP_HEIGHT_PX = 360

        /** Upper bound (px) for a believable params↔screen offset (status bar / cutout), so a transient
         *  getLocationOnScreen glitch can't poison [chipScreenOffsetY] with an absurd value. */
        private const val MAX_SANE_TOP_INSET_PX = 400

        /** Reader-down banner offset below the top edge (dp) — clears the host's status/app bar. */
        private const val BANNER_TOP_OFFSET_DP = 56
    }
}
