package mx.kompara.ocr

import mx.kompara.parsers.model.ConsistencyGrade
import mx.kompara.parsers.model.FieldProvenance
import mx.kompara.parsers.model.IdentityCheck
import mx.kompara.parsers.model.OfferContentBounds
import mx.kompara.parsers.model.OfferValidationEvidence
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.FARE
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.PICKUP_KM
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.PICKUP_MINUTES
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.RATE
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TOTAL_KM
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TOTAL_MINUTES
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TRIP_KM
import mx.kompara.parsers.model.OfferValidationEvidence.Companion.TRIP_MINUTES
import mx.kompara.parsers.model.ValidatedField
import mx.kompara.parsers.model.ValidatedField.Companion.REPAIR_CONFUSABLE
import mx.kompara.parsers.model.ValidatedField.Companion.REPAIR_DECIMAL
import mx.kompara.parsers.model.ValidatedField.Companion.REPAIR_UNIT
import kotlin.math.abs

/**
 * The modern Uber card's own arithmetic (0.2.15, validated on 190 real S25 frames): TOTAL minutes =
 * pickup + trip exactly; TOTAL tenths of a km = pickup + trip exactly; and the displayed per-km rate
 * is the fare over TOTAL km rounded to the cent, so 2·|r·t − 10·f| ≤ t in fare cents f, rate cents
 * per km r and TOTAL tenths of a km t (equality accepted: the frames do not show Uber's tie-breaking).
 * Checked Long arithmetic only, never a floating epsilon.
 *
 * Tokens reach this object only from slots that card geometry and unit/currency syntax already
 * established; letters become digits here and nowhere else (l, L, I, | -> 1; O, o -> 0). The
 * identities validate restricted textual candidates — a separatorless distance's literal and
 * one-decimal readings, a separatorless amount's fixed two decimals — and never generate money.
 * Total distance is one shared value across the route and money identities. A missing trip
 * dimension of an identified row may be recovered as TOTAL minus pickup when both operands were read
 * as printed (one reading, no repair) and the other route dimension's identity holds independently;
 * nothing else is recovered.
 */
internal object UberCardChecksum {

    /** One reading of a slot in integer units; [repairs] 0 means read as printed. */
    data class Candidate(val units: Long, val repairs: Int = 0)

    /** A slot's alternative readings (empty when unread) and the row it was read from. */
    data class Slot(val candidates: List<Candidate> = emptyList(), val bounds: OfferContentBounds? = null)

    /** What the card may publish: only established values, all null when its identities contradict. */
    class Result(val evidence: OfferValidationEvidence, val fare: Double?, val pickupMin: Double?,
        val pickupKm: Double?, val tripMin: Double?, val tripKm: Double?, val routeWellFormed: Boolean)

    private const val NUMERIC = "0123456789lLI|Oo"
    private val minutesToken = Regex("""[0-9lLI|Oo]{1,4}""")
    private val distanceToken = Regex("""([0-9lLI|Oo]{1,4})(?:([.,])([0-9lLI|Oo]))?""")
    private val thousands = Regex("""(\d{1,3}(?:,\d{3})+)\.(\d{2})""")
    private val decimal = Regex("""(\d+)[.,](\d{2})""")
    private val separatorless = Regex("""\d{3,9}""")

    private fun digit(c: Char) = when (c) {
        'l', 'L', 'I', '|' -> '1'
        'O', 'o' -> '0'
        else -> c
    }
    private fun confusable(token: String) = if (token.any { it in NUMERIC && !it.isDigit() }) REPAIR_CONFUSABLE else 0

    /** Complete integer minutes; never truncated. A malformed slot (e.g. `dlZ`) stays unread. */
    fun minutes(token: String?, unitRepaired: Boolean = false): List<Candidate> {
        if (token == null || !minutesToken.matches(token)) return emptyList()
        val units = token.map(::digit).joinToString("").toLongOrNull() ?: return emptyList()
        return listOf(Candidate(units, confusable(token) or (if (unitRepaired) REPAIR_UNIT else 0)))
    }

    /**
     * Tenths of a km. A separator (point or comma) with one decimal is one reading, as is a single
     * digit (`0 km` is genuine); a separatorless multi-digit token is ambiguous between its literal
     * reading and one inserted decimal, which only an independent identity can resolve.
     */
    fun distance(token: String?, unitRepaired: Boolean = false): List<Candidate> {
        val match = token?.let(distanceToken::matchEntire) ?: return emptyList()
        val repairs = confusable(token) or (if (unitRepaired) REPAIR_UNIT else 0)
        val whole = match.groupValues[1].map(::digit).joinToString("").toLong()
        if (match.groupValues[2].isNotEmpty()) {
            return listOf(Candidate(whole * 10 + digit(match.groupValues[3].single()).digitToInt(), repairs))
        }
        if (match.groupValues[1].length == 1) return listOf(Candidate(whole * 10, repairs))
        return listOf(Candidate(whole * 10, repairs), Candidate(whole, repairs or REPAIR_DECIMAL))
    }

