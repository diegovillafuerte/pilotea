package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferContentBounds
import mx.kompara.data.service.ScreenRect
import java.util.Locale
import kotlin.math.abs

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
 * - **Fare**: an anchored currency amount in the first headline area, at least ~1.4× body height
 *   and above the legs. Visual rows and attached fragments exclude rates, bonuses and labels before
 *   considering size. Ambiguous headlines yield no fare: OCR carries no strikethrough metadata.
 *   Bare `$` requires an Uber heading; terminal MXN and legacy MX prefixes remain supported.
 *   S-as-dollar recovery is deliberately disabled. The pipeline masks our chip before parsing.
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

    // Currency is mandatory; only a terminal MXN suffix is allowed. Never recover S as $: geometry
    // cannot establish that a recognized letter was a currency glyph rather than label text.
    private val amountPattern = """((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)[.,][0-9]{2})"""
    private val fareRegex = Regex(
        """(?:(?:MX[N$]\s*\$?|\$)\s*$amountPattern(?:\s+MXN)?|$amountPattern\s+MXN)""",
        RegexOption.IGNORE_CASE,
    )
    private val excludedContext = Regex(
        """/\s*km\b|\bkm\s+est\b|\best\.|\bincluido\b|\bpara\s+inicio\b|\btu\s+(?:meta|m[ií]nimo)\b|\bantes\b|\b(?:precio|tarifa)\s+original\b""",
        RegexOption.IGNORE_CASE,
    )
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

    fun parse(blocks: List<OcrBlock>, cardBounds: OcrBounds? = null, chipRect: ScreenRect? = null): OfferCard? {
        val cardBlocks = cardBlocks(blocks, cardBounds, chipRect)
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
    fun hasCardSignature(blocks: List<OcrBlock>, cardBounds: OcrBounds? = null, chipRect: ScreenRect? = null): Boolean {
        val cardBlocks = cardBlocks(blocks, cardBounds, chipRect)
        val fare = extractFare(cardBlocks)
        val text = cardBlocks.joinToString(" ") { it.text }
        val trip = extractTripLeg(cardBlocks)
        val action = acceptRegex.containsMatchIn(text)
        val host = hostRegex.containsMatchIn(text)
        // A zero wallet pill cannot erase an independently recognized offer with an unread fare.
        if (fare == null && !(action && (trip != null || host)) &&
            cardBlocks.any { standaloneFare(it, host) == 0.0 }) return false
        return (fare != null && (trip != null || cardBlocks.any { legRegex.containsMatchIn(it.text) })) ||
            // Recognition may survive weak/missing glyph geometry; payment never bypasses the
            // headline checks. This preserves incomplete legacy MX offers without inventing a fare.
            (cardBlocks.any { it.text.trim().startsWith("MX", true) && (standaloneFare(it, host) ?: 0.0) > 0 } &&
                cardBlocks.any { legRegex.containsMatchIn(it.text) }) ||
            (trip != null && (action || host)) || (host && action)
    }

    private data class FareHit(val value: Double, val bounds: OcrBounds)

    /** Observed card bounds take precedence; the heading/action band is the OCR-only fallback. */
    private fun cardBlocks(blocks: List<OcrBlock>, bounds: OcrBounds? = null, chip: ScreenRect? = null): List<OcrBlock> {
        if (KomparaUiGuard.isOwnUi(blocks.joinToString(" ") { it.text })) return emptyList()
        val ordered = ChipMask.maskOwnChip(blocks, chip)
            .filter { bounds == null || (bounds.valid && bounds.contains(it.bounds)) }
            .sortedWith(compareBy<OcrBlock> { it.bounds.top }.thenBy { it.bounds.left })
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
        val match = fareRegex.matchEntire(text) ?: return null
        return match.groupValues.drop(1).first { it.isNotEmpty() }.let(::cleanNumber).toDoubleOrNull()
    }

    private val OcrBounds.height: Int get() = bottom - top
    private val OcrBounds.valid: Boolean get() = right > left && bottom > top
    private fun OcrBounds.contains(other: OcrBounds): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    private fun isRouteLine(block: OcrBlock): Boolean =
        block.text.contains("min", true) && block.text.contains("(") &&
            distanceRegex.containsMatchIn(block.text) && !block.text.contains("•") &&
            !excludedContext.containsMatchIn(block.text)

    // Significant overlap avoids the tall close-button X joining the heading to the fare. Requiring
    // agreement with every row member prevents a chain of fragments from joining independent rows.
    private fun sameRow(a: OcrBounds, b: OcrBounds): Boolean =
        minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) >= minOf(a.height, b.height) * 0.5 ||
            abs((a.top + a.bottom) / 2.0 - (b.top + b.bottom) / 2.0) <= minOf(a.height, b.height) * 0.25

    private fun visualRows(blocks: List<OcrBlock>): List<List<OcrBlock>> {
        val rows = mutableListOf<MutableList<OcrBlock>>()
        for (block in blocks.filter { it.bounds.valid }) {
            val row = rows.firstOrNull { row -> row.all { sameRow(it.bounds, block.bounds) } }
            if (row == null) rows += mutableListOf(block) else row += block
        }
        return rows.map { it.sortedBy { block -> block.bounds.left } }
    }

    private fun medianHeight(blocks: List<OcrBlock>): Double? {
        val heights = blocks.map { it.bounds.height }.sorted()
        if (heights.isEmpty()) return null
        return (heights[(heights.size - 1) / 2] + heights[heights.size / 2]) / 2.0
    }

    /** Context and headline evidence are prerequisites, never tie-breakers for arbitrary amounts. */
    private fun extractFare(blocks: List<OcrBlock>): FareHit? {
        val hasHost = blocks.any { hostRegex.containsMatchIn(it.text) }
        val positioned = blocks.filter { it.bounds.valid }
        val firstLeg = positioned.filter(::isRouteLine).minOfOrNull { it.bounds.top } ?: return null
        val body = positioned.filterNot {
            hostRegex.containsMatchIn(it.text) || acceptRegex.containsMatchIn(it.text) ||
                it.text.trim().length <= 1 || it.text.equals("Exclusivo", true) ||
                fareRegex.matches(it.text.trim())
        }
        val bodyHeight = medianHeight(body) ?: return null
        // Without observed card edges, don't let an amount expand the card's horizontal envelope.
        // Legacy hostless frames have only indented route anchors; allow two body glyphs of inset.
        val anchors = positioned.filterNot { fareRegex.matches(it.text.trim()) }
        if (anchors.isEmpty()) return null
        val inset = if (hasHost) 0 else (bodyHeight * 2).toInt()
        val left = anchors.minOf { it.bounds.left } - inset
        val right = anchors.maxOf { it.bounds.right } + inset
        val headingBottom = positioned.filter { hostRegex.containsMatchIn(it.text) }
            .maxOfOrNull { it.bounds.bottom }
        // A fare belongs before the first body/metadata row, not somewhere between bonuses and legs.
        val headlineEnd = body.filter { headingBottom == null || it.bounds.top >= headingBottom }
            .minOfOrNull { it.bounds.top } ?: firstLeg
        val rows = visualRows(positioned)
        val candidates = positioned.mapNotNull { block ->
            val value = standaloneFare(block, hasHost)?.takeIf { it > 0 && it.isFinite() } ?: return@mapNotNull null
            val b = block.bounds
            if (b.left < left || b.right > right || b.bottom > firstLeg || b.bottom > headlineEnd) return@mapNotNull null
            val row = rows.first { block in it }
            // Wrapped labels immediately above/below and touching fragments are context, but a
            // separate body row (e.g. S25's summary 29px below the headline) is not its suffix.
            val attached = positioned.filter { other ->
                val o = other.bounds
                val gap = maxOf(b.top - o.bottom, o.top - b.bottom, 0)
                val horizontalGap = maxOf(b.left - o.right, o.left - b.right, 0)
                other !in row && gap <= bodyHeight * 0.25 && horizontalGap <= bodyHeight * 0.5
            }
            val immediatelyLeft = positioned.filter {
                it.bounds.right <= b.left && minOf(it.bounds.bottom, b.bottom) > maxOf(it.bounds.top, b.top)
            }.maxByOrNull { it.bounds.right }
            val context = (row + attached).joinToString(" ") { it.text.trim() }
            if (excludedContext.containsMatchIn(context) ||
                immediatelyLeft?.text?.trimStart()?.startsWith("+") == true ||
                row.any { it.text.trimStart().startsWith("+") } ||
                attached.any { it.text.trimStart().startsWith("+") }) return@mapNotNull null
            FareHit(value, b)
        }
        // A strike is not represented by ML Kit's text/bounds. If two clean headlines survive, no
        // height or reading-order guess can prove which is current driver payment: withhold both.
        val candidate = candidates.singleOrNull() ?: return null
        // Check ambiguity before height: a smaller current amount beside a tall original price is
        // still a second price. Integer rounding tolerates subpixel OCR measurement of the 1.4 ratio.
        return candidate.takeIf { it.bounds.height > bodyHeight && it.bounds.height >= (bodyHeight * 1.4).toInt() }
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
