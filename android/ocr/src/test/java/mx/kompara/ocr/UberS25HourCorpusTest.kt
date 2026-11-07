package mx.kompara.ocr

import mx.kompara.parsers.model.ConsistencyGrade
import mx.kompara.parsers.model.OfferContentBounds
import mx.kompara.parsers.model.ValidatedField
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToLong

/**
 * The 0.2.15 device run of 2026-11-06 17:42–17:46 (126 frames, 8 cards, 3 of them an hour or longer)
 * against its literal oracle (truth read from the screenshots; hour-card grades and the obscured H3
 * frames' unread values from the 0.2.16 spec). Input is production-masked by each frame's recorded
 * chip. Failures report group/index/field/number only, never block text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberS25HourCorpusTest {
    private val parser = UberOcrParser()
    private val codes = listOf("F", "R", "TM", "TK", "PM", "PK", "QM", "QK")
    private val corpus get() = UberS25RealCorpus.pmGroups

    private fun parse(blocks: List<OcrBlock>) = parser.parse(blocks, hostConfirmed = true)

    @Test fun `every frame of the hour run parses to the oracle with its provenance, grade and same-card bounds`() =
        assertOracle(corpus, UberS25RealCorpus.PM_DIR)

    /**
     * The late Uber cards of the same run (18:09–18:12, 60 frames): H0's hour-only TOTAL `1 h` (read
     * `lh78 km`, `lh•78 km-`), H4/H5/H6 (`lh 29`, `lh19`, `h lh14`, `h lh 10`, `lh10`) and the $78.08
     * Priority (`h 22`, capital `H 22 … knm`), each true from its first complete frame.
     */
    @Test fun `every frame of the late hour cards parses to the oracle with its provenance, grade and same-card bounds`() =
        assertOracle(UberS25RealCorpus.lateGroups, UberS25RealCorpus.LATE_DIR)

    private fun assertOracle(corpus: List<UberS25RealCorpus.Group>, dir: String) {
        val failures = mutableListOf<String>()
        val grades = mutableMapOf<ConsistencyGrade, Int>()
        for (g in corpus) for (f in g.frames) {
            fun fail(field: String, detail: Any?) { failures += "${f.id} $field: $detail" }
            val signature = parser.hasCardSignature(f.masked, hostConfirmed = true)
            val card = parse(f.masked)
            if (f.index in g.signatureFree) {
                if (signature) fail("signature", "true on a signature-free frame")
                if (card != null) fail("card", "parsed on a signature-free frame")
                continue
            }
            if (!signature) fail("signature", false)
            val a = g.appearance(f.index) ?: run { fail("oracle", "no appearance"); null } ?: continue
            if (card == null) { fail("card", null); continue }
            g.incomplete[f.index]?.takeIf { it.grade == null }?.let { inc ->
                // Legacy path (no TOTAL row): no validation, only the literal legible values.
                if (card.validation != null) fail("validation", card.validation?.grade)
                if (card.paymentBasis != inc.paymentBasis) fail("paymentBasis", card.paymentBasis)
                if (card.fare != inc.fare) fail("fare", card.fare)
                if (listOf(card.pickupEtaMin, card.pickupDistanceKm) != inc.pickup) fail("pickup", listOf(card.pickupEtaMin, card.pickupDistanceKm))
                if (listOf(card.tripDurationMin, card.tripDistanceKm) != inc.trip) fail("trip", listOf(card.tripDurationMin, card.tripDistanceKm))
                if (card.instanceEvidence?.routeWellFormed != false) fail("routeWellFormed", card.instanceEvidence?.routeWellFormed)
                continue
            }
            val v = card.validation ?: run { fail("validation", null); null } ?: continue
            if (card.paymentBasis != "DRIVER_PAYMENT") fail("paymentBasis", card.paymentBasis)
            grades.merge(v.grade, 1, Int::plus)
            g.incomplete[f.index]?.let { inc ->
                // Obscured rows: the literal unread values, never borrowed from another frame.
                if (card.fare != inc.fare) fail("fare", card.fare)
                if (listOf(card.pickupEtaMin, card.pickupDistanceKm) != inc.pickup) fail("pickup", listOf(card.pickupEtaMin, card.pickupDistanceKm))
                if (listOf(card.tripDurationMin, card.tripDistanceKm) != inc.trip) fail("trip", listOf(card.tripDurationMin, card.tripDistanceKm))
                if (v.grade.name != inc.grade) fail("grade", v.grade)
                if (card.instanceEvidence?.routeWellFormed != false) fail("routeWellFormed", card.instanceEvidence?.routeWellFormed)
                inc.units.forEach { (code, units) -> if (v.slots[codes.indexOf(code)].units != units) fail(code, v.slots[codes.indexOf(code)].units) }
                inc.provenance.forEach { (code, p) -> if (v.slots[codes.indexOf(code)].provenance?.name != p) fail("$code provenance", v.slots[codes.indexOf(code)].provenance) }
                return@let
            } ?: run {
                if (card.fare != a.fare) fail("fare", card.fare)
                if (card.pickupEtaMin != a.pickup.first.toDouble()) fail("PM", card.pickupEtaMin)
                if (card.pickupDistanceKm != a.pickup.second) fail("PK", card.pickupDistanceKm)
                if (card.tripDurationMin != a.trip.first.toDouble()) fail("QM", card.tripDurationMin)
                if (card.tripDistanceKm != a.trip.second) fail("QK", card.tripDistanceKm)
                if (card.instanceEvidence?.routeWellFormed != true) fail("routeWellFormed", card.instanceEvidence?.routeWellFormed)
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
                if (!v.minutes.holds || !v.distance.holds || !v.money.holds) {
                    // A recovered trip minute is no independent minute identity (spec: h6 frames).
                    if (exceptions["QM"] != "RECOVERED" || !v.distance.holds || !v.money.holds)
                        fail("identities", "minutes=${v.minutes.holds} distance=${v.distance.holds} money=${v.money.holds}")
                }
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
        }
        val summary = JSONObject(UberS25RealCorpus.text("oracle.json", dir)).getJSONObject("summary")
        assertTrue(failures.size.toString() + " failures\n" + failures.joinToString("\n"), failures.isEmpty())
        assertEquals(summary.getInt("consistent"), grades[ConsistencyGrade.CONSISTENT] ?: 0)
        assertEquals(summary.getInt("repaired"), grades[ConsistencyGrade.REPAIRED] ?: 0)
        assertEquals(summary.getInt("contradictory"), grades[ConsistencyGrade.CONTRADICTORY] ?: 0)
        assertEquals(summary.getInt("unverifiable"), grades[ConsistencyGrade.UNVERIFIABLE] ?: 0)
    }

    /**
     * The spec's literal acceptance tables, by capturedAtMs: (fare, pickup min, pickup km, trip min,
     * trip km) and grade. Ranges cover the fixture files inside the inclusive timestamp bounds.
     */
    @Test fun `the spec's hour-card and regression tuples hold frame by frame`() {
        data class Row(val from: Long, val to: Long, val count: Int, val tuple: List<Double?>, val grade: ConsistencyGrade?)
        val h1 = listOf(257.91, 5.0, 0.8, 78.0, 21.4)
        val h2 = listOf(1320.90, 18.0, 4.4, 286.0, 217.3)
        val rows = listOf(
            Row(1794008587494, 1794008598677, 13, h1, ConsistencyGrade.REPAIRED),
            Row(1794008634179, 1794008646636, 15, h1, ConsistencyGrade.REPAIRED),
            Row(1794008677258, 1794008677258, 1, h2, ConsistencyGrade.CONSISTENT),
            Row(1794008678189, 1794008688037, 12, h2, ConsistencyGrade.REPAIRED),
            Row(1794008688938, 1794008688938, 1, h2, ConsistencyGrade.CONSISTENT),
            Row(1794008743732, 1794008743732, 1, listOf(257.22, 20.0, 1.8, 65.0, 12.8), ConsistencyGrade.REPAIRED),
            Row(1794008744663, 1794008744663, 1, listOf(257.22, null, null, null, null), ConsistencyGrade.UNVERIFIABLE),
            Row(1794008745581, 1794008745581, 1, emptyList(), null),
            Row(1794008608641, 1794008608641, 1, listOf(119.89, 9.0, 1.4, 44.0, 6.4), null),
            Row(1794008650977, 1794008650977, 1, listOf(148.16, 5.0, 0.8, 36.0, 11.3), null),
            Row(1794008655560, 1794008655560, 1, listOf(35.98, 6.0, 0.9, 6.0, 0.8), null),
            Row(1794008666492, 1794008667392, 2, listOf(35.98, 6.0, 0.9, 6.0, 0.8), null),
            Row(1794008696104, 1794008696104, 1, listOf(124.58, 3.0, 0.5, 49.0, 5.2), null),
            Row(1794008746496, 1794008746496, 1, listOf(142.01, 5.0, 0.7, 52.0, 10.2), null),
            Row(1794008754548, 1794008754548, 1, listOf(142.01, 5.0, 0.7, 52.0, 10.2), null),
        )
        val frames = corpus.flatMap { it.frames }
        val failures = mutableListOf<String>()
        for (row in rows) {
            val inRange = frames.filter { requireNotNull(it.capturedAtMs) in row.from..row.to }
            if (inRange.size != row.count) failures += "${row.from}..${row.to}: ${inRange.size} frames, spec ${row.count}"
            for (f in inRange) {
                val card = parse(f.masked)
                if (row.tuple.isEmpty()) { if (card != null) failures += "${f.capturedAtMs}: parsed a card"; continue }
                val got = listOf(card?.fare, card?.pickupEtaMin, card?.pickupDistanceKm, card?.tripDurationMin, card?.tripDistanceKm)
                if (got != row.tuple) failures += "${f.capturedAtMs}: $got"
                if (row.grade != null && card?.validation?.grade != row.grade) failures += "${f.capturedAtMs}: ${card?.validation?.grade}"
            }
        }
        // X1's glued route icon: trip minutes recovered as 12 - 6 (spec), never decoded; X2's knm repairs trip km.
        for (t in listOf(1794008666492, 1794008667392)) {
            val v = parse(frames.single { it.capturedAtMs == t }.masked)?.validation
            if (v?.tripMinutes?.provenance?.name != "RECOVERED") failures += "$t trip minutes ${v?.tripMinutes?.provenance}"
        }
        val knm = parse(frames.single { it.capturedAtMs == 1794008754548 }.masked)?.validation
        if (knm?.tripKm?.provenance?.name != "REPAIRED") failures += "1794008754548 trip km ${knm?.tripKm?.provenance}"
        // H3's complete frame: an independently evaluated minute identity 85 = 20 + 65, no recovery operand.
        val h3 = parse(frames.single { it.capturedAtMs == 1794008743732 }.masked)?.validation
        if (h3?.minutes?.evaluated != true || h3.minutes.holds != true || h3.totalMinutes.units != 85L ||
            h3.tripMinutes.operands != 0) failures += "1794008743732 minutes ${h3?.minutes} total ${h3?.totalMinutes?.units} operands ${h3?.tripMinutes?.operands}"
        // H3's obscured second frame keeps its recognized TOTAL with evidence units 85.
        val h3b = parse(frames.single { it.capturedAtMs == 1794008744663 }.masked)?.validation
        if (h3b?.totalMinutes?.units != 85L) failures += "1794008744663 total ${h3b?.totalMinutes?.units}"
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `every frame of the hour run parses identically in original, reversed and ten shuffled block orders`() =
        assertOrderFree(corpus)

    @Test fun `every frame of the late hour cards parses identically in original, reversed and ten shuffled block orders`() =
        assertOrderFree(UberS25RealCorpus.lateGroups)

    /**
     * The late cards' literal acceptance values by capturedAtMs (truth-new-run.md): H0's hour-only
     * TOTAL is 60 minutes (confusable `l`) over 7.8 km (decimal lost), 60 = 18 + 42 and 78 = 17 + 61;
     * the capital `H ` icon frame of the $78.08 Priority reads trip 22 with its `knm` repaired.
     */
    @Test fun `the late cards' hour-only TOTAL and capital icon frames hold their literal values`() {
        val frames = UberS25RealCorpus.lateGroups.flatMap { it.frames }
        val failures = mutableListOf<String>()
        fun card(at: Long) = parse(frames.single { it.capturedAtMs == at }.masked)
        for (at in listOf(1794010170571, 1794010171500)) {
            val c = card(at)
            val v = c?.validation
            val got = listOf(c?.fare, c?.pickupEtaMin, c?.pickupDistanceKm, c?.tripDurationMin, c?.tripDistanceKm)
            if (got != listOf(121.50, 18.0, 1.7, 42.0, 6.1)) failures += "$at: $got"
            if (v?.totalMinutes?.units != 60L || v.totalMinutes.repairs != ValidatedField.REPAIR_CONFUSABLE)
                failures += "$at TOTAL minutes ${v?.totalMinutes}"
            if (v?.totalKm?.units != 78L || v.totalKm.repairs != ValidatedField.REPAIR_DECIMAL) failures += "$at TOTAL km ${v?.totalKm}"
            if (v?.minutes?.holds != true || v.distance.holds != true || v.money.holds != true) failures += "$at identities ${v?.minutes} ${v?.distance} ${v?.money}"
            if (v?.grade != ConsistencyGrade.REPAIRED) failures += "$at grade ${v?.grade}"
        }
        val capital = card(1794010282702)
        val got = listOf(capital?.fare, capital?.pickupEtaMin, capital?.pickupDistanceKm, capital?.tripDurationMin, capital?.tripDistanceKm)
        if (got != listOf(78.08, 7.0, 0.9, 22.0, 3.7)) failures += "1794010282702: $got"
        if (capital?.validation?.tripMinutes?.provenance?.name != "READ") failures += "1794010282702 trip minutes ${capital?.validation?.tripMinutes}"
        if (capital?.validation?.tripKm?.repairs != ValidatedField.REPAIR_UNIT) failures += "1794010282702 trip km ${capital?.validation?.tripKm}"
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun assertOrderFree(corpus: List<UberS25RealCorpus.Group>) {
        val failures = mutableListOf<String>()
        for (g in corpus) for (f in g.frames) {
            val base = fingerprint(f.masked)
            for ((name, order) in UberS25RealCorpus.orders(f.masked)) {
                if (fingerprint(order) != base) failures += "${f.id} $name"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun fingerprint(blocks: List<OcrBlock>): String {
        val card = parse(blocks)
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
