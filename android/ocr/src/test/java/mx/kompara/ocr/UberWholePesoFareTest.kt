package mx.kompara.ocr

import mx.kompara.parsers.model.ConsistencyGrade
import mx.kompara.parsers.model.FieldProvenance
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferContentBounds
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.FARE
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.RATE
import mx.kompara.parsers.model.ValidatedField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The S25 frame of 2026-11-10 (build 1dbb957): an UberX Exclusivo whose fare Uber printed `$109`, no
 * decimals (109.00 / 8.6 km = `$12.67/km`). All 17 frames were read cleanly and rejected as
 * PARSED_CONTRADICTORY because `$109` was read only as 1.09. Numbers and geometry exactly as read; the
 * three address lines, the two map labels and the rider rating are made-up text of similar shape.
 */
internal object UberWholePesoFrame {
    const val HEADLINE = "\$109"
    const val TOTAL = "51 min 8.6 km \$12.67/km est."
    const val HEADING = "2 UberX Exclusivo"
    /** The TOTAL row as other frames of the same offer read it. */
    val totalVariants = listOf("51 min• 8.6 km • \$12.67/km est.", "51 min 8.6 km• \$12.67/km est.")
    /** The heading as other frames of the same offer read it. */
    val headingVariants = listOf("UberX Exclusivo", "2 UberX", "UberX")
    val fareBounds = OfferContentBounds(99, 1021, 394, 1130)

    val blocks: List<OcrBlock> get() = listOf(
        block(18, 1988, 30, 2030, "n"),
        block(229, 461, 532, 508, "Colonia"),
        block(126, 918, 611, 969, HEADING),
        block(99, 1021, 394, 1130, HEADLINE),
        block(166, 1263, 642, 1309, "D ldentidad verificada"),
        block(98, 1176, 837, 1222, TOTAL),
        block(122, 1391, 440, 1432, "* 4.80 (1200)"),
        block(194, 1546, 514, 1591, "4 min (0.8 km)"),
        block(813, 450, 932, 519, "Ma"),
        block(193, 1613, 807, 1661, "Calle Uno - Colonia / Zona"),
        block(194, 1690, 534, 1735, "47 min (7.8 km)"),
        block(440, 1996, 637, 2047, "Aceptar"),
        block(196, 1764, 856, 1807, "Estacion Central Dos, Col"),
        block(195, 1825, 860, 1869, "Tres, Alcaldia - Zona"),
        block(1059, 183, 1077, 214, "P"),
        block(894, 944, 933, 986, "X"),
        block(1041, 1119, 1070, 1150, "D"),
    )

