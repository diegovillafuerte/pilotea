package mx.kompara.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferFramePipelineDiagnosticsTest {
    @org.junit.Before fun foreground() {
        mx.kompara.data.service.ScreenReaderState.setForegroundHost(UberOcrParser.UBER_PACKAGE,
            android.os.SystemClock.elapsedRealtime())
    }
    private fun block(text: String, top: Int) =
        OcrBlock(text, OcrBounds(20, top, 373, top + if (text == "MXN148.00") 40 else 24))

    @Test fun `read offer without geometry logs reason but no recognized personal or payment data`() {
        ShadowLog.clear()
        OfferFramePipeline().processOwnedFrame(listOf(
            block("UberX Exclusivo", 500), block("MXN148.00", 540),
            block("A 4 min (2.0 km)", 600), block("Viaje: 20 min (10.0 km)", 650),
            block("Alice Example, Calle Privada 123", 700), block("Aceptar", 780),
        ), null, null)
        val messages = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
        assertTrue(messages.any { "geometry missing" in it })
        assertFalse(messages.any { "148.00" in it || "Alice" in it || "Calle Privada" in it })
    }

    @Test fun `recognized offer with unread payment logs incomplete reading`() {
        ShadowLog.clear()
        OfferFramePipeline().processOwnedFrame(listOf(block("UberX", 500), block("4 min (2.0 km)", 600),
            block("Aceptar", 780)), null, null)
        assertTrue(ShadowLog.getLogsForTag("KomparaOCR").any { "incomplete reading" in it.msg })
    }

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { index ->
            val item = json.getJSONObject(index)
            OcrBlock(item.getString("text"), OcrBounds(item.getInt("left"), item.getInt("top"),
                item.getInt("right"), item.getInt("bottom")))
        }
    }

    private fun OfferFramePipeline.frame(blocks: List<OcrBlock>) = processOwnedFrame(blocks,
        ScreenRect(325, 242, 1040, 572), null, displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)

    @Test fun `parsed but unpublished real fixture reports a throttled reason and counts every withheld frame`() = runBlocking {
        ShadowLog.clear()
        val published = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) published += it }
        }
        try {
            val pipeline = OfferFramePipeline()
            val incomplete = fixture().map {
                if (it.text == "1 min (0.2 km)") it.copy(text = "— min (0.2 km)") else it
            }
            repeat(4) { pipeline.frame(incomplete); yield() }
            pipeline.onCaptureEnd(System.currentTimeMillis())
            val messages = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
            assertEquals(0, published.size)
            assertEquals(1, messages.count { it == "offer visibility: PARSED_ROUTE_INCOMPLETE" })
            val summary = messages.single { it.startsWith("burst:") }
            assertTrue(summary, "signature=4 parsed=4 publishedOffers=0" in summary)
            assertTrue(summary, "PARSED_ROUTE_INCOMPLETE=4" in summary)
            assertFalse(messages.any { "79.45" in it || "Calle Falsa" in it || "Avenida Ficticia" in it })
        } finally { collector.cancelAndJoin() }
    }

    @Test fun `parsed outlier is accounted for once without hiding earlier successful publications`() = runBlocking {
        ShadowLog.clear()
        val published = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) published += it }
        }
        try {
            val pipeline = OfferFramePipeline()
            val good = fixture()
            pipeline.frame(good)
            pipeline.frame(good)
            pipeline.frame(good.map { if (it.text == "$79.45") it.copy(text = "$7945.00") else it })
            yield()
            pipeline.onCaptureEnd(System.currentTimeMillis())
            val messages = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
            assertEquals(2, published.size)
            assertEquals(1, messages.count { it == "offer visibility: PARSED_OCR_OUTLIER" })
            val summary = messages.single { it.startsWith("burst:") }
            assertTrue(summary, "signature=3 parsed=3 publishedOffers=2" in summary)
            assertTrue(summary, "PARSED_OCR_OUTLIER=1" in summary)
            assertFalse(messages.any { "79.45" in it || "7945.00" in it || "Calle Falsa" in it })
        } finally { collector.cancelAndJoin() }
    }

    @Test fun `a route-incomplete frame retired by the parse ceiling is counted as such, not as an outlier`() = runBlocking {
        // Round 3 (B8): device counters must stay truthful about why the held offer stopped publishing.
        ShadowLog.clear()
        val pipeline = OfferFramePipeline()
        val incomplete = fixture().map { if (it.text == "1 min (0.2 km)") it.copy(text = "— min (0.2 km)") else it }
        pipeline.frame(fixture())
        for (t in 1..19) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(850))
            foreground()
            pipeline.frame(incomplete)
        }
        pipeline.onCaptureEnd(System.currentTimeMillis())
        val summary = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }.single { it.startsWith("burst:") }
        assertTrue(summary, "PARSE_CEILING_EXPIRED=1" in summary)
        assertFalse(summary, "PARSED_OCR_OUTLIER" in summary)
    }

    @Test fun `parsed host rejected by readiness reports the host gate without logging offer text`() = runBlocking {
        ShadowLog.clear()
        val readiness = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) = Unit
        }, java.time.Clock.systemUTC()).apply {
            bound()
            supportedApps(setOf(DidiOcrParser.DIDI_PACKAGE))
        }
        assertTrue(readiness.canRead())
        val published = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) published += it }
        }
        try {
            val pipeline = OfferFramePipeline(readiness)
            pipeline.processOwnedFrame(fixture(), ScreenRect(325, 242, 1040, 572), null,
                token = readiness.captureToken(), displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
            yield()
            pipeline.onCaptureEnd(System.currentTimeMillis())
            val messages = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
            assertEquals(0, published.size)
            assertTrue(messages.contains("offer visibility: PARSED_READINESS_BLOCKED"))
            val summary = messages.single { it.startsWith("burst:") }
            assertTrue(summary, "signature=1 parsed=1 publishedOffers=0" in summary)
            assertTrue(summary, "PARSED_READINESS_BLOCKED=1" in summary)
            assertFalse(messages.any { "79.45" in it || "Calle Falsa" in it || "Avenida Ficticia" in it })
        } finally { collector.cancelAndJoin() }
    }

    // 0.2.14: a chip over the heading alone no longer counts (a missing heading is not masked
    // evidence: real DiDi cards print none). A chip inside the derived card still does.
    @Test fun `first parsed offer with chip over the card reports masked evidence`() = assertParsedDrop(
        fixture(), ScreenRect(80, 920, 810, 1000), "PARSED_CARD_MASKED")

    @Test fun `ambiguous visible action above the selected real card reports extraction uncertainty`() = assertParsedDrop(
        fixture() + OcrBlock("Aceptar", OcrBounds(80, 150, 500, 210)),
        ScreenRect(325, 242, 1040, 572), "PARSED_EXTRACTION_UNCERTAIN")

    private fun assertParsedDrop(blocks: List<OcrBlock>, chip: ScreenRect, reason: String) = runBlocking {
        ShadowLog.clear()
        val published = mutableListOf<OfferEvent.Parsed>()
        val collector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) published += it }
        }
        try {
            val pipeline = OfferFramePipeline()
            pipeline.processOwnedFrame(blocks, chip, null,
                displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
            yield()
            pipeline.onCaptureEnd(System.currentTimeMillis())
            val messages = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
            assertEquals(0, published.size)
            assertTrue(messages.toString(), messages.contains("offer visibility: $reason"))
            val summary = messages.single { it.startsWith("burst:") }
            assertTrue(summary, "signature=1 parsed=1 publishedOffers=0" in summary)
            assertTrue(summary, "$reason=1" in summary)
            assertFalse(messages.any { "79.45" in it || "Calle Falsa" in it || "Avenida Ficticia" in it })
        } finally { collector.cancelAndJoin() }
    }
}
