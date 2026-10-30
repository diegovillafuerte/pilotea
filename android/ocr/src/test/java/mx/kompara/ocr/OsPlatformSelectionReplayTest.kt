package mx.kompara.ocr

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.parsers.model.HostRect
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reviewer S25 replay. Fixtures contain fictitious addresses only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OsPlatformSelectionReplayTest {
    private val uber = UberOcrParser()
    private val didi = DidiOcrParser()

    @After fun clearHost() { ScreenReaderState.setForegroundHost(null, SystemClock.elapsedRealtime()) }

    @Test fun `masked growing Uber cannot fall through to a phantom DiDi payment`() {
        ScreenReaderState.setForegroundHost(UberOcrParser.UBER_PACKAGE, SystemClock.elapsedRealtime())
        val original = fixture().filter { it.bounds.top >= 650 }
        val grown = original.map { block ->
            if (block.bounds.top >= 650 && block.text != "Aceptar") block.copy(bounds = block.bounds.let {
                OcrBounds(it.left, it.top - 250, it.right, it.bottom - 250)
            }) else block
        }
        val masked = ChipMask.maskOwnChip(grown, ScreenRect(324, 221, 1044, 557))
        assertFalse(masked.any { it.text == "Uber Priority" || it.text == "$79.45" })
        val cards = listOf(original, masked, grown).map { selectOfferCard(uber, didi, it) }
        assertEquals(UberOcrParser.UBER_PACKAGE, cards.first()!!.platform)
        assertTrue("The masked frame is uncertain, never DiDi", cards[1] == null || cards[1]!!.platform == UberOcrParser.UBER_PACKAGE)
        assertEquals("The OS host retains a readable partial card for placement recovery", UberOcrParser.UBER_PACKAGE, cards[1]!!.platform)
        assertNull(cards[1]!!.fare)
        assertEquals(2.4, cards[1]!!.tripDistanceKm!!, 0.001)
        assertEquals(79.45, cards.last()!!.fare!!, 0.001)
    }

    @Test fun `initial self masked semantic anchors cannot mint a session or ledger offer`() = runBlocking {
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val seen = mutableListOf<LifecycleSignal.OfferSeen>()
        val eventJob = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        val lifecycleJob = launch(Dispatchers.Unconfined) {
            OcrLifecycleBus.signals.collect { if (it is LifecycleSignal.OfferSeen) seen += it }
        }
        try {
            val original = fixture().filter { it.bounds.top >= 650 }
            val grown = original.map { block ->
                if (block.bounds.top >= 650 && block.text != "Aceptar") block.copy(bounds = block.bounds.let {
                    OcrBounds(it.left, it.top - 250, it.right, it.bottom - 250)
                }) else block
            }
            val pipeline = OfferFramePipeline()
            ScreenReaderState.setForegroundHost(UberOcrParser.UBER_PACKAGE, SystemClock.elapsedRealtime())
            pipeline.process(grown, ScreenRect(324, 221, 1044, 557), null,
                displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
            yield()
            assertTrue("UNCERTAIN first frames never mint UUIDs", parsed.isEmpty())
            assertTrue("UNCERTAIN first frames never feed the ledger", seen.isEmpty())
            pipeline.process(grown, null, null, displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
            yield()
            assertEquals(1, parsed.size)
            assertNotNull(parsed.single().card.sessionId)
            assertEquals(79.45, parsed.single().card.fare!!, 0.001)
            assertEquals(1, seen.size)
        } finally {
            eventJob.cancelAndJoin()
            lifecycleJob.cancelAndJoin()
        }
    }

    @Test fun `OCR cannot select any platform without OS foreground evidence`() {
        ScreenReaderState.setForegroundHost(null, SystemClock.elapsedRealtime())
        assertNull(selectOfferCard(uber, didi, fixture()))
    }

    @Test fun `Uber text cannot override OS DiDi foreground`() {
        ScreenReaderState.setForegroundHost(DidiOcrParser.DIDI_PACKAGE, SystemClock.elapsedRealtime())
        assertNull(selectOfferCard(uber, didi, fixture()))
    }

    @Test fun `DiDi excludes rate amounts in a headline summary and accept button`() {
        for (rate in listOf("$30.56/km est.", "$30.56 / km", "25 min • 2.6 km • $30.56/km est.", "Aceptar $30.56/km", "$30.56 km est.", "$30.56 est.")) {
            val blocks = listOf(OcrBlock(rate, OcrBounds(80, 800, 800, 950)),
                OcrBlock("1min (0.2km)", OcrBounds(80, 1200, 500, 1260)),
                OcrBlock("24min (2.4km)", OcrBounds(80, 1400, 500, 1460)),
                OcrBlock("Aceptar", OcrBounds(80, 1800, 500, 1860)))
            assertNull(rate, didi.parse(blocks)!!.fare)
        }
    }

    @Test fun `DiDi excludes a rate suffix split into a separate OCR block`() {
        val blocks = listOf(OcrBlock("$30.56", OcrBounds(80, 800, 400, 900)),
            OcrBlock("/km est.", OcrBounds(410, 820, 650, 880)),
            OcrBlock("1min (0.2km)", OcrBounds(80, 1200, 500, 1260)),
            OcrBlock("24min (2.4km)", OcrBounds(80, 1400, 500, 1460)),
            OcrBlock("Aceptar", OcrBounds(80, 1800, 500, 1860)))
        assertNull(didi.parse(blocks)!!.fare)
    }

    @Test fun `rate unit split across three OCR boxes cannot become DiDi payment`() {
        val blocks = listOf(OcrBlock("$30.56", OcrBounds(80, 800, 400, 900)),
            OcrBlock("/", OcrBounds(405, 820, 420, 880)), OcrBlock("km", OcrBounds(425, 820, 470, 880)),
            OcrBlock("1min (0.2km)", OcrBounds(80, 1200, 500, 1260)),
            OcrBlock("24min (2.4km)", OcrBounds(80, 1400, 500, 1460)),
            OcrBlock("Aceptar", OcrBounds(80, 1800, 500, 1860)))
        assertNull(didi.parse(blocks)!!.fare)
    }

    @Test fun `wide spacing between DiDi rate amount and same row suffix cannot turn it into payment`() {
        val blocks = listOf(OcrBlock("$30.56", OcrBounds(80, 800, 400, 900)),
            OcrBlock("/km est.", OcrBounds(800, 820, 1000, 880)),
            OcrBlock("1min (0.2km)", OcrBounds(80, 1200, 500, 1260)),
            OcrBlock("24min (2.4km)", OcrBounds(80, 1400, 500, 1460)),
            OcrBlock("Aceptar", OcrBounds(80, 1800, 500, 1860)))
        assertNull(didi.parse(blocks)!!.fare)
    }

    @Test fun `variant labels preserve independent positive evidence`() {
        val full = fixture()
        val surge = full + OcrBlock("Tarifa dinámica", OcrBounds(96, 1220, 550, 1280))
        assertEquals(setOf("exclusive", "surge"), uber.parse(surge)!!.instanceEvidence!!.labels)
        assertEquals(setOf("exclusive"), uber.parse(full)!!.instanceEvidence!!.labels)
    }

    @Test fun `all complete visible card bands are retained for stacked selection evidence`() {
        val first = fixture().filter { it.bounds.top >= 650 }.map {
            it.copy(bounds = it.bounds.copy(top = 650 + ((it.bounds.top - 650) * .45).toInt(),
                bottom = 650 + ((it.bounds.bottom - 650) * .45).toInt()))
        }
        val second = first.map { it.copy(bounds = it.bounds.copy(top = it.bounds.top + 800, bottom = it.bounds.bottom + 800)) }
        val evidence = uber.parse(first + second)!!.instanceEvidence!!
        assertEquals(2, evidence.visibleCards)
        assertEquals(2, evidence.cardBands.size)
        assertEquals(uber.parse(second)!!.instanceEvidence!!.cardBands.single(), evidence.cardBands.last())
    }

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { index -> json.getJSONObject(index).let {
            OcrBlock(it.getString("text"), OcrBounds(it.getInt("left"), it.getInt("top"), it.getInt("right"), it.getInt("bottom")))
        } }
    }
}
