package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

    @Test fun `supported currency formats survive the complete pipeline`() {
        for ((text, expected) in listOf("MX\$79.45" to 79.45, "MXN 79.45" to 79.45,
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
}
