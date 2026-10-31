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
 * Value-only transcriptions use deterministic synthetic card geometry; address text is fictitious.
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
        block("Avenida Ficticia 123, Colonia Ejemplo"),
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
        block("Calle Falsa 456, Colonia Ejemplo"),
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
    fun `garbled trip label keeps an incomplete Uber offer with unconfirmed payment`() {
        // The amount and pickup still identify Uber. Keep the partial offer; no trip leg or
        // payment meaning is inferred from the garbled label.
        val garbled = listOf(
            block("MXN84.63"),
            block("A9 min (2.3 km)"),
            block("Vlaje 33 min (8.7 km)"),
        )
        assertNull(parser.parse(garbled)!!.tripDistanceKm)
        assertEquals("UNCONFIRMED", parser.parse(garbled)!!.paymentBasis)
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
        // Both hosts now show bare "$" fares. Without an Uber heading or legacy MX fare signature,
        // UberOcrParser must stay silent so DiDi can own its two-leg offer.
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
        // The OCR service tries Uber then DiDi. An unread trip label keeps an incomplete Uber
        // card; DiDi must not interpret the "$" inside "MX$" as its own payment.
        val uberMxDollarGarbledTrip = listOf(
            block("UberX Exclusivo"),
            block("MX\$135.00"),
            block("A 5 min (0.8 km)"),
            block("Vlaje 14 min (4.2 km)"), // "Viaje" mangled → no labelled trip leg
        )
        assertNull(parser.parse(uberMxDollarGarbledTrip)!!.tripDistanceKm) // Uber: incomplete trip
        assertNull(DidiOcrParser().parse(uberMxDollarGarbledTrip)) // DiDi must not steal the MX$ fare
        // And the same offer, un-garbled, IS a clean Uber parse (MX$ fare supported).
        val intact = uberMxDollarGarbledTrip.dropLast(1) + block("Viaje: 14 min (4.2 km)")
        assertEquals(135.00, parser.parse(intact)!!.fare!!, 0.001)
    }

    @Test
    fun `recovers a pickup distance whose decimal point OCR dropped`() {
        // Real first-frame garble from a live offer (2026-06-15): the "0.4 km" pickup OCR'd as
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
    fun `recovers ×10 trip and pickup distances whose decimal point OCR dropped`() {
        // The exact ×10 corruption frame (fixture ocr_1781569264865.json, live 2026-06-15): the real
        // 1.0 km pickup OCR'd as "A5 min (L0 km)" and the real 13.0 km trip as "Viaje: 51 min (130 km)"
        // — the decimal point dropped. Naively that reads 10.0 km / 130.0 km, making net $/km ~10× too
        // low so a great ride paints RED. The fare on this frame was itself garbled ("MXNAggo"), so we
        // pair the real corrupted legs (with their fixture bounds) with the readable fare the SAME
        // offer shows on its clean frames (MXN137.28, fixture ocr_1781569258770.json).
        val decimalDropped = listOf(
            block("MXN137.28", left = 103, top = 785, right = 769, bottom = 881),
            block("A5 min (L0 km)", left = 191, top = 1551, right = 559, bottom = 1625),
            block("Viaje: 51 min (130 km)", left = 191, top = 1763, right = 685, bottom = 1827),
        )
        val card = parser.parse(decimalDropped)!!
        assertEquals(137.28, card.fare!!, 0.001)
        assertEquals(1.0, card.pickupDistanceKm!!, 0.001) // "L0" → 1.0, not 10.0
        assertEquals(5.0, card.pickupEtaMin!!, 0.001)
        assertEquals(13.0, card.tripDistanceKm!!, 0.001) // "130" → 13.0, not 130.0
        assertEquals(51.0, card.tripDurationMin!!, 0.001)
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
        // Host/route evidence still recognizes an incomplete card. Without glyph geometry the
        // payment is untrusted; the degenerate route union cannot authorize overlay placement.
        val card = parser.parse(exclusivo.map { it.copy(bounds = OcrBounds(0, 0, 0, 0)) })!!
        assertNull(card.fare)
        val cb = card.contentBounds
        assertTrue(cb == null || (cb.right == 0 && cb.bottom == 0))
    }

    @Test
    fun `recognized offer with unread zero amount emits C4 without a zero fare`() {
        // Zero is never treated as payment. Independent offer anchors retain a grey partial card.
        val zeroFare = listOf(
            block("2 Reservar UberX"),
            block("MXN0.00"),
            block("A 5 min (1.0 km)"),
            block("Viaje: 51 min (13.0 km)"),
            block("Aceptar"),
        )
        assertNull(parser.parse(zeroFare)!!.fare)
        assertTrue(parser.hasCardSignature(zeroFare))
        val walletOnly = listOf(block("MXN0.00"), block("A 5 min (1.0 km)"))
        assertNull(parser.parse(walletOnly))
        assertFalse(parser.hasCardSignature(walletOnly))
    }

    @Test
    fun `known variants quote estimated driver payment`() {
        for (frame in listOf(exclusivo, priorityWithBonus, garbledDistance, commaDecimalFare,
            exclusivo + block("Reserva"), exclusivo + block("2 paradas"), exclusivo + block("Tarifa dinámica"))) {
            assertEquals("DRIVER_PAYMENT", parser.parse(frame)!!.paymentBasis)
        }
    }

    @Test
    fun `missing fare still recognizes C4 and retains route bounds`() {
        val frame = exclusivo.filterNot { it.text == "MXN96.17" }
        val card = parser.parse(frame)!!
        assertTrue(parser.hasCardSignature(frame))
        assertNull(card.fare)
        assertEquals(11.3, card.tripDistanceKm!!, 0.001)
        assertEquals("DRIVER_PAYMENT", card.paymentBasis)
    }

    @Test
    fun `an unrelated zero wallet pill cannot erase a missing-fare offer`() {
        val frame = exclusivo.filterNot { it.text == "MXN96.17" } +
            block("MXN0.00", left = 300, top = 70, right = 450, bottom = 110)
        val card = parser.parse(frame)!!
        assertNull(card.fare)
        assertEquals("DRIVER_PAYMENT", card.paymentBasis)
        assertTrue(parser.hasCardSignature(frame))
    }

    @Test
    fun `missing times retain distances for C5 without inventing zero minutes`() {
        val frame = listOf(block("UberX"), block("MXN148.00"), block("A — min (2.0 km)"),
            block("Viaje: — min (10.0 km)"), block("Aceptar"))
        val card = parser.parse(frame)!!
        assertEquals(148.0, card.fare!!, 0.001)
        assertEquals(2.0, card.pickupDistanceKm!!, 0.001)
        assertEquals(10.0, card.tripDistanceKm!!, 0.001)
        assertNull(card.pickupEtaMin)
        assertNull(card.tripDurationMin)
        assertEquals("DRIVER_PAYMENT", card.paymentBasis)
    }

    @Test
    fun `unknown Uber offer structure is recognized without certifying payment`() {
        // 0.2.14: a card signature needs a route leg; one unlabeled leg is still an unknown structure.
        assertNull(parser.parse(listOf(block("Uber nueva modalidad"), block("MXN148.00"), block("Aceptar"))))
        val card = parser.parse(listOf(block("Uber nueva modalidad"), block("MXN148.00"),
            block("8 min (2.0 km)"), block("Aceptar")))!!
        assertEquals("UNCONFIRMED", card.paymentBasis)
        assertNull(card.tripDistanceKm)
    }

    /** Synthetic geometry for text transcriptions, separate from the explicit captured bounds. */
    private fun block(text: String): OcrBlock {
        val top = when {
            text == "+MXN 4" || text == "+MXN 10" -> 400 // map incentive, above the card
            text.contains("Uber") || text.startsWith("Radar de solicitud") -> 650
            text.startsWith("MX") || text.startsWith("$") -> 770
            text.startsWith("Aceptar") -> 1980
            text.startsWith("Viaje:") || text.startsWith("Vlaje") -> 1600
            text.startsWith("Viaje disponible") -> 1720
            text.contains("min") && text.contains("(") -> if (text.startsWith("39")) 1600 else 1400
            text.startsWith("Avenida Ficticia") || text.startsWith("Calle Falsa") -> 1480
            text.contains("incluido") || text.startsWith("+MXN") -> 1200
            text.contains("★") || text.startsWith("*") -> 1100
            text == "Reserva" || text == "2 paradas" || text == "Tarifa dinámica" -> 1250
            else -> 1000
        }
        val headline = text.startsWith("MX") || text.startsWith("$")
        return OcrBlock(text, OcrBounds(96, top, if (headline) 499 else 960,
            top + if (headline) 127 else 60))
    }
    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrBlock(text, OcrBounds(left, top, right, bottom))
}
