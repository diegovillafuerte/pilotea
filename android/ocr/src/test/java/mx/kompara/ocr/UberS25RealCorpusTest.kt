package mx.kompara.ocr

import mx.kompara.parsers.model.ConsistencyGrade
import mx.kompara.parsers.model.OfferContentBounds
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToLong

/**
 * Per-group parser replays over all 190 real S25 frames (13 groups, 17 card appearances) against the
 * literal oracle (spec section B). Input is production-masked by each frame's recorded chip. Failures
 * report group/index/field/number only, never block text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberS25RealCorpusTest {
    private val parser = UberOcrParser()
    /** 0.2.14's extractor: the legacy labeled-route path alone. */
    private val legacy = UberOcrParser(modernTotals = false)
    private val codes = listOf("F", "R", "TM", "TK", "PM", "PK", "QM", "QK")

    private fun parse(p: UberOcrParser, blocks: List<OcrBlock>) = p.parse(blocks, hostConfirmed = true)

    @Test fun `every real frame parses to the oracle with its provenance, grade and same-card bounds`() {
        val failures = mutableListOf<String>()
        val grades = mutableMapOf<ConsistencyGrade, Int>()
        for (g in UberS25RealCorpus.groups) for (f in g.frames) {
            fun fail(field: String, detail: Any?) { failures += "${f.id} $field: $detail" }
            val signature = parser.hasCardSignature(f.masked, hostConfirmed = true)
            val card = parse(parser, f.masked)
            if (f.index in g.signatureFree) {
                if (signature) fail("signature", "true on a signature-free frame")
                if (card?.fare != null) fail("fare", "payable on a signature-free frame")
                continue
            }
            if (!signature) fail("signature", false)
            val a = g.appearance(f.index) ?: run { fail("oracle", "no appearance"); null } ?: continue
            if (card == null) { fail("card", null); continue }
            if (card.fare != a.fare) fail("fare", card.fare)
            if (card.pickupEtaMin != a.pickup.first.toDouble()) fail("PM", card.pickupEtaMin)
            if (card.pickupDistanceKm != a.pickup.second) fail("PK", card.pickupDistanceKm)
            if (card.tripDurationMin != a.trip.first.toDouble()) fail("QM", card.tripDurationMin)
            if (card.tripDistanceKm != a.trip.second) fail("QK", card.tripDistanceKm)
            if (card.paymentBasis != "DRIVER_PAYMENT") fail("paymentBasis", card.paymentBasis)
            if (card.instanceEvidence?.routeWellFormed != true) fail("routeWellFormed", card.instanceEvidence?.routeWellFormed)
            val v = card.validation ?: run { fail("validation", null); null } ?: continue
            val expected = listOf(cents(a.fare), cents(a.rate), a.total.first.toLong(), tenths(a.total.second),
                a.pickup.first.toLong(), tenths(a.pickup.second), a.trip.first.toLong(), tenths(a.trip.second))
            val exceptions = g.provenance[f.index].orEmpty()
            v.slots.forEachIndexed { i, slot ->
                if (slot.units != expected[i]) fail(codes[i], slot.units)
                val want = exceptions[codes[i]] ?: "READ"
                if (slot.provenance?.name != want) fail("${codes[i]} provenance", slot.provenance)
            }
            val grade = if (exceptions.isEmpty()) ConsistencyGrade.CONSISTENT else ConsistencyGrade.REPAIRED
            if (v.grade != grade) fail("grade", v.grade)
            grades.merge(v.grade, 1, Int::plus)
            // Every selected numeric bound belongs to the same card: heading, headline, both legs, action.
            val heading = card.instanceEvidence?.headingBounds
            val fare = card.fareBounds
            val pickup = v.pickupKm.bounds
            val trip = v.tripKm.bounds
            val action = card.instanceEvidence?.actionBounds
            if (heading == null || fare == null || pickup == null || trip == null) {
                fail("bounds", "heading=${heading != null} fare=${fare != null} pickup=${pickup != null} trip=${trip != null}")
            } else {
                if (fare.top < heading.bottom) fail("fareBounds", "above the heading")
                if (pickup.top < fare.bottom || trip.top < pickup.bottom) fail("rows", "out of card order")
                if (action != null && trip.bottom > action.top) fail("rows", "below the action")
                if (card.contentBounds != union(fare, pickup, trip)) fail("contentBounds", card.contentBounds)
            }
        }
        val summary = JSONObject(UberS25RealCorpus.text("oracle.json")).getJSONObject("summary")
        assertTrue(failures.size.toString() + " failures\n" + failures.joinToString("\n"), failures.isEmpty())
        assertEquals(summary.getInt("consistent"), grades[ConsistencyGrade.CONSISTENT] ?: 0)
        assertEquals(summary.getInt("repaired"), grades[ConsistencyGrade.REPAIRED] ?: 0)
        assertEquals(summary.getInt("contradictory"), grades[ConsistencyGrade.CONTRADICTORY] ?: 0)
        assertEquals(summary.getInt("unverifiable"), grades[ConsistencyGrade.UNVERIFIABLE] ?: 0)
    }

    @Test fun `every real frame parses identically in original, reversed and ten shuffled block orders`() {
        val failures = mutableListOf<String>()
        for (g in UberS25RealCorpus.groups) for (f in g.frames) {
            val base = fingerprint(f.masked)
            for ((name, order) in UberS25RealCorpus.orders(f.masked)) {
                if (fingerprint(order) != base) failures += "${f.id} $name"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `the 0_2_14 read and miss sets stay recorded as separate baseline data`() {
        val failures = mutableListOf<String>()
        for (g in UberS25RealCorpus.groups) for (f in g.frames) {
            if (f.index in g.signatureFree) continue
            val a = requireNotNull(g.appearance(f.index))
            val old = requireNotNull(f.legacy) { f.id }
            if ((old.fare == a.fare) != (f.index in g.legacyFareRead) || old.fare != null && old.fare != a.fare)
                failures += "${f.id} 0.2.14 fare ${old.fare}"
            val trip = listOf(a.trip.first.toDouble(), a.trip.second)
            if ((old.trip == trip) != (f.index in g.legacyTripRead) || old.trip[0] != null && old.trip != trip)
                failures += "${f.id} 0.2.14 trip ${old.trip}"
            if (old.pickup != listOf(a.pickup.first.toDouble(), a.pickup.second)) failures += "${f.id} 0.2.14 pickup ${old.pickup}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `the legacy labeled-route path alone still reproduces every recorded 0_2_14 reading`() {
        val failures = mutableListOf<String>()
        for (g in UberS25RealCorpus.groups) for (f in g.frames) {
            val card = parse(legacy, f.masked)
            val old = f.legacy
            if (old == null) {
                if (card != null) failures += "${f.id} legacy parsed a signature-free frame"
                continue
            }
            if (card == null) { failures += "${f.id} legacy card null"; continue }
            if (card.fare != old.fare) failures += "${f.id} fare ${card.fare}"
            if (listOf(card.pickupEtaMin, card.pickupDistanceKm) != old.pickup) failures += "${f.id} pickup"
            if (listOf(card.tripDurationMin, card.tripDistanceKm) != old.trip) failures += "${f.id} trip"
            if (card.instanceEvidence?.action != old.action) failures += "${f.id} action"
            if (card.validation != null) failures += "${f.id} legacy carries validation"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun fingerprint(blocks: List<OcrBlock>): String {
        val card = parse(parser, blocks)
        return listOf(parser.hasCardSignature(blocks, hostConfirmed = true), card?.fare, card?.pickupEtaMin,
            card?.pickupDistanceKm, card?.tripDurationMin, card?.tripDistanceKm, card?.paymentBasis, card?.variant,
            card?.fareBounds, card?.contentBounds, card?.instanceEvidence?.routeWellFormed,
            card?.instanceEvidence?.action, card?.validation).toString()
    }

    private fun cents(v: Double) = (v * 100).roundToLong()
    private fun tenths(v: Double) = (v * 10).roundToLong()
    private fun union(vararg r: OfferContentBounds) =
        OfferContentBounds(r.minOf { it.left }, r.minOf { it.top }, r.maxOf { it.right }, r.maxOf { it.bottom })
}
