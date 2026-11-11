package mx.kompara.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostOcrGeometryEvidence
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.OfferCard
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaModel
import mx.kompara.ui.components.ReglaPosition
import mx.kompara.ui.components.ReglaUnavailable
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowViewGroup
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * WP-AA (S25, 2026-11-10): on 2 of 5 offers the log said `window reveal` and every frame line said
 * `chip=<rect>`, yet one screenshot a second showed nothing drawn there for the whole offer, and after
 * the reveal no `place` line followed any `show`. These replays drive the real controller, its real
 * Compose window and the S25 frame sequences (numbers from the device log) and check what a screenshot
 * checks: whenever a frame line would say `chip=<rect>`, the window's last drawn frame paints the plate
 * there, fully present, with the controller's current verdict.
 *
 * Why the window stops producing frames on the phone is not known (see docs/v4-progress.md, WP-AA).
 * The replays stop it at the reveal in the two ways the phone's lines allow: the window's composition
 * gets no further frame (nothing recomposes: 35d9 never measured its rated pill), or the window gets no
 * further traversal (nothing is laid out or drawn). Either way the claim must not outlive the ink.
 *
 * WP-AB: a new window now waits for the verdict and its plate is present from its first composition, so
 * the traversal that reveals it draws the final chip. A window that stops right after its reveal (3f3c,
 * 35d9) keeps showing the right chip; one that stops before drawing a later change (a new verdict, a
 * back-to-back offer's "Leyendo oferta") is still taken down. A third stop, frames no longer delivered
 * to the process (vsync), and the stall probe's lines are replayed too.
 *
 * What these replays prove is the controller's bookkeeping, not what the compositor presents. "Drawn" here is the
 * window's own software draw, sampled at its pre-draw. The harness also gives each test its own frame clock, sends
 * Compose's snapshot notifications itself, and feeds drags through the detector's callback. None of that observes
 * SurfaceFlinger, so only a screenshot on the phone can show that a frame the app drew actually reached the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi",
    shadows = [InvisibleChipReplayTest.WindowLocation::class, InvisibleChipReplayTest.GroupWindowLocation::class,
        InvisibleChipReplayTest.FreezableViewRoot::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvisibleChipReplayTest {
    /** WindowManager's transform, which the JVM lacks: a window's screen origin from its gravity, x/y and size. */
    @Implements(View::class)
    open class WindowLocation : ShadowViewGroup() {
        @RealObject lateinit var real: View
        @Implementation fun getLocationOnScreen(location: IntArray) {
            val root = real.rootView
            val p = root.layoutParams as? WindowManager.LayoutParams
            val metrics = real.resources.displayMetrics
            location[0] = when {
                p == null -> 0
                p.gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.RIGHT -> metrics.widthPixels - p.x - root.width
                else -> p.x
            }
            location[1] = when {
                p == null -> 0
                p.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.BOTTOM -> metrics.heightPixels - p.y - root.height
                else -> p.y
            }
        }
    }

    @Implements(ViewGroup::class)
    class GroupWindowLocation : WindowLocation()

    /** A window whose traversals stop: scheduleTraversals is dropped for the one frozen ViewRootImpl. */
    @Implements(className = "android.view.ViewRootImpl", isInAndroidSdk = false)
    class FreezableViewRoot : ShadowViewRootImpl() {
        @RealObject lateinit var root: Any
        @Implementation protected fun scheduleTraversals() {
            if (root !== frozen) reflector(Traversals::class.java, root).scheduleTraversals()
        }
        companion object { var frozen: Any? = null }
    }

    @ForType(className = "android.view.ViewRootImpl")
    interface Traversals { @Direct fun scheduleTraversals() }

    private enum class Freeze { NONE, COMPOSITION, TRAVERSALS, VSYNC }

    /** What the window's last traversal painted, sampled at its pre-draw (after the controller's own). */
    private data class Drawn(val rect: ScreenRect, val ink: Int, val spoken: String?)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uber = "com.ubercab.driver"
    private val didi = "com.didiglobal.driver"
    private val looper get() = shadowOf(Looper.getMainLooper())
    private val reading = VerdictChipState.reading(PreferredMetric.IPK)
    private val rated = VerdictChipState(reading.copy(band = ReglaBand.Rated(ReglaPosition.ABOVE, BigDecimal("12.34"))), "15")
    private val next = VerdictChipState(reading.copy(band = ReglaBand.Rated(ReglaPosition.BELOW, BigDecimal("3.21"))), "4")

    /**
     * Production windows get [createLifecycleAwareWindowRecomposer] on AndroidUiDispatcher.Main, a process-wide
     * dispatcher bound to the main thread's Choreographer the first time any test in the JVM touches it. Robolectric
     * replaces the Choreographer between tests, so each test builds the same lifecycle-aware recomposer on this
     * test's own Choreographer and main dispatcher.
     */
    @OptIn(InternalComposeUiApi::class)
    @Before fun ownFrameClock() {
        WindowRecomposerPolicy.setFactory { root ->
            root.createLifecycleAwareWindowRecomposer(Dispatchers.Main + AndroidUiFrameClock(Choreographer.getInstance()))
        }
    }

    private val frameDelay = ShadowChoreographer.getFrameDelay()

    @OptIn(InternalComposeUiApi::class)
    @After fun thaw() {
        WindowRecomposerPolicy.setFactory(WindowRecomposerFactory.LifecycleAware)
        FreezableViewRoot.frozen = null
        ShadowChoreographer.setPaused(false); ShadowChoreographer.setFrameDelay(frameDelay)
        ScreenReaderState.clearOverlayChipRect()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    /** sid=3f3c: Uber, rawCardTop 626 on every frame, upper anchors and routes jittering by a pixel or two. */
    private fun steady(at: Long, mask: HostRect?) = HostGeometry(uber, "replay", at, HostRect(0, 0, 1080, 2340),
        HostOrientation.PORTRAIT, HostRect(0, 0, 1080, 91), HostRect(0, 626, 1080, 2196),
        listOf(HostRect(890, 700, 933, 743), HostRect(396, 1992, 686, 2037), HostRect(891, 702, 933, 744),
            HostRect(0, 0, 1080, 91), HostRect(1038, 418, 1080, 760), HostRect(858, 1402, 1062, 1606), HostRect(0, 2196, 1080, 2340)),
        emptyList(), source = HostGeometrySource.OCR_TEXT_BOUNDS,
        ocrEvidence = HostOcrGeometryEvidence(mask, listOf(HostRect(163, 674, 479, 728), HostRect(890, 700, 933, 743),
            HostRect(891, 702, 933, 744)), listOf(HostRect(193, 1419, 509, 1469), HostRect(194, 1628, 537, 1672)), false),
        fareBounds = HostRect(102, 778, 493, 888), declineControls = listOf(HostRect(890, 700, 933, 743), HostRect(891, 702, 933, 744)))

    /** sid=35d9: Uber's stacked Radar layout (soft obstacles), rawCardTop 727 on every frame; rule HIGHER on the phone. */
    private fun radar(at: Long, mask: HostRect?) = HostGeometry(uber, "replay", at, HostRect(0, 0, 1080, 2340),
        HostOrientation.PORTRAIT, HostRect(0, 0, 1080, 91), HostRect(0, 727, 1080, 2196),
        listOf(HostRect(396, 1908, 685, 1953), HostRect(888, 799, 932, 843), HostRect(0, 0, 1080, 91),
            HostRect(1038, 418, 1080, 760), HostRect(858, 1402, 1062, 1606), HostRect(0, 2196, 1080, 2340)),
        emptyList(), source = HostGeometrySource.OCR_TEXT_BOUNDS,
        ocrEvidence = HostOcrGeometryEvidence(mask, listOf(HostRect(124, 775, 322, 820), HostRect(204, 575, 871, 623),
            HostRect(888, 799, 932, 843), HostRect(202, 570, 795, 642)), listOf(HostRect(194, 1402, 513, 1447), HostRect(116, 1597, 528, 1650)), false),
        fareBounds = HostRect(99, 876, 468, 986), softObstacles = listOf(HostRect(204, 575, 871, 623), HostRect(202, 570, 873, 642)),
        declineControls = listOf(HostRect(888, 799, 932, 843)))

    private class Replay(val controller: OverlayController) {
        val screens = mutableMapOf<ComposeView, Drawn?>()
        val violations = mutableListOf<String>()
        var revealedWidth: Int? = null
        /** The first drawn sample of the frozen window, taken in the traversal that revealed it. */
        var revealDrawn: Drawn? = null
        var windowAtReading: Boolean? = null
        /** Runs at every pre-draw of every window, before its sample is taken. */
        var onPreDraw: ((ComposeView) -> Unit)? = null
    }

    /**
     * Replays [frames] at the phone's cadence (one capture every 900 ms). Before each later frame it checks,
     * like the screenshot script, the claim the frame line would carry against the window's last drawn frame.
     * [freezeAtReveal] stops the FIRST window right after its reveal pre-draw; [freezeBefore] stops whatever
     * window is attached just before that frame index; [freezeOnLeyendo] stops the window at that frame index
     * once it draws a new session's "Leyendo oferta", before the verdict; [freezeAtAttach] stops the FIRST window
     * as soon as it is attached, before its first traversal. From [goneFrom] on, frames show no offer.
     */
    private fun replay(frames: Int, geometry: (Long, HostRect?) -> HostGeometry, freezeAtReveal: Freeze = Freeze.NONE,
        showingAfterFirstMeasure: Boolean = false, freezeBefore: Pair<Int, Freeze>? = null, goneFrom: Int = Int.MAX_VALUE,
        freezeOnLeyendo: Pair<Int, Freeze>? = null, freezeAtAttach: Freeze = Freeze.NONE,
        state: (Int) -> VerdictChipState = { rated }, session: (Int) -> String = { "s" }): Replay {
        val replay = Replay(controller())
        ShadowLog.clear()
        var at = System.currentTimeMillis()
        if (freezeAtReveal != Freeze.NONE) replay.onPreDraw = { view ->
            if (view.alpha == 1f) {
                replay.onPreDraw = null
                replay.revealedWidth = view.width
                freeze(replay.controller, freezeAtReveal)
            }
        }
        for (i in 0 until frames) {
            if (i > 0) check(replay, i)
            freezeBefore?.takeIf { it.first == i }?.let { freeze(replay.controller, it.second) }
            val claim = ScreenReaderState.overlayChipRect
            val event = OfferEvent.Parsed(uber, at, OfferCard(platform = uber, fare = 79.45, sessionId = session(i),
                hostGeometry = geometry(at, claim?.let { HostRect(it.left - 8, it.top - 8, it.right + 8, it.bottom + 8) })))
            if (i >= goneFrom) {
                render(replay.controller, OverlayVisibility.SoftHidden)
                repeat(60) { frame(); watch(replay) }
                at += 900
                continue
            }
            if (i == 0 || session(i) != session(i - 1)) {
                render(replay.controller, OverlayVisibility.Reading(session(i), event))
                watch(replay)
                if (showingAfterFirstMeasure && i == 0) {
                    frame()
                    replay.windowAtReading = field("composeView").get(replay.controller) != null
                }
                freezeOnLeyendo?.takeIf { it.first == i }?.let { (_, how) ->
                    replay.onPreDraw = { view ->
                        if (view.alpha == 1f && spoken(view) == reading.spokenBand) { replay.onPreDraw = null; freeze(replay.controller, how) }
                    }
                    repeat(20) { frame() }
                }
            }
            render(replay.controller, OverlayVisibility.Showing(state(i), session(i), event))
            if (i == 0) freeze(replay.controller, freezeAtAttach)
            watch(replay)
            repeat(60) { frame(); watch(replay) }
            at += 900
        }
        check(replay, frames)
        return replay
    }

    private fun frame() = looper.idleFor(Duration.ofMillis(15))

    /** Records every pre-draw of the current window (the frame it is about to draw), once per window. */
    private fun watch(replay: Replay) {
        val view = field("composeView").get(replay.controller) as ComposeView? ?: return
        if (view in replay.screens) return
        replay.screens[view] = null
        view.viewTreeObserver.addOnPreDrawListener {
            val freezing = replay.onPreDraw != null
            replay.onPreDraw?.invoke(view)
            if (field("composeView").get(replay.controller) === view && view.width > 0) {
                val location = IntArray(2).also(view::getLocationOnScreen)
                replay.screens[view] = Drawn(ScreenRect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                    (ink(view) * view.alpha).toInt(), spoken(view))
                if (freezing && replay.onPreDraw == null) replay.revealDrawn = replay.screens[view]
            }
            true
        }
    }

    private fun freeze(controller: OverlayController, how: Freeze) {
        val view = field("composeView").get(controller) as ComposeView? ?: return
        when (how) {
            Freeze.COMPOSITION -> (view.compositionContext as Recomposer).cancel()
            // No further traversal, including one already scheduled (an attached window's first one).
            Freeze.TRAVERSALS -> view.parent.let { root ->
                FreezableViewRoot.frozen = root
                root.javaClass.getDeclaredMethod("unscheduleTraversals").apply { isAccessible = true }.invoke(root)
            }
            // Frames stop reaching the process; the window then asks for one traversal (an alpha flip or a layout
            // request does), whose barrier waits for a vsync that never comes and holds back plain messages.
            Freeze.VSYNC -> {
                ShadowChoreographer.setPaused(true); ShadowChoreographer.setFrameDelay(VSYNC_GAP)
                view.post { view.invalidate() }
            }
            Freeze.NONE -> Unit
        }
    }

    /** Delivers the vsync a VSYNC freeze withheld, so the window can be taken down. */
    private fun thawVsync() {
        looper.idleFor(VSYNC_GAP)
        ShadowChoreographer.setPaused(false); ShadowChoreographer.setFrameDelay(frameDelay)
        looper.idle()
    }

    /**
     * The screenshot check: a claimed chip is painted where claimed, fully present, with the current verdict; a
     * chip the controller holds hidden is painted nowhere. (A soft-hidden window keeps the reader's mask by design.)
     */
    private fun check(replay: Replay, frame: Int) {
        val view = field("composeView").get(replay.controller) as ComposeView?
        val drawn = view?.let { replay.screens[it] }
        if (view != null && view.alpha == 0f) {
            if (drawn != null && drawn.ink > 0)
                replay.violations += "frame ${frame + 1}: the controller holds the chip hidden but the window's last drawn frame paints it at alpha ${drawn.ink}/255"
            return
        }
        val claim = ScreenReaderState.overlayChipRect ?: return
        @Suppress("UNCHECKED_CAST")
        val current = ((field("chipState\$delegate").get(replay.controller) as MutableState<ReglaModel?>).value)?.spokenBand
        val problem = when {
            drawn == null -> "no frame of the window was drawn"
            drawn.rect != claim -> "the window was drawn at ${drawn.rect}"
            drawn.ink < 250 -> "the window's last drawn frame paints the plate at alpha ${drawn.ink}/255"
            drawn.spoken != current -> "the window's last drawn frame shows \"${drawn.spoken}\", the controller holds \"$current\""
            else -> null
        }
        if (problem != null) replay.violations += "frame ${frame + 1}: the frame line says chip=${claim.left},${claim.top},${claim.right},${claim.bottom} but $problem"
    }

    @Test fun `a steady card draws its chip and re-places on every frame`() = steadyCard()

    @Test fun `a steady card draws its chip and re-places on every frame with animations off`() {
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        steadyCard()
    }

    /** sid=3f3c's geometry in a window that keeps its frames: the steady card alone neither stops placement nor ink. */
    private fun steadyCard() {
        val replay = replay(4, ::steady)
        try {
            assertEquals(emptyList<String>(), replay.violations)
            assertNotNull("A chip is claimed", ScreenReaderState.overlayChipRect)
            val lines = logs()
            val revealAt = lines.indexOfFirst { "TEMP DIAG window reveal" in it }
            val after = lines.drop(revealAt)
            assertEquals("The steady card's 3 later frames", 3, after.count { "TEMP DIAG show" in it })
            assertTrue("Every later show is followed by a place line: " + after.map { it.substringBefore(" at=").substringBefore(" rect=") },
                after.zipWithNext().all { (a, b) -> "TEMP DIAG show" !in a || "TEMP DIAG place" in b })
            assertEquals("Drawn once", 1, lines.count { "TEMP DIAG window drawn" in it })
            assertTrue("Drawn by the traversal that revealed it: ${lines[revealAt + 1]}",
                lines[revealAt + 1].startsWith("TEMP DIAG window drawn") && lines[revealAt + 1].endsWith("preDraws=1"))
            assertNull("No deadline is left pending", field("drawCheck").get(replay.controller))
            assertFalse(lines.any { "UNDRAWN" in it || "undrawn" in it || "window stall" in it })
        } finally { replay.controller.stop() }
        val life = lives().single()
        assertTrue("A live window pre-draws, composes and re-places on every captured frame: $life",
            life.preDraws >= 4 && life.compositions >= 4 && life.placed >= 4)
        assertTrue("and did so until its detach: $life", life.lastPreDrawMsAgo in 0..900 && life.lastCompositionMsAgo in 0..900)
    }

    private fun logs() = ShadowLog.getLogsForTag("OverlayController").map { it.msg }
    private fun undrawn() = logs().single { "TEMP DIAG window undrawn" in it }
    private fun stall() = logs().single { "TEMP DIAG window stall" in it }

    private data class Life(val ms: Long, val preDraws: Int, val compositions: Int, val lastPreDrawMsAgo: Long,
        val lastCompositionMsAgo: Long, val placed: Int)

    /** The `window life` line of every window detached so far, oldest first. */
    private fun lives() = logs().mapNotNull { LIFE.find(it)?.groupValues }.map {
        Life(it[1].toLong(), it[2].toInt(), it[3].toInt(), it[4].toLong(), it[5].toLong(), it[6].toInt())
    }

    /**
     * What a window that stopped right after its reveal shows now: the plate drawn whole by that traversal, at the
     * claimed rectangle, with the verdict, for the rest of the offer, in one window; the draw check is met there.
     */
    private fun assertShownThoughStopped(replay: Replay) {
        assertEquals(emptyList<String>(), replay.violations)
        val drawn = requireNotNull(replay.revealDrawn) { "The window was never revealed" }
        assertTrue("The reveal's own traversal paints the plate whole: alpha ${drawn.ink}/255", drawn.ink >= 250)
        assertEquals("with the verdict", rated.regla.spokenBand, drawn.spoken)
        assertEquals("where the frame line says", ScreenReaderState.overlayChipRect, drawn.rect)
        assertEquals("One window for the whole offer", 1, replay.screens.size)
        assertTrue(logs().any { "TEMP DIAG window drawn" in it })
        assertFalse(logs().any { "UNDRAWN" in it || "undrawn" in it })
    }

    @Test fun `3f3c a window whose composition stops right after its reveal still shows the chip`() {
        val replay = replay(4, ::steady, freezeAtReveal = Freeze.COMPOSITION)
        try {
            assertShownThoughStopped(replay)
            // Probed at the second captured frame after the stop: plain messages, frames and traversals all run, the
            // window's composition does not (the replay cancelled its recomposer).
            assertTrue(stall(), "handler=true choreographer=true invalidatePreDraw=true recomposed=false recomposer=ShutDown " +
                "lifecycle=RESUMED attached=true windowVisibility=0 shown=true kept=true" in stall())
        } finally { replay.controller.stop() }
        val life = lives().single()
        assertTrue("Composed only before it stopped, ~3 s before its detach: $life",
            life.compositions <= 2 && life.lastCompositionMsAgo >= 2_700 && life.placed <= 2)
    }

    @Test fun `35d9 the Radar card's first window composes the verdict and shows it though it stops at the reveal`() {
        val replay = replay(4, ::radar, freezeAtReveal = Freeze.COMPOSITION, showingAfterFirstMeasure = true)
        try {
            assertEquals("No window while the offer is read, so nothing measures the 571 px Leyendo plate", false, replay.windowAtReading)
            val placed = field("placement").get(replay.controller) as OverlayPlacement.Displayed
            assertEquals("Revealed at the rated pill's width, its first and only measure", placed.size.width, replay.revealedWidth)
            assertEquals(1, logs().count { "TEMP DIAG window reveal" in it })
            val rect = placed.position.bounds(placed.size)
            assertEquals("The phone's Radar slot (rule HIGHER, right edge 1044), at the rated width", PlacementRule.HIGHER to
                HostRect(1044 - placed.size.width, 202, 1044, 370), placed.rule to rect)
            assertShownThoughStopped(replay)
        } finally { replay.controller.stop() }
    }

    @Test fun `a window whose traversals stop right after its reveal still shows the chip`() {
        val replay = replay(4, ::steady, freezeAtReveal = Freeze.TRAVERSALS)
        try {
            assertShownThoughStopped(replay)
            // The composition still runs on each frame; nothing lays the window out or draws it, the probe's request included.
            assertTrue(stall(), "handler=true choreographer=true invalidatePreDraw=false recomposed=true " in stall())
        } finally { replay.controller.stop() }
        val life = lives().single()
        assertTrue("Composed on every frame, pre-drew only until it stopped: $life",
            life.compositions >= 4 && life.lastCompositionMsAgo in 0..900 && life.lastPreDrawMsAgo >= 2_700)
    }

    /**
     * Frames stop reaching the process (no vsync): nothing composes, lays out or draws, and a traversal the window
     * asks for keeps its barrier on the main queue, which holds back every plain message. The probe's report is
     * asynchronous, so it still arrives and says all three. Not seen on the phone yet; the JVM can only model it.
     */
    @Test fun `a window whose process gets no more frames still shows the chip and its probe names all three`() {
        val replay = replay(4, ::steady, freezeAtReveal = Freeze.VSYNC)
        try {
            assertShownThoughStopped(replay)
            assertTrue(stall(), "handler=false choreographer=false invalidatePreDraw=false recomposed=false recomposer=PendingWork " in stall())
        } finally { thawVsync(); replay.controller.stop() }
    }

    /** The 571 px plate on a window already up: a back-to-back offer's Leyendo, drawn, then the window stops. */
    @Test fun `a window that stops on a back-to-back offer's Leyendo plate is replaced`() {
        val replay = replay(5, ::steady, freezeOnLeyendo = 2 to Freeze.COMPOSITION,
            state = { if (it < 2) rated else next }, session = { if (it < 2) "first" else "second" })
        try {
            assertEquals(emptyList<String>(), replay.violations)
            val leyendo = requireNotNull(replay.revealDrawn) { "The window never drew Leyendo" }
            assertEquals("Drawn whole: 'Leyendo oferta', 571 px wide as on the S25", reading.spokenBand to 571,
                leyendo.spoken to leyendo.rect.right - leyendo.rect.left)
            assertTrue("It never composed the new verdict", "composed=false" in undrawn())
            assertTrue(undrawn(), "lifecycle=RESUMED attached=true windowVisibility=0 shown=true" in undrawn())
            assertEquals("A fresh window shows the second offer's verdict", next.regla.spokenBand,
                replay.screens.entries.last().value?.spoken)
            assertNotNull(ScreenReaderState.overlayChipRect)
        } finally { replay.controller.stop() }
    }

    @Test fun `a later verdict on a window that stopped after its reveal is still replaced`() {
        val replay = replay(4, ::steady, freezeAtReveal = Freeze.COMPOSITION, state = { if (it < 2) rated else next })
        try {
            assertEquals(emptyList<String>(), replay.violations)
            assertTrue(logs().any { "chip hidden reason=UNDRAWN" in it })
            assertTrue("The stopped window never composed the new verdict", "presence=true/true composed=false" in undrawn())
            assertEquals("A fresh window draws it", 2, replay.screens.size)
            assertEquals(next.regla.spokenBand, replay.screens.entries.last().value?.spoken)
            assertNotNull(ScreenReaderState.overlayChipRect)
        } finally { replay.controller.stop() }
        assertEquals("Each window's life is logged at its detach", 2, lives().size)
    }

    /** The opposite failure: the same stop after a drawn chip leaves the old verdict over the next offer. */
    @Test fun `a back-to-back offer on a window that stopped does not keep claiming the old chip`() {
        for (how in listOf(Freeze.COMPOSITION, Freeze.TRAVERSALS)) {
            val replay = replay(5, ::steady, freezeBefore = 2 to how,
                state = { if (it < 2) rated else next }, session = { if (it < 2) "first" else "second" })
            try {
                assertEquals("$how", emptyList<String>(), replay.violations)
                assertNotNull("$how: the second offer gets its own drawn chip", ScreenReaderState.overlayChipRect)
            } finally { replay.controller.stop(); FreezableViewRoot.frozen = null }
        }
    }

    /** The other stale chip: the offer leaves (a frame without it soft-hides the chip) while the window has stopped. */
    /**
     * Before WP-AB's second round no deadline ran until a reveal, so a window that stopped before its first one sat
     * attached and transparent for the whole offer and no line said so. Now the attach arms the deadline, met only by
     * the reveal's confirming traversal; this one never traverses at all.
     */
    @Test fun `a window whose traversals stop right after attach is replaced and the next one shows the chip`() {
        val replay = replay(4, ::steady, freezeAtAttach = Freeze.TRAVERSALS)
        try {
            assertEquals(emptyList<String>(), replay.violations)
            assertTrue(undrawn(), "revealed=false" in undrawn() && "preDraws=0" in undrawn())
            assertTrue(logs().any { "chip hidden reason=UNDRAWN revealed=false" in it })
            assertEquals("The stopped window and its replacement", 2, replay.screens.size)
            assertNull("The stopped window never drew a frame", replay.screens.entries.first().value)
            assertEquals("The replacement shows the chip", rated.regla.spokenBand, replay.screens.entries.last().value?.spoken)
            assertEquals(ScreenReaderState.overlayChipRect, replay.screens.entries.last().value?.rect)
        } finally { replay.controller.stop() }
        assertEquals("No pre-draw, composition or placement, ever", Life(lives().first().ms, 0, 0, -1, -1, 0), lives().first())
    }

    /**
     * Exiting fades the plate out (160 ms); Hidden takes the window down 160 ms after Exiting. A fade that ends first
     * leaves the display slot with no node, and the next measure (the slot's own removal, or a later inset or geometry
     * write) used to throw in `single()` on the main thread, taking the service's process down. Now the window is held
     * hidden for an absent plate: no ink, no reader mask, the window kept until Hidden.
     */
    @Test fun `an exit that ends before Hidden is held hidden, and its next measure does not throw`() {
        val replay = replay(2, ::steady)
        val controller = replay.controller
        try {
            val view = field("composeView").get(controller) as ComposeView
            assertEquals(1f, view.alpha)
            render(controller, OverlayVisibility.Exiting)
            repeat(30) { frame() } // 450 ms: the fade ends and the plate leaves its layout
            remeasure(controller)
            check(replay, 2)
            assertEquals(emptyList<String>(), replay.violations)
            assertSame("Kept until Hidden", view, field("composeView").get(controller))
            assertEquals("Held hidden", 0f, view.alpha)
            assertEquals("Nothing drawn", 0, replay.screens[view]?.ink)
            assertNull("and no chip claimed: the reader's mask is down", ScreenReaderState.overlayChipRect)
            assertTrue(logs().any { "chip hidden reason=PLATE_ABSENT" in it })
            assertFalse(logs().any { "UNDRAWN" in it })
            render(controller, OverlayVisibility.Hidden)
            assertNull(field("composeView").get(controller))
            assertNull(ScreenReaderState.overlayChipRect)
        } finally { controller.stop() }
    }

    /** A window attached while the chip is exiting (a configuration change re-attaches it) composes no plate at all. */
    @Test fun `a window attached while the chip is exiting claims nothing, and its first measure does not throw`() {
        val replay = replay(2, ::steady)
        val controller = replay.controller
        try {
            render(controller, OverlayVisibility.Exiting)
            // onConfigurationChanged: detach, then show the current event again.
            val current = field("event").get(controller) as OfferEvent.Parsed
            OverlayController::class.java.getDeclaredMethod("detach", String::class.java).apply { isAccessible = true }
                .invoke(controller, "")
            OverlayController::class.java.getDeclaredMethod("show", OfferEvent.Parsed::class.java).apply { isAccessible = true }
                .invoke(controller, current)
            Snapshot.sendApplyNotifications()
            val view = field("composeView").get(controller) as ComposeView
            watch(replay)
            repeat(60) { frame() }
            remeasure(controller)
            assertSame("Kept until Hidden", view, field("composeView").get(controller))
            assertEquals("Never revealed", 0f, view.alpha)
            assertNull("No chip claimed", ScreenReaderState.overlayChipRect)
            assertTrue(logs().any { "chip hidden reason=PLATE_ABSENT" in it })
            assertFalse("Not a window that stopped: it was attached with nothing to show", logs().any { "UNDRAWN" in it })
            render(controller, OverlayVisibility.Hidden)
            assertNull(field("composeView").get(controller))
        } finally { controller.stop() }
    }

    /** A measure pass of the chip's window with nothing else changed, as an inset or geometry write asks for. */
    private fun remeasure(controller: OverlayController) {
        @Suppress("UNCHECKED_CAST")
        val geometry = field("geometry\$delegate").get(controller) as MutableState<HostGeometry?>
        geometry.value = geometry.value?.copy(capturedAtMs = geometry.value!!.capturedAtMs + 1)
        Snapshot.sendApplyNotifications()
        repeat(10) { frame() }
    }

    @Test fun `an offer that leaves a window that stopped does not leave its chip on screen`() {
        val replay = replay(4, ::steady, freezeBefore = 2 to Freeze.TRAVERSALS, goneFrom = 2)
        try {
            assertEquals(emptyList<String>(), replay.violations)
            assertNull("The stopped window was taken down", field("composeView").get(replay.controller))
            assertNull(ScreenReaderState.overlayChipRect)
        } finally { replay.controller.stop() }
    }

    /**
     * Codex review of WP-AA: a sustained drag must never be taken for a window that stopped. On DiDi a clear strict
     * chip is draggable; each move hides it (pass-through) until its next traversal settles it, reveals it and gives
     * touch back with a flags-only window update. That update requests layout after the traversal was verified, so
     * the draw check must not wait for a layout with no request pending, or a drag longer than its deadline loses
     * the chip mid-gesture. S25 Pon Tu Precio frame (invented addresses).
     */
    @Test fun `a DiDi chip dragged for longer than the draw deadline stays attached under the finger`() {
        val controller = controller()
        try {
            field("foregroundHost").set(controller, didi)
            val event = didiPonTuPrecio()
            ShadowLog.clear()
            render(controller, OverlayVisibility.Reading("ptp", event))
            render(controller, OverlayVisibility.Showing(rated, "ptp", event))
            repeat(40) { frame() }
            val view = field("composeView").get(controller) as ComposeView
            val params = view.layoutParams as WindowManager.LayoutParams
            val start = field("placement").get(controller) as OverlayPlacement.Displayed
            assertEquals("A clear strict DiDi chip takes touches", 0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            assertTrue(ShadowLog.getLogsForTag("OverlayController").any { "TEMP DIAG window drawn" in it.msg })
            // The drag gesture's output: one move in every frame before its traversal (input runs first in a
            // Choreographer frame), for longer than the ~520 ms deadline. The detector's callback is fed directly:
            // the JVM has no input system to map a finger through a window that moves under it.
            val drag = OverlayController::class.java.getDeclaredMethod("onDrag", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .apply { isAccessible = true }
            val dragEnd = OverlayController::class.java.getDeclaredMethod("onDragEnd").apply { isAccessible = true }
            val steps = 60
            var moves = 0
            var lost: String? = null
            val began = android.os.SystemClock.uptimeMillis()
            val choreographer = Choreographer.getInstance()
            choreographer.postFrameCallback(object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (lost == null && field("composeView").get(controller) !== view) lost = "after move $moves, " +
                        "${android.os.SystemClock.uptimeMillis() - began} ms into the drag: " +
                        ShadowLog.getLogsForTag("OverlayController").map { it.msg }.lastOrNull { "detach" in it }
                    if (moves == steps) return
                    moves++; drag.invoke(controller, -4f, 0f)
                    choreographer.postFrameCallback(this)
                }
            })
            while (moves < steps) frame()
            frame()
            assertNull("The window under the finger was taken down $lost", lost)
            assertTrue("The drag outlasted the deadline", android.os.SystemClock.uptimeMillis() - began > 600)
            val moved = field("placement").get(controller) as OverlayPlacement.Displayed
            val at = IntArray(2).also(view::getLocationOnScreen)
            assertTrue(view.isAttachedToWindow)
            assertEquals("Visible under the finger", 1f, view.alpha)
            assertEquals("The window is where the drag put the chip", moved.position, OverlayPosition(at[0], at[1]))
            assertEquals("The chip is where the finger put it", OverlayPosition(start.position.x - 4 * steps, start.position.y), moved.position)
            assertEquals("Touchable again at the end of each settled traversal", 0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            assertFalse(ShadowLog.getLogsForTag("OverlayController").any { "UNDRAWN" in it.msg || "window detach" in it.msg })
            dragEnd.invoke(controller); repeat(40) { frame() }
            assertSame("Released, the chip stays", view, field("composeView").get(controller))
            assertEquals(1f, view.alpha)
            assertFalse(ShadowLog.getLogsForTag("OverlayController").any { "UNDRAWN" in it.msg })
        } finally { controller.stop() }
    }

    /** The S25 Pon Tu Precio frame through the real OCR pipeline, as the reader publishes it (no chip of ours yet). */
    private fun didiPonTuPrecio(): OfferEvent.Parsed = runBlocking {
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it } }
        kotlinx.coroutines.yield()
        OfferFramePipeline().processOwnedFrame(RealGeometryFixtures.didiPtpS25(), null, null,
            displayBounds = HostRect(0, 0, 1080, 2340), density = 3f, foregroundPackage = didi)
        job.cancel()
        parsed.single()
    }

    /** One capture of a notice run: the chip rect it was captured under, what it published, what is claimed after it. */
    private data class Captured(val under: ScreenRect?, val underNotice: Boolean, val notice: OfferEvent.Unreadable?,
        val parsed: OfferEvent.Parsed?, val diag: String, val claim: ScreenRect?,
        /** Round 3, [NoticeRun.capture] with `held`: the window and chip rect once the frame's events were handled, before any frame callback. */
        val heldWindow: ComposeView? = null, val heldClaim: ScreenRect? = null)

    /**
     * WP-AD: the production path for the reader's unread notice. The real OCR pipeline publishes on the bus, start()
     * wires the state machine into the real controller and Compose window, and each frame is captured under the chip
     * rect the controller reports, so the session tracker's own first-frame rule sees our mask. Every capture taken
     * under a notice is checked against that capture's own card: the notice's rect, expanded by the capture's mask
     * margin, never touches the card envelope or a card band.
     */
    private inner class NoticeRun(val host: String) {
        val offers = BoundaryOfferDao()
        val presented = mutableListOf<String>()
        val controller = controller(offers = offers, costs = BoundaryCostProfileDao(), presentations = presentations(presented))
        val replay = Replay(controller)
        val captures = mutableListOf<Captured>()
        private val pipeline = OfferFramePipeline()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val events = mutableListOf<OfferEvent>()
        private val listener = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).launch { OfferEventBus.events.collect { events += it } }

        fun start() {
            controller.start(scope, OfferEventBus.events, context, flowOf(host))
            looper.idle()
            ShadowLog.clear()
        }

        /**
         * [held] (round 3): no frame callback runs while the frame's events are handled (the choreographer is paused for
         * that stretch), and the window and chip rect are sampled right then: whatever the controller did, it did inside
         * its event handling, with no composition, measure or pre-draw. [masked]: this capture is expected to be taken
         * under a notice's mask that touches the frame's card (the one frame a card change can cost); otherwise every
         * capture under a notice must be clear of the frame's own card.
         */
        fun capture(blocks: List<mx.kompara.ocr.OcrBlock>, held: Boolean = false, masked: Boolean = false): Captured {
            if (captures.isNotEmpty()) check(replay, captures.size)
            val under = ScreenReaderState.overlayChipRect
            val underNotice = under != null && field("notice").get(controller) == true
            val before = events.size
            val diags = ShadowLog.getLogsForTag("KomparaOCR").size
            if (held) { ShadowChoreographer.setPaused(true); ShadowChoreographer.setFrameDelay(HELD_FRAME_GAP) }
            pipeline.processOwnedFrame(blocks, under, null, displayBounds = HostRect(0, 0, 1080, 2340), density = 3f,
                foregroundPackage = host)
            // The bus reaches the controller on the main looper: watch a window as soon as it is attached,
            // before its first traversal (which may reveal and draw it), one due task at a time.
            while (!looper.isIdle) { looper.runOneTask(); watch(replay) }
            val heldWindow = if (held) field("composeView").get(controller) as ComposeView? else null
            val heldClaim = if (held) ScreenReaderState.overlayChipRect else null
            if (held) {
                looper.idleFor(HELD_FRAME_GAP)
                ShadowChoreographer.setPaused(false); ShadowChoreographer.setFrameDelay(frameDelay)
                looper.idle()
            }
            repeat(60) { frame(); watch(replay) }
            val published = events.drop(before)
            val captured = Captured(under, underNotice, published.filterIsInstance<OfferEvent.Unreadable>().lastOrNull(),
                published.filterIsInstance<OfferEvent.Parsed>().lastOrNull(),
                ShadowLog.getLogsForTag("KomparaOCR").drop(diags).map { it.msg }.single { it.startsWith("TEMP DIAG frame") },
                ScreenReaderState.overlayChipRect, heldWindow, heldClaim)
            captures += captured
            val card = captured.notice?.card ?: captured.parsed?.card
            // A frame the tracker rejected as masked publishes no card (round 3b): the rejection is the tracker's own proof.
            if (masked) assertTrue("capture ${captures.size} was expected under a notice's mask on its card: ${captured.diag}",
                under != null && underNotice && (card?.let { maskTouches(under, it) } ?: ("rejection=PARSED_CARD_MASKED" in captured.diag)))
            else if (under != null && underNotice && card != null) assertClearOfCard("capture ${captures.size}", under, card)
            return captured
        }

        fun stop() { controller.stop(); scope.cancel(); listener.cancel() }
    }

    private fun noticeRun(host: String, body: NoticeRun.() -> Unit) {
        val run = NoticeRun(host)
        ScreenReaderState.setForegroundHost(host, android.os.SystemClock.elapsedRealtime())
        try { run.start(); run.body() } finally {
            run.stop(); ScreenReaderState.setForegroundHost(null, android.os.SystemClock.elapsedRealtime())
        }
    }

    /** The tracker's first-frame rule (OcrOfferSessionTracker.update): the mask is the chip rect expanded by the margin. */
    private fun assertClearOfCard(label: String, chip: ScreenRect, card: OfferCard,
        clearance: Int = ScreenReaderState.CHIP_MASK_MARGIN_PX) {
        val mask = HostRect(chip.left, chip.top, chip.right, chip.bottom).expanded(clearance)
        val envelope = requireNotNull(card.hostGeometry).offerCard
        assertFalse("$label: the notice's rect +$clearance px $mask touches the card envelope $envelope", mask.overlaps(envelope))
        for (band in card.instanceEvidence?.cardBands.orEmpty())
            assertFalse("$label: the notice's rect +$clearance px $mask touches the card band $band", mask.overlaps(band))
    }

    /** The tracker's first-frame rule, as a predicate: the chip rect plus the capture's mask margin touches the card. */
    private fun maskTouches(chip: ScreenRect, card: OfferCard): Boolean {
        val mask = HostRect(chip.left, chip.top, chip.right, chip.bottom).expanded(ScreenReaderState.CHIP_MASK_MARGIN_PX)
        return (listOf(requireNotNull(card.hostGeometry).offerCard) + card.instanceEvidence?.cardBands.orEmpty()).any(mask::overlaps)
    }

    /** Round 2b: the notice's keep-clear distance, the capture's mask margin plus the OCR-jitter allowance (20 px on the S25). */
    private val noticeClearance get() = ScreenReaderState.CHIP_MASK_MARGIN_PX +
        Math.round(PlacementEvidence.CEILING_JITTER_DP * context.resources.displayMetrics.density)

    /** Vertical room from the notice's bottom edge to the nearest card rect below it (envelope or a band it spans). */
    private fun slack(chip: ScreenRect, card: OfferCard) = (listOf(card.hostGeometry!!.offerCard) + card.instanceEvidence?.cardBands.orEmpty())
        .filter { it.left < chip.right && it.right > chip.left }.minOf { it.top } - chip.bottom

    @Suppress("UNCHECKED_CAST")
    private fun heldChip(controller: OverlayController) =
        requireNotNull((field("chipState\$delegate").get(controller) as MutableState<ReglaModel?>).value)

    /**
     * WP-AD: the reader's unread notice on the production path ([NoticeRun]). Its window draws the grey "Sin lectura"
     * pill whole at the placed rectangle, above the card, and takes no touch, and neither the ledger nor the presentation
     * log hears of it. The card's first readable frame, captured under that chip, then shows its verdict in the same
     * window, with its own row. S25 Priority card (invented addresses), its TOTAL row's rate misread so that every frame
     * contradicts itself.
     */
    @Test fun `the unread notice draws Sin lectura untouchable with no row, and a readable frame takes the same window`() = noticeRun(uber) {
        val readable = priority()
        val contradictory = readable.map { if ("/km est" in it.text) it.copy(text = "25 min • 2.6 km • \$9.99/km est.") else it }
        repeat(2) { capture(contradictory) }
        assertNull("Nothing before three unread frames span 1.8 s", field("composeView").get(controller))
        val shown = capture(contradictory)
        val view = requireNotNull(field("composeView").get(controller) as ComposeView?) { logs().toString() }
        capture(contradictory)
        check(replay, captures.size)
        assertEquals(emptyList<String>(), replay.violations)
        assertEquals("Revealed", 1f, view.alpha)
        assertEquals("The grey unread pill", ReglaUnavailable.FARE.spoken, spoken(view))
        val claim = requireNotNull(ScreenReaderState.overlayChipRect) { "No chip claimed" }
        assertEquals("Round 1's rectangle, 16 dp above the card", ScreenRect(577, 351, 1044, 519), claim)
        assertEquals(HostRect(0, 605, 1080, 2340), requireNotNull(shown.notice).card.hostGeometry?.offerCard)
        assertClearOfCard("the shown notice", claim, shown.notice.card)
        assertEquals("Drawn where placed", claim, replay.screens[view]?.rect)
        assertTrue("Drawn whole", (replay.screens[view]?.ink ?: 0) >= 250)
        assertTrue("Taps pass through", (view.layoutParams as WindowManager.LayoutParams).flags and
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(logs().any { "TEMP DIAG window drawn" in it })
        assertEquals("No ledger row", emptyList<mx.kompara.data.db.entity.OfferEntity>(), offers.all())
        assertEquals("No presentation row", emptyList<String>(), presented)
        // The card turns readable under the notice's own chip: its first readable frame starts the session.
        val first = capture(readable)
        assertTrue("Captured under the notice", first.underNotice)
        assertTrue(first.diag, first.parsed?.card?.sessionId != null && "rejection=null" in first.diag)
        capture(readable)
        check(replay, captures.size)
        assertEquals(emptyList<String>(), replay.violations)
        assertSame("The same window", view, field("composeView").get(controller))
        val held = heldChip(controller)
        assertTrue("The offer's own verdict: $held", held.band is ReglaBand.Rated)
        assertEquals(held.spokenBand, spoken(view))
        val row = offers.all().single()
        assertEquals("Only the offer is presented", listOf(row.sessionId), presented.distinct())
        assertFalse(logs().any { "UNDRAWN" in it })
        assertFalse(captures.any { "PARSED_CARD_MASKED" in it.diag })
    }

    /**
     * WP-AD round 2 (Codex): on the tall Pon Tu Precio layout (card from y 423) ordinary placement docks the 168 px pill
     * below the decline reservation, across the card's top edge, so a notice there would mask the card and every
     * complete first frame would be rejected as PARSED_CARD_MASKED until its 15 s cap. Round 2b: on the second tall card
     * (top 437) the dock slot's mask ends exactly at the card top, with no room for OCR jitter. Neither has a slot clear by
     * the keep-clear distance, so the notice is not shown at all: no window revealed, no chip rect, no mask. The card's
     * first readable frame starts its session at once and its verdict docks where the notice may not. S25 frames of
     * 2026-11-06 (invented addresses), their trip leg unread on the first frames.
     */
    @Test fun `on the tall Pon Tu Precio card the notice is not shown, and the first readable frame shows its verdict`() =
        tallCard(RealGeometryFixtures.didiPtpTallS25(), "14 min (2.1 km)" to "1? min (?.1 km)", HostRect(0, 423, 1080, 2340),
            HostRect(104, 471, 717, 1882), ScreenRect(379, 260, 1044, 428))

    @Test fun `on the second tall Pon Tu Precio card the notice is not shown either`() =
        tallCard(RealGeometryFixtures.didiPtpTall2S25(), "13 min (2.7 km)" to "1? min (?.7 km)", HostRect(0, 437, 1080, 2340),
            HostRect(105, 485, 717, 1879), TALL2_VERDICT)

    private fun tallCard(readable: List<mx.kompara.ocr.OcrBlock>, unreadTrip: Pair<String, String>, envelope: HostRect,
        band: HostRect, verdictRect: ScreenRect) = noticeRun(didi) {
        val unread = RealGeometryFixtures.retext(readable, unreadTrip)
        val notices = (1..4).map { capture(unread) }
        val notice = requireNotNull(notices[2].notice) { notices[2].diag }
        assertEquals("The raw card envelope", envelope, notice.card.hostGeometry?.offerCard)
        assertEquals("and its band", listOf(band), notice.card.instanceEvidence?.cardBands)
        for ((i, c) in notices.withIndex()) {
            assertTrue(c.diag, "rejection=PARSED_ROUTE_INCOMPLETE" in c.diag)
            assertNull("f$i: no chip rect reported, so no mask", c.claim)
            val view = field("composeView").get(controller) as ComposeView?
            assertTrue("f$i: no window revealed", view == null || view.alpha == 0f)
        }
        assertTrue("The notice was wanted (f2, f3)", notices[2].notice != null && notices[3].notice != null)
        assertTrue(logs().toString(), logs().any { "chip hidden reason=SUPPRESSED_CARD_KEEP_CLEAR" in it })
        assertEquals(emptyList<mx.kompara.data.db.entity.OfferEntity>(), offers.all())
        assertEquals(emptyList<String>(), presented)
        // The route becomes readable: the first readable frame starts its session, with no mask to reject it.
        val first = capture(readable)
        assertNull("Captured with no chip of ours", first.under)
        assertTrue(first.diag, first.parsed?.card?.sessionId != null && "rejection=null" in first.diag)
        capture(readable)
        check(replay, captures.size)
        assertEquals(emptyList<String>(), replay.violations)
        val view = requireNotNull(field("composeView").get(controller) as ComposeView?) { logs().toString() }
        assertEquals("The verdict is on screen", 1f, view.alpha)
        val held = heldChip(controller)
        assertTrue("$held", held.band is ReglaBand.Rated)
        assertEquals(held.spokenBand, spoken(view))
        val verdict = requireNotNull(ScreenReaderState.overlayChipRect)
        // Ordinary placement, unchanged: the session's chip docks within the keep-clear distance of the card top.
        assertEquals("Docked below the decline reservation, right slot", verdictRect, verdict)
        assertTrue("The dock slot is too close to the card for a notice", HostRect(verdict.left, verdict.top, verdict.right,
            verdict.bottom).expanded(noticeClearance).overlaps(envelope))
        // Round 3: the notice's check never touches an offer's chip, which may sit over its own card as before.
        val detaches = logs().count { "window detach" in it }
        val next = capture(readable, held = true)
        assertSame("The session's chip keeps its window", view, next.heldWindow)
        assertEquals(verdict, next.heldClaim)
        capture(readable)
        assertSame(view, field("composeView").get(controller))
        assertEquals(verdict, ScreenReaderState.overlayChipRect)
        assertEquals(detaches, logs().count { "window detach" in it })
        assertFalse(logs().any { "NOTICE_NEAR_CARD" in it })
        assertEquals(1, offers.all().size)
        assertFalse(captures.any { "PARSED_CARD_MASKED" in it.diag })
    }

    /** Round 3: Pon Tu Precio read without its heading and trip row: no upper anchor, so the notice docks above a card inferred at 726. */
    private fun headlessPtp() = RealGeometryFixtures.retext(RealGeometryFixtures.didiPtpS25().filterNot { it.text == "Pon Tu Precio" },
        "39 min (7.2 km)" to "3? min (?.2 km)")

    /**
     * WP-AD round 3 (Codex): a notice already up keeps its footprint when the card under it changes. Pon Tu Precio read
     * without its heading and trip row docks the notice above a card inferred at 726; the window's composition then stops
     * (the S25 failure, TD-V4-13), and the tall Pon Tu Precio card (envelope from 423) replaces the first. Its first frame
     * is captured under the old mask and rejected (the one frame a card change costs); handling that frame, the controller
     * takes the window down and clears the chip rect before any frame callback, so the next capture carries no mask and
     * starts the session; the verdict shows. Driven through the real pipeline and tracker with the rect the controller reports.
     */
    @Test fun `a notice whose card is replaced by a taller one comes down at once though its composition stopped`() {
        replacedByTallCard(stopComposition = true, readableFirst = true)
        replacedByTallCard(stopComposition = true, readableFirst = false)
    }

    @Test fun `a notice whose card is replaced by a taller one comes down at once`() {
        replacedByTallCard(stopComposition = false, readableFirst = true)
        replacedByTallCard(stopComposition = false, readableFirst = false)
    }

    /**
     * [readableFirst]: the tall card's first frame is complete, so the tracker rejects it as PARSED_CARD_MASKED and the
     * pipeline's guard (round 3b) ends the notice on that frame. Otherwise that frame is unread for its own reasons (trip
     * row) and the controller's check (round 3) takes the window down. Either way, before any frame callback.
     */
    private fun replacedByTallCard(stopComposition: Boolean, readableFirst: Boolean) = noticeRun(didi) {
        repeat(4) { capture(headlessPtp()) }
        val old = requireNotNull(field("composeView").get(controller) as ComposeView?) { logs().toString() }
        val stale = requireNotNull(ScreenReaderState.overlayChipRect) { logs().toString() }
        assertEquals("Docked above the inferred card", ScreenRect(577, 261, 1044, 429), stale)
        assertEquals(726, captures.last().notice?.card?.hostGeometry?.offerCard?.top)
        assertEquals(1f, old.alpha)
        if (stopComposition) freeze(controller, Freeze.COMPOSITION)
        val tall = RealGeometryFixtures.didiPtpTallS25()
        val tallUnread = RealGeometryFixtures.retext(tall, "14 min (2.1 km)" to "1? min (?.1 km)")
        val replaced = capture(if (readableFirst) tall else tallUnread, held = true, masked = true)
        assertTrue("$readableFirst: the old window is down before any frame callback",
            replaced.heldWindow !== old && !old.isAttachedToWindow)
        assertNull("and the chip rect is cleared with it", replaced.heldClaim)
        assertNull("No clear slot on the tall card", replaced.claim)
        val ocr = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
        if (readableFirst) {
            assertTrue("Captured under the old mask: rejected", "rejection=PARSED_CARD_MASKED" in replaced.diag)
            assertNull("The guard emits no notice for it", replaced.notice)
            assertTrue(replaced.diag, "notice=0" in replaced.diag)
            assertTrue(ocr.toString(), ocr.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=MASKED") })
        } else {
            assertTrue(replaced.diag, "rejection=PARSED_ROUTE_INCOMPLETE" in replaced.diag)
            assertEquals("Its envelope", 423, replaced.notice?.card?.hostGeometry?.offerCard?.top)
            assertTrue(logs().any { "window detach reason=NOTICE_NEAR_CARD" in it })
        }
        val first = capture(tall)
        assertNull("The next capture carries no mask", first.under)
        assertTrue(first.diag, first.parsed?.card?.sessionId != null && "rejection=null" in first.diag)
        capture(tall)
        check(replay, captures.size)
        assertEquals(emptyList<String>(), replay.violations)
        val view = requireNotNull(field("composeView").get(controller) as ComposeView?) { logs().toString() }
        assertEquals("The verdict is on screen", 1f, view.alpha)
        val held = heldChip(controller)
        assertTrue("$held", held.band is ReglaBand.Rated)
        assertEquals(held.spokenBand, spoken(view))
        assertEquals("At most the frame captured under the old mask was rejected", if (readableFirst) 1 else 0,
            captures.count { "PARSED_CARD_MASKED" in it.diag })
        assertEquals(1, offers.all().size)
    }

    /** Round 3: a notice whose card does not change keeps its window on every frame: no churn. */
    @Test fun `a notice over an unchanged card keeps its window`() = noticeRun(didi) {
        repeat(4) { capture(headlessPtp()) }
        val view = requireNotNull(field("composeView").get(controller) as ComposeView?) { logs().toString() }
        val rect = ScreenReaderState.overlayChipRect
        repeat(4) {
            val c = capture(headlessPtp(), held = true)
            assertSame(view, c.heldWindow)
            assertEquals(rect, c.heldClaim)
        }
        assertFalse(logs().any { "window detach" in it || "NOTICE_NEAR_CARD" in it })
    }

    /**
     * Round 3: a soft-hidden notice keeps its mask (by design), so a taller card appearing at once is captured under it.
     * Pinned: that first frame is rejected, the controller takes the window and its mask down while handling it, and the
     * next frame starts the session. Read with its trip row unread, the taller card shows no notice at all (no clear slot)
     * and its first readable frame starts the session.
     */
    @Test fun `a soft-hidden notice followed by a taller card costs that card one frame`() {
        val ptp = RealGeometryFixtures.retext(RealGeometryFixtures.didiPtpS25(), "39 min (7.2 km)" to "3? min (?.2 km)")
        val tall = RealGeometryFixtures.didiPtpTallS25()
        val tallUnread = RealGeometryFixtures.retext(tall, "14 min (2.1 km)" to "1? min (?.1 km)")
        for (readableFirst in listOf(true, false)) noticeRun(didi) {
            repeat(4) { capture(ptp) }
            val old = requireNotNull(field("composeView").get(controller) as ComposeView?)
            val kept = requireNotNull(ScreenReaderState.overlayChipRect)
            assertEquals(ScreenRect(577, 377, 1044, 545), kept)
            capture(emptyList())
            assertEquals("Soft-hidden, the mask stays", kept, ScreenReaderState.overlayChipRect)
            assertEquals(0f, old.alpha)
            val next = capture(if (readableFirst) tall else tallUnread, held = true, masked = true)
            if (readableFirst) assertTrue(next.diag, "rejection=PARSED_CARD_MASKED" in next.diag)
            assertEquals(kept, next.under)
            assertTrue("$readableFirst: the old window is down at once", next.heldWindow !== old && !old.isAttachedToWindow)
            assertNull(next.heldClaim)
            assertNull("No clear slot on the tall card", next.claim)
            if (!readableFirst) {
                assertTrue(next.diag, "rejection=PARSED_ROUTE_INCOMPLETE" in next.diag)
                repeat(2) { assertNull(capture(tallUnread).claim) }
            }
            val first = capture(tall)
            assertNull(first.under)
            assertTrue(first.diag, first.parsed?.card?.sessionId != null && "rejection=null" in first.diag)
            assertEquals(if (readableFirst) 1 else 0, captures.count { "PARSED_CARD_MASKED" in it.diag })
        }
    }

    /**
     * WP-AD round 2b: the clearance survives OCR jitter. On the shown layout with the least room (Pon Tu Precio, notice
     * 48 px above the card top 593), the next frames read the card's top edge 11 px higher (the allowance less 1 px): the
     * notice stays where it was, and the card's first readable frame, captured under it, starts its session.
     */
    @Test fun `a notice with the least room survives the card top jittering up by the allowance less one pixel`() = noticeRun(didi) {
        val readable = RealGeometryFixtures.didiPtpS25()
        val unread = RealGeometryFixtures.retext(readable, "39 min (7.2 km)" to "3? min (?.2 km)")
        val jitter = Math.round(PlacementEvidence.CEILING_JITTER_DP * context.resources.displayMetrics.density) - 1
        fun raised(blocks: List<mx.kompara.ocr.OcrBlock>) = blocks.map {
            if (it.text == "Pon Tu Precio") it.copy(bounds = mx.kompara.ocr.OcrBounds(it.bounds.left, it.bounds.top - jitter,
                it.bounds.right, it.bounds.bottom - jitter)) else it
        }
        repeat(3) { capture(unread) }
        val placed = requireNotNull(ScreenReaderState.overlayChipRect) { logs().toString() }
        val card = requireNotNull(captures.last().notice).card
        assertEquals(ScreenRect(577, 377, 1044, 545), placed)
        assertEquals(HostRect(0, 593, 1080, 2340), card.hostGeometry?.offerCard)
        assertEquals("48 px of room", 48, slack(placed, card))
        val shownView = field("composeView").get(controller) as ComposeView
        val moved = capture(raised(unread), held = true)
        assertEquals("The card top read $jitter px higher", 593 - jitter, moved.notice?.card?.hostGeometry?.offerCard?.top)
        assertSame("Round 3: within the allowance the window stays (no churn)", shownView, moved.heldWindow)
        assertEquals(placed, moved.heldClaim)
        assertEquals("The notice stays where it was: no blink", placed, moved.claim)
        val view = field("composeView").get(controller) as ComposeView
        assertSame(shownView, view)
        assertFalse(logs().any { "NOTICE_NEAR_CARD" in it || "window detach" in it })
        assertEquals(1f, view.alpha)
        assertEquals(ReglaUnavailable.FARE.spoken, spoken(view))
        val first = capture(raised(readable))
        assertTrue("Captured under the notice", first.underNotice && first.under == placed)
        assertEquals(593 - jitter, first.parsed?.card?.hostGeometry?.offerCard?.top)
        assertTrue(first.diag, first.parsed?.card?.sessionId != null && "rejection=null" in first.diag)
        assertFalse(captures.any { "PARSED_CARD_MASKED" in it.diag })
    }

    /**
     * WP-AD round 2: the notice on every saved S25 layout the overlay tests carry, through the real controller and its
     * measured window. Wherever it is shown, its rect expanded by the keep-clear distance (round 2b: the capture's mask
     * margin plus the OCR-jitter allowance) is clear of the card envelope and of every card band; elsewhere it is not
     * shown and no chip rect is reported. The rectangles are pinned: the allowance moves no notice, it only withdraws
     * the two tall Pon Tu Precio ones.
     */
    @Test fun `on every saved layout a notice is either clear of the card or not shown`() {
        val layouts = listOf(uber to ("radar" to RealGeometryFixtures.radar()), uber to ("uberx" to RealGeometryFixtures.uberx()),
            uber to ("priority" to RealGeometryFixtures.priority()), uber to ("radarHigh" to RealGeometryFixtures.radarHighDistinct()),
            uber to ("priorityRadarStack" to RealGeometryFixtures.priorityRadarStack()),
            didi to ("didi" to RealGeometryFixtures.didi()), didi to ("didiBid" to RealGeometryFixtures.didiBid()),
            didi to ("didiLive" to RealGeometryFixtures.didiLive()), didi to ("ptp" to RealGeometryFixtures.didiPtpS25()),
            didi to ("tall" to RealGeometryFixtures.didiPtpTallS25()), didi to ("tall2" to RealGeometryFixtures.didiPtpTall2S25()),
            didi to ("masRapido" to RealGeometryFixtures.didiMasRapidoS25()))
        val expected = mapOf("radar" to ScreenRect(577, 398, 1044, 566), "uberx" to ScreenRect(577, 572, 1044, 740),
            "priority" to ScreenRect(577, 351, 1044, 519), "radarHigh" to ScreenRect(577, 171, 1044, 339),
            "priorityRadarStack" to ScreenRect(577, 231, 1044, 399), "didi" to ScreenRect(577, 171, 1044, 339),
            "didiBid" to ScreenRect(577, 436, 1044, 604), "didiLive" to ScreenRect(577, 171, 1044, 339),
            "ptp" to ScreenRect(577, 377, 1044, 545), "tall" to null, "tall2" to null, "masRapido" to ScreenRect(36, 171, 503, 339))
        val outcomes = mutableListOf<String>()
        for ((host, layout) in layouts) {
            val (name, blocks) = layout
            val card = firstParsed(host, blocks)?.card
            if (card == null) { outcomes += "$name: no card parsed on a first frame"; continue }
            val notice = OfferEvent.Unreadable(host, card.hostGeometry!!.capturedAtMs, OfferCard(platform = host, captureSource = "OCR",
                contentBounds = card.contentBounds, fareBounds = card.fareBounds, hostGeometry = card.hostGeometry,
                instanceEvidence = card.instanceEvidence))
            val states = runBlocking {
                OverlayStateMachine(preferred = { PreferredMetric.IPK }, evaluate = { error("A notice is never evaluated") })
                    .visibility(flowOf(notice)).toList()
            }
            val controller = controller()
            try {
                field("foregroundHost").set(controller, host)
                states.forEach { render(controller, it) }
                repeat(40) { frame() }
                val claim = ScreenReaderState.overlayChipRect
                val view = field("composeView").get(controller) as ComposeView?
                assertEquals(name, expected.getValue(name), claim)
                if (claim != null) {
                    assertClearOfCard(name, claim, card, noticeClearance)
                    assertEquals("$name: drawn", 1f, view?.alpha)
                    outcomes += "$name: shown at $claim, room ${slack(claim, card)} px (${slack(claim, card) - noticeClearance} beyond " +
                        "$noticeClearance), card ${card.hostGeometry?.offerCard} bands ${card.instanceEvidence?.cardBands}"
                } else {
                    assertTrue("$name: no window revealed", view == null || view.alpha == 0f)
                    outcomes += "$name: not shown, card ${card.hostGeometry?.offerCard} bands ${card.instanceEvidence?.cardBands}"
                }
            } finally { controller.stop(); ScreenReaderState.clearOverlayChipRect() }
        }
        println("WPAD layouts\n" + outcomes.joinToString("\n"))
    }

    /** The first frame of [blocks] through the real pipeline, as the reader would publish it (no chip of ours). */
    private fun firstParsed(host: String, blocks: List<mx.kompara.ocr.OcrBlock>): OfferEvent.Parsed? = runBlocking {
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it } }
        kotlinx.coroutines.yield()
        OfferFramePipeline().processOwnedFrame(blocks, null, null, displayBounds = HostRect(0, 0, 1080, 2340), density = 3f,
            foregroundPackage = host)
        job.cancel()
        parsed.firstOrNull()
    }

    /** WP-AD: on DiDi, where a settled offer in a clear strict slot takes touches, the notice in that slot never does. */
    @Test fun `a DiDi notice never takes touches where a settled offer does`() {
        val controller = controller()
        try {
            field("foregroundHost").set(controller, didi)
            val offer = didiPonTuPrecio()
            val notice = OfferEvent.Unreadable(didi, offer.timestampMs, OfferCard(platform = didi, captureSource = "OCR",
                contentBounds = offer.card.contentBounds, fareBounds = offer.card.fareBounds, hostGeometry = offer.card.hostGeometry,
                instanceEvidence = offer.card.instanceEvidence))
            val states = runBlocking {
                OverlayStateMachine(preferred = { PreferredMetric.IPK }, evaluate = { error("A notice is never evaluated") })
                    .visibility(flowOf(notice)).toList()
            }
            assertEquals(2, states.size)
            ShadowLog.clear()
            states.forEach { render(controller, it) }
            repeat(40) { frame() }
            val view = field("composeView").get(controller) as ComposeView
            val params = view.layoutParams as WindowManager.LayoutParams
            assertEquals(1f, view.alpha)
            assertEquals(ReglaUnavailable.FARE.spoken, spoken(view))
            assertTrue("The notice takes no touch", params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            assertTrue(ShadowLog.getLogsForTag("OverlayController").any { "TEMP DIAG window drawn" in it.msg })
            // The same frame as a settled offer is touchable in that window (the drag test's precondition).
            render(controller, OverlayVisibility.Reading("ptp", offer))
            render(controller, OverlayVisibility.Showing(rated, "ptp", offer))
            repeat(40) { frame() }
            assertSame(view, field("composeView").get(controller))
            assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } finally { controller.stop() }
    }

    /** The S25 Priority card the OCR tests read (`uber_priority_s25_2026_10_29.json`, invented addresses). */
    private fun priority(): List<mx.kompara.ocr.OcrBlock> {
        val json = org.json.JSONArray(java.io.File("../ocr/src/test/resources/uber_priority_s25_2026_10_29.json").readText())
        return (0 until json.length()).map { i ->
            val b = json.getJSONObject(i)
            mx.kompara.ocr.OcrBlock(b.getString("text"), mx.kompara.ocr.OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }
    }

    /** A presentation log that only remembers which sessions it was asked to record. */
    private fun presentations(recorded: MutableList<String>): OfferPresentationDao =
        Proxy.newProxyInstance(OfferPresentationDao::class.java.classLoader, arrayOf(OfferPresentationDao::class.java)) { _, method, args ->
            when (method.name) {
                "forSession" -> null
                "record" -> { recorded += (args[0] as mx.kompara.data.db.entity.OfferPresentationEntity).sessionId; Unit }
                else -> error("Unexpected presentation call: ${method.name}")
            }
        } as OfferPresentationDao

    /** Max alpha over a 9-point grid inside the plate, as the view itself draws it (its own alpha excluded). */
    private fun ink(view: ComposeView): Int {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return listOf(0.25f, 0.5f, 0.75f).flatMap { fx -> listOf(0.3f, 0.5f, 0.7f).map { fy ->
            android.graphics.Color.alpha(bitmap.getPixel((view.width * fx).toInt(), (view.height * fy).toInt()))
        } }.max()
    }

    private fun spoken(view: ComposeView): String? {
        val root = (view.getChildAt(0) as? RootForTest)?.semanticsOwner?.unmergedRootSemanticsNode ?: return null
        fun find(node: SemanticsNode): String? =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: node.children.firstNotNullOfOrNull(::find)
        return find(root)
    }

    private fun controller(offers: OfferDao = dao(), costs: CostProfileDao = dao(),
        presentations: OfferPresentationDao = dao()): OverlayController {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No writes")
        }
        val settings = SettingsRepository(store)
        val readiness = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) = Unit
        }, Clock.systemUTC()).apply { supportedApps(setOf(uber, didi)); bound(); captureRunning(true) }
        return OverlayController(OfferRecorder(offers, CostProfileRepository(costs), settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(presentations), readiness).also {
            field("overlayContext").set(it, context)
        }
    }

    private fun field(name: String) = OverlayController::class.java.getDeclaredField(name).apply { isAccessible = true }

    /**
     * The controller's own render path (render → show → attach / applyPlacement / settleWindow). Its state writes
     * reach the window's recomposer through Compose's GlobalSnapshotManager, which posts the apply notification on
     * the same process-wide main dispatcher (stale after the first test); the harness sends it right after the
     * write instead, as that post would before the next frame.
     */
    private fun render(controller: OverlayController, visibility: OverlayVisibility) {
        runBlocking {
            suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                OverlayController::class.java.getDeclaredMethod("render", CoroutineScope::class.java, OverlayVisibility::class.java,
                    Continuation::class.java).apply { isAccessible = true }.invoke(controller, this, visibility, continuation)
            }
        }
        Snapshot.sendApplyNotifications()
    }

    private inline fun <reified T> dao(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "observe" -> flowOf(null)
            "observeVersions" -> flowOf(emptyList<mx.kompara.data.db.entity.CostVersionEntity>())
            "forSession" -> null
            "record" -> Unit
            else -> error("Unexpected DAO call: ${method.name}")
        }
    } as T

    private companion object {
        val LIFE = Regex("TEMP DIAG window life ms=(\\d+) preDraws=(\\d+) compositions=(\\d+) " +
            "lastPreDrawMsAgo=(-?\\d+) lastCompositionMsAgo=(-?\\d+) placed=(\\d+)")
        /** Longer than any replay: a VSYNC freeze's withheld frame. */
        val VSYNC_GAP: Duration = Duration.ofSeconds(60)
        /** Round 3: how long a held capture keeps the next vsync away while the frame's events are handled. */
        val HELD_FRAME_GAP: Duration = Duration.ofMillis(100)
        /** Round 2b: where this harness's ordinary placement docks the second tall card's verdict (its chip is 611 px wide). */
        val TALL2_VERDICT = ScreenRect(433, 261, 1044, 429)
    }
}
