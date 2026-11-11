package mx.kompara.ocr

import mx.kompara.ocr.UberCardChecksum.Candidate
import mx.kompara.parsers.model.ConsistencyGrade
import mx.kompara.parsers.model.ValidatedField.Companion.REPAIR_CONFUSABLE
import mx.kompara.parsers.model.ValidatedField.Companion.REPAIR_DECIMAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * WP-AC (2026-11-10, S25): Uber prints a whole amount without decimals (`$109` is 109.00, `$11/km` is
 * 11.00 per km), and OCR also loses a separator (`$17756` is 177.56, `$740/km` is 7.40). A separatorless
 * amount is whole units and, with three or more digits, also a lost-decimal amount; only the card's own
 * money identity, solved jointly with the distances, chooses between the two. Round 2 (Codex): every
 * prefix of a whole number's digits is another whole number (`$109` read `$10`), so no reading of a token
 * without decimals stands as printed, even a slot's only one: only that identity establishes it.
 */
class UberCardChecksumMoneyTest {
    private val c = UberCardChecksum

    /** A money reading of a token without decimals (round 2): no repair, never "as printed". */
    private fun nd(units: Long, repairs: Int = 0) = Candidate(units, repairs, noDecimals = true)

    @Test fun `a separatorless amount is whole units, and with three or more digits also a lost-decimal amount`() {
        assertEquals("whole first, then lost decimal", listOf(nd(10900), nd(109, REPAIR_DECIMAL)), c.money("109"))
        assertEquals(listOf(nd(1775600), nd(17756, REPAIR_DECIMAL)), c.money("17756"))
        assertEquals("two digits: whole only", listOf(nd(4500)), c.money("45"))
        assertEquals("one digit: whole only", listOf(nd(900)), c.money("9"))
        assertEquals("zero stays zero", listOf(nd(0)), c.money("0"))
        assertEquals("a confusable is a repair in both readings",
            listOf(nd(11000, REPAIR_CONFUSABLE), nd(110, REPAIR_CONFUSABLE or REPAIR_DECIMAL)), c.money("1l0"))
        assertEquals(listOf(nd(1100, REPAIR_CONFUSABLE)), c.money("ll"))
    }

    @Test fun `unambiguous thousands separators without decimals are whole units`() {
        assertEquals(listOf(nd(132000)), c.money("1,320"))
        assertEquals(listOf(nd(123456700)), c.money("1,234,567"))
    }

    @Test fun `only a token without decimals carries the no-decimals mark`() {
        for (token in listOf("12.67", "9,82", "1,320.90", "l2.67")) assertTrue(token, c.money(token).none { it.noDecimals })
        for (token in listOf("0", "10", "109", "17756", "1,320", "999999999")) assertTrue(token, c.money(token).all { it.noDecimals })
    }

    @Test fun `two-decimal syntax keeps its one reading and malformed separators stay unread`() {
        assertEquals(listOf(Candidate(132090)), c.money("1,320.90"))
        assertEquals(listOf(Candidate(1267)), c.money("12.67"))
        assertEquals(listOf(Candidate(982)), c.money("9,82"))
        for (token in listOf("12.6", "1.320", "1,32,0", "1,3200", ",320", "1,320.9", "12..67", "", "1?.67", "\$109"))
            assertEquals(token, emptyList<Candidate>(), c.money(token))
        assertEquals(emptyList<Candidate>(), c.money(null))
    }

    @Test fun `a nine-digit amount reads without overflow and a longer one stays unread`() {
        assertEquals(listOf(nd(99_999_999_900), nd(999_999_999, REPAIR_DECIMAL)), c.money("999999999"))
        assertEquals(emptyList<Candidate>(), c.money("1234567890"))
        assertEquals(emptyList<Candidate>(), c.money("9,223,372,036,854,775,807"))
    }

    // The solver over generated cards.

    private class Card(val fare: String?, val rate: String?, val totalKm: String?, val pickupKm: String?, val tripKm: String?) {
        val slots = listOf(
            UberCardChecksum.Slot(UberCardChecksum.money(fare)), UberCardChecksum.Slot(UberCardChecksum.money(rate)),
            UberCardChecksum.Slot(listOf(Candidate(30))), UberCardChecksum.Slot(UberCardChecksum.distance(totalKm)),
            UberCardChecksum.Slot(listOf(Candidate(10))), UberCardChecksum.Slot(UberCardChecksum.distance(pickupKm)),
            UberCardChecksum.Slot(listOf(Candidate(20))), UberCardChecksum.Slot(UberCardChecksum.distance(tripKm)))
        override fun toString() = "fare=$fare rate=$rate total=$totalKm pickup=$pickupKm trip=$tripKm"
    }

