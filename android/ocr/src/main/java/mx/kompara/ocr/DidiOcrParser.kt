package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferContentBounds
import java.util.Locale

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

    // A bare "$" fare — NOT the "$" inside Uber's "MX$" (the negative lookbehind keeps DiDi disjoint
    // from UberOcrParser so the OCR service's "try Uber, else DiDi" fallback can't mis-attribute a
    // garbled Uber MX$ frame to DiDi).
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

    private fun extractFare(blocks: List<OcrBlock>): FareHit? {
        // A non-positive fare is never a real offer — it's the "$0.00" wallet/earnings pill (or
        // Kompara's own "$0.00/km" chip text self-captured). Reject ≤0 in BOTH paths so the frame
        // reads as "no card" instead of emitting a $0 verdict (the cross-app $0.00 chip bug).
        // Most reliable: the amount on the "Aceptar $X" button echoes the current price — and on the
        // "Pon Tu Precio" bid card it disambiguates the real fare from the higher bid options.
        for (b in blocks) {
            acceptRegex.find(b.text)?.let { m ->
                m.groupValues[1].replace(",", "").toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
                    ?.let { return FareHit(it, b.bounds) }
            }
        }
        // Fallback: the fare is rendered far taller than any other currency text; tallest wins.
        return blocks
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

    private data class Leg(val min: Double?, val km: Double, val bounds: OcrBounds)

    private fun extractLegs(blocks: List<OcrBlock>): List<Leg> =
        blocks
            .mapNotNull { b ->
                legRegex.find(b.text)?.let { m ->
                    val min = m.groupValues[1].toDoubleOrNull()
                    val normalized = m.groupValues[2].replace(",", ".")
                    val km = if (m.groupValues[3].equals("m", ignoreCase = true)) {
                        // Meters are a genuine integer ("862m" → 0.862 km) — never a dropped decimal.
                        normalized.toDoubleOrNull()?.let { it / 1000.0 }
                    } else {
                        // Km always render with one decimal; reinsert a point OCR dropped ("130"→13.0)
                        // so a separator-less leg can't read 10× high (DiDi mirror of the Uber fix).
                        recoverDroppedDistanceDecimal(normalized).toDoubleOrNull()
                    }
                    if (km == null) null else Leg(min, km, b.bounds)
                }
            }
            .sortedBy { it.bounds.top }

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
    }
}
