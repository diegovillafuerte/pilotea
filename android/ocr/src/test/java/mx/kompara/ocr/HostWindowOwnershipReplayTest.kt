package mx.kompara.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.HostWindowOwnership
import mx.kompara.capture.VisibleHostWindow
import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.parsers.model.HostRect
import mx.kompara.data.service.ScreenRect
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostWindowOwnershipReplayTest {
    // The existing DiDi 132.59 capture, scaled into the upper split pane. The status-clock
    // block is absent, and no accept button has been invented. Addresses are fake.
    private val splitFrame = listOf(
        block("$132.59", 83, 486, 543, 542),
        block("1 punto(s)", 96, 567, 306, 597),
        block("9$13.87 de tarifa base dinámica", 77, 643, 882, 668),
        block("4.84 293 viajes", 168, 723, 527, 749),
        block("Tarjeta bancaria verificada", 138, 767, 756, 790),
        block("6min (1.2km)", 139, 846, 441, 872),
        block("Calle Ejemplo 100", 138, 880, 934, 905),
        block("39min (17.3km)", 156, 951, 514, 980),
        block("Avenida Ficticia 200", 139, 984, 832, 1012),
        block("Buscando viajes", 100, 1800, 900, 1900),
    )

    private val display = HostRect(0, 0, 1080, 2340)
    private val host = UberOcrParser.UBER_PACKAGE

    @Test fun `split DiDi offer cannot become a paid Uber offer without window ownership`() = assertNoOffer(null)

    @Test fun `selected lower Uber window excludes upper split DiDi offer before parser and ledger`() {
        val scope = HostWindowOwnership.fromWindows(host, 1_000, display, 1, listOf(
            VisibleHostWindow(1, host, HostRect(0, 1170, 1080, 2340), 1, true),
            VisibleHostWindow(2, DidiOcrParser.DIDI_PACKAGE, HostRect(0, 0, 1080, 1170), 2, true),
        ))!!
        assertNoOffer(scope)
    }

    @Test fun `foreign overlay with offer text is excluded from selected Uber window`() {
        assertNoOffer(HostWindowOwnership(host, 1_000, display, 1, display,
            listOf(HostRect(0, 0, 1080, 1170))))
    }

    @Test fun `missing stale future wrong host or wrong display ownership cannot mint an offer`() {
        val valid = HostWindowOwnership(host, 1_000, display, 1, display)
        for (scope in listOf(null, valid.copy(capturedAtMs = -1), valid.copy(capturedAtMs = 1_001),
            valid.copy(packageName = DidiOcrParser.DIDI_PACKAGE),
            valid.copy(displayBounds = HostRect(0, 0, 2340, 1080)))) assertNoOffer(scope)
    }

    @Test fun `accessibility evidence requires selected window provenance even for same host and pixels`() {
        val scope = HostWindowOwnership(host, 1_000, display, 1, display)
        val evidence = mx.kompara.capture.HostWindowEvidence(host, 1_000, display, 3f,
            HostRect(0, 0, 1080, 100), listOf(mx.kompara.parsers.model.HostTextBounds(
                "Buscando viajes", HostRect(240, 1940, 840, 2030))), windowId = 1)
        assertEquals(evidence, evidence.ownedBy(scope))
        assertNull("Lower same-host window cannot prove selected card absence", evidence.copy(windowId = 2).ownedBy(scope))
        assertNull("Unproven node provenance is uncertainty", evidence.copy(windowId = null).ownedBy(scope))
    }

    @Test fun `own chip is excluded from OCR without repelling placement while foreign windows remain obstacles`() = runBlocking {
        val chip = HostRect(324, 221, 1044, 557)
        val notification = HostRect(0, 0, 1080, 200)
        val scope = HostWindowOwnership.fromWindows(host, 1_000, display, 1, listOf(
            VisibleHostWindow(1, host, display, 1, true),
            VisibleHostWindow(2, "mx.kompara", chip, 4, false, accessibilityOverlay = true),
            VisibleHostWindow(3, null, notification, 5, false),
        ), ownOverlayPackage = "mx.kompara")!!
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        val blocks = List(json.length()) { index -> json.getJSONObject(index).let {
            block(it.getString("text"), it.getInt("left"), it.getInt("top"), it.getInt("right"), it.getInt("bottom"))
        } } + listOf(block("$999.00", 325, 242, 1040, 400), block("$888.00", 20, 20, 800, 180))
        val offers = mutableListOf<OfferEvent.Parsed>()
        val ledger = mutableListOf<LifecycleSignal.OfferSeen>()
        val overlayCollector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) offers += it }
        }
        val ledgerCollector = launch(Dispatchers.Unconfined) {
            OcrLifecycleBus.signals.collect { if (it is LifecycleSignal.OfferSeen) ledger += it }
        }
        try {
            OfferFramePipeline().process(blocks, ScreenRect(chip.left, chip.top, chip.right, chip.bottom),
                null, capturedAtMs = 1_000, displayBounds = display, density = 3f,
                foregroundPackage = host, windowOwnership = scope)
            yield()
            assertEquals(79.45, offers.single().card.fare!!, 0.001)
            assertEquals(79.45, ledger.single().card.fare!!, 0.001)
            val controls = offers.single().card.hostGeometry!!.protectedControls
            assertTrue("Foreign notification remains a placement obstacle", notification in controls)
            assertFalse("Our known chip cannot repel its own next placement", chip in controls)
            assertFalse(scope.owns(chip))
            assertFalse(scope.owns(notification))
        } finally {
            overlayCollector.cancelAndJoin()
            ledgerCollector.cancelAndJoin()
        }
    }

    private fun assertNoOffer(scope: HostWindowOwnership?) = runBlocking {
        val offers = mutableListOf<OfferEvent.Parsed>()
        val ledger = mutableListOf<LifecycleSignal.OfferSeen>()
        val overlayCollector = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) offers += it }
        }
        val ledgerCollector = launch(Dispatchers.Unconfined) {
            OcrLifecycleBus.signals.collect { if (it is LifecycleSignal.OfferSeen) ledger += it }
        }
        try {
            OfferFramePipeline().process(splitFrame, null, null, capturedAtMs = 1_000,
                displayBounds = display, density = 3f,
                foregroundPackage = host, windowOwnership = scope)
            yield()
            assertTrue("No owned Uber pixels means no overlay offer", offers.isEmpty())
            assertTrue("No owned Uber pixels means no ledger offer", ledger.isEmpty())
        } finally {
            overlayCollector.cancelAndJoin()
            ledgerCollector.cancelAndJoin()
        }
    }

    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) =
        OcrBlock(text, OcrBounds(l, t, r, b))
}
