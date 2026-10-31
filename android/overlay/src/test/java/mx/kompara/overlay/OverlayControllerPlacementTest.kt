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
import kotlinx.coroutines.flow.MutableSharedFlow
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
import mx.kompara.parsers.model.HostGeometryDeriver
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostOcrGeometryEvidence
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.HostTextBounds
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

    // 0.2.14: the initial-boundary / two-capture gates detached the first frame of every real
    // offer (12:04:14, 13:12:09...). An unobserved upper boundary now docks at once instead.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `initial fare-only geometry attaches at once and asks for the top dock`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            val pending = geometry.copy(displayBounds = v.bounds,
                offerCard = HostRect(0, 722, v.bounds.right, v.bounds.bottom),
                source = HostGeometrySource.OCR_TEXT_BOUNDS,
                ocrEvidence = HostOcrGeometryEvidence(null, emptyList(),
                    listOf(HostRect(190, 1420, 510, 1477), HostRect(190, 1625, 541, 1682)), false))
            repeat(3) {
                val event = OfferEvent.Parsed(pending.packageName, pending.capturedAtMs,
                    OfferCard(platform = pending.packageName, fare = 79.45, sessionId = "slide-in",
                        hostGeometry = pending))
                render(controller, backgroundScope, OverlayVisibility.Showing(rated, "slide-in", event))
                assertNotNull("A fare-only first frame is never left without a chip", field("composeView").get(controller))
                assertEquals(true, (field("dockRequested\$delegate").get(controller) as MutableState<*>).value)
            }
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `replacement swaps content in place and restarts only its placement evidence`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            val observed = geometry.copy(displayBounds = v.bounds,
                offerCard = HostRect(0, 605, v.bounds.right, v.bounds.bottom),
                source = HostGeometrySource.OCR_TEXT_BOUNDS,
                ocrEvidence = HostOcrGeometryEvidence(null, listOf(HostRect(40, 653, 600, 710)),
                    listOf(HostRect(190, 1420, 510, 1477), HostRect(190, 1625, 541, 1682)), false))
            val first = OfferEvent.Parsed(observed.packageName, observed.capturedAtMs,
                OfferCard(platform = observed.packageName, fare = 79.45, sessionId = "old-card", hostGeometry = observed))
            render(controller, backgroundScope, OverlayVisibility.Reading("old-card", first))
            render(controller, backgroundScope, OverlayVisibility.Showing(rated, "old-card", first))
            val window = field("composeView").get(controller)
            assertNotNull("A heading-bounded card is displayed on its first frame", window)
            assertEquals(false, (field("dockRequested\$delegate").get(controller) as MutableState<*>).value)

            val partial = observed.copy(offerCard = observed.offerCard.copy(top = 722),
                ocrEvidence = observed.ocrEvidence!!.copy(upperAnchors = emptyList()))
            val replacement = first.copy(card = first.card.copy(sessionId = "replacement", fare = null,
                hostGeometry = partial))
            // A corroborated replacement may emit Reading directly, without an intervening Hidden.
            render(controller, backgroundScope, OverlayVisibility.Reading("replacement", replacement))
            assertSame("Back-to-back offers keep the window: no blink between them", window,
                field("composeView").get(controller))
            assertEquals("The old card's upper band cannot vouch for the replacement: it docks", true,
                (field("dockRequested\$delegate").get(controller) as MutableState<*>).value)
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

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
            val rowAgreement = RowBackedAgreement()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                OverlayStateMachine(evaluate = { event ->
                    rowAgreement.check(event) // Round 3 (A3): the row the chip is painted from agrees
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
            // This harness starts with an already verified window. Establish its corresponding
            // two-capture geometry history too; the following four frames test payment dropout,
            // not first attachment of an unknown layout at this fixture's mdpi viewport.
            val at = System.currentTimeMillis() - 1_000
            val settledGeometry = requireNotNull(HostGeometryDeriver.offer("com.ubercab.driver", at,
                v.bounds, v.density, full.filter { it.bounds.top >= 650 }.map {
                    HostTextBounds(it.text, HostRect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom))
                }, HostGeometrySource.OCR_TEXT_BOUNDS, recognizedOffer = true))
            val placementHistory = field("placementEvidence").get(controller) as PlacementEvidence
            assertFalse(placementHistory.observe(settledGeometry, v).dock)
            state(controller, "geometry", settledGeometry.copy(capturedAtMs = at + 500))
            val pipeline = OfferFramePipeline()
            for (frame in listOf(full, unread, unread, full)) {
                pipeline.processOwnedFrame(frame, ScreenRect(325, 242, 1040, 572), null,
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
            pipeline.processOwnedFrame(full, null, null, displayBounds = v.bounds, density = v.density, foregroundPackage = "com.ubercab.driver")
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
                pipeline.processOwnedFrame(grown, ScreenRect(rect.left, rect.top, rect.right, rect.bottom), null,
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
                assertNotNull("Masked frames never detach the window", field("composeView").get(controller))
                shown = measuredPlacement(controller, next, v)
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
            pipeline.processOwnedFrame(full, null, null, displayBounds = v.bounds, density = v.density, foregroundPackage = "com.ubercab.driver")
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
                pipeline.processOwnedFrame(replayFrame, ScreenRect(rect.left, rect.top, rect.right, rect.bottom), null,
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
                assertNotNull("Masked frames never detach the window", field("composeView").get(controller))
                shown = measuredPlacement(controller, next, v)
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
    @Test fun `independently moving close control never detaches and is avoided once observed`() = runTest {
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
            pipeline.processOwnedFrame(full, null, null, displayBounds = v.bounds, density = v.density,
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
            pipeline.processOwnedFrame(moved, ScreenReaderState.overlayChipRect, null, displayBounds = v.bounds,
                density = v.density, foregroundPackage = "com.ubercab.driver")
            testScheduler.runCurrent()
            val hidden = parsed.last()
            assertEquals(first.card.sessionId, hidden.card.sessionId)
            assertEquals(605, hidden.card.hostGeometry!!.offerCard.top)
            render(controller, backgroundScope, OverlayVisibility.Showing(rated, hidden.card.sessionId!!, hidden))
            // 0.2.14: a synthetic independently moving close no longer detaches (review-only case).
            assertNotNull("A masked frame never detaches the window", field("composeView").get(controller))
            // The recovery half below runs on an UNMASKED frame (e.g. after a detach). With the
            // controller's live mask the chip keeps covering the moved close (ChipMask hides it and
            // its last rect lies outside the mask, so it is never "lost"): synthetic, TD-059 (C2).
            pipeline.processOwnedFrame(moved, null, null, displayBounds = v.bounds, density = v.density,
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

    // 0.2.14: AwaitingUnobscuredCapture (detach) is gone. A signature-free frame soft-hides.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `signature-free frame soft-hides without detaching or clearing the durable verdict`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = prepared()
        try {
            val currentEvent = field("event").get(controller)
            val currentGeometry = field("geometry\$delegate").let { it.get(controller) as MutableState<*> }.value
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            val params = field("layoutParams").get(controller) as WindowManager.LayoutParams
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, placement)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1f, view.alpha)
            val chip = ScreenReaderState.overlayChipRect
            assertNotNull(chip)
            render(controller, backgroundScope, OverlayVisibility.SoftHidden)
            assertSame("The window survives a soft hide", view, field("composeView").get(controller))
            assertEquals(0f, view.alpha)
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            assertEquals("The mask stays while the compositor may still hold the plate", chip, ScreenReaderState.overlayChipRect)
            assertSame(currentEvent, field("event").get(controller))
            assertSame(currentGeometry, (field("geometry\$delegate").get(controller) as MutableState<*>).value)
            assertNotNull((field("chipState\$delegate").get(controller) as MutableState<*>).value)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("A settled pre-draw does not reveal a soft-hidden chip", 0f, view.alpha)
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `frame silence soft-hides after three seconds and the parse ceiling detaches at fifteen`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = prepared()
        try {
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, placement)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1f, view.alpha)
            val heartbeat = heartbeat()
            heartbeat.invoke(controller, backgroundScope, true, true)
            advance(2_999)
            assertEquals("A live offer keeps its chip between frames", 1f, view.alpha)
            heartbeat.invoke(controller, backgroundScope, false, true) // a held signature frame
            advance(2_999)
            assertEquals(1f, view.alpha)
            advance(2)
            assertEquals("No processed frame for 3 s: invisible and untouchable", 0f, view.alpha)
            assertSame(view, field("composeView").get(controller))
            advance(15_000)
            assertNull("15 s without a successful parse detaches", field("composeView").get(controller))
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    private fun heartbeat() = method("heartbeat", CoroutineScope::class.java,
        Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)

    /** The watchdog's deadlines are absolute on the clock: advance it with virtual time, 10 ms at a time. */
    private fun kotlinx.coroutines.test.TestScope.advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(10L, left)
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(step))
            testScheduler.advanceTimeBy(step); testScheduler.runCurrent()
            left -= step
        }
    }

    /** Beats every [everyMs] until [untilMs]; returns when the chip soft-hid and when it detached. */
    private fun kotlinx.coroutines.test.TestScope.beats(controller: OverlayController, everyMs: Long, untilMs: Long,
        beat: () -> Unit): Pair<Long?, Long?> {
        val view = field("composeView").get(controller) as ComposeView
        var elapsed = 0L
        var softHiddenAt: Long? = null
        var detachedAt: Long? = null
        while (elapsed < untilMs) {
            advance(everyMs); elapsed += everyMs
            if (softHiddenAt == null && view.alpha == 0f) softHiddenAt = elapsed
            if (detachedAt == null && field("composeView").get(controller) == null) detachedAt = elapsed
            beat()
        }
        return softHiddenAt to detachedAt
    }

    private fun revealed(controller: OverlayController) {
        method("attach").invoke(controller)
        val view = field("composeView").get(controller) as ComposeView
        view.layout(0, 0, 720, 336)
        method("applyPlacement", OverlayPlacement::class.java).invoke(controller, placement)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1f, view.alpha)
    }

    // A4 (Codex C4): unknown visibility (IDLE ticks with a null root every 750 ms, window-change
    // drops) delivers no frame, so it can postpone neither deadline.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `uncertainty beats postpone neither the soft hide nor the parse ceiling`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = prepared()
        try {
            revealed(controller)
            val heartbeat = heartbeat()
            heartbeat.invoke(controller, backgroundScope, true, true)
            val (softHiddenAt, detachedAt) = beats(controller, 750, 20_000) {
                heartbeat.invoke(controller, backgroundScope, false, false) // onCaptureUncertain: no frame
            }
            assertTrue("Soft hide 3 s after the last processed frame: $softHiddenAt", softHiddenAt in 3_000L..3_750L)
            assertTrue("Detach 15 s after the last parse: $detachedAt", detachedAt in 15_000L..15_750L)
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `held signature frames defer the soft hide but not the parse ceiling`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = prepared()
        try {
            revealed(controller)
            val heartbeat = heartbeat()
            heartbeat.invoke(controller, backgroundScope, true, true)
            val (softHiddenAt, detachedAt) = beats(controller, 850, 20_000) {
                heartbeat.invoke(controller, backgroundScope, false, true) // a held signature frame
            }
            assertTrue("Held frames keep the chip visible until the ceiling: $softHiddenAt",
                softHiddenAt == null || softHiddenAt >= 15_000L)
            assertTrue("Detach 15 s after the last parse: $detachedAt", detachedAt in 15_000L..15_850L)
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    /** The real event path: start() with a controlled bus and a real ledger behind the evaluation. */
    private suspend fun kotlinx.coroutines.test.TestScope.started(controller: OverlayController): MutableSharedFlow<OfferEvent> {
        val events = MutableSharedFlow<OfferEvent>(extraBufferCapacity = 64)
        controller.start(backgroundScope, events, context, flowOf(null))
        testScheduler.runCurrent()
        val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
        val live = geometry.copy(displayBounds = v.bounds, capturedAtMs = System.currentTimeMillis())
        events.emit(OfferEvent.Parsed(live.packageName, live.capturedAtMs, OfferCard(platform = live.packageName,
            fare = 79.45, pickupDistanceKm = 0.2, pickupEtaMin = 1.0, tripDistanceKm = 2.4, tripDurationMin = 24.0,
            paymentBasis = "DRIVER_PAYMENT", captureSource = "OCR", sessionId = "live", sessionStartedAt = live.capturedAtMs,
            hostGeometry = live)))
        testScheduler.runCurrent()
        assertNotNull("The Parsed attached the chip", field("composeView").get(controller))
        assertEquals(false, field("softHidden").get(controller))
        return events
    }

    // B5 (round 3): the event path's LayoutUncertain -> heartbeat(processed = processedFrame) wiring.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `unknown visibility beats on the event path never defer the soft hide but held frames do`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        for (processed in listOf(false, true)) {
            val controller = controller(BoundaryOfferDao(), BoundaryCostProfileDao())
            try {
                val events = started(controller)
                var elapsed = 0L
                var softHiddenAt: Long? = null
                var detachedAt: Long? = null
                while (elapsed < 16_500) {
                    advance(750); elapsed += 750
                    val view = field("composeView").get(controller) as ComposeView?
                    if (softHiddenAt == null && field("softHidden").get(controller) == true && view?.alpha == 0f) softHiddenAt = elapsed
                    if (detachedAt == null && view == null) detachedAt = elapsed
                    events.emit(OfferEvent.LayoutUncertain(geometry.packageName, System.currentTimeMillis(), processedFrame = processed))
                    testScheduler.runCurrent()
                }
                if (processed) assertTrue("Held frames keep the chip until the ceiling: $softHiddenAt",
                    softHiddenAt == null || softHiddenAt >= 15_000L)
                else assertTrue("Unknown-visibility beats: soft hide 3 s after the Parsed: $softHiddenAt", softHiddenAt in 3_000L..3_750L)
                assertTrue("Detach 15 s after the Parsed: $detachedAt", detachedAt in 15_000L..15_750L)
            } finally { controller.stop() }
        }
        Dispatchers.resetMain()
    }

    // B3 (round 3): the parse-ceiling detach leaves nothing a configuration change could re-show.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `a configuration change after the parse ceiling never re-shows the stale verdict`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(BoundaryOfferDao(), BoundaryCostProfileDao())
        try {
            started(controller)
            advance(15_010)
            assertNull("15 s without a parse detaches", field("composeView").get(controller))
            assertNull("The stale event is cleared", field("event").get(controller))
            assertNull((field("chipState\$delegate").get(controller) as MutableState<*>).value)
            assertEquals(false, (field("chipVisible\$delegate").get(controller) as MutableState<*>).value)
            (field("configurationCallbacks").get(controller) as android.content.ComponentCallbacks)
                .onConfigurationChanged(context.resources.configuration)
            testScheduler.runCurrent()
            assertNull("A configuration change cannot re-attach a 15 s-stale verdict", field("composeView").get(controller))
        } finally { controller.stop(); Dispatchers.resetMain() }
    }

    // B4 (round 3): heartbeats run on the collector's thread while the watchdog runs on Main. A
    // Parsed landing INSIDE the soft-hide iteration (here: during its window update) must not leave
    // the loop asleep until the stale ceiling; the next soft hide comes 3 s after that Parsed.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `a parse landing inside the soft-hide iteration never delays the next soft hide`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = prepared()
        val heartbeat = heartbeat()
        var inside: (() -> Unit)? = null
        val real = context.getSystemService(WindowManager::class.java)
        val windows = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, m, args ->
            if (m.name == "updateViewLayout") inside?.also { inside = null }?.invoke()
            m.invoke(real, *(args ?: emptyArray()))
        } as WindowManager
        field("overlayContext").set(controller, object : android.content.ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) windows else super.getSystemService(name)
        })
        try {
            revealed(controller)
            val view = field("composeView").get(controller) as ComposeView
            heartbeat.invoke(controller, backgroundScope, true, true)
            inside = { heartbeat.invoke(controller, backgroundScope, true, true) }
            advance(3_010)
            assertEquals("The first soft hide fired", 0f, view.alpha)
            assertNull("The Parsed landed inside that iteration", inside)
            // That Parsed's Showing render reveals the chip again (render: softHidden = false, re-reveal).
            field("softHidden").set(controller, false); view.alpha = 1f
            advance(2_900)
            assertEquals("Visible until 3 s after the Parsed", 1f, view.alpha)
            advance(850)
            assertEquals("The next soft hide comes 3 s after the Parsed, not at the stale ceiling", 0f, view.alpha)
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

    /** What MeasuredVerdictChip computes from the controller's state for this frame. */
    private fun measuredPlacement(controller: OverlayController, next: OfferEvent.Parsed, v: OverlayViewport) =
        OverlayPositioning.place((field("geometry\$delegate").get(controller) as MutableState<*>).value as HostGeometry,
            next.packageName, v, next.timestampMs, ChipSize(720, 336), ChipSize(720, 200),
            (field("savedPosition\$delegate").get(controller) as MutableState<*>).value as OverlayPosition?,
            (field("dockRequested\$delegate").get(controller) as MutableState<*>).value as Boolean,
            (field("savedDocked\$delegate").get(controller) as MutableState<*>).value as Boolean) as OverlayPlacement.Displayed

    private fun prepared() = controller().also {
        ScreenLocation.reads = 0; ScreenLocation.position = placement.position
        state(it, "geometry", geometry); state(it, "viewport", viewport); state(it, "chipState", reading)
        field("event").set(it, OfferEvent.Parsed(geometry.packageName, geometry.capturedAtMs,
            OfferCard(platform = geometry.packageName, hostGeometry = geometry)))
    }

    private fun controller(offers: OfferDao = dao(), costs: CostProfileDao = dao()): OverlayController {
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
        return OverlayController(OfferRecorder(offers, CostProfileRepository(costs), settings, NetProfitEngine()),
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
