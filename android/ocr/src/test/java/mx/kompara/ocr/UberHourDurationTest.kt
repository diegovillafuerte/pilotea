package mx.kompara.ocr

import mx.kompara.parsers.model.ConsistencyGrade
import mx.kompara.parsers.model.FieldProvenance
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.PICKUP_MINUTES
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
 * 0.2.16 duration grammar (spec: Codex Astra, 2026-11-06): Uber prints `1 h 23 min` and ML Kit reads
 * `lh 23 min`, `5h4 min`, `4h46`, `lh5`. A whole duration slot is identified first and decoded second
 * (`60·H + M`, 1 ≤ H ≤ 23, 0 ≤ M ≤ 59; unit conversion is no repair, confusables are); a failed hour
 * parse never retries on its trailing minute number. Cards are the real S25 frames of the hour run,
 * edited only where a case needs a token the device never showed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberHourDurationTest {
    private val parser = UberOcrParser()

    private fun frame(capturedAtMs: Long) = UberS25RealCorpus.pmGroups.flatMap { it.frames }.single { it.capturedAtMs == capturedAtMs }.masked
    /** H1 (UberX, $257.91): TOTAL `lh 23 min 22.2 km $1l.62/km est.`, pickup `5 min (0.8 km)`, trip `lh 18 min (21.4 km)`. */
    private val h1 get() = frame(1794008587494)
    /** H3 (Priority, $257.22): TOTAL `lh 25 min 14.6 km$17.62/km est.`, pickup `20 min (1.8 km)`, trip `h lh5 min (12.8 km)`. */
    private val h3 get() = frame(1794008743732)
    private val h1Total = "lh 23 min 22.2 km \$1l.62/km est."
    private val h3Total = "lh 25 min 14.6 km\$17.62/km est."

    private fun parse(blocks: List<OcrBlock>) = requireNotNull(parser.parse(blocks, hostConfirmed = true)) { "no card" }
    private fun List<OcrBlock>.retext(from: String, to: String) = also { require(any { it.text == from }) { from } }
        .map { if (it.text == from) it.copy(text = to) else it }
    private fun OfferCard.slot(index: Int) = requireNotNull(validation) { "no validation: the modern path did not run" }.slots[index]
    private fun OfferCard.grade() = requireNotNull(validation) { "no validation" }.grade

    @Test fun `every accepted duration form decodes to whole minutes in the TOTAL slot with its provenance`() {
        val accepted = listOf("lh 23" to (83L to true), "1 h 23" to (83L to false), "5h4" to (304L to false),
            "4h46" to (286L to false), "4h 46" to (286L to false), "lh5" to (65L to true), "l h 6" to (66L to true),
            "1 h l" to (61L to true), "1 h 0" to (60L to false))
        for ((form, expected) in accepted) {
            val card = parse(h1.retext(h1Total, "$form min 22.2 km \$1l.62/km est."))
            val slot = card.slot(TOTAL_MINUTES)
            assertEquals(form, expected.first, slot.units)
            assertEquals(form, if (expected.second) FieldProvenance.REPAIRED else FieldProvenance.READ, slot.provenance)
            assertEquals(form, if (expected.second) ValidatedField.REPAIR_CONFUSABLE else 0, slot.repairs)
        }
    }

    @Test fun `out-of-bound and malformed hours stay unread and never expose their minute suffix`() {
        for (form in listOf("1 h 60", "0 h 5", "24 h 0", "xh 18", "h18", "xh 23", "h 23")) {
            val card = parse(h1.retext(h1Total, "$form min 22.2 km \$1l.62/km est."))
            // A recognized TOTAL envelope keeps the modern path even when its duration is unread.
            assertNotNull("$form: the TOTAL envelope must select the modern path", card.validation)
            assertNull("$form: no TOTAL minutes", card.slot(TOTAL_MINUTES).units)
            assertNull("$form: an unread total proves no trip", card.tripDurationMin)
        }
    }

    @Test fun `route rows decode the whole duration after at most one allowed icon prefix`() {
        val h2 = frame(1794008677258) // trip `t 4h46 min (217.3 km)`
        assertEquals(286L, parse(h2).slot(TRIP_MINUTES).units)
        assertEquals("The t prefix is no repair", FieldProvenance.READ, parse(h2).slot(TRIP_MINUTES).provenance)
        val kmn = parse(frame(1794008678189)) // trip `h 4h46 min (217.3 kmn)`
        assertEquals(286L, kmn.slot(TRIP_MINUTES).units)
        assertEquals(ValidatedField.REPAIR_UNIT, kmn.slot(TRIP_KM).repairs)
        assertEquals(286.0 to 217.3, kmn.tripDurationMin to kmn.tripDistanceKm)
        val spaced = parse(frame(1794008688938)) // trip `4h 46 min (217.3 km)`
        assertEquals(286.0 to 217.3, spaced.tripDurationMin to spaced.tripDistanceKm)
        val lh = parse(h1) // `lh 18`: the l is the hour digit, never an icon
        assertEquals(78L, lh.slot(TRIP_MINUTES).units)
        assertEquals(ValidatedField.REPAIR_CONFUSABLE, lh.slot(TRIP_MINUTES).repairs)
        val glued = parse(h3) // `h lh5`: one `h ` icon, then `lh5`
        assertEquals(65L, glued.slot(TRIP_MINUTES).units)
        assertEquals(FieldProvenance.REPAIRED, glued.slot(TRIP_MINUTES).provenance)
        // Only one prefix is removed; a second `h ` is never stripped into bare minutes.
        assertNull(parse(h3.retext("h lh5 min (12.8 km)", "h h 65 min (12.8 km)")).slot(TRIP_MINUTES).units)
    }

    @Test fun `the complete H3 frame books trip 65 with an independent minute identity, never 5`() {
        val card = parse(h3)
        assertEquals(257.22, card.fare)
        assertEquals(20.0 to 1.8, card.pickupEtaMin to card.pickupDistanceKm)
        assertEquals(65.0 to 12.8, card.tripDurationMin to card.tripDistanceKm)
        val v = requireNotNull(card.validation)
        assertEquals(85L, v.totalMinutes.units)
        assertEquals(FieldProvenance.REPAIRED, v.totalMinutes.provenance)
        assertEquals(FieldProvenance.REPAIRED, v.tripMinutes.provenance)
        assertEquals("No recovery operand", 0, v.tripMinutes.operands)
        assertTrue(v.minutes.evaluated && v.minutes.holds)
        assertEquals(0L, v.minutes.residual)
        assertEquals(ConsistencyGrade.REPAIRED, v.grade)
    }

    @Test fun `a repaired lh TOTAL is never a recovery operand, so an unread trip never becomes 5`() {
        val card = parse(h3.retext("h lh5 min (12.8 km)", "h xh5 min (12.8 km)"))
        assertEquals(85L, card.slot(TOTAL_MINUTES).units)
        assertNull(card.tripDurationMin)
        assertNull(card.validation?.tripMinutes?.provenance)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, card.grade())
        assertEquals(false, card.instanceEvidence?.routeWellFormed)
        // A TOTAL read as printed (`1 h 25`) is an operand: 85 − 20, with the distance identity holding.
        val printed = parse(h3.retext(h3Total, "1 h 25 min 14.6 km\$17.62/km est.").retext("h lh5 min (12.8 km)", "h xh5 min (12.8 km)"))
        assertEquals(65.0, printed.tripDurationMin)
        assertEquals(FieldProvenance.RECOVERED, printed.slot(TRIP_MINUTES).provenance)
    }

    @Test fun `a long TOTAL with sub-hour legs balances 65 = 10 + 55`() {
        val card = parse(h3.retext(h3Total, "1 h 5 min 14.6 km\$17.62/km est.")
            .retext("20 min (1.8 km)", "10 min (1.8 km)").retext("h lh5 min (12.8 km)", "h 55 min (12.8 km)"))
        assertEquals(65L, card.slot(TOTAL_MINUTES).units)
        assertEquals(FieldProvenance.READ, card.slot(TOTAL_MINUTES).provenance)
        assertEquals(10.0 to 55.0, card.pickupEtaMin to card.tripDurationMin)
        assertTrue(card.validation!!.minutes.holds)
        assertEquals(ConsistencyGrade.CONSISTENT, card.grade())
    }

    @Test fun `a readable h 18 is the icon plus 18 and a contradiction stays a contradiction`() {
        // Short card: TOTAL 23 = 5 + 18 agrees with the icon reading.
        val short = parse(h1.retext(h1Total, "23 min 22.2 km \$1l.62/km est.").retext("lh 18 min (21.4 km)", "h 18 min (21.4 km)"))
        assertEquals(18.0, short.tripDurationMin)
        assertTrue(short.validation!!.minutes.holds)
        // Hour card: TOTAL 83 ≠ 5 + 18. The readable 18 is never emptied to let 83 − 5 recover 78.
        val long = parse(h1.retext("lh 18 min (21.4 km)", "h 18 min (21.4 km)"))
        assertEquals(ConsistencyGrade.CONTRADICTORY, long.grade())
        assertEquals(18L, long.slot(TRIP_MINUTES).units)
        assertFalse(long.slot(TRIP_MINUTES).provenance == FieldProvenance.RECOVERED)
        assertNull(long.tripDurationMin)
        assertNull(long.fare)
    }

    @Test fun `a syntactically valid lh 18 is always 78 and a true short TOTAL rejects it`() {
        val card = parse(h1.retext(h1Total, "23 min 22.2 km \$1l.62/km est."))
        assertEquals(78L, card.slot(TRIP_MINUTES).units)
        assertEquals(ConsistencyGrade.CONTRADICTORY, card.grade())
        assertNull(card.tripDurationMin)
    }

    /**
     * Round 2 (device evidence: Uber prints exactly 60 minutes as `1 h`) supersedes round 1's "hour-only
     * stays unread": an anchored `1 h` row is 60 minutes, and a `1 h` whose minutes OCR lost is caught by
     * the minute identity — CONTRADICTORY, nothing published, never a validated 60 and never a route.
     */
    @Test fun `an hour-only reading with lost minutes ends contradictory and never completes a route`() {
        // H1's trip, printed `1 h 18 min` (78), read `1 h`: 83 ≠ 5 + 60.
        val card = parse(h1.retext("lh 18 min (21.4 km)", "1 h (21.4 km)"))
        assertNull(card.tripDurationMin)
        assertEquals(false, card.instanceEvidence?.routeWellFormed)
        assertEquals(60L, card.slot(TRIP_MINUTES).units)
        assertEquals(ConsistencyGrade.CONTRADICTORY, card.grade())
        assertNull(card.fare)
        // H3's trip, printed `1 h 5 min` (65), read `1 h` behind its `h ` icon: 85 ≠ 20 + 60.
        val h3Trip = parse(h3.retext("h lh5 min (12.8 km)", "h 1 h (12.8 km)"))
        assertEquals(60L, h3Trip.slot(TRIP_MINUTES).units)
        assertEquals(ConsistencyGrade.CONTRADICTORY, h3Trip.grade())
        assertNull(h3Trip.tripDurationMin)
        assertNull(h3Trip.fare)
        // A TOTAL printed `1 h 5 min` (65 = 10 + 55) read `1 h`: 60 ≠ 10 + 55.
        val total = parse(h3.retext(h3Total, "1 h 14.6 km\$17.62/km est.").retext("20 min (1.8 km)", "10 min (1.8 km)")
            .retext("h lh5 min (12.8 km)", "h 55 min (12.8 km)"))
        assertEquals(60L, total.slot(TOTAL_MINUTES).units)
        assertEquals(ConsistencyGrade.CONTRADICTORY, total.grade())
        assertNull(total.tripDurationMin)
        assertNull(total.fare)
    }

    // Hour-only durations (round 2): `1 h`, read `lh78 km`, `lh•78 km-` on H0's TOTAL.

    /** H0 (UberX, $121.50): TOTAL `lh78 km $15.58/km est.` (printed `1 h • 7.8 km`), pickup `18 min (1.7 km)`, trip `42 min (6.1 km)`. */
    private val h0 get() = UberS25RealCorpus.lateGroups.flatMap { it.frames }.single { it.capturedAtMs == 1794010170571 }.masked
    private val h0Total = "lh78 km \$15.58/km est."

    @Test fun `an hour-only TOTAL at the row start followed directly by the distance is 60 minutes`() {
        val forms = listOf("lh78 km \$15.58/km est." to ValidatedField.REPAIR_CONFUSABLE,
            "lh•78 km- \$15.58/km est." to ValidatedField.REPAIR_CONFUSABLE,
            "1 h • 7.8 km • \$15.58/km est." to 0, "cid 1 h • 7.8 km • \$15.58/km est." to 0)
        for ((form, repairs) in forms) {
            val card = parse(h0.retext(h0Total, form))
            assertEquals(form, 60L, card.slot(TOTAL_MINUTES).units)
            assertEquals(form, repairs, card.slot(TOTAL_MINUTES).repairs)
            assertEquals(form, listOf(121.50, 18.0, 1.7, 42.0, 6.1),
                listOf(card.fare, card.pickupEtaMin, card.pickupDistanceKm, card.tripDurationMin, card.tripDistanceKm))
            assertTrue(form, card.validation!!.minutes.holds)
        }
    }

    @Test fun `a two-hour TOTAL is 120 minutes and balances its legs`() {
        val card = parse(h3.retext(h3Total, "2 h • 14.6 km • \$17.62/km est.").retext("h lh5 min (12.8 km)", "h 1 h 40 min (12.8 km)"))
        assertEquals(120L, card.slot(TOTAL_MINUTES).units)
        assertEquals(FieldProvenance.READ, card.slot(TOTAL_MINUTES).provenance)
        assertEquals(20.0 to 100.0, card.pickupEtaMin to card.tripDurationMin)
        assertEquals(ConsistencyGrade.CONSISTENT, card.grade())
    }

    @Test fun `an hour token followed by anything but the distance is no hour-only TOTAL`() {
        // `lh5 • 7.8 km` lost its minute unit (printed `1 h 5 min`): never 60 with the 5 swallowed.
        val forms = listOf("lh5 • 7.8 km \$15.58/km est.", "lh 5 7.8 km \$15.58/km est.", "lh • 7x8 km \$15.58/km est.", "xh • 7.8 km • \$15.58/km est.", "0 h • 7.8 km • \$15.58/km est.",
            "24 h • 7.8 km • \$15.58/km est.", "Total 1 h • 7.8 km • \$15.58/km est.")
        for (form in forms) {
            val card = requireNotNull(parser.parse(h0.retext(h0Total, form), hostConfirmed = true)) { form }
            assertNull(form, card.validation?.totalMinutes?.units)
        }
    }

    @Test fun `an hour-only route row after at most one icon prefix is 60 minutes`() {
        // `1 h (12.3 km)` on a balanced card: 80 = 20 + 60, 14.1 = 1.8 + 12.3, 2·|1824·141 − 257220| ≤ 141.
        val literal = parse(h3.retext(h3Total, "80 min 14.1 km\$18.24/km est.").retext("h lh5 min (12.8 km)", "1 h (12.3 km)"))
        assertEquals(60L, literal.slot(TRIP_MINUTES).units)
        assertEquals(60.0 to 12.3, literal.tripDurationMin to literal.tripDistanceKm)
        assertEquals(ConsistencyGrade.CONSISTENT, literal.grade())
        val icon = parse(h3.retext(h3Total, "80 min 14.6 km\$17.62/km est.").retext("h lh5 min (12.8 km)", "h 1 h (12.8 km)"))
        assertEquals(60.0 to 12.8, icon.tripDurationMin to icon.tripDistanceKm)
        assertEquals(FieldProvenance.READ, icon.slot(TRIP_MINUTES).provenance)
        assertEquals(ConsistencyGrade.CONSISTENT, icon.grade())
        // One prefix only, the whole remainder an hour token, a known unit and a readable distance.
        for (row in listOf("h h 1 h (12.8 km)", "x h (12.8 km)", "1 hx (12.8 km)", "0 h (12.8 km)", "24 h (12.8 km)",
            "1 h (128 k)", "1 h (x.y km)", "1 h 5 (12.8 km)")) {
            val card = parse(h3.retext("h lh5 min (12.8 km)", row))
            assertNull(row, card.slot(TRIP_MINUTES).units)
            assertNull(row, card.tripDistanceKm)
        }
    }

    /**
     * Round 3 supersedes round 2's "a printed one is" an operand: an hour-only `1 h` is ambiguous (its
     * minutes may be what OCR lost), so neither a repaired `lh` nor a printed `1 h` is subtracted, and
     * without an evaluated minute identity the TOTAL minutes are withheld. Fare and distances stand.
     */
    @Test fun `neither a repaired nor a printed hour-only TOTAL is a recovery operand`() {
        for (total in listOf(h0Total, "1 h • 7.8 km • \$15.58/km est.")) {
            val card = parse(h0.retext(h0Total, total).retext("42 min (6.1 km)", "4x min (6.1 km)"))
            assertNull(total, card.slot(TOTAL_MINUTES).units)
            assertNull(total, card.tripDurationMin)
            assertNull(total, card.slot(TRIP_MINUTES).provenance)
            assertEquals(total, 0, card.slot(TRIP_MINUTES).operands)
            assertFalse(total, card.validation!!.minutes.evaluated)
            assertEquals(total, ConsistencyGrade.UNVERIFIABLE, card.grade())
            assertEquals(total, listOf(121.50, 18.0, 1.7, 6.1),
                listOf(card.fare, card.pickupEtaMin, card.pickupDistanceKm, card.tripDistanceKm))
        }
    }

    // Round 3 (Codex review, REALISTIC): an hour-only reading publishes only through an independently
    // evaluated minute identity (TOTAL, pickup and trip minutes all read); otherwise its slot is withheld.

    @Test fun `an hour-only reading carries its ambiguity apart from the repair bits`() {
        assertEquals(UberCardChecksum.Candidate(60, 0, hourOnly = true), UberCardChecksum.hourOnly("1 h"))
        assertEquals(UberCardChecksum.Candidate(60, ValidatedField.REPAIR_CONFUSABLE, hourOnly = true), UberCardChecksum.hourOnly("lh"))
        assertEquals(UberCardChecksum.Candidate(304, 0), UberCardChecksum.hours("5h4"))
        assertEquals(listOf(UberCardChecksum.Candidate(23, 0)), UberCardChecksum.duration("23"))
    }

    @Test fun `an hour-only TOTAL without an evaluated minute identity is withheld and never recovers a trip`() {
        // H2 ($1,320.90, true 304 = 18 + 286): TOTAL `5h4 min` loses `4 min`, the trip icon glues to `t4h46`.
        val card = parse(frame(1794008677258).retext("5h4 min 221.7 km \$5.96/km est.", "5h 221.7 km \$5.96/km est.")
            .retext("t 4h46 min (217.3 km)", "t4h46 min (217.3 km)"))
        val v = requireNotNull(card.validation)
        assertNull("no TOTAL 300", v.totalMinutes.units)
        assertNull("no trip 282 = 300 - 18", v.tripMinutes.units)
        assertNull(v.tripMinutes.provenance)
        assertEquals(0, v.tripMinutes.operands)
        assertFalse(v.minutes.evaluated)
        assertNull(card.tripDurationMin)
        assertEquals(false, card.instanceEvidence?.routeWellFormed)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, v.grade)
        assertEquals(1320.90, card.fare)
        assertEquals(listOf(18.0, 4.4, 217.3), listOf(card.pickupEtaMin, card.pickupDistanceKm, card.tripDistanceKm))
        assertEquals(2217L, v.totalKm.units)
        assertTrue(v.distance.holds && v.money.holds)
    }

    @Test fun `an hour-only trip row without an evaluated minute identity is withheld, never a published 60`() {
        // H1 ($257.91, true trip 78): TOTAL `lh 23` loses its hour digit (`h 23`, unread), the trip `1 h 18 min` reads `1 h`.
        val card = parse(h1.retext(h1Total, "h 23 min 22.2 km \$1l.62/km est.").retext("lh 18 min (21.4 km)", "1 h (21.4 km)"))
        val v = requireNotNull(card.validation)
        assertNull(v.totalMinutes.units)
        assertNull("no trip 60", v.tripMinutes.units)
        assertNull(card.tripDurationMin)
        assertFalse(v.minutes.evaluated)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, v.grade)
        assertEquals(listOf(257.91, 5.0, 0.8, 21.4), listOf(card.fare, card.pickupEtaMin, card.pickupDistanceKm, card.tripDistanceKm))
    }

    @Test fun `an hour-only route row publishes 60 only with an evaluated minute identity`() {
        val balanced = h3.retext(h3Total, "80 min 14.1 km\$18.24/km est.").retext("h lh5 min (12.8 km)", "1 h (12.3 km)")
        val evaluated = parse(balanced)
        assertEquals(60.0, evaluated.tripDurationMin)
        assertTrue(evaluated.validation!!.minutes.evaluated && evaluated.validation!!.minutes.holds)
        // TOTAL minutes unread: no evaluated identity, so the 60 is withheld; fare and distances stand.
        val unread = parse(balanced.retext("80 min 14.1 km\$18.24/km est.", "8x min 14.1 km\$18.24/km est."))
        assertNull(unread.slot(TRIP_MINUTES).units)
        assertNull(unread.tripDurationMin)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, unread.grade())
        assertEquals(listOf(257.22, 20.0, 1.8, 12.3), listOf(unread.fare, unread.pickupEtaMin, unread.pickupDistanceKm, unread.tripDistanceKm))
        // An hour-only pickup is no operand either: a printed TOTAL 85 minus `1 h` never recovers a 25-minute trip.
        val pickup = parse(h3.retext(h3Total, "1 h 25 min 14.6 km\$17.62/km est.").retext("20 min (1.8 km)", "1 h (1.8 km)")
            .retext("h lh5 min (12.8 km)", "h xh5 min (12.8 km)"))
        assertNull(pickup.tripDurationMin)
        assertNull(pickup.slot(TRIP_MINUTES).provenance)
        assertNull(pickup.slot(PICKUP_MINUTES).units)
        assertNull(pickup.pickupEtaMin)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, pickup.grade())
    }

    @Test fun `a capital H route icon is one allowed prefix like t`() {
        // The $78.08 Priority's frame reading its trip row `H 22 min (3.7 knm)` (true 22 / 3.7).
        val p4 = UberS25RealCorpus.lateGroups.flatMap { it.frames }.single { it.capturedAtMs == 1794010282702 }.masked
        val card = parse(p4)
        assertEquals(22.0 to 3.7, card.tripDurationMin to card.tripDistanceKm)
        assertEquals(FieldProvenance.READ, card.slot(TRIP_MINUTES).provenance)
        assertEquals(ValidatedField.REPAIR_UNIT, card.slot(TRIP_KM).repairs)
        assertEquals(ConsistencyGrade.REPAIRED, card.grade())
        // The same protections as `t `: one prefix only, never glued. The duration stays unread; only the
        // unchanged rule fills it, as TOTAL 29 − pickup 7 (RECOVERED, like the glued `h6` frames).
        for (row in listOf("H h 22 min (3.7 knm)", "H22 min (3.7 knm)")) {
            val slot = parse(p4.retext("H 22 min (3.7 knm)", row)).slot(TRIP_MINUTES)
            assertEquals(row, FieldProvenance.RECOVERED, slot.provenance)
        }
    }

    @Test fun `a (128 k) trip row is no route row, so trip km stays unread even with the pickup readable`() {
        // H3's complete frame (pickup `20 min (1.8 km)` intact, no banner) with its trip row retexted.
        val trip = h3.single { it.text == "h lh5 min (12.8 km)" }.bounds
        val pickup = h3.single { it.text == "20 min (1.8 km)" }.bounds
        val card = parse(h3.retext("h lh5 min (12.8 km)", "lh5min (128 k)"))
        assertNull(card.tripDistanceKm)
        assertNull(card.tripDurationMin)
        assertNull(card.slot(TRIP_KM).units)
        assertNull("The row is not recognized: no trip row bounds", card.slot(TRIP_KM).bounds)
        assertEquals(false, card.instanceEvidence?.routeWellFormed)
        assertEquals(ConsistencyGrade.UNVERIFIABLE, card.grade())
        // Only the readable pickup row is a route row: the protected content ends with it, above the trip row.
        assertEquals(pickup.bottom, card.contentBounds?.bottom)
        assertTrue(requireNotNull(card.contentBounds).bottom < trip.top)
        assertEquals(85L, card.slot(TOTAL_MINUTES).units)
    }

    @Test fun `the old G6 cid-prefixed TOTAL keeps its 12 read minutes`() {
        val g6 = UberS25RealCorpus.groups[6].frames[0].masked
        require(g6.any { it.text.startsWith("cid 12 min") })
        val card = parse(g6)
        assertEquals(12L, card.slot(TOTAL_MINUTES).units)
        assertEquals(FieldProvenance.READ, card.slot(TOTAL_MINUTES).provenance)
        assertEquals(FieldProvenance.READ, card.slot(PICKUP_MINUTES).provenance)
    }

    // Legacy labeled route (June 2026 card: `Viaje: 1 h 18 min (34.3 km)`, no TOTAL row).

    private val june get() = UberJuneHourFrames.blocks("ocr_1781567037619.json")
    private val juneTrip = "Viaje: 1 h 18 min (34.3 km)"
    private fun legacyTrip(row: String) = parse(june.retext(juneTrip, row)).let { it.tripDurationMin to it.tripDistanceKm }

    @Test fun `legacy hour rows decode 60H + M and genuine minute-only tokens keep their rule`() {
        assertEquals(78.0 to 34.3, legacyTrip(juneTrip))
        assertEquals(286.0 to 34.3, legacyTrip("Viaje: 4h46 min (34.3 km)"))
        assertEquals(61.0 to 34.3, legacyTrip("Viaje: 1 h l min (34.3 km)"))
        assertEquals(66.0 to 34.3, legacyTrip("Viaje:l h 6 min (34.3 km)"))
        assertEquals(21.0 to 34.3, legacyTrip("Viaje: 21l min (34.3 km)"))
        assertEquals(41.0 to 34.3, legacyTrip("Viaje: 41l min (34.3 km)"))
        assertEquals(21.0 to 34.3, legacyTrip("Viaje: 2l min (34.3 km)"))
        assertNull(parse(june).validation)
    }

    @Test fun `a legacy hour span that fails never truncates or retries on its minute suffix`() {
        assertEquals(null to 34.3, legacyTrip("Viaje: 1 h 61 min (34.3 km)"))
        assertEquals(null to 34.3, legacyTrip("Viaje: xh 18 min (34.3 km)"))
        // Legacy gains no icon stripping: an unresolved leading h is not bare minutes.
        assertEquals(null to 34.3, legacyTrip("Viaje: h 18 min (34.3 km)"))
        // Hour-only: no minute unit, no minutes.
        assertEquals(null to 34.3, legacyTrip("Viaje: 1 h (34.3 km)"))
    }

    @Test fun `an unrelated h earlier in a legacy leg never hides its minutes and the span before min still decodes`() {
        // The June card's unlabeled pickup line `A 12 min (2.0 km)`, with a per-hour amount earlier in the block.
        fun pickup(row: String) = parse(june.retext("A 12 min (2.0 km)", row)).let { it.pickupEtaMin to it.pickupDistanceKm }
        assertEquals(12.0 to 2.0, pickup("A 12 min (2.0 km)"))
        assertEquals(12.0 to 2.0, pickup("\$243/h 12 min (2.0 km)"))
        assertEquals(12.0 to 2.0, pickup("\$243/H 12 min (2.0 km)"))
        // The duration span right before the unit still decides: a complete hour decodes, a failed one stays unread.
        assertEquals(72.0 to 2.0, pickup("\$243/h 1 h 12 min (2.0 km)"))
        assertEquals(null to 2.0, pickup("\$243/h h 12 min (2.0 km)"))
        assertEquals(null to 2.0, pickup("\$243/h 1 h 61 min (2.0 km)"))
        assertEquals(null to 2.0, pickup("h 12 min (2.0 km)"))
        // A Viaje label anchors its span: an h before the label is outside it, one after it is inside.
        assertEquals(78.0 to 34.3, legacyTrip("\$243/h Viaje: 1 h 18 min (34.3 km)"))
        assertEquals(18.0 to 34.3, legacyTrip("\$243/h Viaje: 18 min (34.3 km)"))
        assertEquals(null to 34.3, legacyTrip("Viaje: xh 18 min (34.3 km)"))
    }

    @Test fun `the joined-text fallback parses from the Viaje label's duration start`() {
        // The label and the leg split into two OCR blocks on one row. One copy of the frame: `june` rebuilds
        // its blocks on every read, so the identity filter must run on the same list.
        val frame = june
        val row = frame.single { it.text == juneTrip }
        fun split(leg: String) = frame.filterNot { it === row } + listOf(
            OcrBlock("Viaje:", OcrBounds(row.bounds.left, row.bounds.top, row.bounds.left + 120, row.bounds.bottom)),
            OcrBlock(leg, OcrBounds(row.bounds.left + 130, row.bounds.top, row.bounds.right, row.bounds.bottom)))
        val whole = split("1 h 18 min (34.3 km)")
        assertTrue("the labeled row is gone", whole.none { it.text.contains("Viaje: ") })
        assertEquals(78.0 to 34.3, parse(whole).let { it.tripDurationMin to it.tripDistanceKm })
        // A leg block that is no unlabeled leg (its closing parenthesis lost): only the label-anchored joined
        // text (tripLegRegex) can read the trip, and 0.2.15's minutes-only regex found none.
        val open = split("1 h 18 min (34.3 km")
        assertEquals(78.0 to 34.3, parse(open).let { it.tripDurationMin to it.tripDistanceKm })
    }
}
