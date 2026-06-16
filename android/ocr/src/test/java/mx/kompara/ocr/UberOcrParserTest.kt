package mx.kompara.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinned to REAL OCR captures from live Uber offers on a Samsung S25 in CDMX (2026-06-15), the same
 * corpus the parser was tuned against. Uber renders the offer card in a Compose/TextureView with no
 * accessibility text, so the node path can't read it — these frames come from MediaProjection OCR.
 */
class UberOcrParserTest {

    private val parser = UberOcrParser()

    // Clean "UberX Exclusivo" card — MXN96.17, pickup 5 min (0.8 km), trip 32 min (11.3 km).
    // The "+MXN 4" incentive badge must NOT be read as the fare.
    private val exclusivo = listOf(
        block("+MXN 4"),
        block("UberX Exclusivo"),
        block("MXN96.17"),
        block("Identidad verificada"),
        block("★ 4.58 (587)"),
        block("A5 min (0.8 km)"),
        block("Av Horacio, Polanco / Anzures"),
        block("Viaje: 32 min (11.3 km)"),
        block("Aceptar"),
    )

    @Test
    fun `parses a real Exclusivo offer and ignores the bonus badge`() {
        val card = parser.parse(exclusivo)!!
        assertEquals(96.17, card.fare!!, 0.001) // not the "+MXN 4" badge
        assertEquals(0.8, card.pickupDistanceKm!!, 0.001)
        assertEquals(5.0, card.pickupEtaMin!!, 0.001)
        assertEquals(11.3, card.tripDistanceKm!!, 0.001)
        assertEquals(32.0, card.tripDurationMin!!, 0.001)
        assertEquals("exclusive", card.variant)
        assertFalse(card.surge)
        assertEquals(UberOcrParser.UBER_PACKAGE, card.platform)
    }

    // "Uber Priority" long trip with a "+MXN12.63 por inicio de viaje" pickup bonus AND a Trip-Radar
    // banner — the fare is the standalone MXN255.75, never the +12.63 bonus.
    private val priorityWithBonus = listOf(
        block("Radar de solicitud de viaje 2"),
        block("Uber Priority"),
        block("MXN255.75"),
        block("O Identidad verificada"),
        block("* 4.62 (83)"),
        block("+MXN12.63 por inicio de viaje"),
        block("A 8 min (1.8 km)"),
        block("Calle Juan de Lafontaine, Polanco / Anzures"),
        block("Viaje: 55 min (45.6 km)"),
    )

    @Test
    fun `fare is the trip fare, not the per-trip bonus`() {
        val card = parser.parse(priorityWithBonus)!!
        assertEquals(255.75, card.fare!!, 0.001) // NOT 12.63
        assertEquals(1.8, card.pickupDistanceKm!!, 0.001)
        assertEquals(8.0, card.pickupEtaMin!!, 0.001)
        assertEquals(45.6, card.tripDistanceKm!!, 0.001)
        assertEquals(55.0, card.tripDurationMin!!, 0.001)
        assertEquals("trip_radar", card.variant)
    }

    // Garbled trip distance ("1l.4" for 11.4) plus a "+MXN4.00 incluido" bonus line.
    private val garbledDistance = listOf(
        block("+MXN 4"),
        block("2 UberX"),
        block("MXN139.94"),
        block("* 4.72 (504)"),
        block("Identidad verificada"),
        block("9 +MXN4.00 incluido"),
        block("A8 min (1.9 km)"),
        block("Viaje: 42 min (1l.4 km)"),
    )

    @Test
    fun `recovers OCR-garbled distance digits and excludes the included bonus`() {
        val card = parser.parse(garbledDistance)!!
        assertEquals(139.94, card.fare!!, 0.001) // not the +MXN4.00 included bonus
        assertEquals(1.9, card.pickupDistanceKm!!, 0.001)
        assertEquals(11.4, card.tripDistanceKm!!, 0.001) // "1l.4" -> 11.4
        assertEquals(42.0, card.tripDurationMin!!, 0.001)
        assertNull(card.variant) // plain UberX, no markers
    }

