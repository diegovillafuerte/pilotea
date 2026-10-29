package mx.kompara.overlay

import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.ocr.*
import mx.kompara.parsers.model.*
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaUnavailable
import org.junit.Assert.*
import org.junit.Test

/** Real parser output crosses the money and placement boundaries without a certified layout. */
class LiveOfferIntegrationTest {
    private val viewport = OverlayViewport(HostRect(0, 0, 393, 852), 1f, HostOrientation.PORTRAIT)
    private val limits = PlatformThreshold.DEFAULT
    private val engine = NetProfitEngine()
    private fun box(text: String, top: Int) = OcrBlock(text, OcrBounds(20, top, 373, top + 24))
    private fun uber(fare: Boolean = true, time: Boolean = true) = listOfNotNull(
        box("UberX Exclusivo", 500), if (fare) box("MXN148.00", 540) else null,
        box("A ${if (time) "4" else "—"} min (2.0 km)", 600),
        box("Viaje: ${if (time) "20" else "—"} min (10.0 km)", 650), box("Aceptar", 780))
    private fun didi(fare: Boolean = true, time: Boolean = true) = listOfNotNull(
        if (fare) box("\$148.00", 540) else null,
        box("${if (time) "4" else "—"} min (2.0 km)", 600),
        box("${if (time) "20" else "—"} min (10.0 km)", 650),
        box(if (fare) "Aceptar \$148.00" else "Aceptar", 780))
    private fun metrics(card: OfferCard, unit: PreferredMetric = PreferredMetric.IPK) =
        engine.evaluate(OfferMapping.toTripOffer(card), "2.84".toBigDecimal(), limits, unit)

    @Test fun `both live parsers calculate contribution and a colored chip as estimated payment`() {
        for (card in listOf(UberOcrParser().parse(uber())!!, DidiOcrParser().parse(didi())!!)) {
            val result = metrics(card)
            assertEquals("DRIVER_PAYMENT", card.paymentBasis)
            assertEquals(113.92, result.netMxn!!, 0.000001)
            assertEquals(9.4933333333, result.netPerKm!!, 0.000001)
            assertTrue(VerdictChipState.from(result, limits).regla.band is ReglaBand.Rated)
        }
    }

    @Test fun `C4 missing fare renders Sin lectura for either recognized host`() {
        for (card in listOf(UberOcrParser().parse(uber(fare = false))!!, DidiOcrParser().parse(didi(fare = false))!!)) {
            val state = VerdictChipState.from(metrics(card), limits)
            assertEquals(ReglaUnavailable.FARE, (state.regla.band as ReglaBand.Unavailable).input)
            assertEquals("Sin lectura", state.heroRate)
            assertNull(state.regla.rateText)
        }
    }

    @Test fun `C5 missing time renders Falta tiempo in hourly unit while km stays computable`() {
        for (card in listOf(UberOcrParser().parse(uber(time = false))!!, DidiOcrParser().parse(didi(time = false))!!)) {
            val state = VerdictChipState.from(metrics(card, PreferredMetric.IPH), limits)
            assertEquals(ReglaUnavailable.TIME, (state.regla.band as ReglaBand.Unavailable).input)
            assertEquals("Falta tiempo", state.heroRate)
            assertNull(state.regla.rateText)
            assertTrue(VerdictChipState.from(metrics(card), limits).regla.band is ReglaBand.Rated)
        }
    }

    private fun derived(blocks: List<OcrBlock>, card: OfferCard, chromeBottom: Int = 128,
        markers: List<MapMarker> = emptyList()) = HostGeometryDeriver.offer(card.platform, 1_000,
        viewport.bounds, viewport.density,
        blocks.map { HostTextBounds(it.text, HostRect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom)) },
        HostGeometrySource.OCR_TEXT_BOUNDS, card.contentBounds,
        topChrome = HostRect(0, 0, 393, chromeBottom), markers = markers)!!
    private fun place(g: HostGeometry) = OverlayPositioning.place(g, g.packageName, viewport, 1_000,
        ChipSize(240, 112), ChipSize(240, 80))

    @Test fun `OCR derived card uses right foot 16dp above its top and never covers fare or accept`() {
        for ((blocks, card) in listOf(uber() to UberOcrParser().parse(uber())!!, didi() to DidiOcrParser().parse(didi())!!)) {
            val g = derived(blocks, card)
            val p = place(g) as OverlayPlacement.Displayed
            assertEquals(141, p.position.x)
            assertEquals(g.offerCard.top - 16, p.position.y + p.size.height)
            assertTrue(g.offerCard.top <= 540 - HostGeometryDeriver.CARD_TEXT_PADDING_DP)
            assertFalse(p.position.bounds(p.size).overlaps(g.offerCard))
            assertEquals(HostGeometrySource.OCR_TEXT_BOUNDS, g.source)
        }
    }

    @Test fun `OCR band switches to compact then suppresses only when even compact cannot fit`() {
        val blocks = uber()
        val card = UberOcrParser().parse(blocks)!!
        val top = derived(blocks, card).offerCard.top
        assertEquals(PlateFormat.COMPACT, (place(derived(blocks, card, top - 112)) as OverlayPlacement.Displayed).format)
        assertTrue(place(derived(blocks, card, top - 111)) is OverlayPlacement.Suppressed)
        assertTrue(place(derived(blocks, card, top + 10)) is OverlayPlacement.Suppressed)
    }

    @Test fun `known OCR-frame marker exclusions relocate before compact marker fallback`() {
        val blocks = uber()
        val card = UberOcrParser().parse(blocks)!!
        val g = derived(blocks, card)
        val marker = MapMarker(MapMarkerKind.DESTINATION, HostRect(345, g.offerCard.top - 70, 365, g.offerCard.top - 40))
        val p = place(derived(blocks, card, markers = listOf(marker))) as OverlayPlacement.Displayed
        assertEquals(12, p.position.x)
        assertFalse(p.position.bounds(p.size).overlaps(marker.outerBounds.expanded(16)))
    }

    @Test fun `C6 places from fresh idle host bounds without any offer record`() {
        val g = HostGeometryDeriver.idleHost(UberOcrParser.UBER_PACKAGE, 1_000, viewport.bounds,
            HostRect(0, 0, 393, 128), listOf(HostRect(0, 760, 393, 852)))!!
        val p = OverlayPositioning.place(g, g.packageName, viewport, 1_000, ChipSize(240, 72), ChipSize(240, 72))
            as OverlayPlacement.Displayed
        assertTrue(g.isIdleHost)
        assertEquals(HostGeometrySource.IDLE_HOST, g.source)
        assertEquals(OverlayPosition(141, 672), p.position)
    }
}
