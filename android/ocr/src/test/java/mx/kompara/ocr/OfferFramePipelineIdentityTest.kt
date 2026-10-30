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
    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { i ->
            val b = json.getJSONObject(i)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }
    }

    private fun replay(rejected: (List<OcrBlock>) -> List<OcrBlock>): List<OfferEvent.Parsed> = runBlocking {
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        try {
            val pipeline = OfferFramePipeline()
            val full = fixture()
            val unread = full.filterNot { it.text == "$79.45" }
            for ((index, blocks) in listOf(full, full, rejected(full), unread).withIndex()) {
                pipeline.process(blocks, ScreenRect(325, 242, 1040, 572), null,
                    displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
                yield()
                assertEquals("The third frame must be rejected before publication", if (index < 2) index + 1 else if (index == 2) 2 else 3,
                    parsed.size)
            }
        } finally {
            collector.cancelAndJoin()
        }
        parsed
    }

    @Test fun `rejected changed route invalidates retained payment before an unread old route returns`() {
        val parsed = replay { blocks -> blocks.map {
            if (it.text == "24 min (2.4 km)") it.copy(text = "24 min (24.0 km)") else it
        } }
        assertEquals(79.45, parsed.first().card.fare!!, 0.001)
        assertNull(parsed.last().card.fare)
        assertEquals("Rejected numeric OCR noise must not split the ledger instance",
            parsed.first().card.sessionId, parsed.last().card.sessionId)
    }

    @Test fun `rejected unknown route also invalidates retained payment`() {
        val parsed = replay { blocks -> blocks.map {
            when (it.text) {
                "$79.45" -> it.copy(text = "$7945.00")
                "1 min (0.2 km)" -> it.copy(text = "— min (0.2 km)")
                else -> it
            }
        } }
        assertNull(parsed.last().card.fare)
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
            pipeline.process(fixture(), null, null, capturedAtMs = at, displayBounds = display, density = 3f)
            pipeline.process(fixture(), null, null, capturedAtMs = at + 300, displayBounds = display, density = 3f,
                hostObservation = mx.kompara.parsers.model.HostLayoutObservation(idle, true, true))
            yield()
            assertEquals("Independent disappearance must not publish stale OCR as a new record", 1, parsed.size)
            pipeline.process(fixture().filterNot { it.text == "$79.45" }, null, null,
                capturedAtMs = at + 600, displayBounds = display, density = 3f)
            yield()
            assertEquals(2, parsed.size)
            assertNull(parsed.last().card.fare)
            assertNotEquals(parsed.first().card.sessionId, parsed.last().card.sessionId)
        } finally { collector.cancelAndJoin() }
    }
}
