package mx.kompara.ocr

import mx.kompara.capture.HostWindowEvidence
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostLayoutObservation
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame-level parser selection: Uber-first, with the cross-app hold that stops a garbled Uber
 * frame from being mis-attributed to DiDi via an on-screen "$0.00" pill. See [selectOfferCard].
 */
class OfferCardSelectorTest {

    private val uber = UberOcrParser()
    private val didi = DidiOcrParser()

    @Test
    fun `selects the Uber card on an Uber frame`() {
        val frame = listOf(
            block("UberX Exclusivo"),
            block("MXN96.17"),
            block("A5 min (0.8 km)"),
            block("Viaje: 32 min (11.3 km)"),
        )
        assertEquals(UberOcrParser.UBER_PACKAGE, selectOfferCard(uber, didi, frame, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!.platform)
    }

    @Test
    fun `selects the DiDi card when there is no Uber signature`() {
        val frame = listOf(
            block("\$132.59", 83, 973, 543, 1085),
            block("6min (1.2km)", 139, 1692, 441, 1745),
            block("39min (17.3km)", 156, 1902, 514, 1960),
            block("Aceptar \$132.59", 200, 2150, 880, 2240),
        )
        assertEquals(DidiOcrParser.DIDI_PACKAGE, selectOfferCard(uber, didi, frame, foregroundPackage = DidiOcrParser.DIDI_PACKAGE)!!.platform)
    }

    @Test
    fun `keeps an incomplete Uber frame instead of flipping it to DiDi`() {
        // The Uber trip label is unreadable, but the MXN fare and pickup still identify its card.
        // A bare "$" elsewhere on screen must never reattribute that partial offer to DiDi.
        val garbledUber = listOf(
            block("MXN119.68", 64, 700, 540, 770),
            block("A 9 min (2.1 km)", 190, 980, 540, 1030), // pickup only; trip remains unread
            block("\$123.45", 280, 120, 460, 220), // a bare "$" DiDi would latch
            block("6min (1.2km)", 139, 1692, 441, 1745),
            block("24min (4km)", 156, 1902, 514, 1960),
        )
        assertNull(uber.parse(garbledUber)!!.tripDistanceKm) // no labelled trip leg
        assertTrue(uber.hasCardSignature(garbledUber)) // but still an Uber card
        // The partial Uber card owns this frame instead of DiDi's unrelated $123.45 pill.
        assertEquals(UberOcrParser.UBER_PACKAGE, selectOfferCard(uber, didi, garbledUber, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!.platform)
    }


    @Test fun `text boxes without display geometry cannot authorize placement`() {
        val uberCard = selectOfferCard(uber,didi,listOf(block("UberX Exclusivo"), block("MXN96.17",40,600,300,650),
            block("A5 min (0.8 km)",40,700,300,750),block("Viaje: 32 min (11.3 km)",40,800,300,850)), foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
        assertTrue(uberCard.contentBounds != null)
        assertNull(uberCard.hostGeometry)
        val didiCard = selectOfferCard(uber,didi,listOf(block("$132.59",83,973,543,1085),
            block("6min (1.2km)",139,1692,441,1745),block("39min (17.3km)",156,1902,514,1960),
            block("Aceptar $132.59",200,2150,880,2240)), foregroundPackage = DidiOcrParser.DIDI_PACKAGE)!!
        assertTrue(didiCard.contentBounds != null)
        assertNull(didiCard.hostGeometry)
    }

    @Test fun `measured geometry travels only with its exact host and frame without certification`() {
        val frame=listOf(block("UberX Exclusivo"),block("MXN96.17"),block("A5 min (0.8 km)"),block("Viaje: 32 min (11.3 km)"))
        val g=mx.kompara.parsers.model.HostGeometry(UberOcrParser.UBER_PACKAGE,"test",1000,
            mx.kompara.parsers.model.HostRect(0,0,393,852),mx.kompara.parsers.model.HostOrientation.PORTRAIT,
            mx.kompara.parsers.model.HostRect(0,80,393,128),mx.kompara.parsers.model.HostRect(0,545,393,838),
            listOf(mx.kompara.parsers.model.HostRect(20,750,373,810)),emptyList())
        val observation=mx.kompara.parsers.model.HostLayoutObservation(g,true,true)
        val adapter=mx.kompara.parsers.model.HostGeometryAdapter(setOf(g.packageName to g.layoutId))
        assertEquals(g,selectOfferCard(uber,didi,frame,1000,observation,adapter, foregroundPackage = UberOcrParser.UBER_PACKAGE)?.hostGeometry)
        assertNull(selectOfferCard(uber,didi,frame,1001,observation,adapter, foregroundPackage = UberOcrParser.UBER_PACKAGE)?.hostGeometry)
        assertEquals(g,selectOfferCard(uber,didi,frame,1000,observation, foregroundPackage = UberOcrParser.UBER_PACKAGE)?.hostGeometry)
    }

    // The amount fits the OCR-only card envelope, but crosses independently observed right edges.
    // Core heading/action/route blocks remain inside, so rejection must retain an incomplete card.
    private val boundedFrame = listOf(
        block("UberX", 20, 550, 180, 574),
        block("Exclusivo", 250, 550, 380, 574),
        block("MXN96.17", 20, 590, 373, 630),
        block("A5 min (0.8 km)", 20, 650, 290, 674),
        block("Viaje: 32 min (11.3 km)", 20, 700, 290, 724),
        block("Aceptar", 100, 780, 220, 804),
    )
    private val boundedGeometry = HostGeometry(
        UberOcrParser.UBER_PACKAGE, "measured-card", 1_000, HostRect(0, 0, 393, 852),
        HostOrientation.PORTRAIT, HostRect(0, 80, 393, 128), HostRect(0, 545, 300, 838),
        listOf(HostRect(100, 780, 220, 804)), emptyList(),
    )

    @Test fun `observed layout bounds exclude an outside headline before fare selection`() {
        val observation = HostLayoutObservation(boundedGeometry, true, true)
        val card = selectOfferCard(uber, didi, boundedFrame, 1_000, observation,
            displayBounds = boundedGeometry.displayBounds, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
        assertEquals(UberOcrParser.UBER_PACKAGE, card.platform)
        assertEquals(11.3, card.tripDistanceKm!!, 0.001)
        assertNull(card.fare)
    }

    @Test fun `fresh accessibility card bounds exclude an outside headline before fare selection`() {
        val evidence = boundedEvidence()
        val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = evidence, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
        assertEquals(UberOcrParser.UBER_PACKAGE, card.platform)
        assertEquals(11.3, card.tripDistanceKm!!, 0.001)
        assertNull(card.fare)
    }

    @Test fun `previous shorter accessibility card cannot crop the current taller offer`() {
        assertEquals(96.17, selectOfferCard(uber, didi, boundedFrame, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!.fare!!, 0.001)
        val previousCard = boundedEvidence().copy(
            capturedAtMs = 900,
            observedCardBounds = HostRect(0, 640, 393, 852),
        )
        val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = previousCard, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
        assertEquals(96.17, card.fare!!, 0.001)
        assertTrue(card.hostGeometry!!.offerCard.top < 590)
    }

    @Test fun `older accessibility edges need a current heading before clipping a legacy fare`() {
        val frame = boundedFrame.filterNot { it.text == "UberX" }
        val previousCard = boundedEvidence().copy(observedCardBounds = HostRect(0, 640, 393, 852))
        val card = selectOfferCard(uber, didi, frame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = previousCard, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
        assertEquals(96.17, card.fare!!, 0.001)
    }

    @Test fun `same frame accessibility edges remain authoritative for fare selection`() {
        val evidence = boundedEvidence().copy(capturedAtMs = 1_000)
        val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = evidence, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
        assertNull(card.fare)
    }

    @Test fun `other host and stale layout bounds cannot suppress a valid OCR fare`() {
        assertEquals(96.17, selectOfferCard(uber, didi, boundedFrame, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!.fare!!, 0.001)
        for (geometry in listOf(boundedGeometry.copy(capturedAtMs = 999),
            boundedGeometry.copy(packageName = DidiOcrParser.DIDI_PACKAGE))) {
            val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
                HostLayoutObservation(geometry, true, true), displayBounds = boundedGeometry.displayBounds, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
            assertEquals(96.17, card.fare!!, 0.001)
        }
    }

    @Test fun `other host stale future or rotated accessibility bounds cannot suppress a valid OCR fare`() {
        val good = boundedEvidence()
        for (evidence in listOf(good.copy(packageName = DidiOcrParser.DIDI_PACKAGE),
            good.copy(capturedAtMs = -1), good.copy(capturedAtMs = 1_001),
            good.copy(displayBounds = HostRect(0, 0, 852, 393)))) {
            val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
                displayBounds = boundedGeometry.displayBounds, accessibility = evidence, foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
            assertEquals(96.17, card.fare!!, 0.001)
        }
    }

    // 0.2.17, S25 Pon Tu Precio $68.60 (2026-11-06 1794009689245): bounds verbatim, addresses invented.
    private val s25 = HostRect(0, 0, 1080, 2340)
    private val ptp = listOf(block("X Rechazo permitido", 480, 163, 993, 213), block("Pon Tu Precio", 105, 641, 425, 682),
        block("\$68.60", 83, 774, 511, 886), block("1 punto", 101, 936, 247, 991),
        block("Tarjeta bancaria verificada", 149, 1180, 767, 1226), block("O 10 min (1.2 km)", 118, 1339, 504, 1394),
        block("Avenida Ficticia 9, Colonia Prueba", 165, 1406, 974, 1462), block("39 min (7.2 km)", 151, 1550, 524, 1602),
        block("Aceptar \$68.60", 348, 1822, 722, 1876), block("\$73.60 \$76.60 \$79.60", 99, 1996, 796, 2045))

    @Test fun `DiDi pill under our chip mask reaches geometry from the unmasked blocks`() {
        val chip = mx.kompara.data.service.ScreenRect(36, 204, 756, 540)
        val masked = ChipMask.maskOwnChip(ptp, chip)
        assertTrue("ChipMask hides the pill", masked.none { it.text.contains("Rechazo") })
        val chipText = listOf(block("\$5.33/km neto", 78, 240, 500, 300))
        val card = selectOfferCard(uber, didi, masked, 1_000, displayBounds = s25, density = 3f, chipRect = chip,
            foregroundPackage = DidiOcrParser.DIDI_PACKAGE, unmaskedBlocks = ptp + chipText)!!
        assertEquals(68.60, card.fare!!, 0.001)
        val g = card.hostGeometry!!
        assertEquals(listOf(HostRect(480, 163, 993, 213)), g.declineControls)
        assertTrue(HostRect(480, 163, 993, 213) in g.protectedControls)
        assertEquals(listOf(HostRect(105, 641, 425, 682)), g.ocrEvidence!!.upperAnchors)
        assertTrue("The price row is protected", HostRect(99, 1996, 796, 2045) in g.protectedControls)
    }

    @Test fun `a metres pickup row is a route anchor like a kilometre one`() {
        val masRapido = listOf(block("X", 957, 163, 997, 200), block("Más Rápido", 104, 836, 371, 888),
            block("\$152.36", 84, 973, 541, 1085), block("1 punto", 97, 1135, 249, 1188),
            block("6 min (688 m)", 152, 1694, 495, 1746), block("Calle Supuesta 77 - Colonia Ejemplo", 171, 1818, 998, 1876),
            block("54 min (13.1 km)", 165, 1902, 536, 1956))
        val card = selectOfferCard(uber, didi, masRapido, 1_000, displayBounds = s25, density = 3f,
            foregroundPackage = DidiOcrParser.DIDI_PACKAGE)!!
        assertEquals(0.688, card.pickupDistanceKm!!, 0.0001)
        assertEquals(listOf(HostRect(152, 1694, 495, 1746), HostRect(165, 1902, 536, 1956)),
            card.hostGeometry!!.ocrEvidence!!.routeAnchors)
        assertEquals(listOf(HostRect(957, 163, 997, 200)), card.hostGeometry!!.ocrEvidence!!.upperAnchors)
    }

    private fun boundedEvidence() = HostWindowEvidence(UberOcrParser.UBER_PACKAGE, 900,
        boundedGeometry.displayBounds, 1f, boundedGeometry.topChrome, emptyList(), boundedGeometry.offerCard)

    /** Card-local geometry also fits the measured 393×852 host fixture above. */
    private fun block(text: String): OcrBlock {
        val top = when {
            text.contains("Uber") -> 550
            text.startsWith("MX") -> 590
            text.startsWith("Viaje:") -> 700
            else -> 650
        }
        return OcrBlock(text, OcrBounds(20, top, 373, top + if (text.startsWith("MX")) 40 else 24))
    }
    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) = OcrBlock(text, OcrBounds(l, t, r, b))
}
