package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferContentBounds

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
 * ## Strategy (textual — Uber labels its fields, unlike DiDi which needs geometry)
 * - **Fare**: `MXN<amount>` (or `MX$`) with two decimals. The per-trip bonuses
 *   ("+MXN4.00 incluido", "+MXN12.63 por inicio de viaje", "+MXN 4") carry a leading `+` and are
 *   dropped. Requiring `MX[N$]` (never a bare `$`) keeps this disjoint from [DidiOcrParser] so the two
 *   can be tried in turn without a separate platform detector.
 * - **Pickup leg**: the `N min (X.X km)` line WITHOUT a `Viaje:` prefix (often an `A`/pin glyph).
 * - **Trip leg**: the `Viaje: N min (X.X km)` line.
 * - **Variant/surge**: keyword presence ("Reserva", "paradas", "Tarifa dinámica", "Exclusivo",
 *   "Radar de solicitud de viaje").
 *
 * Tuned against 874 real OCR frames across 12 live offers (2026-06-15). OCR garble handled: minute and
 * distance digits substitute O→0 / I,L→1; decimal separator is `.` or `,`. Returns null unless it
 * finds a fare AND the labeled trip leg, so non-offer screens and badly-garbled frames produce no
 * verdict — [CardPresenceTracker] holds the last good verdict across those. Pickup is best-effort
 * (the engine degrades gracefully on a missing leg).
 */
class UberOcrParser {

    // Fare: an "MXN"/"MX$" amount with two decimals, NOT immediately preceded by "+". The negative
    // lookbehind drops the incentive lines ("+MXN4.00 …"); the "+MXN 4"/"+MXN 6" badges are integers
    // and fail the two-decimal requirement anyway. The decimal separator is "." OR "," — OCR reads
    // the same fare as "MXN33.45" on some frames and "MXN33,45" on others (seen live on radar cards);
    // [cleanNumber] normalizes both, so the chip no longer flickers out on the comma frames.
    private val fareRegex = Regex("""(?<!\+)MX[N$]\s?\$?\s?([0-9][0-9.,]*[.,][0-9]{2})""")
    // A distance/time leg: "N min (X.X km)". Minute and distance chars allow the O/0, l/1, I/1 OCR
    // confusions; cleaned in [cleanNumber].
    private val legRegex =
        Regex("""([0-9OlI]+)\s*min\s*\(\s*([0-9OlI][0-9OlI.,]*)\s*km""", RegexOption.IGNORE_CASE)
    // The trip leg, distinguished from the pickup leg purely by its "Viaje:" label.
    private val tripLegRegex =
        Regex("""Viaje\s*:?\s*([0-9OlI]+)\s*min\s*\(\s*([0-9OlI][0-9OlI.,]*)\s*km""", RegexOption.IGNORE_CASE)

    fun parse(blocks: List<OcrBlock>): OfferCard? {
        val fareHit = extractFare(blocks) ?: return null
        val trip = extractTripLeg(blocks) ?: return null
        val pickup = extractPickupLeg(blocks)

        val text = blocks.joinToString(" ") { it.text }
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
            fare = fareHit.value,
            pickupDistanceKm = pickup?.km,
            pickupEtaMin = pickup?.min,
            tripDistanceKm = trip.km,
            tripDurationMin = trip.min,
            surge = surge,
            paymentType = null,
            raw = mapOf("fare" to "%.2f".format(fareHit.value)),
            contentBounds = unionBounds(fareHit.bounds, trip.bounds, pickup?.bounds),
        )
    }

    /**
     * Whether the frame still carries the offer-card *signature* — a (non-bonus) fare plus at least
     * one leg line — even if full parsing failed. Mirrors [DidiOcrParser.hasCardSignature]: the
     * animating map garbles single frames ("1l min", "Vlaje"), so a failed parse WITH the signature
     * means "card still up, frame garbled", not "card gone". The searching/idle screen ("+MXN 10",
     * "1-2 min") fails this — its only currency is a "+" bonus and it has no leg line.
     */
    fun hasCardSignature(blocks: List<OcrBlock>): Boolean =
        extractFare(blocks) != null && blocks.any { legRegex.containsMatchIn(it.text) }

    private data class FareHit(val value: Double, val bounds: OcrBounds)

    /** The real fare (largest non-bonus "MXN" amount) and the block it was read from. */
    private fun extractFare(blocks: List<OcrBlock>): FareHit? =
        blocks
            .asSequence()
            .filterNot {
                it.text.contains("incluido", ignoreCase = true) ||
                    it.text.contains("por inicio", ignoreCase = true)
            }
            .mapNotNull { b ->
                fareRegex.find(b.text)?.groupValues?.get(1)
                    ?.let { cleanNumber(it).toDoubleOrNull() }
                    ?.let { FareHit(it, b.bounds) }
            }
            .maxByOrNull { it.value }

    private data class Leg(val min: Double, val km: Double, val bounds: OcrBounds?)

    /** Per-block first (precise, keeps the block's bounds); joined-text fallback has no single block. */
    private fun extractTripLeg(blocks: List<OcrBlock>): Leg? {
        blocks.firstNotNullOfOrNull { b -> tripLegRegex.find(b.text)?.let { toLeg(it, b.bounds) } }
            ?.let { return it }
        return tripLegRegex.find(blocks.joinToString(" ") { it.text })?.let { toLeg(it, null) }
    }

    /** The pickup leg is the one leg line that is NOT the labeled trip leg. */
    private fun extractPickupLeg(blocks: List<OcrBlock>): Leg? =
        blocks
            .asSequence()
            .filterNot { it.text.contains("Viaje", ignoreCase = true) }
            .firstNotNullOfOrNull { b -> legRegex.find(b.text)?.let { toLeg(it, b.bounds) } }

    private fun toLeg(m: MatchResult, bounds: OcrBounds?): Leg? {
        val min = cleanMinutes(m.groupValues[1]) ?: return null
        val km = cleanNumber(m.groupValues[2]).toDoubleOrNull() ?: return null
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
        val normalized = if (digits.contains('.')) digits.replace(",", "") else digits.replace(',', '.')
        // Recover a dropped decimal point. Uber renders sub-10 km legs with one decimal ("0.4 km"),
        // so a separator-less token with a leading zero ("04") is an OCR decimal-loss, never a real
        // value — no card ever shows "04 km". Reinsert it ("04" → "0.4") so a 0.4 km pickup can't be
        // read as 4.0 km: a 10× error seen live (2026-06-15) that the overlay self-corrected on the
        // next frame, but which the ledger had already frozen into the offer's first-frame record.
        return if (!normalized.contains('.') && normalized.matches(LEADING_ZERO_DECIMAL_LOSS)) {
            "0." + normalized.substring(1)
        } else {
            normalized
        }
    }

    companion object {
        const val UBER_PACKAGE = "com.ubercab.driver"

        /** A leading zero followed by more digits and no separator — the OCR decimal-loss signature. */
        private val LEADING_ZERO_DECIMAL_LOSS = Regex("""0[0-9]+""")
    }
}
