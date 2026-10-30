package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferContentBounds
import java.util.Locale

/**
 * Parses an Uber Driver (MX) trip-offer card from OCR text blocks.
 *
 * ## Why OCR and not the accessibility node tree
 * On the current Uber Driver build the offer card is rendered inside a
 * `com.uber.rib.core.compose.root.UberComposeView` (a full-screen Compose host) with NO accessibility
 * semantics — verified on-device 2026-06-15 (Samsung S25, CDMX): `getWindows()` returns only ~22
 * chrome nodes ("Buscando viajes", "+MXN 10", …) and the fare/distance/Aceptar text is in neither
 * `text` nor `contentDescription`. So Uber joins DiDi/inDrive on the MediaProjection + OCR path; the
 * declarative node-path `uber-driver.json` spec is dead for live offers (kept only for the simulator
 * and as a fixture reference).
 *
 * ## Strategy (text + card geometry)
 * - **Fare**: a standalone `$`, `MX$` or `MXN` amount with two decimals, in the card's heading/action
 *   band. Largest glyph height wins, with reading order breaking ties. Whole-line matching excludes
 *   bonuses, included amounts, rates and map labels. Bare `$` requires an Uber heading so a DiDi
 *   card cannot be claimed solely by its currency. The pipeline masks our own chip before parsing.
 * - **Pickup / trip**: the legacy pickup plus `Viaje:` pair, or two standalone `N min (X.X km)`
 *   lines in reading order on the current Uber card. The summary above them is not a route leg.
 * - **Variant/surge**: keyword presence ("Reserva", "paradas", "Tarifa dinámica", "Exclusivo",
 *   "Radar de solicitud de viaje").
 *
 * Tuned against 874 real OCR frames across 12 live offers (2026-06-15). OCR garble handled: minute and
 * distance digits substitute O→0 / I,L→1; decimal separator is `.` or `,`. Offer recognition is
 * independent of extracting the amount and times: a readable card with missing fields still yields
 * the grey Sin lectura / Falta tiempo state. A known route structure supplies estimated driver
 * payment; a recognized offer with an unknown structure keeps its payment basis unconfirmed.
 */
class UberOcrParser {

    // Always matchEntire: a leading +, suffix /km or any surrounding words disqualifies the block.
    // Preserve comma decimals on old MXN radar frames and thousands separators on current $ fares.
    private val fareRegex = Regex("""(?:MX[N$]\s*\$?|\$)\s*([0-9][0-9.,]*[.,][0-9]{2})""", RegexOption.IGNORE_CASE)
    // A distance/time leg: "N min (X.X km)". Minute and distance chars allow the O/0, l/1, I/1 OCR
    // confusions; cleaned in [cleanNumber].
    private val legRegex =
        Regex("""([0-9OlI]+)\s*min\s*\(\s*([0-9OlI][0-9OlI.,]*)\s*km""", RegexOption.IGNORE_CASE)
    // The legacy trip leg, distinguished from the pickup leg by its "Viaje:" label.
    private val tripLegRegex =
        Regex("""Viaje\s*:?\s*([0-9OlI]+)\s*min\s*\(\s*([0-9OlI][0-9OlI.,]*)\s*km""", RegexOption.IGNORE_CASE)
    private val unlabeledLegRegex =
        Regex("""(?:[0-9OlI]+|—|-)\s*min\s*\(\s*[0-9OlI][0-9OlI.,]*\s*km\s*\)""", RegexOption.IGNORE_CASE)

    private val distanceRegex = Regex("""([0-9OlI][0-9OlI.,]*)\s*km\b""", RegexOption.IGNORE_CASE)
    private val minutesRegex = Regex("""([0-9OlI]+)\s*min\b""", RegexOption.IGNORE_CASE)
    private val acceptRegex = Regex("""\b(Aceptar|Coincidir)\b""", RegexOption.IGNORE_CASE)
    private val hostRegex = Regex("""\bUber\w*|Radar de solicitud""", RegexOption.IGNORE_CASE)

