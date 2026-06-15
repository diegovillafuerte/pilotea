package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard

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
 * Returns null unless it finds a fare AND both distance/time lines (the offer-card signature), so a
 * non-offer screen produces no verdict.
 */
class DidiOcrParser {

    // A bare "$" fare — NOT the "$" inside Uber's "MX$" (the negative lookbehind keeps DiDi disjoint
    // from UberOcrParser so the OCR service's "try Uber, else DiDi" fallback can't mis-attribute a
    // garbled Uber MX$ frame to DiDi).
    private val fareRegex = Regex("""(?<![A-Za-z])\$\s*([0-9][0-9,]*\.[0-9]{2})""")
    private val acceptRegex = Regex("""Aceptar\s*\$\s*([0-9][0-9,]*\.[0-9]{2})""", RegexOption.IGNORE_CASE)
    // Distance leg: "6min (1.2km)" OR short pickups in meters "5min (862m)".
    private val legRegex =
        Regex("""([0-9]+)\s*min\s*\(\s*([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val surgeRegex = Regex("""[0-9]+(?:\.[0-9]+)?\s*x\b""", RegexOption.IGNORE_CASE)

    fun parse(blocks: List<OcrBlock>): OfferCard? {
        val fare = extractFare(blocks) ?: return null
        val legs = extractLegs(blocks)
        if (legs.size < 2) return null
        val (pickup, trip) = legs[0] to legs[1]

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
            fare = fare,
            pickupDistanceKm = pickup.km,
            pickupEtaMin = pickup.min,
            tripDistanceKm = trip.km,
            tripDurationMin = trip.min,
            surge = surge,
            paymentType = payment,
            raw = mapOf("fare" to "%.2f".format(fare)),
        )
    }

    /**
     * Whether the frame still carries the offer-card *signature* — a currency amount plus at least
     * one "Nmin (X km)" leg — even though full parsing failed (B-077). The map animating under the
     * card garbles single frames often ("1nmin", "Zmin"), so a failed parse with the signature
     * present means "card still up, frame garbled", not "card gone". The idle map screen fails
     * this: its "$0.00" wallet pill has no leg line.
     */
    fun hasCardSignature(blocks: List<OcrBlock>): Boolean {
        val text = blocks.joinToString(" ") { it.text }
        return fareRegex.containsMatchIn(text) && legRegex.containsMatchIn(text)
    }

    private fun extractFare(blocks: List<OcrBlock>): Double? {
        // Most reliable: the amount on the "Aceptar $X" button echoes the current price — and on the
        // "Pon Tu Precio" bid card it disambiguates the real fare from the higher bid options.
        for (b in blocks) {
            acceptRegex.find(b.text)?.let { m ->
                m.groupValues[1].replace(",", "").toDoubleOrNull()?.let { return it }
            }
        }
        // Fallback: the fare is rendered far taller than any other currency text; tallest wins.
        return blocks
            .mapNotNull { b ->
                fareRegex.find(b.text)?.let { m ->
                    m.groupValues[1].replace(",", "").toDoubleOrNull()
                        ?.let { it to (b.bounds.bottom - b.bounds.top) }
                }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private data class Leg(val min: Double, val km: Double, val top: Int)

    private fun extractLegs(blocks: List<OcrBlock>): List<Leg> =
        blocks
            .mapNotNull { b ->
                legRegex.find(b.text)?.let { m ->
                    val min = m.groupValues[1].toDoubleOrNull()
                    val value = m.groupValues[2].replace(",", ".").toDoubleOrNull()
                    val km = when {
                        value == null -> null
                        m.groupValues[3].equals("m", ignoreCase = true) -> value / 1000.0
                        else -> value
                    }
                    if (min == null || km == null) null else Leg(min, km, b.bounds.top)
                }
            }
            .sortedBy { it.top }

    companion object {
        const val DIDI_PACKAGE = "com.didiglobal.driver"
    }
}