    // Real Trip-Radar card whose fare OCR'd with a COMMA decimal ("MXN33,45") instead of a period —
    // this is the card that showed no chip on-device (2026-06-15): the comma made the fare (and the
    // card signature) unparseable, so the verdict couldn't hold.
    private val commaDecimalFare = listOf(
        block("Radar de solicitud de viaje 2"),
        block("2 UberX"),
        block("MXN33,45"),
        block("Identidad verificada"),
        block("★ 4.79 (71) 9 +MXN4.00 incluido"),
        block("A6 min (0.8 km)"),
        block("Viaje: 7 min (1.4 km)"),
        block("Viaje disponible"),
    )

    @Test
    fun `parses a radar card whose fare OCR'd with a comma decimal`() {
        val card = parser.parse(commaDecimalFare)!!
        assertEquals(33.45, card.fare!!, 0.001) // "MXN33,45" -> 33.45, NOT 3345
        assertEquals(0.8, card.pickupDistanceKm!!, 0.001)
        assertEquals(1.4, card.tripDistanceKm!!, 0.001)
        assertEquals(7.0, card.tripDurationMin!!, 0.001)
        assertEquals("trip_radar", card.variant)
        assertTrue(parser.hasCardSignature(commaDecimalFare)) // must hold through comma frames
    }

    @Test
    fun `garbled trip label keeps the card signature but does not parse (B-077)`() {
        // "Viaje" mangled to "Vlaje": no labeled trip leg -> no verdict, but a fare + a leg remain,
        // so the signature says the card is still on screen and the prior verdict should hold.
        val garbled = listOf(
            block("MXN84.63"),
            block("A9 min (2.3 km)"),
            block("Vlaje 33 min (8.7 km)"),
        )
        assertNull(parser.parse(garbled))
        assertTrue(parser.hasCardSignature(garbled))
    }

    @Test
    fun `recovers OCR-garbled minute glyphs without inflating them 10x`() {
        // Live frames render the same minutes as "2l" (=21, l IS the digit) and "21l"/"41l" (=21/41,
        // l is a spurious trailing glyph). Both must resolve correctly, never 211/411.
        fun tripMin(legText: String): Double {
            val card = parser.parse(
                listOf(
                    block("UberX"),
                    block("MXN80.00"),
                    block("A 3 min (0.6 km)"),
                    block(legText),
                ),
            )!!
            return card.tripDurationMin!!
        }
        assertEquals(21.0, tripMin("Viaje: 2l min (3.5 km)"), 0.001) // l is the 2nd digit
        assertEquals(21.0, tripMin("Viaje: 21l min (3.5 km)"), 0.001) // l is spurious noise
        assertEquals(41.0, tripMin("Viaje: 41l min (3.5 km)"), 0.001)
        assertEquals(11.0, tripMin("Viaje: 1l min (3.5 km)"), 0.001)
        assertEquals(12.0, tripMin("Viaje: 12 min (3.5 km)"), 0.001) // clean stays clean
    }

    @Test
    fun `searching screen is not an offer`() {
        // The real online-but-idle screen we captured: a "+" incentive and a radar ETA, no card.
        val searching = listOf(
            block("+MXN 10"),
            block("1-2 min"),
            block("Buscando viajes"),
            block("No es posible desconectarse"),
        )
        assertNull(parser.parse(searching))
        assertFalse(parser.hasCardSignature(searching))
    }

    @Test
    fun `a DiDi card does not parse as Uber`() {
        // DiDi fares are bare "$" with no "MXN" — UberOcrParser must stay silent so the two OCR
        // parsers can be tried in turn (Uber first) without fighting on the bus.
        val didi = listOf(
            block("\$132.59"),
            block("6min (1.2km)"),
            block("39min (17.3km)"),
            block("Aceptar \$132.59"),
        )
        assertNull(parser.parse(didi))
    }

