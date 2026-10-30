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
        assertEquals(UberOcrParser.UBER_PACKAGE, selectOfferCard(uber, didi, frame)!!.platform)
    }

    @Test
    fun `selects the DiDi card when there is no Uber signature`() {
        val frame = listOf(
            block("\$132.59", 83, 973, 543, 1085),
            block("6min (1.2km)", 139, 1692, 441, 1745),
            block("39min (17.3km)", 156, 1902, 514, 1960),
            block("Aceptar \$132.59", 200, 2150, 880, 2240),
        )
        assertEquals(DidiOcrParser.DIDI_PACKAGE, selectOfferCard(uber, didi, frame)!!.platform)
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
        assertEquals(UberOcrParser.UBER_PACKAGE, selectOfferCard(uber, didi, garbledUber)!!.platform)
    }


    @Test fun `text boxes without display geometry cannot authorize placement`() {
        val uberCard = selectOfferCard(uber,didi,listOf(block("UberX Exclusivo"), block("MXN96.17",40,600,300,650),
            block("A5 min (0.8 km)",40,700,300,750),block("Viaje: 32 min (11.3 km)",40,800,300,850)))!!
        assertTrue(uberCard.contentBounds != null)
        assertNull(uberCard.hostGeometry)
        val didiCard = selectOfferCard(uber,didi,listOf(block("$132.59",83,973,543,1085),
            block("6min (1.2km)",139,1692,441,1745),block("39min (17.3km)",156,1902,514,1960),
            block("Aceptar $132.59",200,2150,880,2240)))!!
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
        assertEquals(g,selectOfferCard(uber,didi,frame,1000,observation,adapter)?.hostGeometry)
        assertNull(selectOfferCard(uber,didi,frame,1001,observation,adapter)?.hostGeometry)
        assertEquals(g,selectOfferCard(uber,didi,frame,1000,observation)?.hostGeometry)
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
            displayBounds = boundedGeometry.displayBounds)!!
        assertEquals(UberOcrParser.UBER_PACKAGE, card.platform)
        assertEquals(11.3, card.tripDistanceKm!!, 0.001)
        assertNull(card.fare)
    }

    @Test fun `fresh accessibility card bounds exclude an outside headline before fare selection`() {
        val evidence = boundedEvidence()
        val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = evidence)!!
        assertEquals(UberOcrParser.UBER_PACKAGE, card.platform)
        assertEquals(11.3, card.tripDistanceKm!!, 0.001)
        assertNull(card.fare)
    }

    @Test fun `previous shorter accessibility card cannot crop the current taller offer`() {
        assertEquals(96.17, selectOfferCard(uber, didi, boundedFrame)!!.fare!!, 0.001)
        val previousCard = boundedEvidence().copy(
            capturedAtMs = 900,
            observedCardBounds = HostRect(0, 640, 393, 852),
        )
        val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = previousCard)!!
        assertEquals(96.17, card.fare!!, 0.001)
        assertTrue(card.hostGeometry!!.offerCard.top < 590)
    }

    @Test fun `older accessibility edges need a current heading before clipping a legacy fare`() {
        val frame = boundedFrame.filterNot { it.text == "UberX" }
        val previousCard = boundedEvidence().copy(observedCardBounds = HostRect(0, 640, 393, 852))
        val card = selectOfferCard(uber, didi, frame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = previousCard)!!
        assertEquals(96.17, card.fare!!, 0.001)
    }

    @Test fun `same frame accessibility edges remain authoritative for fare selection`() {
        val evidence = boundedEvidence().copy(capturedAtMs = 1_000)
        val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
            displayBounds = boundedGeometry.displayBounds, accessibility = evidence)!!
        assertNull(card.fare)
    }

    @Test fun `other host and stale layout bounds cannot suppress a valid OCR fare`() {
        assertEquals(96.17, selectOfferCard(uber, didi, boundedFrame)!!.fare!!, 0.001)
        for (geometry in listOf(boundedGeometry.copy(capturedAtMs = 999),
            boundedGeometry.copy(packageName = DidiOcrParser.DIDI_PACKAGE))) {
            val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
                HostLayoutObservation(geometry, true, true), displayBounds = boundedGeometry.displayBounds)!!
            assertEquals(96.17, card.fare!!, 0.001)
        }
    }

    @Test fun `other host stale future or rotated accessibility bounds cannot suppress a valid OCR fare`() {
        val good = boundedEvidence()
        for (evidence in listOf(good.copy(packageName = DidiOcrParser.DIDI_PACKAGE),
            good.copy(capturedAtMs = -1), good.copy(capturedAtMs = 1_001),
            good.copy(displayBounds = HostRect(0, 0, 852, 393)))) {
            val card = selectOfferCard(uber, didi, boundedFrame, 1_000,
                displayBounds = boundedGeometry.displayBounds, accessibility = evidence)!!
            assertEquals(96.17, card.fare!!, 0.001)
        }
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
