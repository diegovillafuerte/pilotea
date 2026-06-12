package mx.kompara.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinned to REAL OCR captures from a DiDi offer on a Samsung S25 (2026-06-11): fare $132.59,
 * pickup 6 min (1.2 km), trip 39 min (17.3 km), card payment, dynamic-fare surge line.
 */
class DidiOcrParserTest {

    private val parser = DidiOcrParser()

    // A clean capture (ocr_..585727.json).
    private val realCard = listOf(
        block("8:16", 67, 41, 142, 70),
        block("\$132.59", 83, 973, 543, 1085),
        block("1 punto(s)", 96, 1134, 306, 1194),
        block("9\$13.87 de tarifa base dinámica", 77, 1286, 882, 1336),
        block("4.84 293 viajes", 168, 1446, 527, 1499),
        block("Tarjeta bancaria verificada", 138, 1534, 756, 1580),
        block("6min (1.2km)", 139, 1692, 441, 1745),
        block("Pisos Europeos - Avenida Horacio,", 138, 1761, 934, 1811),
        block("39min (17.3km)", 156, 1902, 514, 1960),
        block("Ret. 26 10, Avante, Coyoacán,", 139, 1968, 832, 2025),
    )

    @Test
    fun `parses real DiDi offer card`() {
        val card = parser.parse(realCard)!!
        // Fare is the tall block, not the $13.87 dynamic-fare line.
        assertEquals(132.59, card.fare!!, 0.001)
        assertEquals(1.2, card.pickupDistanceKm!!, 0.001)
        assertEquals(6.0, card.pickupEtaMin!!, 0.001)
        assertEquals(17.3, card.tripDistanceKm!!, 0.001)
        assertEquals(39.0, card.tripDurationMin!!, 0.001)
        assertEquals("tarjeta", card.paymentType)
        assertTrue(card.surge)
        assertEquals(DidiOcrParser.DIDI_PACKAGE, card.platform)
    }

    @Test
    fun `gross per km is sane`() {
        val card = parser.parse(realCard)!!
        val totalKm = card.pickupDistanceKm!! + card.tripDistanceKm!!
        assertEquals(7.17, card.fare!! / totalKm, 0.01) // 132.59 / 18.5
    }

    // Real "Pon Tu Precio" bid card (ocr_..21556): $89.12, pickup 5min (862m), trip 21min (10.2km),
    // multiple bid options ($96.12/$100.12/$103.12), 1.3x surge. Pickup is in METERS.
    private val realBidCard = listOf(
        block("Pon Tu Precio", 100, 700, 400, 760),
        block("\$89.12 1.3x", 80, 900, 600, 1020),
        block("4.92 639 viajes", 168, 1446, 527, 1499),
        block("Tarjeta bancaria verificada", 138, 1534, 756, 1580),
        block("5min (862m)", 139, 1692, 441, 1745),
        block("21min (10.2km)", 156, 1902, 514, 1960),
        block("Aceptar \$89.12", 200, 2150, 880, 2240),
        block("\$96.12 \$100.12 \$103.12", 200, 2280, 880, 2330),
    )

    @Test
    fun `parses Pon Tu Precio bid card with meters pickup`() {
        val card = parser.parse(realBidCard)!!
        assertEquals(89.12, card.fare!!, 0.001) // from "Aceptar $89.12", not the bid options
        assertEquals(0.862, card.pickupDistanceKm!!, 0.001) // 862m → km
        assertEquals(5.0, card.pickupEtaMin!!, 0.001)
        assertEquals(10.2, card.tripDistanceKm!!, 0.001)
        assertEquals(21.0, card.tripDurationMin!!, 0.001)
        assertEquals("bid", card.variant)
        assertTrue(card.surge) // 1.3x
    }

    @Test
    fun `non-offer screen yields null`() {
        val home = listOf(
            block("La Magdalena", 134, 866, 393, 903),
            block("Iztacalco", 676, 548, 839, 585),
            block("MEX-5D", 1038, 2, 1066, 124),
        )
        assertNull(parser.parse(home))
    }

    @Test
    fun `fare without both legs yields null`() {
        val partial = listOf(
            block("\$132.59", 83, 973, 543, 1085),
            block("6min (1.2km)", 139, 1692, 441, 1745),
        )
        assertNull(parser.parse(partial))
    }

    @Test
    fun `garbled card frame keeps the signature (B-077)`() {
        // One leg garbled by the animating map ("1nmin"), the other intact: unparseable, but the
        // signature says the card is still on screen.
        val garbled = listOf(
            block("\$132.59", 83, 973, 543, 1085),
            block("1nmin (1.2km)", 139, 1692, 441, 1745),
            block("39min (17.3km)", 156, 1902, 514, 1960),
        )
        assertNull(parser.parse(listOf(garbled[0], garbled[1]))) // fare + 1 leg → no parse
        assertTrue(parser.hasCardSignature(garbled))
    }

    @Test
    fun `idle map screen has no card signature (B-077)`() {
        // The wallet pill matches the fare regex but there is no leg line — must NOT read as a card.
        val idle = listOf(
            block("\$0.00", 400, 80, 520, 130),
            block("Buscando", 134, 866, 393, 903),
            block("MEX-95", 1038, 2, 1066, 124),
        )
        assertNull(parser.parse(idle))
        assertEquals(false, parser.hasCardSignature(idle))
    }

    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) =
        OcrBlock(text, OcrBounds(l, t, r, b))
}
