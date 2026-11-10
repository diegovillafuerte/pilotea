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
    /** v4 (WP-B): an Uber offer's chip is the plain pill, 205 x 56 dp at density 3, as both of the placer's sizes. */
    private val fullSize = ChipSize(615, 168)
    private val compactSize = ChipSize(615, 168)

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(File("../ocr/src/test/resources/uber_priority_s25_2026_10_29.json").readText())
        return (0 until json.length()).map { i ->
            val b = json.getJSONObject(i)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }.filterNot { it.text in setOf("Sin lectura", "falta la tarifa", "tu meta $10.00", "tu mínimo $7.50") }
    }

    // 0.2.14: the 0.2.10/0.2.11 initial-boundary and two-capture gates detached the first frame
    // of every real offer. A card whose upper boundary is unobserved now docks at once (main's
    // validated default); the card ceiling only rises within an offer, so a chip never moves down.
    @Test fun `first heading dropout docks at once clear of the close and never oscillates`() =
        replaySlideIn(missingFrames = 1)

    @Test fun `multiple slide-in frames dock instead of waiting for upper-card evidence`() =
        replaySlideIn(missingFrames = 3)

    @Test fun `translated S25 slide-in is displayed every frame and only ever moves up`() =
        replayTranslation(listOf(250 to 0L, 100 to 900L, 0 to 1800L, 0 to 2700L, 0 to 3600L, 0 to 4500L))

    @Test fun `paused translation and repeated rendering never move the chip down`() =
        replayTranslation(listOf(250 to 0L, 250 to 900L, 100 to 1800L, 0 to 2700L,
            0 to 3600L, 0 to 4500L, 0 to 5400L))

    /** The controller's keep-or-place: a safe chip stays; otherwise the contract re-places it. */
    private fun place(evidence: PlacementEvidence, event: OfferEvent.Parsed,
        shown: OverlayPlacement.Displayed?): OverlayPlacement {
        val decision = evidence.observe(requireNotNull(event.card.hostGeometry), viewport)
        if (shown != null && !(decision.dock && !shown.docked) && OverlayPositioning.valid(shown.position,
                shown.size, decision.geometry, viewport, shown.overMarker, shown.docked)) return shown
        return OverlayPositioning.place(decision.geometry, event.packageName, viewport, event.timestampMs,
            fullSize, compactSize, dock = decision.dock)
    }

    private fun replayTranslation(frames: List<Pair<Int, Long>>) = runTest {
        val pipeline = OfferFramePipeline()
        val evidence = PlacementEvidence()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        val full = fixture()
        var shown: OverlayPlacement.Displayed? = null
        val tops = mutableListOf<Int>()
        val start = System.currentTimeMillis()
        frames.forEachIndexed { index, (shift, elapsed) ->
            // Translate the entire card, including heading, close, fare and action. Background
            // map labels stay still; every translated card block remains inside this display.
            val frame = full.map { block -> if (block.bounds.top < close.top) block else
                block.copy(bounds = block.bounds.copy(top = block.bounds.top + shift,
                    bottom = block.bounds.bottom + shift)) }
            assertTrue(frame.all { it.bounds.bottom <= viewport.bounds.bottom })
            val mask = shown?.position?.bounds(shown!!.size)?.let { ScreenRect(it.left, it.top, it.right, it.bottom) }
            pipeline.processOwnedFrame(frame, mask, null, capturedAtMs = start + elapsed,
                displayBounds = viewport.bounds, density = viewport.density,
                foregroundPackage = UberOcrParser.UBER_PACKAGE)
            testScheduler.runCurrent()
            assertEquals("The complete card is recognized in every animation frame", index + 1, parsed.size)
            shown = place(evidence, parsed.last(), shown) as? OverlayPlacement.Displayed
            assertNotNull("Frame $index is displayed without waiting for another capture", shown)
            val rect = shown!!.position.bounds(shown!!.size)
            assertFalse("Frame $index must clear the moving close", rect.overlaps(close.copy(
                top = close.top + shift, bottom = close.bottom + shift)))
            tops += rect.top
        }
        assertTrue("Within one offer the chip never moves down: $tops", tops.zipWithNext().all { (a, b) -> b <= a })
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
        var session: String? = null
        val placements = mutableListOf<OverlayPlacement.Displayed>()
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
            shown = place(evidence, event, shown) as? OverlayPlacement.Displayed
            assertNotNull("Frame $index is displayed: an unobserved boundary docks, never hides", shown)
            assertFalse("Frame $index must not mask the restored close control",
                shown!!.position.bounds(shown!!.size).overlaps(close))
            if (index < missingFrames) assertTrue("No upper anchor yet: the top dock", shown!!.docked)
            placements += shown!!
        }
        assertEquals("The docked chip never oscillates once the heading appears", 1, placements.distinct().size)
    }
}
