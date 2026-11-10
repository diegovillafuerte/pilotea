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
import kotlinx.coroutines.flow.flowOf
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

    private enum class Freeze { NONE, COMPOSITION, TRAVERSALS }

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

    @OptIn(InternalComposeUiApi::class)
    @After fun thaw() {
        WindowRecomposerPolicy.setFactory(WindowRecomposerFactory.LifecycleAware)
        FreezableViewRoot.frozen = null
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
    }

    /**
     * Replays [frames] at the phone's cadence (one capture every 900 ms). Before each later frame it checks,
     * like the screenshot script, the claim the frame line would carry against the window's last drawn frame.
     * [freezeAtReveal] stops the FIRST window right after its reveal pre-draw; [freezeBefore] stops whatever
     * window is attached just before that frame index. From [goneFrom] on, frames show no offer (SoftHidden).
     */
    private fun replay(frames: Int, geometry: (Long, HostRect?) -> HostGeometry, freezeAtReveal: Freeze = Freeze.NONE,
        showingAfterFirstMeasure: Boolean = false, freezeBefore: Pair<Int, Freeze>? = null, goneFrom: Int = Int.MAX_VALUE,
        state: (Int) -> VerdictChipState = { rated }, session: (Int) -> String = { "s" }): Replay {
        val replay = Replay(controller())
        ShadowLog.clear()
        var at = System.currentTimeMillis()
        var froze = false
        for (i in 0 until frames) {
            if (i > 0) check(replay, i)
            freezeBefore?.takeIf { it.first == i }?.let { freeze(replay.controller, it.second) }
            val claim = ScreenReaderState.overlayChipRect
            val event = OfferEvent.Parsed(uber, at, OfferCard(platform = uber, fare = 79.45, sessionId = session(i),
                hostGeometry = geometry(at, claim?.let { HostRect(it.left - 8, it.top - 8, it.right + 8, it.bottom + 8) })))
            if (i >= goneFrom) {
                render(replay.controller, OverlayVisibility.SoftHidden)
                repeat(60) { frame(); watch(replay) {} }
                at += 900
                continue
            }
            if (i == 0 || session(i) != session(i - 1)) {
                render(replay.controller, OverlayVisibility.Reading(session(i), event))
                watch(replay) { view ->
                    if (freezeAtReveal != Freeze.NONE && !froze && view.alpha == 1f) {
                        froze = true
                        replay.revealedWidth = view.width
                        freeze(replay.controller, freezeAtReveal)
                    }
                }
                if (showingAfterFirstMeasure && i == 0) frame()
            }
            render(replay.controller, OverlayVisibility.Showing(state(i), session(i), event))
            watch(replay) {}
            repeat(60) { frame(); watch(replay) {} }
            at += 900
        }
        check(replay, frames)
        return replay
    }

    private fun frame() = looper.idleFor(Duration.ofMillis(15))

    /** Records every pre-draw of the current window (the frame it is about to draw), once per window. */
    private fun watch(replay: Replay, onPreDraw: (ComposeView) -> Unit) {
        val view = field("composeView").get(replay.controller) as ComposeView? ?: return
        if (view in replay.screens) return
        replay.screens[view] = null
        view.viewTreeObserver.addOnPreDrawListener {
            onPreDraw(view)
            if (field("composeView").get(replay.controller) === view && view.width > 0) {
                val location = IntArray(2).also(view::getLocationOnScreen)
                replay.screens[view] = Drawn(ScreenRect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                    (ink(view) * view.alpha).toInt(), spoken(view))
            }
            true
        }
    }

    private fun freeze(controller: OverlayController, how: Freeze) {
        val view = field("composeView").get(controller) as ComposeView? ?: return
        when (how) {
            Freeze.COMPOSITION -> (view.compositionContext as Recomposer).cancel()
            Freeze.TRAVERSALS -> FreezableViewRoot.frozen = view.parent
            Freeze.NONE -> Unit
        }
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
            val lines = ShadowLog.getLogsForTag("OverlayController").map { it.msg }
            val after = lines.drop(lines.indexOfFirst { "TEMP DIAG window reveal" in it })
            assertEquals("The steady card's 3 later frames", 3, after.count { "TEMP DIAG show" in it })
            assertTrue("Every later show is followed by a place line: " + after.map { it.substringBefore(" at=").substringBefore(" rect=") },
                after.zipWithNext().all { (a, b) -> "TEMP DIAG show" !in a || "TEMP DIAG place" in b })
            assertEquals("Drawn once, at the end of its entrance", 1, lines.count { "TEMP DIAG window drawn" in it })
            assertFalse(lines.any { "UNDRAWN" in it || "undrawn" in it })
        } finally { replay.controller.stop() }
    }

    private fun undrawn() = ShadowLog.getLogsForTag("OverlayController").single { "TEMP DIAG window undrawn" in it.msg }.msg

    @Test fun `3f3c a window whose composition stops at the reveal does not keep claiming the chip`() {
        val replay = replay(4, ::steady, freezeAtReveal = Freeze.COMPOSITION)
        try {
            assertEquals("Only the frozen window may miss; the next window draws", emptyList<String>(), replay.violations)
            assertNotNull("A fresh window shows the chip again", ScreenReaderState.overlayChipRect)
            assertTrue(ShadowLog.getLogsForTag("OverlayController").any { "chip hidden reason=UNDRAWN" in it.msg })
            assertTrue("The plate never entered", "presence=false/false" in undrawn())
        } finally { replay.controller.stop() }
    }

    @Test fun `35d9 the Radar card revealed at its first-measure width does not keep claiming the chip`() {
        val replay = replay(4, ::radar, freezeAtReveal = Freeze.COMPOSITION, showingAfterFirstMeasure = true)
        try {
            val reveal = ShadowLog.getLogsForTag("OverlayController").first { "TEMP DIAG window reveal" in it.msg }.msg
            assertEquals("Revealed with the Reading plate's width, before the rated pill was measured", 571, replay.revealedWidth)
            assertTrue(reveal, "TEMP DIAG window reveal rect=473,202,1044,370" in reveal)
            assertEquals(emptyList<String>(), replay.violations)
            assertNotNull(ScreenReaderState.overlayChipRect)
            assertTrue("Never re-measured with the rated pill", undrawn().let { "presence=false/false" in it && "composed=false" in it })
        } finally { replay.controller.stop() }
    }

    @Test fun `a window whose traversals stop at the reveal does not keep claiming the chip`() {
        val replay = replay(4, ::steady, freezeAtReveal = Freeze.TRAVERSALS)
        try {
            assertEquals(emptyList<String>(), replay.violations)
            assertNotNull(ScreenReaderState.overlayChipRect)
            assertTrue("The plate entered and was composed, but no traversal drew it", undrawn().let {
                "presence=true/true" in it && "composed=true" in it })
        } finally { replay.controller.stop() }
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

    private fun controller(): OverlayController {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No writes")
        }
        val settings = SettingsRepository(store)
        val readiness = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) = Unit
        }, Clock.systemUTC()).apply { supportedApps(setOf(uber, didi)); bound(); captureRunning(true) }
        return OverlayController(OfferRecorder(dao<OfferDao>(), CostProfileRepository(dao<CostProfileDao>()), settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(dao<OfferPresentationDao>()), readiness).also {
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
}
