package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.data.service.ScreenRect
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.ocr.UberOcrParser
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Slide-in replay uses the same pipeline, chip mask and placement evidence as the S25 reader. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InitialPlacementReplayTest {
    private val viewport = OverlayViewport(HostRect(0, 0, 1080, 2340), 3f, HostOrientation.PORTRAIT)
    private val close = HostRect(837, 650, 983, 793)
    private val fullSize = ChipSize(720, 336)
    private val compactSize = ChipSize(720, 200)

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(File("../ocr/src/test/resources/uber_priority_s25_2026_10_29.json").readText())
        return (0 until json.length()).map { i ->
            val b = json.getJSONObject(i)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }.filterNot { it.text in setOf("Sin lectura", "falta la tarifa", "tu meta $10.00", "tu mínimo $7.50") }
    }

    @Test fun `first heading dropout never attaches over the close and recovers without oscillation`() =
        replaySlideIn(missingFrames = 1)

    @Test fun `multiple slide-in frames wait for upper-card evidence before attaching`() =
        replaySlideIn(missingFrames = 3)

    @Test fun `translated S25 slide-in reveals immediately without ever covering settled close`() = runTest {
        val pipeline = OfferFramePipeline()
        val evidence = PlacementEvidence()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        val full = fixture()
        var shown: OverlayPlacement.Displayed? = null
        val placements = mutableListOf<OverlayPlacement.Displayed>()
        val start = System.currentTimeMillis()
        listOf(250, 100, 0, 0, 0, 0).forEachIndexed { index, shift ->
            // Translate the entire card, including heading, close, fare and action. Background
            // map labels stay still; every translated card block remains inside this display.
            val frame = full.map { block -> if (block.bounds.top < close.top) block else
                block.copy(bounds = block.bounds.copy(top = block.bounds.top + shift,
                    bottom = block.bounds.bottom + shift)) }
            assertTrue(frame.all { it.bounds.bottom <= viewport.bounds.bottom })
            val mask = shown?.position?.bounds(shown!!.size)?.let {
                ScreenRect(it.left, it.top, it.right, it.bottom)
            }
            pipeline.processOwnedFrame(frame, mask, null, capturedAtMs = start + index * 900L,
                displayBounds = viewport.bounds, density = viewport.density,
                foregroundPackage = UberOcrParser.UBER_PACKAGE)
            testScheduler.runCurrent()
            assertEquals("The complete card is recognized in every animation frame", index + 1, parsed.size)
            val event = parsed.last()
            val decision = evidence.observe(requireNotNull(event.card.hostGeometry), viewport)
            shown = OverlayPositioning.place(decision.geometry, event.packageName, viewport,
                event.timestampMs, fullSize, compactSize, shown?.position) as? OverlayPlacement.Displayed
            assertNotNull("The first complete frame reveals without another capture", shown)
            val rect = shown!!.position.bounds(shown!!.size)
            assertFalse("Frame $index must already clear the close's settled destination", rect.overlaps(close))
            assertFalse("Frame $index must clear the moving close", rect.overlaps(close.copy(
                top = close.top + shift, bottom = close.bottom + shift)))
            placements += shown!!
        }
        assertTrue("Placement can improve at most once and cannot oscillate",
            placements.zipWithNext().count { (a, b) -> a != b } <= 1)
    }

    @Test fun `paused translation and repeated rendering cannot promote before two settled captures`() = runTest {
        val pipeline = OfferFramePipeline()
        val evidence = PlacementEvidence()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        val full = fixture()
        var shown: OverlayPlacement.Displayed? = null
        val placements = mutableListOf<OverlayPlacement.Displayed>()
        val promotions = mutableListOf<Int>()
        val start = System.currentTimeMillis()
        val frames = listOf(250 to 0L, 250 to 900L, 100 to 1800L, 0 to 2700L,
            0 to 3600L, 0 to 4500L, 0 to 5400L)
        frames.forEachIndexed { index, (shift, elapsed) ->
            val frame = full.map { block -> if (block.bounds.top < close.top) block else
                block.copy(bounds = block.bounds.copy(top = block.bounds.top + shift,
                    bottom = block.bounds.bottom + shift)) }
            val mask = shown?.position?.bounds(shown!!.size)?.let { ScreenRect(it.left, it.top, it.right, it.bottom) }
            pipeline.processOwnedFrame(frame, mask, null, capturedAtMs = start + elapsed,
                displayBounds = viewport.bounds, density = viewport.density,
                foregroundPackage = UberOcrParser.UBER_PACKAGE)
            testScheduler.runCurrent()
            val event = parsed.last()
            val geometry = requireNotNull(event.card.hostGeometry)
            val decision = evidence.observe(geometry, viewport)
            if (decision.preferPreferredPosition) promotions += index
            assertFalse("Reading and Showing from one screenshot must not count as two captures",
                evidence.observe(geometry, viewport).preferPreferredPosition)
            // This is the same one-shot release of the saved provisional slot as OverlayController.
            val saved = if (decision.preferPreferredPosition) null else shown?.position
            shown = OverlayPositioning.place(decision.geometry, event.packageName, viewport,
                event.timestampMs, fullSize, compactSize, saved) as? OverlayPlacement.Displayed
            assertNotNull(shown)
            assertFalse(shown!!.position.bounds(shown!!.size).overlaps(close))
            placements += shown!!
        }
        assertEquals("Only the second distinct capture at the known destination can promote", listOf(4), promotions)
        assertEquals(1, placements.zipWithNext().count { (a, b) -> a != b })
        assertEquals(placements[0], placements[3])
        assertEquals(placements[4], placements.last())
    }

    private fun replaySlideIn(missingFrames: Int) = runTest {
        val pipeline = OfferFramePipeline()
        val evidence = PlacementEvidence()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        val full = fixture()
        val slideIn = full.filterNot { it.text in setOf("Uber Priority", "Exclusivo", "X") }
        var shown: OverlayPlacement.Displayed? = null
        var settled: OverlayPlacement.Displayed? = null
        var session: String? = null
        val start = System.currentTimeMillis()
        (List(missingFrames) { slideIn } + List(4) { full }).forEachIndexed { index, frame ->
            val mask = shown?.position?.bounds(shown!!.size)?.let {
                ScreenRect(it.left, it.top, it.right, it.bottom)
            }
            pipeline.processOwnedFrame(frame, mask, null, capturedAtMs = start + index * 900L,
                displayBounds = viewport.bounds, density = viewport.density,
                foregroundPackage = UberOcrParser.UBER_PACKAGE)
            testScheduler.runCurrent()
            assertEquals("Every frame remains a recognized card", index + 1, parsed.size)
            val event = parsed.last()
            if (session == null) session = event.card.sessionId
            assertEquals(session, event.card.sessionId)
            val decision = evidence.observe(requireNotNull(event.card.hostGeometry), viewport)
            val placement = OverlayPositioning.place(decision.geometry, event.packageName, viewport,
                event.timestampMs, fullSize, compactSize, shown?.position)
            shown = placement as? OverlayPlacement.Displayed
            assertFalse("Frame $index must not mask the restored close control",
                shown?.let { it.position.bounds(it.size).overlaps(close) } == true)
            if (index < missingFrames) {
                assertTrue("The next capture must remain unobscured", decision.needsUnobscuredCapture)
                assertTrue("An initial fare cannot validate missing upper controls", placement is OverlayPlacement.Suppressed)
                assertNull("No safe band has yet been observed", shown)
            } else {
                assertFalse("Unobscured upper evidence must finish recovery", decision.needsUnobscuredCapture)
                assertNotNull("The restored heading makes the preferred band available", shown)
                if (settled == null) settled = shown
                assertEquals("The settled chip must not oscillate", settled, shown)
            }
        }
    }
}