    fun parse(blocks: List<OcrBlock>): OfferCard? {
        val cardBlocks = cardBlocks(blocks)
        if (!hasCardSignature(cardBlocks)) return null
        val fareHit = extractFare(cardBlocks)
        val trip = extractTripLeg(cardBlocks)
        val pickup = extractPickupLeg(cardBlocks)

        val text = cardBlocks.joinToString(" ") { it.text }
        val surge = text.contains("dinámica", ignoreCase = true) ||
            text.contains("dinamica", ignoreCase = true)
        val variant = when {
            text.contains("Reserva", ignoreCase = true) ||
                text.contains("Programad", ignoreCase = true) -> "reservation"
            text.contains("paradas", ignoreCase = true) -> "multi_stop"
            surge -> "surge"
            text.contains("Exclusivo", ignoreCase = true) -> "exclusive"
            text.contains("Radar de solicitud", ignoreCase = true) -> "trip_radar"
            else -> null
        }

        return OfferCard(
            platform = UBER_PACKAGE,
            variant = variant,
            fare = fareHit?.value,
            captureSource = "OCR",
            // Known Uber offer amounts estimate the driver payment, never the settled payment.
            paymentBasis = if (trip != null) "DRIVER_PAYMENT" else "UNCONFIRMED",
            pickupDistanceKm = pickup?.km,
            pickupEtaMin = pickup?.min,
            tripDistanceKm = trip?.km,
            tripDurationMin = trip?.min,
            surge = surge,
            paymentType = null,
            raw = fareHit?.let { mapOf("fare" to String.format(Locale.US, "%.2f", it.value)) } ?: emptyMap(),
            contentBounds = unionBounds(fareHit?.bounds, trip?.bounds, pickup?.bounds),
        )
    }

    /** A card survives missing money/time, but idle bonuses and explicit zero-wallet pills do not. */
    fun hasCardSignature(blocks: List<OcrBlock>): Boolean {
        val cardBlocks = cardBlocks(blocks)
        val fare = extractFare(cardBlocks)
        val text = cardBlocks.joinToString(" ") { it.text }
        val trip = extractTripLeg(cardBlocks)
        val action = acceptRegex.containsMatchIn(text)
        val host = hostRegex.containsMatchIn(text)
        // A zero wallet pill cannot erase an independently recognized offer with an unread fare.
        if (fare == null && !(action && (trip != null || host)) &&
            cardBlocks.any { standaloneFare(it, host) == 0.0 }) return false
        return (fare != null && (trip != null || cardBlocks.any { legRegex.containsMatchIn(it.text) })) ||
            (trip != null && (action || host)) || (host && action)
    }

    private data class FareHit(val value: Double, val bounds: OcrBounds)

    /** Keep map/chip text above the heading and map text below the action outside the card. */
    private fun cardBlocks(blocks: List<OcrBlock>): List<OcrBlock> {
        val ordered = blocks.sortedWith(compareBy<OcrBlock> { it.bounds.top }.thenBy { it.bounds.left })
        val heading = ordered.firstOrNull { hostRegex.containsMatchIn(it.text) } ?: return ordered
        val action = ordered.firstOrNull {
            it.bounds.top >= heading.bounds.top && acceptRegex.containsMatchIn(it.text)
        }
        return ordered.filter {
            it.bounds.top >= heading.bounds.top && (action == null || it.bounds.bottom <= action.bounds.bottom)
        }
    }

    /** Bare-dollar fares need host evidence; legacy MX fares retain their existing route signature. */
    private fun standaloneFare(block: OcrBlock, hasHost: Boolean): Double? {
        val text = block.text.trim()
        if (!hasHost && !text.startsWith("MX", ignoreCase = true)) return null
        return fareRegex.matchEntire(text)?.groupValues?.get(1)?.let(::cleanNumber)?.toDoubleOrNull()
    }

    /** Card blocks are in reading order; maxByOrNull retains the first equal-height amount. */
    private fun extractFare(blocks: List<OcrBlock>): FareHit? {
        val hasHost = blocks.any { hostRegex.containsMatchIn(it.text) }
        return blocks
            .asSequence()
            .mapNotNull { b ->
                standaloneFare(b, hasHost)
                    // A non-positive fare is never a real ride — it's an OCR misread (a garbled glyph
                    // cleaned to "0.00") or a captured "$0.00" pill. Drop it so no $0 verdict can reach
                    // the chip (the red "$0.00/km" seen over a cross-app Uber broadcast, 2026-06-15).
                    ?.takeIf { it > 0.0 }
                    ?.let { FareHit(it, b.bounds) }
            }
            .maxByOrNull { it.bounds.bottom - it.bounds.top }
    }