    @Test
    fun `an Uber card does not parse as DiDi`() {
        // The mirror guarantee: DidiOcrParser must stay silent on an Uber frame (no bare "$").
        assertNull(DidiOcrParser().parse(exclusivo))
    }

    @Test
    fun `DiDi parser ignores an Uber MX$ fare even when the Uber parse fails`() {
        // The OCR service tries Uber then DiDi. If an "MX$" Uber offer garbles its "Viaje:" label,
        // UberOcrParser returns null — DiDi must NOT step in: its bare-$ fare regex must not match the
        // "$" inside "MX$", or the ledger would mis-attribute a live Uber offer to DiDi.
        val uberMxDollarGarbledTrip = listOf(
            block("UberX Exclusivo"),
            block("MX\$135.00"),
            block("A 5 min (0.8 km)"),
            block("Vlaje 14 min (4.2 km)"), // "Viaje" mangled → no labelled trip leg
        )
        assertNull(parser.parse(uberMxDollarGarbledTrip)) // Uber: no trip leg → null
        assertNull(DidiOcrParser().parse(uberMxDollarGarbledTrip)) // DiDi must not steal the MX$ fare
        // And the same offer, un-garbled, IS a clean Uber parse (MX$ fare supported).
        val intact = uberMxDollarGarbledTrip.dropLast(1) + block("Viaje: 14 min (4.2 km)")
        assertEquals(135.00, parser.parse(intact)!!.fare!!, 0.001)
    }

    @Test
    fun `recovers a pickup distance whose decimal point OCR dropped`() {
        // Real first-frame garble from a live Polanco offer (2026-06-15): the "0.4 km" pickup OCR'd as
        // "04 km", which naively parsed to 4.0 km — a 10× error that the overlay self-corrected on the
        // next frame but the ledger had already frozen into the offer record. A leading-zero,
        // separator-less distance is a dropped decimal, so it must read back as 0.4 km.
        val decimalDropped = listOf(
            block("UberX Exclusivo"),
            block("MXN29.45"),
            block("A 2 min (04 km)"),
            block("Viaje: 5 min (0.8 km)"),
            block("Aceptar"),
        )
        val card = parser.parse(decimalDropped)!!
        assertEquals(29.45, card.fare!!, 0.001)
        assertEquals(0.4, card.pickupDistanceKm!!, 0.001) // not 4.0
        assertEquals(2.0, card.pickupEtaMin!!, 0.001)
        assertEquals(0.8, card.tripDistanceKm!!, 0.001)
    }

    @Test
    fun `captures the fare and leg block union as contentBounds`() {
        // Real fixture coordinates (ocr_1781569258770.json): fare top=785, legs lower; the union is
        // what the overlay keeps the chip off so it never occludes the fare from the screen capture.
        val frame = listOf(
            block("MXN137.28", left = 103, top = 785, right = 769, bottom = 881),
            block("A 5 min (1.0 km)", left = 192, top = 1339, right = 551, bottom = 1384),
            block("Viaje: 51 min (13.0 km)", left = 216, top = 1537, right = 687, bottom = 1590),
        )
        val card = parser.parse(frame)!!
        assertEquals(137.28, card.fare!!, 0.001)
        val cb = card.contentBounds!!
        assertEquals(103, cb.left)
        assertEquals(785, cb.top)
        assertEquals(769, cb.right)
        assertEquals(1590, cb.bottom)
    }

    @Test
    fun `contentBounds degrades to a degenerate rect when blocks carry no bounds`() {
        // The value-only fixtures (zero-bounds blocks) still parse; the union is (0,0,0,0), which the
        // overlay's avoid() treats as no-overlap, so positioning is unaffected.
        val cb = parser.parse(exclusivo)!!.contentBounds
        assertTrue(cb == null || (cb.right == 0 && cb.bottom == 0))
    }

    private fun block(text: String) = OcrBlock(text, OcrBounds(0, 0, 0, 0))
    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrBlock(text, OcrBounds(left, top, right, bottom))
}
