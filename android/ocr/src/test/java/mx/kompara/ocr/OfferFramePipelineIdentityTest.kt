package mx.kompara.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferFramePipelineIdentityTest {
    @org.junit.Before fun foreground() {
        mx.kompara.data.service.ScreenReaderState.setForegroundHost(UberOcrParser.UBER_PACKAGE,
            android.os.SystemClock.elapsedRealtime())
    }
    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { i ->
            val b = json.getJSONObject(i)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }
    }

    /** [misreadPublishes]: the rejected frame reads another well-formed trip leg, so (A1 (a)) it
     *  publishes WITHOUT payment and in the same session instead of being held. */
    private fun replay(misreadPublishes: Boolean = false,
        rejected: (List<OcrBlock>) -> List<OcrBlock>): List<OfferEvent.Parsed> = runBlocking {
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        try {
            val pipeline = OfferFramePipeline()
            val full = fixture()
            val unread = full.filterNot { it.text == "$79.45" }
            for ((index, blocks) in listOf(full, full, rejected(full), unread).withIndex()) {
                pipeline.processOwnedFrame(blocks, ScreenRect(325, 242, 1040, 572), null,
                    displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
                yield()
                val extra = if (misreadPublishes && index >= 2) 1 else 0
                assertEquals("The third frame must be rejected before publication", (if (index < 2) index + 1 else if (index == 2) 2 else 3) + extra,
                    parsed.size)
            }
            if (misreadPublishes) {
                assertNull("The misread frame never shows a fare", parsed[2].card.fare)
                assertEquals(parsed.first().card.sessionId, parsed[2].card.sessionId)
            }
        } finally {
            collector.cancelAndJoin()
        }
        parsed
    }

    // 0.2.14 round 2 (A1 (a), accepted cost): a x10 trip-leg misread of the same offer is one
    // payment-less frame instead of a held verdict; the confirmed payment returns on the next frame.
    @Test fun `rejected changed route retains confirmed payment before unread old route returns`() {
        val parsed = replay(misreadPublishes = true) { blocks -> blocks.map {
            if (it.text == "24 min (2.4 km)") it.copy(text = "24 min (24.0 km)") else it
        } }
        assertEquals(79.45, parsed.first().card.fare!!, 0.001)
        assertEquals(79.45, parsed.last().card.fare!!, 0.001)
        assertEquals("Rejected numeric OCR noise must not split the ledger instance",
            parsed.first().card.sessionId, parsed.last().card.sessionId)
    }

    @Test fun `rejected unknown route preserves confirmed payment and session`() {
        val parsed = replay { blocks -> blocks.map {
            when (it.text) {
                "$79.45" -> it.copy(text = "$7945.00")
                "1 min (0.2 km)" -> it.copy(text = "— min (0.2 km)")
                else -> it
            }
        } }
        assertEquals(79.45, parsed.last().card.fare!!, 0.001)
        assertEquals("Rejected numeric OCR noise must not split the ledger instance",
            parsed.first().card.sessionId, parsed.last().card.sessionId)
    }

    @Test fun `rejected fare garble with unchanged complete legs still retains the known payment`() {
        val parsed = replay { blocks -> blocks.map {
            if (it.text == "$79.45") it.copy(text = "$7945.00") else it
        } }
        assertEquals(79.45, parsed.last().card.fare!!, 0.001)
        assertEquals(parsed.first().card.sessionId, parsed.last().card.sessionId)
    }

    @Test fun `independently observed idle layout breaks continuity despite stale OCR card text`() = runBlocking {
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        try {
            val pipeline = OfferFramePipeline()
            val display = HostRect(0, 0, 1080, 2340)
            val at = System.currentTimeMillis()
            val idle = mx.kompara.parsers.model.HostGeometryDeriver.idleHost(UberOcrParser.UBER_PACKAGE,
                at + 300, display, HostRect(0, 0, 1080, 200))!!
                .copy(source = mx.kompara.parsers.model.HostGeometrySource.HOST_LAYOUT)
            pipeline.processOwnedFrame(fixture(), null, null, capturedAtMs = at, displayBounds = display, density = 3f)
            pipeline.processOwnedFrame(fixture(), null, null, capturedAtMs = at + 300, displayBounds = display, density = 3f,
                hostObservation = mx.kompara.parsers.model.HostLayoutObservation(idle, true, true))
            yield()
            assertEquals("Independent disappearance must not publish stale OCR as a new record", 1, parsed.size)
            pipeline.processOwnedFrame(fixture().filterNot { it.text == "$79.45" }, null, null,
                capturedAtMs = at + 600, displayBounds = display, density = 3f)
            yield()
            assertEquals(2, parsed.size)
            assertNull(parsed.last().card.fare)
            assertNotEquals(parsed.first().card.sessionId, parsed.last().card.sessionId)
        } finally { collector.cancelAndJoin() }
    }
    // Round 2 (A4): only a processed host frame may defer the overlay's soft hide.
    @Test fun `held frames are tagged processed and unknown visibility is not`() = runBlocking {
        val events = mutableListOf<OfferEvent>()
        val collector = launch(Dispatchers.Unconfined) { OfferEventBus.events.collect { events += it } }
        try {
            val pipeline = OfferFramePipeline()
            val full = fixture()
            val garbled = full.map { if (it.text == "1 min (0.2 km)") it.copy(text = "— min (0.2 km)") else it }
                .map { if (it.text == "$79.45") it.copy(text = "$7945.00") else it }
            for (blocks in listOf(full, full, garbled)) {
                pipeline.processOwnedFrame(blocks, null, null, displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
                yield()
            }
            val held = events.filterIsInstance<OfferEvent.LayoutUncertain>()
            assertEquals("The guard-rejected garbled frame is held", 1, held.size)
            assertTrue(held.single().processedFrame)
            pipeline.onCaptureUncertain(System.currentTimeMillis())
            yield()
            assertFalse(events.filterIsInstance<OfferEvent.LayoutUncertain>().last().processedFrame)
        } finally { collector.cancelAndJoin() }
    }

    @Test fun `unknown OS visibility retains identity and breaks consecutive payment votes`() = runBlocking {
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        try {
            val pipeline = OfferFramePipeline()
            val full = fixture()
            val revision = full.map { if (it.text == "$79.45") it.copy(text = "$99.45") else it }
            suspend fun frame(blocks: List<OcrBlock>) {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(900))
                pipeline.processOwnedFrame(blocks, null, null, displayBounds = HostRect(0, 0, 1080, 2340),
                    density = 3f, foregroundPackage = UberOcrParser.UBER_PACKAGE)
                yield()
            }
            frame(full)
            frame(revision)
            pipeline.onCaptureUncertain(System.currentTimeMillis())
            frame(revision)
            assertEquals(listOf(79.45, 79.45, 79.45), parsed.map { it.card.fare })
            frame(revision)
            assertEquals(99.45, parsed.last().card.fare!!, 0.001)
            assertEquals(1, parsed.map { it.card.sessionId }.distinct().size)
            assertEquals(1, parsed.map { it.card.sessionStartedAt }.distinct().size)
        } finally { collector.cancelAndJoin() }
    }

}
