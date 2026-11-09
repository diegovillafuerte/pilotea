package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferContentBounds
import java.util.Locale
import kotlin.math.abs

/**
 * Parses a DiDi Conductor (MX) offer card from OCR text blocks. DiDi renders its UI on a Flutter
 * SurfaceView (design doc §7), so there is no accessibility text — we read the screen-capture OCR
 * output instead. Validated against real captured cards (2026-06-11).
 *
 * Strategy (geometry + pattern, never absolute coordinates):
 * - **Fare**: the `$NNN.NN` rendered far larger than anything else — pick the currency match in the
 *   tallest block. This separates the real fare ("$132.59") from the small dynamic-fare line
 *   ("$13.87 de tarifa base dinámica").
 * - **Pickup / trip**: the two `Nmin (X.Ykm)` lines, top-to-bottom: first is pickup, second is trip.
 * - **Payment / surge**: keyword presence.
 *
 * Recognition uses the route lines and offer action independently of fare/time extraction, so a
 * recognized card can show Sin lectura or Falta tiempo. Known two-leg offers carry estimated driver
 * payment; other recognized shapes keep an unconfirmed payment basis.
 */
class DidiOcrParser {

    // A bare "$" fare. The OS chooses this parser; rate/bonus context still cannot supply payment.
    private val fareRegex = Regex("""(?<![A-Za-z])\$\s*([0-9][0-9,]*\.[0-9]{2})""")
    private val acceptRegex = Regex("""Aceptar\s*\$\s*([0-9][0-9,]*\.[0-9]{2})""", RegexOption.IGNORE_CASE)
    // Distance leg: "6min (1.2km)" OR short pickups in meters "5min (862m)".
    private val legRegex =
        Regex("""(?:([0-9]+)\s*min\s*)?\(\s*([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val surgeRegex = Regex("""[0-9]+(?:\.[0-9]+)?\s*x\b""", RegexOption.IGNORE_CASE)

    fun parse(blocks: List<OcrBlock>): OfferCard? {
        if (!hasCardSignature(blocks)) return null
        val fareHit = extractFare(blocks)
        val legs = extractLegs(blocks)
        val pickup = legs.getOrNull(0)
        val trip = legs.getOrNull(1)

        val text = blocks.joinToString(" ") { it.text }
        val payment = when {
            text.contains("Tarjeta", ignoreCase = true) -> "tarjeta"
            text.contains("Efectivo", ignoreCase = true) -> "efectivo"
            else -> null
        }
        val bid = text.contains("Pon Tu Precio", ignoreCase = true)
        val surge = text.contains("dinámica", ignoreCase = true) ||
            text.contains("dinamica", ignoreCase = true) ||
            surgeRegex.containsMatchIn(text)

        return OfferCard(
            platform = DIDI_PACKAGE,
            variant = if (bid) "bid" else if (surge) "surge" else null,
            fare = fareHit?.value,
            captureSource = "OCR",
            // The existing two-leg standard/surge/bid offers quote estimated driver payment.
            paymentBasis = if (legs.size >= 2) "DRIVER_PAYMENT" else "UNCONFIRMED",
            pickupDistanceKm = pickup?.km,
            pickupEtaMin = pickup?.min,
            tripDistanceKm = trip?.km,
            tripDurationMin = trip?.min,
            surge = surge,
            paymentType = payment,
            raw = fareHit?.let { mapOf("fare" to String.format(Locale.US, "%.2f", it.value)) } ?: emptyMap(),
            contentBounds = unionBounds(fareHit?.bounds, pickup?.bounds, trip?.bounds),
            fareBounds = visibleFareBounds(blocks, legs.firstOrNull()?.bounds?.top),
            instanceEvidence = instanceEvidence(blocks, blocks),
        )
    }

    /** Missing fields keep a card visible; Uber signatures and zero wallet pills never claim DiDi. */
    fun hasCardSignature(blocks: List<OcrBlock>): Boolean {
        val text = blocks.joinToString(" ") { it.text }
        if (Regex("""MX[N$]|\bUber\w*""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)) return false
        val fare = extractFare(blocks)
        val legs = extractLegs(blocks)
        val action = Regex("""\bAceptar\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        // The wallet is independent of an offer's price; retain C4 when the action/route identify it.
        if (fare == null && !(action && (legs.size >= 2 || text.contains("DiDi", ignoreCase = true))) &&
            blocks.any { b -> fareRegex.findAll(b.text).any {
                it.groupValues[1].replace(",", "").toDoubleOrNull() == 0.0
            } }) return false
        return (fare != null && legs.isNotEmpty()) || (action && legs.size >= 2) ||
            (action && text.contains("DiDi", ignoreCase = true))
    }

    private data class FareHit(val value: Double, val bounds: OcrBounds)

    private val rateContext = Regex("""/\s*km\b|\bkm\s+est\b|\best\.""", RegexOption.IGNORE_CASE)

    /** Currency and rate suffixes can be separate OCR boxes; inspect the visual row as Uber does. */
    private fun isRate(block: OcrBlock, blocks: List<OcrBlock>): Boolean {
        val a = block.bounds
        val context = blocks.filter { other ->
            val b = other.bounds
            val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            other == block || overlap >= minOf(a.bottom - a.top, b.bottom - b.top) * 0.5
        }.sortedBy { it.bounds.left }.joinToString(" ") { it.text }
        return rateContext.containsMatchIn(context)
    }

    private fun extractFare(blocks: List<OcrBlock>): FareHit? {
        val paymentBlocks = blocks.filterNot { isRate(it, blocks) }
        // A non-positive fare is never a real offer — it's the "$0.00" wallet/earnings pill (or
        // Kompara's own "$0.00/km" chip text self-captured). Reject ≤0 in BOTH paths so the frame
        // reads as "no card" instead of emitting a $0 verdict (the cross-app $0.00 chip bug).
        // Most reliable: the amount on the "Aceptar $X" button echoes the current price — and on the
        // "Pon Tu Precio" bid card it disambiguates the real fare from the higher bid options.
        for (b in paymentBlocks) {
            acceptRegex.find(b.text)?.let { m ->
                m.groupValues[1].replace(",", "").toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
                    ?.let { return FareHit(it, b.bounds) }
            }
        }
        // Fallback: the fare is rendered far taller than any other currency text; tallest wins.
        return paymentBlocks
            .filterNot { it.text.contains("tarifa base", ignoreCase = true) ||
                it.text.contains("bono", ignoreCase = true) || it.text.trimStart().startsWith("+") }
            .mapNotNull { b ->
                fareRegex.find(b.text)?.let { m ->
                    m.groupValues[1].replace(",", "").toDoubleOrNull()
                        ?.takeIf { it > 0.0 }
                        ?.let { FareHit(it, b.bounds) to (b.bounds.bottom - b.bounds.top) }
                }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * Where the fare is DRAWN, for placement only: the tallest standalone amount above the first
     * leg. Never the "Aceptar $X" button below the legs, which [extractFare] prefers for the value
     * (techdebt 2026-06-15, DiDi Aceptar bounds). This never changes the parsed payment.
     */
    private fun visibleFareBounds(blocks: List<OcrBlock>, firstLegTop: Int?): OfferContentBounds? {
        if (firstLegTop == null) return null
        return blocks.filterNot { isRate(it, blocks) || acceptRegex.containsMatchIn(it.text) ||
                it.text.contains("tarifa base", ignoreCase = true) || it.text.contains("bono", ignoreCase = true) ||
                it.text.trimStart().startsWith("+") }
            .filter { it.bounds.bottom <= firstLegTop && fareRegex.containsMatchIn(it.text) }
            .maxByOrNull { it.bounds.bottom - it.bounds.top }
            ?.bounds?.let { OfferContentBounds(it.left, it.top, it.right, it.bottom) }
    }

    /** [minToken], [startsBlock] and [printedKm] (before decimal recovery) are the row as read, for [plausibleMinutes] and [extractLegs]. */
    private data class Leg(val min: Double?, val km: Double, val bounds: OcrBounds, val minToken: String,
        val startsBlock: Boolean, val printedKm: Double)

    private fun extractLegs(blocks: List<OcrBlock>): List<Leg> {
        val legs = blocks
            .mapNotNull { b ->
                legRegex.find(b.text)?.let { m ->
                    val min = m.groupValues[1].toDoubleOrNull()
                    val normalized = m.groupValues[2].replace(",", ".")
                    val meters = m.groupValues[3].equals("m", ignoreCase = true)
                    val km = if (meters) {
                        // Meters are a genuine integer ("862m" → 0.862 km) — never a dropped decimal.
                        normalized.toDoubleOrNull()?.let { it / 1000.0 }
                    } else {
                        // Only a candidate: DiDi prints some km whole ("30 km") and OCR can drop a point
                        // ("50" for 5.0). extractLegs keeps this value or printedKm once it knows the minutes.
                        recoverDroppedDistanceDecimal(normalized).toDoubleOrNull()
                    }
                    if (km == null) null else
                        Leg(min, km, b.bounds, m.groupValues[1], b.text.take(m.range.first).isBlank(),
                            normalized.toDouble() / if (meters) 1000.0 else 1.0)
                }
            }
            .sortedBy { it.bounds.top }
        // More km than minutes is over 60 km/h: OCR dropped the point. Unread minutes prove nothing.
        return legs.map { leg ->
            val min = plausibleMinutes(leg, legs, blocks)
            leg.copy(min = min, km = if (min != null && leg.printedKm > min) leg.km else leg.printedKm)
        }
    }

    /**
     * On a dimmed card OCR can merge the round row icon into the row as a leading digit (0.2.19:
     * "925 min (7.4 km)" for 25), and DiDi prints no total to check a leg against. Low speed alone is
     * not corruption (a real "2 min (50 m)" is 1.5 km/h): only a leg of at least [QUESTIONED_MIN]
     * minutes implying under [MIN_LEG_KMH] is questioned. An icon-glued row whose minutes begin its
     * block drops that digit when the rest is plausible; any other questioned row's minutes stay
     * unread and its distance stands. An icon already read apart ("9 94 min") is not in the token.
     * Speed uses the distance as printed: a row with no separator ("243min (14km)") is ambiguous
     * and cannot prove its minutes wrong.
     */
    private fun plausibleMinutes(leg: Leg, legs: List<Leg>, blocks: List<OcrBlock>): Double? {
        val min = leg.min ?: return null
        if (min < QUESTIONED_MIN || kmPerHour(leg.printedKm, min) >= MIN_LEG_KMH) return min
        if (!leg.startsBlock || !isIconGlued(leg, legs, blocks)) return null
        return leg.minToken.drop(1).toDouble().takeIf { it >= 1.0 && kmPerHour(leg.printedKm, it) in MIN_LEG_KMH..MAX_LEG_KMH }
    }

    private fun kmPerHour(km: Double, min: Double) = km / (min / 60.0)

    /**
     * The row starts at least half its own height left of the text column: the nearest non-leg text
     * directly below it (its address line, within two row heights), else the other leg's left edge.
     */
    private fun isIconGlued(leg: Leg, legs: List<Leg>, blocks: List<OcrBlock>): Boolean {
        val row = leg.bounds
        val height = row.bottom - row.top
        val addressLine = blocks.filter {
            it.text.isNotBlank() && !legRegex.containsMatchIn(it.text) &&
                it.bounds.top in row.bottom..row.bottom + 2 * height &&
                it.bounds.left < row.right && it.bounds.right > row.left
        }.minByOrNull { it.bounds.top }
        val column = addressLine?.bounds?.left
            ?: legs.filter { it !== leg }.minByOrNull { abs(it.bounds.top - row.top) }?.bounds?.left
            ?: return false
        return column - row.left >= height / 2.0
    }

    /** Union the fare + leg block bounds into the offer-content rect the overlay keeps the chip off. */
    private fun unionBounds(vararg parts: OcrBounds?): OfferContentBounds? {
        val present = parts.filterNotNull()
        if (present.isEmpty()) return null
        return OfferContentBounds(
            left = present.minOf { it.left },
            top = present.minOf { it.top },
            right = present.maxOf { it.right },
            bottom = present.maxOf { it.bottom },
        )
    }

    companion object {
        const val DIDI_PACKAGE = "com.didiglobal.driver"
        private const val QUESTIONED_MIN = 60.0
        private const val MIN_LEG_KMH = 2.0
        private const val MAX_LEG_KMH = 110.0
    }
}
