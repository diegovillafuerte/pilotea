package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.ocr.UberOcrParser
import mx.kompara.parsers.model.HostRect
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaUnavailable
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

/** One anonymized live frame crosses the actual bus, mapping, evaluator and presentation state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferFramePipelineVerdictTest {
    private val limits = PlatformThreshold(10.0, 200.0, 7.5, 150.0)
    private val ownChip = ScreenRect(325, 242, 1040, 572)
    private val display = HostRect(0, 0, 1080, 2340)

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(File("../ocr/src/test/resources/uber_priority_s25_2026_10_29.json").readText())
        return (0 until json.length()).map { i ->
            val b = json.getJSONObject(i)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }
    }

    private data class Result(val showing: OverlayVisibility.Showing, val metrics: OfferMetrics)

    private fun replay(blocks: List<OcrBlock>, chip: ScreenRect = ownChip): Result {
        var result: Result? = null
        runTest {
            var evaluated: OfferMetrics? = null
            val seen = mutableListOf<OverlayVisibility>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                OverlayStateMachine(evaluate = { event ->
                    NetProfitEngine().evaluate(OfferMapping.toTripOffer(event.card), "2.84".toBigDecimal(),
                        limits, PreferredMetric.IPK).also { evaluated = it }
                        .let { VerdictChipState.from(it, limits) }
                }).visibility(OfferEventBus.events).collect { seen += it }
            }
            OfferFramePipeline().process(blocks, chip, fixtureDir = null,
                capturedAtMs = System.currentTimeMillis(), displayBounds = display, density = 3f)
            testScheduler.runCurrent()
            assertEquals("First parsed frame publishes without a debounce", 0L, testScheduler.currentTime)
            assertEquals(1, seen.filterIsInstance<OverlayVisibility.Reading>().size)
            result = Result(seen.filterIsInstance<OverlayVisibility.Showing>().single(), requireNotNull(evaluated))
        }
        return requireNotNull(result)
    }

    @Test fun `live Priority frame produces a driver payment verdict from the main fare`() {
        ShadowLog.clear()
        val (showing, metrics) = replay(fixture())
        val card = showing.event.card
        assertEquals(UberOcrParser.UBER_PACKAGE, card.platform)
        assertEquals("exclusive", card.variant)
        assertEquals("DRIVER_PAYMENT", card.paymentBasis)
        assertEquals(79.45, card.fare!!, 0.000001)
        assertEquals(1.0, card.pickupEtaMin!!, 0.000001)
        assertEquals(0.2, card.pickupDistanceKm!!, 0.000001)
        assertEquals(24.0, card.tripDurationMin!!, 0.000001)
        assertEquals(2.4, card.tripDistanceKm!!, 0.000001)
        assertNotNull(card.hostGeometry)
        assertEquals(2.6, metrics.totalKm!!, 0.000001)
        assertEquals(25.0, metrics.totalMin!!, 0.000001)
        assertEquals(72.066, metrics.netMxn!!, 0.000001)
        assertEquals(VerdictLevel.GREEN, metrics.verdict.level)
        assertNull(metrics.verdict.unavailableReason)
        assertTrue(metrics.verdict.missingInputs.isEmpty())
        assertTrue(showing.state.regla.band is ReglaBand.Rated)
        val logs = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
        assertFalse(logs.any { "incomplete reading" in it || "OWN_UI" in it })
        assertFalse(logs.any { line -> listOf("79.45", "16.00", "10.60", "30.56", "10.00", "7.50",
            "Calle Falsa", "Avenida Ficticia", "McDonald's", "Carl's Jr.").any { it in line } })
    }

    /** Reported 19:38 offer numbers on the anonymized S25 geometry; addresses remain fictitious. */
    private fun priority147() = fixture().map {
        it.copy(text = when (it.text) {
            "$79.45" -> "$147.46"
            "25 min • 2.6 km • $30.56/km est." -> "53 min • 7.0 km • $21.07/km est."
            "1 min (0.2 km)" -> "4 min (0.7 km)"
            "24 min (2.4 km)" -> "49 min (6.3 km)"
            else -> it.text
        })
    }

    @Test fun `reported 147_46 Priority offer produces the observed net verdict`() {
        val (showing, metrics) = replay(priority147())
        assertEquals(147.46, showing.event.card.fare!!, 0.000001)
        assertEquals(7.0, metrics.totalKm!!, 0.000001)
        assertEquals(53.0, metrics.totalMin!!, 0.000001)
        assertEquals(18.225714, metrics.netPerKm!!, 0.000001)
        assertTrue(showing.state.regla.band is ReglaBand.Rated)
        assertEquals(VerdictLevel.GREEN, metrics.verdict.level)
    }

    @Test fun `same offer holds verdict and placement through unread fares then resets for changed legs`() = runTest {
        val pipeline = OfferFramePipeline()
        val seen = mutableListOf<OverlayVisibility>()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OverlayStateMachine(evaluate = { event ->
                parsed += event
                VerdictChipState.from(NetProfitEngine().evaluate(OfferMapping.toTripOffer(event.card),
                    "2.84".toBigDecimal(), limits, PreferredMetric.IPK), limits)
            }).visibility(OfferEventBus.events).collect { seen += it }
        }
        val full = priority147()
        val unread = full.filterNot { it.text == "$147.46" }
        val start = System.currentTimeMillis()
        val viewport = OverlayViewport(display, 3f, mx.kompara.parsers.model.HostOrientation.PORTRAIT)
        var firstPlacement: OverlayPlacement.Displayed? = null
        for ((index, frame) in listOf(full, unread, unread, full).withIndex()) {
            pipeline.process(frame, ownChip, null, capturedAtMs = start + index * 300,
                displayBounds = display, density = 3f)
            testScheduler.runCurrent()
            val showing = seen.last() as OverlayVisibility.Showing
            assertEquals(147.46, showing.event.card.fare!!, 0.000001)
            assertTrue(showing.state.regla.band is ReglaBand.Rated)
            assertEquals(parsed.first().card.sessionId, showing.event.card.sessionId)
            assertEquals(parsed.first().card.hostGeometry!!.offerCard, showing.event.card.hostGeometry!!.offerCard)
            assertEquals(start + index * 300, showing.event.card.hostGeometry!!.capturedAtMs)
            val placement = OverlayPositioning.place(showing.event.card.hostGeometry, showing.event.packageName,
                viewport, start + index * 300, ChipSize(715, 330), ChipSize(715, 200)) as OverlayPlacement.Displayed
            if (firstPlacement == null) firstPlacement = placement
            assertEquals(firstPlacement, placement)
            assertTrue(OverlayPositioning.valid(firstPlacement.position, firstPlacement.size,
                showing.event.card.hostGeometry!!, viewport))
        }
        assertEquals(4, parsed.size)
        assertEquals(1, seen.filterIsInstance<OverlayVisibility.Reading>().size)
        assertFalse(seen.any { it == OverlayVisibility.Hidden || it == OverlayVisibility.Exiting })
        assertEquals(1, seen.filterIsInstance<OverlayVisibility.Showing>().map { it.state }.distinct().size)

        // A sustained, independently readable fare revision wins the existing smoothing window.
        repeat(3) { index ->
            pipeline.process(full.map { if (it.text == "$147.46") it.copy(text = "$160.00") else it },
                ownChip, null, capturedAtMs = start + 1200 + index * 300, displayBounds = display, density = 3f)
            testScheduler.runCurrent()
        }
        val corrected = seen.last() as OverlayVisibility.Showing
        assertEquals(160.0, corrected.event.card.fare!!, 0.000001)
        assertEquals(parsed.first().card.sessionId, corrected.event.card.sessionId)
        assertNotEquals(seen.filterIsInstance<OverlayVisibility.Showing>().first().state, corrected.state)
        assertEquals(1, seen.filterIsInstance<OverlayVisibility.Reading>().size)

        // A transient changed pickup loses money confidence, but needs a second agreeing route
        // observation before it can create a durable replacement instance.
        val changedPickup = unread.map {
            if (it.text == "4 min (0.7 km)") it.copy(text = "5 min (0.9 km)") else it
        }
        pipeline.process(changedPickup, ownChip, null, capturedAtMs = start + 2100, displayBounds = display, density = 3f)
        testScheduler.runCurrent()
        val uncertain = seen.last() as OverlayVisibility.Showing
        assertNull(uncertain.event.card.fare)
        assertEquals(parsed.first().card.sessionId, uncertain.event.card.sessionId)
        pipeline.process(changedPickup, ownChip, null, capturedAtMs = start + 2400, displayBounds = display, density = 3f)
        testScheduler.runCurrent()
        val next = seen.last() as OverlayVisibility.Showing
        assertNull(next.event.card.fare)
        assertNotEquals(parsed.first().card.sessionId, next.event.card.sessionId)
        assertEquals(2, seen.filterIsInstance<OverlayVisibility.Reading>().size)
        assertEquals(ReglaUnavailable.FARE, (next.state.regla.band as ReglaBand.Unavailable).input)
    }

    @Test fun `a card replacement after one absent frame cannot inherit fare even with identical legs`() = runTest {
        val pipeline = OfferFramePipeline()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
        }
        for (blocks in listOf(priority147(), emptyList(), priority147().filterNot { it.text == "$147.46" })) {
            pipeline.process(blocks, ownChip, null, displayBounds = display, density = 3f)
            testScheduler.runCurrent()
        }
        assertEquals(2, parsed.size)
        assertNull(parsed.last().card.fare)
        assertNotEquals(parsed.first().card.sessionId, parsed.last().card.sessionId)
    }

    @Test fun `supported currency formats survive the complete pipeline`() {
        for ((text, expected) in listOf("MX\$79.45" to 79.45, "MXN 79.45" to 79.45,
            "\$79.45 MXN" to 79.45, "MX\$79.45 MXN" to 79.45, "79.45 MXN" to 79.45,
            "\$1,234.56" to 1234.56)) {
            val (showing, metrics) = replay(fixture().map { if (it.text == "\$79.45") it.copy(text = text) else it })
            assertEquals(expected, showing.event.card.fare!!, 0.000001)
            assertEquals("DRIVER_PAYMENT", showing.event.card.paymentBasis)
            assertTrue(showing.state.regla.band is ReglaBand.Rated)
            assertNull(metrics.verdict.unavailableReason)
        }
    }

    @Test fun `bonus-only card never borrows own chip amounts or the per-km amount`() {
        val (showing, metrics) = replay(fixture().filterNot { it.text == "\$79.45" })
        assertNull(showing.event.card.fare)
        assertEquals("FARE", metrics.verdict.unavailableReason)
        assertEquals(ReglaUnavailable.FARE, (showing.state.regla.band as ReglaBand.Unavailable).input)
        assertEquals("Sin lectura", showing.state.heroRate)
    }

    @Test fun `ownUi mask removes even a standalone amount inside the card before parsing`() {
        // A transient chip over the fare must suppress it, even though its text and bounds would
        // otherwise be the strongest candidate. Bonused/per-km/goal amounts cannot replace it.
        val (showing, metrics) = replay(fixture(), ScreenRect(90, 760, 510, 910))
        assertNull(showing.event.card.fare)
        assertEquals("FARE", metrics.verdict.unavailableReason)
        assertEquals(ReglaUnavailable.FARE, (showing.state.regla.band as ReglaBand.Unavailable).input)
    }

    @Test fun `split per-km summary cannot produce a verdict on first or repeated frames`() {
        for (unreadable in listOf("S79.45", "unreadable")) {
            val blocks = fixture().filterNot { it.text.contains("/km") }
                .map { if (it.text == "$79.45") it.copy(text = unreadable) else it } + listOf(
                    OcrBlock("25 min • 2.6 km •", OcrBounds(96, 926, 540, 988)),
                    OcrBlock("$30.56", OcrBounds(550, 926, 720, 988)),
                    OcrBlock("/km est.", OcrBounds(730, 926, 858, 988)),
                )
            assertMissingFareAcrossFrames(blocks)
        }
    }

    @Test fun `split included bonus cannot produce a verdict on first or repeated frames`() {
        for (unreadable in listOf("S79.45", "unreadable")) {
            val blocks = fixture().filterNot { it.text == "+$16.00 incluido" }
                .map { if (it.text == "$79.45") it.copy(text = unreadable) else it } + listOf(
                    OcrBlock("+", OcrBounds(476, 1123, 510, 1212)),
                    OcrBlock("$16.00", OcrBounds(520, 1123, 735, 1212)),
                    OcrBlock("incluido", OcrBounds(745, 1123, 959, 1212)),
                )
            assertMissingFareAcrossFrames(blocks)
        }
    }

    private fun assertMissingFareAcrossFrames(blocks: List<OcrBlock>) {
        // The first frame reaches the complete presentation path immediately.
        val (showing, metrics) = replay(blocks)
        assertNull(showing.event.card.fare)
        assertEquals("FARE", metrics.verdict.unavailableReason)
        assertEquals(ReglaUnavailable.FARE, (showing.state.regla.band as ReglaBand.Unavailable).input)
        assertEquals("Sin lectura", showing.state.heroRate)

        // Repeat through one real pipeline: outlier filtering and stabilization must never turn
        // a contextual rate/bonus into payment, even when every frame repeats the same amount.
        runTest {
            val parsed = mutableListOf<OfferEvent.Parsed>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
            }
            val pipeline = OfferFramePipeline()
            repeat(5) { frame ->
                pipeline.process(blocks, ownChip, fixtureDir = null,
                    capturedAtMs = System.currentTimeMillis() + frame * 300,
                    displayBounds = display, density = 3f)
                testScheduler.runCurrent()
                assertEquals(frame + 1, parsed.size)
                val card = parsed.last().card
                assertNull("Frame $frame must have no fare", card.fare)
                val evaluated = NetProfitEngine().evaluate(OfferMapping.toTripOffer(card),
                    "2.84".toBigDecimal(), limits, PreferredMetric.IPK)
                assertEquals("FARE", evaluated.verdict.unavailableReason)
                assertEquals(ReglaUnavailable.FARE,
                    (VerdictChipState.from(evaluated, limits).regla.band as ReglaBand.Unavailable).input)
            }
        }
    }
}