    private fun holds(fare: Long, rate: Long, tenths: Long) = tenths > 0 && 2 * abs(rate * tenths - 10 * fare) <= tenths

    /**
     * The oracle, written independently of [UberCardChecksum.solve]: the readings of slot [money] (0 fare,
     * 1 rate) that some assignment of every other slot's readings admits with the money identity
     * evaluated and holding — the distance identity, where evaluated, not failing.
     */
    private fun moneyAdmitted(card: Card, money: Int): Set<Candidate> {
        fun options(i: Int) = card.slots[i].candidates.ifEmpty { listOf(null) }
        val found = mutableSetOf<Candidate>()
        for (f in options(0)) for (r in options(1)) for (tk in options(3)) for (pk in options(5)) for (qk in options(7)) {
            if (f == null || r == null || tk == null || !holds(f.units, r.units, tk.units)) continue
            if (pk != null && qk != null && tk.units != pk.units + qk.units) continue
            found += if (money == 0) f else r
        }
        return found
    }

    private fun amount(cents: Long) = String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)
    private fun tenths(t: Long) = String.format(Locale.US, "%d.%d", t / 10, t % 10)

    /** Every printed and misread form of an amount the card might show, true or not. */
    private fun amountForms(cents: Long, random: Random): List<String?> = listOfNotNull(
        amount(cents),                                   // as printed with decimals
        amount(cents).replace(".", ""),                  // separator lost
        (cents / 100).toString(),                        // whole as printed (wrong unless cents % 100 == 0)
        (cents / 100).takeIf { cents >= 100_000 }?.let { String.format(Locale.US, "%,d", it) }, // whole with thousands
        amount(cents).replace(".", "").dropLast(1),      // a digit lost too
        amount(cents).dropLast(1),                       // the last decimal lost (malformed, `12.6`)
        (cents / 100).toString().dropLast(1).ifEmpty { null }, // a whole amount's last digit lost (`$109` read `$10`)
        amount(cents).replace(".", "") + random.nextInt(10), // a glyph added
        amount(cents).replace('1', 'l'),                 // confusable
        amount(cents).replace(".", ","),                 // decimal comma
        null,                                            // unread
    )

    /** A money token without decimals, after the confusable map: separatorless, or whole with thousands separators. */
    private val withoutDecimals = Regex("""[0-9lLI|Oo]{1,9}|[0-9lLI|Oo]{1,3}(?:,[0-9lLI|Oo]{3})+""")
    private fun kind(token: String) = when {
        ',' in token -> "thousands"
        token.length <= 2 -> "short"
        else -> "long"
    }

    @Test fun `a fare from a token without decimals publishes only through its holding identity, and two readings never both win`() {
        val random = Random(20261110)
        val cards = mutableListOf<Card>()
        // Plausible cards: the rate is the fare over TOTAL km rounded to the cent, as Uber prints it; one in
        // three fares and one in five rates are whole pesos, the defect's own shape (`$109`, `$11/km`), and one
        // in four fares is a whole amount under 100 pesos (`$45`, `$9`).
        repeat(320) { n ->
            val t = random.nextLong(5, 3000)
            var fare = when {
                n % 4 == 1 -> random.nextLong(1, 100) * 100
                else -> random.nextLong(1500, 400_000).let { if (n % 3 == 0) it / 100 * 100 else it }
            }
            var rate = Math.round(10.0 * fare / t)
            if (n % 5 == 0) {
                rate = maxOf(100, rate / 100 * 100)
                fare = Math.round(rate * t / 10.0)
            }
            val p = random.nextLong(0, t + 1)
            val legs = listOf(tenths(p) to tenths(t - p), tenths(p) to null, tenths(p).replace(".", "") to tenths(t - p))
            val totals = listOf(tenths(t), tenths(t).replace(".", ""), null)
            for (f in amountForms(fare, random)) for (r in amountForms(rate, random)) for (tk in totals) for ((pk, qk) in legs)
                cards += Card(f, r, tk, pk, qk)
        }
        // Adversarial cards: unrelated digit strings in every slot, separators or not.
        fun digits(min: Int, max: Int) = (1..random.nextInt(min, max + 1)).joinToString("") { random.nextInt(10).toString() }
        repeat(40_000) {
            fun money() = when (random.nextInt(4)) { 0 -> digits(1, 6); 1 -> digits(1, 4) + "." + digits(2, 2); 2 -> digits(1, 3); else -> null }
            fun km() = when (random.nextInt(3)) { 0 -> digits(1, 4); 1 -> digits(1, 3) + "." + digits(1, 1); else -> null }
            cards += Card(money(), money(), km(), km(), km())
        }

        // Fares from a token without decimals, by kind (short: one or two digits; long: three or more;
        // thousands: `1,320`) and by whether the rate slot was read.
        val published = sortedMapOf<String, Int>()
        val withheld = sortedMapOf<String, Int>()
        var bothAdmittedContradictory = 0
        val failures = mutableListOf<String>()
        for (card in cards) {
            val result = c.solve(card.slots, rowsIdentified = true, selectionCertain = true, fareCandidates = 1)
            val v = result.evidence
            val contradictory = v.grade == ConsistencyGrade.CONTRADICTORY
            if (result.fare != null && contradictory) failures += "$card: a fare published while CONTRADICTORY"
            for (money in 0..1) {
                if (card.slots[money].candidates.size < 2) continue
                val admitted = moneyAdmitted(card, money)
                if (admitted.size > 1) {
                    bothAdmittedContradictory++
                    if (!contradictory) failures += "$card: slot $money has competing admitted readings $admitted but is $v"
                }
            }
            val token = card.fare
            if (token == null || !withoutDecimals.matches(token) || card.slots[0].candidates.isEmpty()) continue
            val key = kind(token) + if (card.slots[1].candidates.isEmpty()) "/rate-unread" else "/rate-read"
            val fare = result.fare?.let { (it * 100).roundToLong() }
            if (fare == null) { withheld.merge(key, 1, Int::plus); continue }
            published.merge(key, 1, Int::plus)
            val reading = card.slots[0].candidates.singleOrNull { it.units == fare }
            val rate = v.rate.units
            val tk = v.totalKm.units
            if (reading == null) failures += "$card: published $fare, which is none of its readings"
            if (!(v.money.evaluated && v.money.holds) || rate == null || tk == null || !holds(fare, rate, tk))
                failures += "$card: a fare without decimals published without its money identity holding: $v"
            else if (moneyAdmitted(card, 0) != setOfNotNull(reading))
                failures += "$card: published $reading but the identity admits ${moneyAdmitted(card, 0)}"
        }
        val counts = "published=$published withheld=$withheld competing=$bothAdmittedContradictory"
        assertTrue(failures.size.toString() + " failures ($counts)\n" + failures.take(20).joinToString("\n"), failures.isEmpty())
        // With the rate unread no fare without decimals is ever published, of any kind.
        for (kind in listOf("short", "long", "thousands")) assertEquals(counts, null, published["$kind/rate-unread"])
        // The generator exercises every branch it claims to: each kind published, withheld with the rate read
        // and withheld with it unread, and competing readings.
        for (kind in listOf("short", "long", "thousands")) for ((map, branch) in listOf(published to "rate-read",
            withheld to "rate-read", withheld to "rate-unread")) {
            assertTrue("$kind/$branch: $counts", (map["$kind/$branch"] ?: 0) >= 100)
        }
        assertTrue(counts, bothAdmittedContradictory >= 1)
    }

    @Test fun `a whole-peso fare and a lost-decimal fare that both fit a lost-decimal rate are competing values`() {
        // $12400 over 10.0 km at $1240/km: 124.00 at 12.40/km and 12,400.00 at 1,240.00/km both fit exactly.
        val card = Card("12400", "1240", "10.0", "4.0", "6.0")
        assertEquals(2, moneyAdmitted(card, 0).size)
        val result = c.solve(card.slots, rowsIdentified = true, selectionCertain = true, fareCandidates = 1)
        assertEquals(ConsistencyGrade.CONTRADICTORY, result.evidence.grade)
        assertEquals(null, result.fare)
    }
}
