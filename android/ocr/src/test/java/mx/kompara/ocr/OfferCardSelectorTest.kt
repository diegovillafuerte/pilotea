package mx.kompara.ocr

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
    private fun block(text: String) = OcrBlock(text, OcrBounds(0, 0, 0, 0))
    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) = OcrBlock(text, OcrBounds(l, t, r, b))
}