    private fun block(left: Int, top: Int, right: Int, bottom: Int, text: String) = OcrBlock(text, OcrBounds(left, top, right, bottom))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberWholePesoFareTest {
    private val parser = UberOcrParser()
    private val frame get() = UberWholePesoFrame.blocks

    private fun parse(blocks: List<OcrBlock>) = requireNotNull(parser.parse(blocks, hostConfirmed = true)) { "no card" }
    private fun List<OcrBlock>.retext(from: String, to: String) = also { require(any { it.text == from }) { from } }
        .map { if (it.text == from) it.copy(text = to) else it }
    private fun OfferCard.v() = requireNotNull(validation) { "no validation: the modern path did not run" }
    private fun OfferCard.economics() = listOf(fare, pickupEtaMin, pickupDistanceKm, tripDurationMin, tripDistanceKm)

    @Test fun `the phone's $109 frame reads 109 pesos with every slot as printed and the money identity holding`() {
        val forms = listOf(frame) + UberWholePesoFrame.totalVariants.map { frame.retext(UberWholePesoFrame.TOTAL, it) }
        for (blocks in forms) {
            val card = parse(blocks)
            val v = card.v()
            val total = blocks.single { it.text.contains("/km") }.text
            assertEquals(total, listOf(109.0, 4.0, 0.8, 47.0, 7.8), card.economics())
            assertEquals(total, "exclusive", card.variant)
            assertEquals(total, "DRIVER_PAYMENT", card.paymentBasis)
            assertEquals(total, List(8) { FieldProvenance.READ }, v.slots.map { it.provenance })
            assertEquals(total, List(8) { 0 }, v.slots.map { it.repairs })
            assertEquals(total, listOf(10900L, 1267L, 51L, 86L, 4L, 8L, 47L, 78L), v.slots.map { it.units })
            assertEquals(total, ConsistencyGrade.CONSISTENT, v.grade)
            assertTrue(total, v.money.evaluated && v.money.holds && v.moneyValidated)
            assertTrue(total, v.minutes.holds && v.distance.holds && v.routeValidated)
            assertEquals(total, UberWholePesoFrame.fareBounds, card.fareBounds)
            assertEquals(total, mapOf("fare" to "109.00"), card.raw)
            assertEquals(total, true, card.instanceEvidence?.routeWellFormed)
        }
    }

    @Test fun `the heading's other readings keep the fare`() {
        for (heading in UberWholePesoFrame.headingVariants) {
            val card = parse(frame.retext(UberWholePesoFrame.HEADING, heading))
            assertEquals(heading, listOf(109.0, 4.0, 0.8, 47.0, 7.8), card.economics())
            assertEquals(heading, ConsistencyGrade.CONSISTENT, card.v().grade)
            assertEquals(heading, if (heading.contains("Exclusivo")) "exclusive" else null, card.variant)
        }
    }

    private fun wholeRate(total: String) = frame.retext(UberWholePesoFrame.HEADLINE, "\$49.48")
        .retext(UberWholePesoFrame.TOTAL, total).retext("4 min (0.8 km)", "7 min (1.3 km)").retext("47 min (7.8 km)", "12 min (3.2 km)")

    @Test fun `a whole rate $11 per km reads 11 pesos and validates the fare`() {
        // The 2026-11-08 offer: 49.48 / 4.5 km = 10.996, printed `$11/km`; OCR also read it `ll`, `1l`, `l1`.
        for ((total, repairs) in listOf("19 min • 4.5 km • \$ll/km est." to ValidatedField.REPAIR_CONFUSABLE,
            "19 min • 4.5 km • \$11/km est." to 0, "19 min • 4.5 km • \$1l/km est." to ValidatedField.REPAIR_CONFUSABLE)) {
            val card = parse(wholeRate(total))
            val v = card.v()
            assertEquals(total, 1100L, v.rate.units)
            assertEquals(total, repairs, v.rate.repairs)
            assertTrue(total, v.money.evaluated && v.money.holds)
            assertEquals(total, listOf(49.48, 7.0, 1.3, 12.0, 3.2), card.economics())
            assertEquals(total, if (repairs == 0) ConsistencyGrade.CONSISTENT else ConsistencyGrade.REPAIRED, v.grade)
        }
    }

    @Test fun `$109 with the rate unread publishes the legs and never either reading of the fare`() {
        val card = parse(frame.retext(UberWholePesoFrame.TOTAL, "51 min 8.6 km \$1?.67/km est."))
        val v = card.v()
        assertNull(card.fare)
        assertNull("Neither 109.00 nor 1.09: two readings stay unread", v.fare.units)
        assertNull(v.fare.provenance)
        assertEquals("The headline's row is kept", UberWholePesoFrame.fareBounds, v.fare.bounds)
        assertEquals(UberWholePesoFrame.fareBounds, card.fareBounds)
        assertFalse(v.money.evaluated)
        assertEquals(listOf(null, 4.0, 0.8, 47.0, 7.8), card.economics())
        assertEquals(ConsistencyGrade.UNVERIFIABLE, v.grade)
        assertTrue(card.raw.isEmpty())
    }

    @Test fun `$109 with a rate that fits neither reading is contradictory and publishes nothing`() {
        val card = parse(frame.retext(UberWholePesoFrame.TOTAL, "51 min 8.6 km \$9.99/km est."))
        assertEquals(ConsistencyGrade.CONTRADICTORY, card.v().grade)
        assertEquals(List(5) { null }, card.economics())
        assertNotNull("The headline still protects its pixels", card.fareBounds)
    }

    @Test fun `the lost-decimal reading still wins where only it fits`() {
        // `$10900` would be 109.00 printed with its separator lost: 10900 cents fits, 1,090,000 does not.
        val card = parse(frame.retext(UberWholePesoFrame.HEADLINE, "\$10900"))
        assertEquals(109.0, card.fare)
        assertEquals(FieldProvenance.REPAIRED, card.v().slots[FARE].provenance)
        assertEquals(ValidatedField.REPAIR_DECIMAL, card.v().slots[FARE].repairs)
        assertEquals(ConsistencyGrade.REPAIRED, card.v().grade)
        // Both money slots with two readings (`$109`, `$1267/km`): of the four pairs only 109.00 at 12.67/km fits.
        val rate = parse(frame.retext(UberWholePesoFrame.TOTAL, "51 min 8.6 km \$1267/km est."))
        assertEquals(109.0, rate.fare)
        assertEquals(1267L, rate.v().slots[RATE].units)
        assertEquals(ValidatedField.REPAIR_DECIMAL, rate.v().slots[RATE].repairs)
    }

    @Test fun `a $0 token beside the headline is no price, a $5 token still competes`() {
        val zero = parse(frame + OcrBlock("\$0", OcrBounds(450, 1040, 560, 1110)))
        assertEquals(109.0, zero.fare)
        assertEquals(1, zero.v().fareCandidates)
        val five = parse(frame + OcrBlock("\$5", OcrBounds(450, 1040, 560, 1110)))
        assertNull(five.fare)
        assertEquals(2, five.v().fareCandidates)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, five.v().grade)
    }

