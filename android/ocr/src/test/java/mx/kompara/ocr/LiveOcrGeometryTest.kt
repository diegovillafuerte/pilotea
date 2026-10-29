package mx.kompara.ocr

import mx.kompara.capture.HostWindowEvidence
import mx.kompara.parsers.model.*
import org.junit.Assert.*
import org.junit.Test

class LiveOcrGeometryTest {
    private val display = HostRect(0, 0, 393, 852)
    private fun box(text: String, top: Int) = OcrBlock(text, OcrBounds(20, top, 373, top + 24))
    private val frame = listOf(box("UberX Exclusivo", 500), box("MXN148.00", 540),
        box("A 4 min (2.0 km)", 600), box("Viaje: 20 min (10.0 km)", 650), box("Aceptar", 780))
    private fun select(blocks: List<OcrBlock> = frame, evidence: HostWindowEvidence? = null) =
        selectOfferCard(UberOcrParser(), DidiOcrParser(), blocks, 1_000,
            displayBounds = display, density = 1f, accessibility = evidence)!!

    @Test fun `OCR alone supplies geometry for a live offer without a host layout id`() {
        val card = select()
        assertEquals(HostGeometrySource.OCR_TEXT_BOUNDS, card.hostGeometry!!.source)
        assertEquals(484, card.hostGeometry!!.offerCard.top)
        assertTrue(card.hostGeometry!!.protectedControls.contains(HostRect(20, 780, 373, 804)))
    }

    @Test fun `fresh nodes contribute actual card and marker bounds to OCR`() {
        val marker = MapMarker(MapMarkerKind.DRIVER, HostRect(170, 350, 200, 390))
        val cardBounds = HostRect(0, 470, 393, 852)
        val evidence = HostWindowEvidence(UberOcrParser.UBER_PACKAGE, 900, display, 1f,
            HostRect(0, 0, 393, 128), emptyList(), cardBounds, markers = listOf(marker))
        val geometry = select(evidence = evidence).hostGeometry!!
        assertEquals(cardBounds, geometry.offerCard)
        assertEquals(listOf(marker), geometry.markers)
        assertEquals(HostGeometrySource.OCR_AND_ACCESSIBILITY, geometry.source)
    }

    @Test fun `other host old future and rotated node evidence cannot contaminate OCR bounds`() {
        val good = HostWindowEvidence(UberOcrParser.UBER_PACKAGE, 900, display, 1f,
            HostRect(0, 0, 393, 128), emptyList(), HostRect(0, 470, 393, 852))
        for (bad in listOf(good.copy(packageName = DidiOcrParser.DIDI_PACKAGE), good.copy(capturedAtMs = -1),
            good.copy(capturedAtMs = 1_001), good.copy(displayBounds = HostRect(0, 0, 852, 393)))) {
            assertEquals(HostGeometrySource.OCR_TEXT_BOUNDS, select(evidence = bad).hostGeometry!!.source)
            assertEquals(484, select(evidence = bad).hostGeometry!!.offerCard.top)
        }
    }

    @Test fun `recognized unreadable offer retains bounded action geometry`() {
        val card = select(listOf(box("UberX", 500), box("Aceptar", 780)))
        assertNull(card.fare)
        assertNotNull(card.hostGeometry)
        assertEquals("UNCONFIRMED", card.paymentBasis)
    }
}
