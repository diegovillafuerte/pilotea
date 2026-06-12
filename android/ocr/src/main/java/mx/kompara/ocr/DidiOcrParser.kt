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

    private val fareRegex = Regex("""\$\s*([0-9][0-9,]*\.[0-9]{2})""")
    private val legRegex =
        Regex("""([0-9]+)\s*min\s*\(\s*([0-9]+(?:\.[0-9]+)?)\s*km\s*\)""", RegexOption.IGNORE_CASE)

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
        val surge = text.contains("dinámica", ignoreCase = true) ||
            text.contains("dinamica", ignoreCase = true)

        return OfferCard(
            platform = DIDI_PACKAGE,
            variant = if (surge) "surge" else null,
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

    private fun extractFare(blocks: List<OcrBlock>): Double? =
        blocks
            .mapNotNull { b ->
                fareRegex.find(b.text)?.let { m ->
                    val value = m.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (value == null) null else value to (b.bounds.bottom - b.bounds.top)
                }
            }
            // The real fare is rendered far taller than the dynamic-fare line; tallest wins.
            .maxByOrNull { it.second }
            ?.first

    private data class Leg(val min: Double, val km: Double, val top: Int)

    private fun extractLegs(blocks: List<OcrBlock>): List<Leg> =
        blocks
            .mapNotNull { b ->
                legRegex.find(b.text)?.let { m ->
                    val min = m.groupValues[1].toDoubleOrNull()
                    val km = m.groupValues[2].toDoubleOrNull()
                    if (min == null || km == null) null else Leg(min, km, b.bounds.top)
                }
            }
            .sortedBy { it.top }

    companion object {
        const val DIDI_PACKAGE = "com.didiglobal.driver"
    }
}