    private data class Leg(val min: Double?, val km: Double?, val bounds: OcrBounds?)

    /** Per-block first (precise, keeps the block's bounds); joined-text fallback has no single block. */
    private fun extractTripLeg(blocks: List<OcrBlock>): Leg? {
        blocks.firstNotNullOfOrNull { b ->
            if (b.text.contains("Viaje", ignoreCase = true)) partialLeg(b) else null
        }?.let { return it }
        return tripLegRegex.find(blocks.joinToString(" ") { it.text })?.let { toLeg(it, null) }
            ?: unlabeledRouteLegs(blocks).getOrNull(1)
    }

    /** Current cards have two standalone legs; legacy cards label the trip leg. */
    private fun extractPickupLeg(blocks: List<OcrBlock>): Leg? =
        unlabeledRouteLegs(blocks).firstOrNull() ?: blocks
            .asSequence()
            .filterNot { it.text.contains("Viaje", ignoreCase = true) }
            .filterNot { it.text.contains("•") || it.text.contains("/km", ignoreCase = true) }
            .filter { it.text.contains("min", ignoreCase = true) || it.text.contains("(") }
            .firstNotNullOfOrNull(::partialLeg)

    private fun unlabeledRouteLegs(blocks: List<OcrBlock>): List<Leg> {
        if (blocks.none { hostRegex.containsMatchIn(it.text) }) return emptyList()
        // Exactly two standalone lines are the observed current shape. A garbled legacy "Viaje"
        // label must not be silently promoted into a known trip/payment structure.
        return blocks.filter { unlabeledLegRegex.matches(it.text.trim()) }
            .mapNotNull(::partialLeg).takeIf { it.size == 2 } ?: emptyList()
    }

    private fun partialLeg(block: OcrBlock): Leg? {
        val minutes = minutesRegex.find(block.text)?.groupValues?.get(1)?.let(::cleanMinutes)
        val km = distanceRegex.find(block.text)?.groupValues?.get(1)
            ?.let { recoverDroppedDistanceDecimal(cleanNumber(it)).toDoubleOrNull() }
        return if (minutes == null && km == null) null else Leg(minutes, km, block.bounds)
    }

    private fun toLeg(m: MatchResult, bounds: OcrBounds?): Leg? {
        val min = cleanMinutes(m.groupValues[1]) ?: return null
        // Distances render with one decimal; reinsert a point OCR dropped ("130"→13.0, "L0"→1.0) so a
        // separator-less leg can't read 10× high. Fares always keep their separator (the two-decimal
        // fareRegex), so recovery is applied here on the leg, never on the fare.
        val km = recoverDroppedDistanceDecimal(cleanNumber(m.groupValues[2])).toDoubleOrNull() ?: return null
        return Leg(min = min, km = km, bounds = bounds)
    }

    /** Union the fare + leg block bounds into the offer-content rect the overlay keeps the chip off;
     *  null when no block bounds were available (e.g. only a joined-text leg matched). */
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

    /**
     * Minutes are a bare integer, so the l/I→1 substitution that helps decimals ("1l.4 km" → 11.4)
     * mis-reads a SPURIOUS trailing glyph as a digit: live frames show "21l min" for 21 and "41l min"
     * for 41 (the "l" is noise), yet also "2l min" for 21 (the "l" IS the second digit). The rule that
     * fits every observed frame: substitute, then keep only the first two digits — ride-hail ETAs and
     * durations are ≤ 2 digits, so a third digit is always the appended-glyph artifact. (Trade-off:
     * a rare 100-min+ trip reads low, which only makes a long trip look *better*, never worse.)
     */
    private fun cleanMinutes(raw: String): Double? {
        val digits = raw.uppercase()
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
            .filter { it.isDigit() }
        return digits.take(2).toDoubleOrNull()
    }

    /** Undo the common OCR letter-for-digit confusions and normalize the decimal separator. */
    private fun cleanNumber(raw: String): String {
        val digits = raw.uppercase()
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
        // "," is a thousands separator when a "." is also present, otherwise the decimal point.
        return if (digits.contains('.')) digits.replace(",", "") else digits.replace(',', '.')
    }

    companion object {
        const val UBER_PACKAGE = "com.ubercab.driver"
    }
}
