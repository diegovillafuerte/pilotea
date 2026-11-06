package mx.kompara.ocr

import mx.kompara.parsers.model.ConsistencyGrade
import mx.kompara.parsers.model.FieldProvenance
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.FARE
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.PICKUP_KM
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.PICKUP_MINUTES
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.RATE
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TOTAL_KM
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TOTAL_MINUTES
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TRIP_KM
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TRIP_MINUTES
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
 * The spec's failing-first table (section E) and money-safety invariants (section F) on the exact
 * real token strings of the sanitized S25 corpus: each case is a recorded frame, edited only where
 * a negative case needs a token the corpus never showed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberModernParseTest {
    private val parser = UberOcrParser()

    private fun frame(group: Int, index: Int) = UberS25RealCorpus.groups[group].frames[index]
    private fun blocks(group: Int, index: Int, vararg mustContain: String) = frame(group, index).masked.also { b ->
        mustContain.forEach { token -> require(b.any { it.text == token }) { "G$group[$index] lacks $token" } }
    }
    private fun parse(blocks: List<OcrBlock>) = requireNotNull(parser.parse(blocks, hostConfirmed = true)) { "no card" }
    private fun List<OcrBlock>.retext(from: String, to: String) = also { require(any { it.text == from }) { from } }
        .map { if (it.text == from) it.copy(text = to) else it }
    private fun OfferCard.trip() = tripDurationMin to tripDistanceKm
    private fun OfferCard.pickup() = pickupEtaMin to pickupDistanceKm
    private fun OfferCard.slot(index: Int) = requireNotNull(validation) { "no validation" }.slots[index]
    private fun OfferCard.grade() = requireNotNull(validation) { "no validation" }.grade

    @Test fun `icon-prefixed route h 36 min (12_2 km) reads the trip leg`() {
        val card = parse(blocks(4, 0, "h 36 min (12.2 km)"))
        assertEquals(36.0 to 12.2, card.trip())
        assertEquals(FieldProvenance.READ, card.slot(TRIP_MINUTES).provenance)
        assertEquals(FieldProvenance.READ, card.slot(TRIP_KM).provenance)
    }

    @Test fun `multiple prefix glyphs e h and ad h are route-icon prefixes`() {
        assertEquals(36.0 to 12.2, parse(blocks(4, 1, "e h 36 min (12.2 km)")).trip())
        assertEquals(17.0 to 4.3, parse(blocks(10, 1, "ad h 17 min (4.3 km)")).trip())
    }

    @Test fun `existing confusable ll min (2_2 km) reads 11 minutes`() {
        val card = parse(blocks(2, 8, "ll min (2.2 km)"))
        assertEquals(11.0 to 2.2, card.trip())
        assertEquals(FieldProvenance.REPAIRED, card.slot(TRIP_MINUTES).provenance)
        assertEquals(ValidatedField.REPAIR_CONFUSABLE, card.slot(TRIP_MINUTES).repairs)
    }

    @Test fun `confusable plus prefix h 1l min (3_5 km) reads 11 minutes`() {
        val card = parse(blocks(3, 0, "h 1l min (3.5 km)"))
        assertEquals(11.0 to 3.5, card.trip())
        assertEquals(FieldProvenance.REPAIRED, card.slot(TRIP_MINUTES).provenance)
    }

    @Test fun `unreadable dlZ minutes are recovered from the TOTAL and the pickup, never decoded`() {
        val card = parse(blocks(0, 20, "dlZ min (4.2 km)", "3 min (0.7 km)"))
        assertEquals(17.0 to 4.2, card.trip())
        val v = requireNotNull(card.validation)
        assertEquals(FieldProvenance.RECOVERED, v.tripMinutes.provenance)
        assertEquals((1 shl TOTAL_MINUTES) or (1 shl PICKUP_MINUTES), v.tripMinutes.operands)
        assertFalse("A recovered operand is not an independent minute identity", v.minutes.evaluated)
        assertTrue(v.distance.evaluated && v.distance.holds)
        assertEquals(ConsistencyGrade.REPAIRED, v.grade)
        assertEquals(true, card.instanceEvidence?.routeWellFormed)
    }

    @Test fun `unit misspellings knm and nmin are repaired in their route-unit positions`() {
        val knm = parse(blocks(6, 0, "10 min (2.5 knm)"))
        assertEquals(10.0 to 2.5, knm.trip())
        assertEquals(ValidatedField.REPAIR_UNIT, knm.slot(TRIP_KM).repairs)
        assertEquals(FieldProvenance.REPAIRED, knm.slot(TRIP_KM).provenance)
        val nmin = parse(blocks(8, 3, "h 19 nmin (5.2 km)"))
        assertEquals(19.0 to 5.2, nmin.trip())
        assertEquals(ValidatedField.REPAIR_UNIT, nmin.slot(TRIP_MINUTES).repairs)
    }

    @Test fun `TOTAL confusable 5l min reads 51 total minutes`() {
        val card = parse(blocks(1, 1, "5l min 21.8 km \$9.78/km est."))
        assertEquals(51L, card.slot(TOTAL_MINUTES).units)
        assertEquals(FieldProvenance.REPAIRED, card.slot(TOTAL_MINUTES).provenance)
        assertEquals(213.14, card.fare)
        assertEquals(ConsistencyGrade.REPAIRED, card.grade())
    }

    @Test fun `distance repair (l5 km) reads 1_5 only with an independent identity`() {
        val validated = blocks(1, 2, "6 min (l5 km)", "5l min 21.8 km \$9.78/km est.")
        val card = parse(validated)
        assertEquals(6.0 to 1.5, card.pickup())
        assertEquals(FieldProvenance.REPAIRED, card.slot(PICKUP_KM).provenance)
        // Without the TOTAL distance nothing can choose between 15 and 1.5 km: withheld, never guessed.
        val unverifiable = parse(validated.retext("5l min 21.8 km \$9.78/km est.", "5l min ?1.8 km \$9.78/km est."))
        assertNull(unverifiable.pickupDistanceKm)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, unverifiable.grade())
        assertEquals(false, unverifiable.instanceEvidence?.routeWellFormed)
    }

    @Test fun `decimal confusable (2_l km) reads 2_1`() {
        val card = parse(blocks(1, 12, "8 min (2.l km)"))
        assertEquals(8.0 to 2.1, card.pickup())
        assertEquals(FieldProvenance.REPAIRED, card.slot(PICKUP_KM).provenance)
    }

    @Test fun `zero pickup l min (0 km) reads one minute and zero km`() {
        val card = parse(blocks(11, 0, "l min (0 km)"))
        assertEquals(1.0 to 0.0, card.pickup())
        assertEquals(FieldProvenance.REPAIRED, card.slot(PICKUP_MINUTES).provenance)
        assertEquals(FieldProvenance.READ, card.slot(PICKUP_KM).provenance)
    }

    @Test fun `fare decimal loss $17756 is repaired by the rate and the total, never without them`() {
        val real = blocks(1, 14, "\$17756", "48 min • 14.3 km -\$12.42/km est.")
        val card = parse(real)
        assertEquals(177.56, card.fare)
        assertEquals(FieldProvenance.REPAIRED, card.slot(FARE).provenance)
        assertEquals(ValidatedField.REPAIR_DECIMAL, card.slot(FARE).repairs)
        assertEquals(ConsistencyGrade.REPAIRED, card.grade())
        val unreadRate = parse(real.retext("48 min • 14.3 km -\$12.42/km est.", "48 min • 14.3 km -\$1?.42/km est."))
        assertNull("An unvalidated monetary repair is never published", unreadRate.fare)
        assertNotNull("The headline still protects its pixels", unreadRate.fareBounds)
    }

    @Test fun `rate decimal loss $740 and $721 per km are repaired`() {
        val g4 = parse(blocks(4, 0, "40 min • 13.1 km \$740/km est."))
        assertEquals(740L, g4.slot(RATE).units)
        assertEquals(FieldProvenance.REPAIRED, g4.slot(RATE).provenance)
        assertEquals(96.94, g4.fare)
        val g3 = parse(blocks(3, 0, "14 min 4.2 km • \$721/km est."))
        assertEquals(721L, g3.slot(RATE).units)
        assertEquals(30.28, g3.fare)
    }

    @Test fun `total decimal loss 179 km reads 17_9`() {
        val card = parse(blocks(5, 2, "36 min 179 km \$5.56/km est."))
        assertEquals(179L, card.slot(TOTAL_KM).units)
        assertEquals(FieldProvenance.REPAIRED, card.slot(TOTAL_KM).provenance)
        assertEquals(32.0 to 17.2, card.trip())
        assertEquals(99.52, card.fare)
    }

    @Test fun `an included +$10_1l never becomes the fare`() {
        val real = blocks(0, 6, "+\$10.1l incluido para Ejemplo de", "\$84.47")
        assertEquals(84.47, parse(real).fare)
        val headless = parse(real.filterNot { it.text == "\$84.47" })
        assertNull(headless.fare)
        assertNull(headless.validation?.fare?.units)
    }

    @Test fun `side fragments never cut the G4 headline`() {
        for (i in 8..10) {
            val card = parse(blocks(4, i, "\$96.94"))
            assertEquals("G4[$i]", 96.94, card.fare)
            assertEquals("G4[$i]", 961 to 1071, card.fareBounds?.let { it.top to it.bottom })
        }
    }

    @Test fun `side fragments never cut the Exclusivo headline`() {
        for (i in 9..18) {
            val card = parse(blocks(1, i))
            assertEquals("G1[$i]", 177.56, card.fare)
            assertTrue("G1[$i]", card.fareBounds!!.top in 805..806)
        }
    }

    @Test fun `block order never changes a field, provenance, grade or bound`() {
        for ((group, index) in listOf(1 to 14, 0 to 20, 5 to 2, 6 to 0)) {
            val base = parse(blocks(group, index))
            for ((name, order) in UberS25RealCorpus.orders(blocks(group, index))) {
                val card = parse(order)
                assertEquals("G$group[$index] $name", base, card)
            }
        }
    }

    @Test fun `a validated route keeps a three-digit duration`() {
        val card = parse(blocks(1, 0).retext("51 min 21.8 km \$9.78/km est.", "110 min 21.8 km \$9.78/km est.")
            .retext("h 45 min (20.3 km)", "h 104 min (20.3 km)"))
        assertEquals(104.0 to 20.3, card.trip())
        assertEquals(ConsistencyGrade.CONSISTENT, card.grade())
    }

    @Test fun `recovery never subtracts a repaired operand`() {
        // Codex round 2 F2: G5[2]'s TOTAL km "179" is only money-validated as 17.9 (a decimal repair);
        // with the trip distance unread the route stays incomplete instead of recovering 17.9 - 0.7.
        val card = parse(blocks(5, 2, "36 min 179 km \$5.56/km est.").retext("32 min (17.2 km)", "32 min (?.? km)"))
        assertNull(card.tripDistanceKm)
        assertNull(card.validation?.tripKm?.provenance)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, card.grade())
        assertEquals(false, card.instanceEvidence?.routeWellFormed)
        assertEquals("The repaired total itself stays as read", 179L, card.slot(TOTAL_KM).units)
    }

    // Money safety (spec F): each stays Sin lectura.

    @Test fun `a missing headline never becomes rate times distance`() {
        val card = parse(blocks(4, 0).filterNot { it.text == "\$96.94" })
        assertNull(card.fare)
        assertNull(card.fareBounds)
    }

    @Test fun `two eligible headline prices withhold payment even when one fits the checksum`() {
        val card = parse(blocks(4, 0) + OcrBlock("\$90.00", OcrBounds(560, 980, 840, 1060)))
        assertNull(card.fare)
        assertEquals(2, card.validation?.fareCandidates)
        // Ambiguous association withholds (spec I): the price is unknown, the route is still the card's.
        assertEquals(ConsistencyGrade.UNVERIFIABLE, card.grade())
        // Spec D.3 (Codex round 2 F3): both visible prices stay the protected fare region.
        val protectedFare = requireNotNull(card.fareBounds) { "competing prices lost their protected bounds" }
        for ((l, t, r, b) in listOf(listOf(100, 961, 514, 1071), listOf(560, 980, 840, 1060)))
            assertTrue("$protectedFare covers ($l,$t,$r,$b)", protectedFare.left <= l && protectedFare.top <= t &&
                protectedFare.right >= r && protectedFare.bottom >= b)
    }

    @Test fun `a clean fare that contradicts the card's own rate is contradictory`() {
        val card = parse(blocks(4, 0).retext("\$96.94", "\$69.94"))
        assertEquals(ConsistencyGrade.CONTRADICTORY, card.grade())
        assertNull(card.fare)
        assertNull("No route value of a contradictory frame can vote", card.tripDistanceKm)
        assertEquals(false, card.instanceEvidence?.routeWellFormed)
        assertNotNull("The headline still protects its pixels", card.fareBounds)
    }

    @Test fun `contradictory route minutes withhold payment and the route`() {
        val card = parse(blocks(4, 0).retext("40 min • 13.1 km \$740/km est.", "41 min • 13.1 km \$740/km est."))
        assertEquals(ConsistencyGrade.CONTRADICTORY, card.grade())
        assertNull(card.fare)
        assertNull(card.pickupDistanceKm)
    }

    @Test fun `a departing card is never completed from off-screen rows`() {
        for ((group, index) in listOf(8 to 13, 12 to 13)) {
            val blocks = blocks(group, index)
            assertFalse(parser.hasCardSignature(blocks, hostConfirmed = true))
            assertNull(parser.parse(blocks, hostConfirmed = true)?.fare)
        }
    }
}
