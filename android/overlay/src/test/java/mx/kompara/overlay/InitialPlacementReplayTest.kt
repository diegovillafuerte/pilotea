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