    @Test fun `two whole-peso headlines withhold the fare even though one fits the rate`() {
        val card = parse(frame + OcrBlock("\$120", OcrBounds(450, 1021, 745, 1130)))
        assertNull(card.fare)
        assertNull(card.v().fare.units)
        assertEquals(2, card.v().fareCandidates)
        val protectedFare = requireNotNull(card.fareBounds)
        assertTrue("$protectedFare covers both prices", protectedFare.left <= 99 && protectedFare.right >= 745)
        assertEquals(listOf(4.0, 0.8, 47.0, 7.8), card.economics().drop(1))
    }

    @Test fun `a sole whole-peso headline of one or two digits reads whole units`() {
        // 45.00 / 4.5 km = 10.00 per km: `$45` over `$10/km` (both whole) and over `$10.00/km`.
        val base = wholeRate("19 min • 4.5 km • \$10/km est.").retext("\$49.48", "\$45")
        assertEquals(45.0, parse(base).fare)
        assertEquals(ConsistencyGrade.CONSISTENT, parse(base).v().grade)
        assertEquals(45.0, parse(base.retext("19 min • 4.5 km • \$10/km est.", "19 min • 4.5 km • \$10.00/km est.")).fare)
    }

    // Round 2 (Codex, Medium): every prefix of a whole number's digits is another whole number, so an amount
    // without decimals never stands as printed: only its evaluated money identity publishes it.

    /** The phone's card with the headline's and the rate's last digits lost: `$109` read `$10`, `$12.67` read `$12.6`. */
    private val truncated get() = frame.retext(UberWholePesoFrame.HEADLINE, "\$10")
        .retext(UberWholePesoFrame.TOTAL, "51 min 8.6 km \$12.6/km est.")

    @Test fun `a truncated whole headline with the rate unread publishes the legs and no fare`() {
        val card = parse(truncated)
        val v = card.v()
        assertNull("never 10.00", card.fare)
        assertNull("The whole reading is withheld as unread, never kept as an unvalidated READ", v.fare.units)
        assertNull(v.fare.provenance)
        assertEquals(UberWholePesoFrame.fareBounds, v.fare.bounds)
        assertEquals(UberWholePesoFrame.fareBounds, card.fareBounds)
        assertNull("`12.6` is malformed money", v.rate.units)
        assertFalse(v.money.evaluated)
        assertEquals(listOf(null, 4.0, 0.8, 47.0, 7.8), card.economics())
        assertTrue(v.minutes.holds && v.distance.holds && v.routeValidated)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, v.grade)
        assertTrue(card.raw.isEmpty())
    }

    @Test fun `a one- or two-digit whole headline with the rate unread publishes no fare`() {
        for (fare in listOf("\$45", "\$9")) {
            val card = parse(wholeRate("19 min • 4.5 km • \$1?/km est.").retext("\$49.48", fare))
            assertNull(fare, card.fare)
            assertNull(fare, card.v().fare.units)
            assertEquals(fare, listOf(7.0, 1.3, 12.0, 3.2), card.economics().drop(1))
            assertEquals(fare, ConsistencyGrade.UNVERIFIABLE, card.v().grade)
        }
    }

    /** H2's shape ($1,320.90, `5h4 min 221.7 km $5.96/km est.`) on the phone frame's geometry. */
    private fun thousands(fare: String, rate: String) = frame.retext(UberWholePesoFrame.HEADLINE, fare)
        .retext(UberWholePesoFrame.TOTAL, "5h4 min 221.7 km \$$rate/km est.")
        .retext("4 min (0.8 km)", "18 min (4.4 km)").retext("47 min (7.8 km)", "4h46 min (217.3 km)")

    @Test fun `a whole thousands headline publishes only through its money identity`() {
        // 1,320.00 / 221.7 km = 5.954, printed `$5.95/km`: 2·|595·2217 − 13,200,000| = 1770 ≤ 2217.
        val validated = parse(thousands("\$1,320", "5.95"))
        assertEquals(listOf(1320.0, 18.0, 4.4, 286.0, 217.3), validated.economics())
        assertEquals(FieldProvenance.READ, validated.v().slots[FARE].provenance)
        assertTrue(validated.v().money.evaluated && validated.v().money.holds)
        assertEquals(ConsistencyGrade.CONSISTENT, validated.v().grade)
        // The rate unread: no fare, the legs stand.
        val unread = parse(thousands("\$1,320", "5.9?"))
        assertNull(unread.fare)
        assertNull(unread.v().fare.units)
        assertEquals(listOf(18.0, 4.4, 286.0, 217.3), unread.economics().drop(1))
        assertEquals(ConsistencyGrade.UNVERIFIABLE, unread.v().grade)
        // `$1,320.90` whose decimals OCR dropped, over its own `$5.96/km`: 2664 > 2217, contradictory, nothing published.
        val dropped = parse(thousands("\$1,320", "5.96"))
        assertEquals(ConsistencyGrade.CONTRADICTORY, dropped.v().grade)
        assertEquals(List(5) { null }, dropped.economics())
        assertEquals("The printed fare itself still reads", 1320.90, parse(thousands("\$1,320.90", "5.96")).fare)
    }
}