    /**
     * Cents (or cents per km). Two-decimal monetary syntax with a point or comma, unambiguous
     * thousands separators, or a separatorless amount read with its fixed two decimals (a decimal
     * repair). Malformed separators are unread, never cleaned.
     */
    fun money(token: String?): List<Candidate> {
        if (token == null || token.any { it !in NUMERIC && it != '.' && it != ',' }) return emptyList()
        val repairs = confusable(token)
        val normalized = token.map(::digit).joinToString("")
        thousands.matchEntire(normalized)?.let { m ->
            return listOfNotNull((m.groupValues[1].replace(",", "") + m.groupValues[2]).toLongOrNull()?.let { Candidate(it, repairs) })
        }
        decimal.matchEntire(normalized)?.let { m ->
            return listOfNotNull((m.groupValues[1] + m.groupValues[2]).toLongOrNull()?.let { Candidate(it, repairs) })
        }
        if (separatorless.matches(normalized)) return listOf(Candidate(normalized.toLong(), repairs or REPAIR_DECIMAL))
        return emptyList()
    }

    private enum class Check { HOLDS, FAILS, NOT_EVALUATED }

    private fun sum(total: Long?, a: Long?, b: Long?) =
        if (total == null || a == null || b == null) Check.NOT_EVALUATED
        else if (total == Math.addExact(a, b)) Check.HOLDS else Check.FAILS

    private fun money(fare: Long?, rate: Long?, tenths: Long?): Check {
        if (fare == null || rate == null || tenths == null || tenths <= 0) return Check.NOT_EVALUATED
        return try {
            val gap = abs(Math.subtractExact(Math.multiplyExact(rate, tenths), Math.multiplyExact(10L, fare)))
            if (Math.multiplyExact(2L, gap) <= tenths) Check.HOLDS else Check.FAILS
        } catch (overflow: ArithmeticException) {
            Check.FAILS
        }
    }

    /** |F − R·TK| / F in parts per million, bounded: dimensionless, never a monetary amount. */
    private fun moneyPpm(fare: Long, rate: Long, tenths: Long): Long? = try {
        val gap = abs(Math.subtractExact(Math.multiplyExact(rate, tenths), Math.multiplyExact(10L, fare)))
        if (fare <= 0) null else minOf(1_000_000L, Math.multiplyExact(gap, 1_000_000L) / Math.multiplyExact(10L, fare))
    } catch (overflow: ArithmeticException) {
        1_000_000L
    }

