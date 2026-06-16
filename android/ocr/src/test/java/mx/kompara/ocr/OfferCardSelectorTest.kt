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
    fun `holds a garbled Uber frame instead of flipping it to DiDi`() {
        // Uber broadcast over DiDi: the "Viaje:" trip leg is garbled away so the Uber parse drops, but
        // the signature (MXN fare + pickup leg) says it's still an Uber card. A bare "$" with two legs
        // — what DiDi would otherwise grab — must NOT become a DiDi verdict on this Uber screen.
        val garbledUber = listOf(
            block("MXN119.68", 64, 700, 540, 770),
            block("A 9 min (2.1 km)", 190, 980, 540, 1030), // pickup only; no "Viaje:" → Uber parse fails
            block("\$123.45", 280, 120, 460, 220), // a bare "$" DiDi would latch
            block("6min (1.2km)", 139, 1692, 441, 1745),
            block("24min (4km)", 156, 1902, 514, 1960),
        )
        assertNull(uber.parse(garbledUber)) // no labelled trip leg
        assertTrue(uber.hasCardSignature(garbledUber)) // but still an Uber card
        // Without the hold this would return DiDi's $123.45 card; the guard keeps it null (hold).
        assertNull(selectOfferCard(uber, didi, garbledUber))
    }

    private fun block(text: String) = OcrBlock(text, OcrBounds(0, 0, 0, 0))
    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) = OcrBlock(text, OcrBounds(l, t, r, b))
}
