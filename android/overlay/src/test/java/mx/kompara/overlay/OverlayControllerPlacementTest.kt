package mx.kompara.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.OfferCard
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaPosition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowViewGroup
import org.robolectric.shadows.ShadowLog
import org.json.JSONArray
import java.io.File
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Clock
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2340dp-mdpi", shadows = [OverlayControllerPlacementTest.ScreenLocation::class, OverlayControllerPlacementTest.GroupScreenLocation::class])
class OverlayControllerPlacementTest {
    /** Model the WM screen transform explicitly; the JVM has no real display compositor. */
    @Implements(View::class)
    open class ScreenLocation : ShadowViewGroup() {
        @Implementation fun getLocationOnScreen(location: IntArray) {
            reads++
            location[0] = position.x; location[1] = position.y
        }
        companion object {
            var position = OverlayPosition(324, 243)
            var reads = 0
        }
    }

    @Implements(ViewGroup::class)
    class GroupScreenLocation : ScreenLocation()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val viewport = OverlayViewport(HostRect(0, 0, 1080, 2340), 3f, HostOrientation.PORTRAIT)
    private val geometry = HostGeometry("com.ubercab.driver", "fake-live-frame", System.currentTimeMillis(),
        viewport.bounds, viewport.orientation, HostRect(0, 0, 1080, 120), HostRect(48, 627, 1032, 2140),
        emptyList(), emptyList())
    private val placement = OverlayPlacement.Displayed(OverlayPosition(324, 243), ChipSize(720, 336), PlateFormat.FULL)
    private val reading = VerdictChipState.reading(PlatformThreshold.DEFAULT, PreferredMetric.IPK)
    private val rated = VerdictChipState(reading.copy(band = ReglaBand.Rated(ReglaPosition.ABOVE, BigDecimal("12"))), "15")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `read unread unread read frames preserve visible window position and verdict`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            ShadowLog.clear()
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            val shown = OverlayPlacement.Displayed(OverlayPosition(12, 300), ChipSize(240, 112), PlateFormat.FULL)
            val view = ComposeView(context).apply { alpha = 1f }
            val params = WindowManager.LayoutParams().apply { x = 12; y = 300 }
            field("composeView").set(controller, view)
            field("placement").set(controller, shown)
            field("layoutParams").set(controller, params)
            state(controller, "viewport", v)
            val seen = mutableListOf<OverlayVisibility>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                OverlayStateMachine(evaluate = { event ->
                    VerdictChipState.from(NetProfitEngine().evaluate(OfferMapping.toTripOffer(event.card),
                        "2.84".toBigDecimal(), PlatformThreshold.DEFAULT, PreferredMetric.IPK), PlatformThreshold.DEFAULT)
                }).visibility(OfferEventBus.events).collect {
                    seen += it
                    // The first Reading precedes the verified window supplied by this compositor
                    // harness. Any subsequent Reading would detach it in the actual controller.
                    if (it is OverlayVisibility.Showing) render(controller, backgroundScope, it)
                }
            }
            val json = JSONArray(File("../ocr/src/test/resources/uber_priority_s25_2026_10_29.json").readText())
            val full = (0 until json.length()).map { index ->
                val b = json.getJSONObject(index)
                OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                    b.getInt("right"), b.getInt("bottom")))
            }
            val unread = full.filterNot { it.text == "$79.45" }
            val pipeline = OfferFramePipeline()
            for (frame in listOf(full, unread, unread, full)) {
                pipeline.process(frame, ScreenRect(325, 242, 1040, 572), null,
                    displayBounds = v.bounds, density = v.density, foregroundPackage = "com.ubercab.driver")
                testScheduler.runCurrent()
                assertTrue((seen.last() as OverlayVisibility.Showing).state.regla.band is ReglaBand.Rated)
                assertEquals(1f, view.alpha)
                assertSame(view, field("composeView").get(controller))
                assertSame(shown, field("placement").get(controller))
                assertSame(params, field("layoutParams").get(controller))
                assertEquals(12, params.x); assertEquals(300, params.y)
                assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                assertEquals(shown.position, (field("savedPosition\$delegate").get(controller) as MutableState<*>).value)
            }
            assertEquals(1, seen.filterIsInstance<OverlayVisibility.Reading>().size)
            assertTrue(seen.all { it is OverlayVisibility.Reading || it is OverlayVisibility.Showing })
            assertFalse(ShadowLog.getLogsForTag("OverlayController").any { "LAYOUT_PENDING" in it.msg })
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    @Test fun `grown legacy card cannot pin the chip over its masked close control`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            assertEquals(viewport.bounds, v.bounds)
            assertEquals(3f, v.density)
            fun box(text: String, left: Int, top: Int, right: Int, bottom: Int) =
                OcrBlock(text, OcrBounds(left, top, right, bottom))
            val full = listOf(
                box("Uber Priority", 96, 653, 502, 746),
                box("Exclusivo", 530, 653, 785, 746),
                box("X", 837, 650, 983, 793),
                box("MXN79.45", 96, 770, 499, 897),
                box("A 1 min (0.2 km)", 190, 1420, 510, 1477),
                box("Viaje: 24 min (2.4 km)", 190, 1625, 741, 1682),
                box("Aceptar", 442, 1989, 640, 2046),
            )
            val grown = full.map { if (it.text == "Aceptar") it else
                it.copy(bounds = it.bounds.copy(top = it.bounds.top - 250, bottom = it.bounds.bottom - 250)) }
            val close = HostRect(837, 400, 983, 543)
            val accept = HostRect(442, 1989, 640, 2046)
            val pipeline = OfferFramePipeline()
            val parsed = mutableListOf<OfferEvent.Parsed>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
            }
            pipeline.process(full, null, null, displayBounds = v.bounds, density = v.density, foregroundPackage = "com.ubercab.driver")
            testScheduler.runCurrent()
            val first = parsed.single()
            assertEquals(605, first.card.hostGeometry!!.offerCard.top)
            var shown = OverlayPositioning.place(first.card.hostGeometry, first.packageName, v,
                first.timestampMs, ChipSize(720, 336), ChipSize(720, 200)) as OverlayPlacement.Displayed
            assertEquals(OverlayPosition(324, 221), shown.position)
            assertTrue(shown.position.bounds(shown.size).overlaps(close))
            @Suppress("UNCHECKED_CAST")
            val savedPositions = field("savedPositions").get(controller) as MutableMap<OverlayPositionKey, OverlayPosition?>
            savedPositions[OverlayPositionKey(first.packageName, v.orientation)] = shown.position
            val view = ComposeView(context).apply { alpha = 1f }
            field("composeView").set(controller, view)
            field("layoutParams").set(controller, WindowManager.LayoutParams().apply {
                x = shown.position.x; y = shown.position.y
            })
            state(controller, "geometry", first.card.hostGeometry)
            state(controller, "viewport", v)
            field("event").set(controller, first)
            repeat(5) {
                field("placement").set(controller, shown)
                val rect = shown.position.bounds(shown.size)
                pipeline.process(grown, ScreenRect(rect.left, rect.top, rect.right, rect.bottom), null,
                    displayBounds = v.bounds, density = v.density, foregroundPackage = "com.ubercab.driver")
                testScheduler.runCurrent()
                val next = parsed.last()
                assertEquals(it + 2, parsed.size)
                if (it == 0) {
                    assertEquals("The masked frame must exercise the same-instance placement path",
                        first.card.sessionId, next.card.sessionId)
                    assertEquals(1122, next.card.hostGeometry!!.offerCard.top)
                }
                render(controller, backgroundScope, OverlayVisibility.Showing(rated, next.card.sessionId!!, next))
                val saved = (field("savedPosition\$delegate").get(controller) as MutableState<*>).value as OverlayPosition?
                shown = OverlayPositioning.place(next.card.hostGeometry, next.packageName, v,
                    next.timestampMs, ChipSize(720, 336), ChipSize(720, 200), saved) as OverlayPlacement.Displayed
                assertFalse("Frame $it must relocate away from the close control",
                    shown.position.bounds(shown.size).overlaps(close))
                assertFalse("Frame $it must keep Aceptar clear", shown.position.bounds(shown.size).overlaps(accept))
            }
            assertFalse("Self-masked geometry must not preserve a chip over the host close control",
                shown.position.bounds(shown.size).overlaps(close))
            assertFalse(shown.position.bounds(shown.size).overlaps(accept))
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    @Test fun `malformed grown pickup retains unobscured evidence through two one two anchors`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            assertEquals(viewport.bounds, v.bounds)
            assertEquals(3f, v.density)
            fun box(text: String, left: Int, top: Int, right: Int, bottom: Int) =
                OcrBlock(text, OcrBounds(left, top, right, bottom))
            val full = listOf(
                box("Uber Priority", 96, 653, 502, 746),
                box("Exclusivo", 530, 653, 785, 746),
                box("X", 837, 650, 983, 793),
                box("MXN79.45", 96, 770, 499, 897),
                box("A 1 min (0.2 km)", 190, 1420, 510, 1477),
                box("Viaje: 24 min (2.4 km)", 190, 1625, 741, 1682),
                box("Aceptar", 442, 1989, 640, 2046),
            )
            val grown = full.map { if (it.text == "Aceptar") it else
                it.copy(bounds = it.bounds.copy(top = it.bounds.top - 250, bottom = it.bounds.bottom - 250)) }
            val close = HostRect(837, 400, 983, 543)
            val accept = HostRect(442, 1989, 640, 2046)
            val pipeline = OfferFramePipeline()
            val parsed = mutableListOf<OfferEvent.Parsed>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
            }
            pipeline.process(full, null, null, displayBounds = v.bounds, density = v.density, foregroundPackage = "com.ubercab.driver")
            testScheduler.runCurrent()
            val first = parsed.single()
            assertEquals(605, first.card.hostGeometry!!.offerCard.top)
            var shown = OverlayPositioning.place(first.card.hostGeometry, first.packageName, v,
                first.timestampMs, ChipSize(720, 336), ChipSize(720, 200)) as OverlayPlacement.Displayed
            assertEquals(OverlayPosition(324, 221), shown.position)
            assertTrue(shown.position.bounds(shown.size).overlaps(close))
            @Suppress("UNCHECKED_CAST")
            val savedPositions = field("savedPositions").get(controller) as MutableMap<OverlayPositionKey, OverlayPosition?>
            savedPositions[OverlayPositionKey(first.packageName, v.orientation)] = shown.position
            val view = ComposeView(context).apply { alpha = 1f }
            field("composeView").set(controller, view)
            field("layoutParams").set(controller, WindowManager.LayoutParams().apply {
                x = shown.position.x; y = shown.position.y
            })
            state(controller, "geometry", first.card.hostGeometry)
            state(controller, "viewport", v)
            field("event").set(controller, first)
            repeat(5) {
                field("placement").set(controller, shown)
                val rect = shown.position.bounds(shown.size)
                val replayFrame = if (it == 0) grown.map { block ->
                    if (block.text == "A 1 min (0.2 km)") block.copy(text = "A 1 min 0.2 km)") else block
                } else grown
                pipeline.process(replayFrame, ScreenRect(rect.left, rect.top, rect.right, rect.bottom), null,
                    displayBounds = v.bounds, density = v.density, foregroundPackage = "com.ubercab.driver")
                testScheduler.runCurrent()
                val next = parsed.last()
                assertEquals(it + 2, parsed.size)
                if (it == 0) {
                    assertEquals("The masked frame must exercise the same-instance placement path",
                        first.card.sessionId, next.card.sessionId)
                    assertEquals(1122, next.card.hostGeometry!!.offerCard.top)
                }
                render(controller, backgroundScope, OverlayVisibility.Showing(rated, next.card.sessionId!!, next))
                val saved = (field("savedPosition\$delegate").get(controller) as MutableState<*>).value as OverlayPosition?
                shown = OverlayPositioning.place(next.card.hostGeometry, next.packageName, v,
                    next.timestampMs, ChipSize(720, 336), ChipSize(720, 200), saved) as OverlayPlacement.Displayed
                assertFalse("Frame $it must relocate away from the close control",
                    shown.position.bounds(shown.size).overlaps(close))
                assertFalse("Frame $it must keep Aceptar clear", shown.position.bounds(shown.size).overlaps(accept))
            }
            assertFalse("Self-masked geometry must not preserve a chip over the host close control",
                shown.position.bounds(shown.size).overlaps(close))
            assertFalse(shown.position.bounds(shown.size).overlaps(accept))
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `visible same-slot content and geometry updates never hide or reset placement`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            val g = geometry.copy(displayBounds = v.bounds, offerCard = HostRect(0, 600, v.bounds.right, 800),
                topChrome = HostRect(0, 0, v.bounds.right, 100))
            val shown = OverlayPlacement.Displayed(OverlayPosition(12, 300), ChipSize(240, 112), PlateFormat.FULL)
            val view = ComposeView(context).apply { alpha = 1f }
            field("composeView").set(controller, view)
            field("placement").set(controller, shown)
            field("layoutParams").set(controller, WindowManager.LayoutParams().apply { x = 12; y = 300 })
            state(controller, "geometry", g)
            state(controller, "viewport", v)
            state(controller, "chipState", reading)
            val next = OfferEvent.Parsed(g.packageName, g.capturedAtMs,
                OfferCard(platform = g.packageName, fare = 79.45, hostGeometry = g.copy(layoutId = "next-frame",
                    protectedControls = listOf(HostRect(0, 700, 100, 750))), sessionId = "same-offer"))
            render(controller, scope, OverlayVisibility.Showing(rated, "same-offer", next))
            assertEquals("A changed verdict must remain visible while its same footprint is remeasured", 1f, view.alpha)
            assertSame(shown, field("placement").get(controller))
            assertSame(view, field("composeView").get(controller))
            assertEquals(0, (field("layoutParams").get(controller) as WindowManager.LayoutParams).flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } finally {
            controller.stop(); scope.cancel(); Dispatchers.resetMain()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    @Test fun `independently moving close control requires unobscured recovery despite stationary routes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            fun box(text: String, left: Int, top: Int, right: Int, bottom: Int) =
                OcrBlock(text, OcrBounds(left, top, right, bottom))
            val full = listOf(box("Uber Priority", 96, 653, 502, 746),
                box("Exclusivo", 530, 653, 785, 746), box("X", 837, 650, 983, 793),
                box("MXN79.45", 96, 770, 499, 897), box("A 1 min (0.2 km)", 190, 1420, 510, 1477),
                box("Viaje: 24 min (2.4 km)", 190, 1625, 741, 1682), box("Aceptar", 442, 1989, 640, 2046))
            val moved = full.map { if (it.text != "X") it else it.copy(bounds = OcrBounds(837, 400, 983, 543)) }
            val close = HostRect(837, 400, 983, 543)
            val pipeline = OfferFramePipeline()
            val parsed = mutableListOf<OfferEvent.Parsed>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
            }
            pipeline.process(full, null, null, displayBounds = v.bounds, density = v.density,
                foregroundPackage = "com.ubercab.driver")
            testScheduler.runCurrent()
            val first = parsed.single()
            val shown = OverlayPositioning.place(first.card.hostGeometry, first.packageName, v,
                first.timestampMs, ChipSize(720, 336), ChipSize(720, 200)) as OverlayPlacement.Displayed
            assertTrue(shown.position.bounds(shown.size).overlaps(close))
            field("composeView").set(controller, ComposeView(context).apply { alpha = 1f })
            field("placement").set(controller, shown)
            state(controller, "geometry", first.card.hostGeometry)
            state(controller, "viewport", v)
            field("event").set(controller, first)
            val rect = shown.position.bounds(shown.size)
            ScreenReaderState.setOverlayChipRect(ScreenRect(rect.left, rect.top, rect.right, rect.bottom))
            pipeline.process(moved, ScreenReaderState.overlayChipRect, null, displayBounds = v.bounds,
                density = v.density, foregroundPackage = "com.ubercab.driver")
            testScheduler.runCurrent()
            val hidden = parsed.last()
            assertEquals(first.card.sessionId, hidden.card.sessionId)
            assertEquals(605, hidden.card.hostGeometry!!.offerCard.top)
            render(controller, backgroundScope, OverlayVisibility.Showing(rated, hidden.card.sessionId!!, hidden))
            assertNull("A missing close cannot be explained by stationary route anchors", field("composeView").get(controller))
            assertNull(ScreenReaderState.overlayChipRect)
            pipeline.process(moved, null, null, displayBounds = v.bounds, density = v.density,
                foregroundPackage = "com.ubercab.driver")
            testScheduler.runCurrent()
            val recovered = parsed.last()
            assertEquals(first.card.sessionId, recovered.card.sessionId)
            assertTrue(recovered.card.hostGeometry!!.protectedControls.contains(close))
            val safe = OverlayPositioning.place(recovered.card.hostGeometry, recovered.packageName, v,
                recovered.timestampMs, ChipSize(720, 336), ChipSize(720, 200)) as OverlayPlacement.Displayed
            assertFalse(safe.position.bounds(safe.size).overlaps(close))
            repeat(4) {
                assertEquals(safe, OverlayPositioning.place(recovered.card.hostGeometry, recovered.packageName, v,
                    recovered.timestampMs, ChipSize(720, 336), ChipSize(720, 200), safe.position))
            }
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `unverified masked layout clears the compositor mask without clearing durable verdict`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = prepared()
        try {
            val currentEvent = field("event").get(controller)
            val currentGeometry = field("geometry\$delegate").let { it.get(controller) as MutableState<*> }.value
            field("composeView").set(controller, ComposeView(context))
            field("placement").set(controller, placement)
            ScreenReaderState.setOverlayChipRect(ScreenRect(324, 243, 1044, 579))
            render(controller, backgroundScope, OverlayVisibility.AwaitingUnobscuredCapture)
            assertNull(field("composeView").get(controller))
            assertNull(ScreenReaderState.overlayChipRect)
            assertSame(currentEvent, field("event").get(controller))
            assertSame(currentGeometry, (field("geometry\$delegate").get(controller) as MutableState<*>).value)
            assertNotNull((field("chipState\$delegate").get(controller) as MutableState<*>).value)
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @Test fun `normal first attach reveals at first completed layout pre-draw`() {
        val controller = prepared()
        try {
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            val params = field("layoutParams").get(controller) as WindowManager.LayoutParams
            // The real 1080x2340 geometry is anchored before the size is known to WindowManager.
            assertEquals(Gravity.BOTTOM or Gravity.RIGHT, params.gravity)
            assertEquals(36, params.x)
            assertEquals(1761, params.y)
            assertEquals(0f, view.alpha)
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, placement)
            assertEquals("Measurement must not itself reveal or read a provisional screen origin", 0, ScreenLocation.reads)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("Screen verification must happen on first pre-draw", 1, ScreenLocation.reads)
            assertEquals(1f, view.alpha)
            assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } finally { controller.stop() }
    }

    @Test fun `a safe saved position also reveals on its first completed layout`() {
        val controller = prepared()
        val saved = placement.copy(position = OverlayPosition(36, 243))
        try {
            state(controller, "savedPosition", saved.position)
            ScreenLocation.position = saved.position
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            val params = field("layoutParams").get(controller) as WindowManager.LayoutParams
            assertEquals(Gravity.TOP or Gravity.LEFT, params.gravity)
            assertEquals(36, params.x); assertEquals(243, params.y)
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, saved)
            assertEquals(0, ScreenLocation.reads)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1, ScreenLocation.reads)
            assertEquals(1f, view.alpha)
        } finally { controller.stop() }
    }

    @Test fun `a pending layout never reads the provisional screen position`() {
        val controller = prepared()
        try {
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            view.layout(0, 0, 720, 336)
            field("placement").set(controller, placement)
            view.requestLayout()
            ScreenLocation.position = OverlayPosition(0, 0)
            method("settleWindow").invoke(controller)
            assertEquals(0, ScreenLocation.reads)
            assertEquals(0f, view.alpha)
        } finally { controller.stop() }
    }

    @Test fun `real movement hides until the new position has completed layout`() {
        val controller = prepared()
        try {
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, placement)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1f, view.alpha)
            val move = placement.copy(position = OverlayPosition(36, 243))
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, move)
            assertEquals(0f, view.alpha)
            assertTrue("WindowManager moves must request a fresh layout", view.isLayoutRequested)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("Never calibrate against the previous frame's coordinates", 1, ScreenLocation.reads)
            assertEquals(0f, view.alpha)
            // Simulate the next completed WM traversal, then its pre-draw screen transform.
            view.layout(0, 0, 720, 336)
            ScreenLocation.position = move.position
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1f, view.alpha)
            assertEquals(2, ScreenLocation.reads)
        } finally { controller.stop() }
    }

    private fun prepared() = controller().also {
        ScreenLocation.reads = 0; ScreenLocation.position = placement.position
        state(it, "geometry", geometry); state(it, "viewport", viewport); state(it, "chipState", reading)
        field("event").set(it, OfferEvent.Parsed(geometry.packageName, geometry.capturedAtMs,
            OfferCard(platform = geometry.packageName, hostGeometry = geometry)))
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
        }, Clock.systemUTC()).apply {
            supportedApps(setOf(geometry.packageName)); bound(); captureRunning(true)
        }
        return OverlayController(OfferRecorder(dao<OfferDao>(), CostProfileRepository(dao<CostProfileDao>()), settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(dao<OfferPresentationDao>()), readiness).also {
            field("overlayContext").set(it, context)
        }
    }

    private fun field(name: String) = OverlayController::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun method(name: String, vararg args: Class<*>) = OverlayController::class.java.getDeclaredMethod(name, *args).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    private fun state(controller: OverlayController, name: String, value: Any?) {
        (field("$name\$delegate").get(controller) as MutableState<Any?>).value = value
    }
    private suspend fun render(controller: OverlayController, scope: CoroutineScope, visibility: OverlayVisibility): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            method("render", CoroutineScope::class.java, OverlayVisibility::class.java, Continuation::class.java)
                .invoke(controller, scope, visibility, continuation)
        }
    private inline fun <reified T> dao(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "observe" -> flowOf(null)
            "observeVersions" -> flowOf(emptyList<mx.kompara.data.db.entity.CostVersionEntity>())
            else -> error("Unexpected DAO call: ${method.name}")
        }
    } as T
}