    /**
     * Solve the card's slots ([OfferValidationEvidence] order) jointly. [rowsIdentified]: exactly two
     * ordered route rows were found, so a missing trip dimension has a row to belong to.
     * [structuralConflict]: competing TOTAL rows in one card band.
     */
    fun solve(slots: List<Slot>, rowsIdentified: Boolean, selectionCertain: Boolean, fareCandidates: Int,
        structuralConflict: Boolean = false): Result {
        require(slots.size == 8)
        fun single(i: Int) = slots[i].candidates.singleOrNull()
        val (f, r, tm) = listOf(single(FARE), single(RATE), single(TOTAL_MINUTES))
        val (pm, qm) = single(PICKUP_MINUTES) to single(TRIP_MINUTES)
        val minuteCheck = sum(tm?.units, pm?.units, qm?.units)
        // Every assignment of the shared total distance and both leg distances, one interpretation each.
        fun options(i: Int) = slots[i].candidates.ifEmpty { listOf(null) }
        data class Assignment(val tk: Candidate?, val pk: Candidate?, val qk: Candidate?, val distance: Check, val money: Check)
        val admissible = options(TOTAL_KM).flatMap { tk -> options(PICKUP_KM).flatMap { pk -> options(TRIP_KM).map { qk ->
            Assignment(tk, pk, qk, sum(tk?.units, pk?.units, qk?.units), money(f?.units, r?.units, tk?.units))
        } } }.filter { it.distance != Check.FAILS && it.money != Check.FAILS }
        fun resolved(pick: (Assignment) -> Candidate?) = admissible.map(pick).distinct().singleOrNull()
        val tk = resolved { it.tk }
        val pk = resolved { it.pk }
        val qk = resolved { it.qk }
        // Two assignments that both satisfy an identity they were evaluated by: competing values.
        val contested = listOf<(Assignment) -> Candidate?>({ it.tk }, { it.pk }, { it.qk }).withIndex().any { (k, pick) ->
            admissible.map(pick).distinct().size > 1 && admissible.any {
                it.distance == Check.HOLDS || k == 0 && it.money == Check.HOLDS
            }
        }
        val contradictory = structuralConflict || minuteCheck == Check.FAILS || admissible.isEmpty() || contested

        val values = arrayOfNulls<Candidate>(8).also {
            it[FARE] = f; it[RATE] = r; it[TOTAL_MINUTES] = tm; it[TOTAL_KM] = tk
            it[PICKUP_MINUTES] = pm; it[PICKUP_KM] = pk; it[TRIP_MINUTES] = qm; it[TRIP_KM] = qk
        }
        val distanceCheck = sum(tk?.units, pk?.units, qk?.units)
        val moneyCheck = money(f?.units, r?.units, tk?.units)
        val minutesHold = minuteCheck == Check.HOLDS
        val distanceHolds = distanceCheck == Check.HOLDS
        val moneyHolds = moneyCheck == Check.HOLDS
        // Validated by an identity that was independently evaluated and holds.
        fun validated(i: Int) = when (i) {
            FARE, RATE -> moneyHolds
            TOTAL_KM -> moneyHolds || distanceHolds
            PICKUP_KM, TRIP_KM -> distanceHolds
            else -> minutesHold
        }
        fun asPrinted(i: Int) = values[i]?.repairs == 0
        fun established(i: Int) = values[i] != null && (asPrinted(i) || validated(i))

        // Restricted recovery: only an unread trip dimension of an identified row, from operands read
        // as printed — one reading, no repair; never a decimal-repaired or money-selected total (round 2) —
        // while the OTHER route dimension's identity holds independently.
        fun readAsPrinted(i: Int) = asPrinted(i) && slots[i].candidates.size == 1
        val recovered = BooleanArray(8)
        val operands = IntArray(8)
        if (!contradictory && rowsIdentified) {
            if (slots[TRIP_MINUTES].candidates.isEmpty() && distanceHolds &&
                readAsPrinted(TOTAL_MINUTES) && readAsPrinted(PICKUP_MINUTES)) {
                val units = tm!!.units - pm!!.units
                if (units >= 0) {
                    values[TRIP_MINUTES] = Candidate(units)
                    recovered[TRIP_MINUTES] = true
                    operands[TRIP_MINUTES] = (1 shl TOTAL_MINUTES) or (1 shl PICKUP_MINUTES)
                }
            } else if (slots[TRIP_KM].candidates.isEmpty() && minutesHold &&
                readAsPrinted(TOTAL_KM) && readAsPrinted(PICKUP_KM)) {
                val units = tk!!.units - pk!!.units
                if (units >= 0) {
                    values[TRIP_KM] = Candidate(units)
                    recovered[TRIP_KM] = true
                    operands[TRIP_KM] = (1 shl TOTAL_KM) or (1 shl PICKUP_KM)
                }
            }
        }
        val minutesResolved = minutesHold || recovered[TRIP_MINUTES]
        val distanceResolved = distanceHolds || recovered[TRIP_KM]
        fun publishable(i: Int) = !contradictory && (recovered[i] || established(i))

        val fields = (0 until 8).map { i ->
            val v = values[i]
            ValidatedField(units = v?.units,
                provenance = when {
                    v == null -> null
                    recovered[i] -> FieldProvenance.RECOVERED
                    v.repairs != 0 -> FieldProvenance.REPAIRED
                    else -> FieldProvenance.READ
                },
                bounds = slots[i].bounds, repairs = v?.repairs ?: 0, operands = operands[i])
        }
        val grade = when {
            contradictory -> ConsistencyGrade.CONTRADICTORY
            !(minutesResolved && distanceResolved && moneyHolds) -> ConsistencyGrade.UNVERIFIABLE
            fields.any { it.provenance != FieldProvenance.READ } -> ConsistencyGrade.REPAIRED
            else -> ConsistencyGrade.CONSISTENT
        }
        val legs = listOf(PICKUP_MINUTES, PICKUP_KM, TRIP_MINUTES, TRIP_KM).map { i ->
            values[i]?.takeIf { publishable(i) && rowsIdentified }?.units
        }
        val routeWellFormed = legs.all { it != null }
        val evidence = OfferValidationEvidence(fare = fields[FARE], rate = fields[RATE],
            totalMinutes = fields[TOTAL_MINUTES], totalKm = fields[TOTAL_KM],
            pickupMinutes = fields[PICKUP_MINUTES], pickupKm = fields[PICKUP_KM],
            tripMinutes = fields[TRIP_MINUTES], tripKm = fields[TRIP_KM],
            minutes = IdentityCheck(minuteCheck != Check.NOT_EVALUATED, minutesHold,
                if (minuteCheck == Check.NOT_EVALUATED) null else tm!!.units - pm!!.units - qm!!.units),
            distance = IdentityCheck(distanceCheck != Check.NOT_EVALUATED, distanceHolds,
                if (distanceCheck == Check.NOT_EVALUATED) null else tk!!.units - pk!!.units - qk!!.units),
            money = IdentityCheck(moneyCheck != Check.NOT_EVALUATED, moneyHolds,
                if (moneyCheck == Check.NOT_EVALUATED) null else moneyPpm(f!!.units, r!!.units, tk!!.units)),
            selectionCertain = selectionCertain, fareCandidates = fareCandidates,
            routeValidated = !contradictory && routeWellFormed && minutesResolved && distanceResolved,
            moneyValidated = !contradictory && moneyHolds, grade = grade)
        return Result(evidence,
            fare = f?.takeIf { publishable(FARE) && it.units > 0 }?.units?.let { it / 100.0 },
            pickupMin = legs[0]?.toDouble(), pickupKm = legs[1]?.let { it / 10.0 },
            tripMin = legs[2]?.toDouble(), tripKm = legs[3]?.let { it / 10.0 },
            routeWellFormed = routeWellFormed)
    }
}
