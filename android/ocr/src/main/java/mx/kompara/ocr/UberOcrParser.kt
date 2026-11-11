package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferContentBounds
import mx.kompara.parsers.model.OfferActionLabel
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
 * - **Current card (0.2.15)**: a product heading with a TOTAL row ("27 min • 12.6 km • $8.87/km
 *   est.") is read by geometry first (heading -> headline -> TOTAL -> two route rows in one card
 *   band), then validated by the card's own arithmetic ([UberCardChecksum]); the result carries
 *   per-field provenance and a grade on [OfferCard.validation]. Without a TOTAL row the legacy rules
 *   below apply unchanged.
 * - **Fare**: an anchored currency amount in the first headline area, at least ~1.4× body height
 *   and above the legs. Visual rows and attached fragments exclude rates, bonuses and labels before
 *   considering size. Ambiguous headlines yield no fare: OCR carries no strikethrough metadata.
 *   Bare `$` requires an Uber heading; terminal MXN and legacy MX prefixes remain supported.
 *   S-as-dollar recovery is deliberately disabled. The pipeline masks our chip before parsing.
 *   What each path accepts as an amount: on the current card only [UberCardChecksum.money] reads the
 *   headline and rate digits — two-decimal syntax, or (WP-AC) a separatorless amount, which Uber prints for
 *   whole pesos (`$109`, `$11/km`) and which with three or more digits may instead have lost its separator
 *   to OCR (`$17756`); only the card's money identity chooses, and only it establishes any reading without
 *   decimals (a `$10` may be a truncated `$109`). The legacy path (no TOTAL row) accepts only
 *   two-decimal amounts: it has no arithmetic to choose a reading with, so a whole-peso fare stays unread.
 * - **Pickup / trip**: the legacy pickup plus `Viaje:` pair, or two standalone `N min (X.X km)`
 *   lines in reading order on the current Uber card. The summary above them is not a route leg.
 * - **Durations (0.2.16)**: a duration slot is identified whole and decoded once — `1 h 18 min`, read
 *   by ML Kit as `lh 18`, `4h46`, `lh5`, is 60·H + M minutes on both paths; a failed hour stays unread
 *   and never falls back to its trailing minute number. On the current card an hour-only `1 h` (read
 *   `lh78 km`) is 60 where it is structurally anchored — the TOTAL row start directly before its
 *   distance, or a whole route-row duration directly before `(X km)` — but ambiguous (lost minutes read
 *   the same): only an evaluated minute identity publishes it, and it is never a recovery operand; the
 *   legacy path keeps hour-only unread (it has no checksum).
 * - **Variant/surge**: keyword presence ("Reserva", "paradas", "Tarifa dinámica", "Exclusivo",
 *   "Radar de solicitud de viaje").
 *
 * Tuned against 874 real OCR frames across 12 live offers (2026-06-15). OCR garble handled: minute and
 * distance digits substitute O→0 / I,L→1; decimal separator is `.` or `,`. Offer recognition is
 * independent of extracting the amount and times: a readable card with missing fields still yields
 * the grey Sin lectura / Falta tiempo state. A known route structure supplies estimated driver
 * payment; a recognized offer with an unknown structure keeps its payment basis unconfirmed.
 */
class UberOcrParser internal constructor(
    /** False runs only the legacy labeled-route path (0.2.14's extractor); tests pin it as a baseline. */
    private val modernTotals: Boolean,
) {
    constructor() : this(modernTotals = true)

    // Currency is mandatory; only a terminal MXN suffix is allowed. Never recover S as $: geometry
    // cannot establish that a recognized letter was a currency glyph rather than label text.
    private val amountPattern = """((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)[.,][0-9]{2})"""
    private val fareRegex = Regex(
        """(?:(?:MX[N$]\s*\$?|\$)\s*$amountPattern(?:\s+MXN)?|$amountPattern\s+MXN)""",
        RegexOption.IGNORE_CASE,
    )
    private val amountOnlyRegex = Regex(amountPattern)
    private val currencyFragmentRegex = Regex("""MXN|MX\$|\$""", RegexOption.IGNORE_CASE)
    private val excludedContext = Regex(
        """/\s*km\b|\bkm\s+est\b|\best\.|\bincluido\b|\bpara\s+inicio\b|\btu\s+(?:meta|m[ií]nimo)\b|\bantes\b|\b(?:precio|tarifa)\s+original\b""",
        RegexOption.IGNORE_CASE,
    )
    // A whole hour duration "1 h 18" (0.2.16): structural alternatives match it before a bare minute
    // count, so no suffix of it ("18 min") is ever the match. Only [UberCardChecksum.hours] decodes it.
    private val hourDurationPattern = """[0-9lLI|Oo]{1,2}\s*[hH]\s*[0-9lLI|Oo]{1,2}"""
    // A distance/time leg: "N min (X.X km)". Minute and distance chars allow the O/0, l/1, I/1 OCR
    // confusions; cleaned in [cleanNumber].
    private val legRegex =
        Regex("""((?:$hourDurationPattern)|[0-9OlI]+)\s*min\s*\(\s*([0-9OlI][0-9OlI.,]*)\s*km""", RegexOption.IGNORE_CASE)
    // The legacy trip leg, distinguished from the pickup leg by its "Viaje:" label; the first group is
    // the whole duration right after the label.
    private val tripLegRegex = Regex("""Viaje\s*:?\s*((?:$hourDurationPattern)|[0-9OlI]+)\s*min\s*\(\s*([0-9OlI][0-9OlI.,]*)\s*km""",
        RegexOption.IGNORE_CASE)
    private val unlabeledLegRegex =
        Regex("""(?:$hourDurationPattern|[0-9OlI]+|—|-)\s*min\s*\(\s*[0-9OlI][0-9OlI.,]*\s*km\s*\)""", RegexOption.IGNORE_CASE)
    private val viajeLabel = Regex("""Viaje\s*:?\s*""", RegexOption.IGNORE_CASE)
    private val minuteUnit = Regex("""min\b""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")

    private val distanceRegex = Regex("""([0-9OlI][0-9OlI.,]*)\s*km\b""", RegexOption.IGNORE_CASE)
    private val minutesRegex = Regex("""([0-9OlI]+)\s*min\b""", RegexOption.IGNORE_CASE)
    private val hostRegex = Regex("""\bUber\w*|Radar de solicitud""", RegexOption.IGNORE_CASE)

    // Modern card (0.2.15). Slots are found by unit/currency syntax with loose tokens; only
    // [UberCardChecksum] turns a token into a number. The floating Radar pill is never a product heading.
    private val productRegex = Regex("""\bUber\w*""", RegexOption.IGNORE_CASE)
    private val radarPillRegex = Regex("""Radar de solicitud""", RegexOption.IGNORE_CASE)
    // TOTAL row "27 min • 12.6 km • $8.87/km est.": minutes -> km -> currency rate per km, in order,
    // through missing bullets, glued tokens and a dash before the currency (a separator, never a sign).
    // Anchored at the row start (0.2.16): the whole duration slot ("lh 23", "5h4") is captured first and
    // decoded second, so a failed hour never exposes its minutes ("lh 23 min" is never TOTAL 23). The
    // only leading text allowed is the observed `cid ` (old corpus G6[0]).
    private val totalRegex =
        Regex("""^\s*(?:cid\s+)?([^()\r\n]{1,24}?)\s*(?i:min)[\s•·.\-]*(\S{1,7}?)\s*(?i:km)[\s•·.\-]*\$\s*(\S{1,8}?)\s*/\s*(?i:km)""")
    // An hour-only TOTAL "1 h • 7.8 km • $15.58/km est." (Uber prints exactly 60 minutes so; ML Kit reads
    // `lh78 km`, `lh•78 km-`). Tried only when the minute envelope fails: the hour token must open the
    // row (after the same `cid ` allowance) and be followed, through separators only, directly by the
    // distance token, `km` and the rate — a glued token splits only as hour + distance.
    private val hourOnlyTotalRegex =
        Regex("""^\s*(?:cid\s+)?([0-9lLI|Oo]{1,2}\s*[hH])[\s•·.\-]*(\S{1,7}?)\s*(?i:km)[\s•·.\-]*\$\s*(\S{1,8}?)\s*/\s*(?i:km)""")
    // A unit-anchored whole route row; `nmin`/`knm`/`kmn` are the observed unit misreads, recognized
    // only in these unit positions. The duration capture keeps any icon prefix: see [routeIconPrefix].
    private val routeRowRegex =
        Regex("""([^()\r\n]{1,24}?)\s*((?i:n?min))\s*\(\s*(\S{1,6}?)\s*((?i:knm|kmn|km))\s*\)""")
    // A whole route row without a minute unit, tried only when [routeRowRegex] fails: after at most one
    // icon prefix its entire duration must be an hour-only token (`1 h (12.3 km)`), else it is no row.
    private val hourOnlyRouteRowRegex = Regex("""([^()\r\n]{1,24}?)\s*\(\s*(\S{1,6}?)\s*((?i:knm|kmn|km))\s*\)""")
    // The observed route-icon prefixes, space-separated: at most one is removed, then the entire rest
    // is the duration (`h lh5` -> `lh5`; `lh 18` keeps its l, the hour digit; glued `h6` stays unread).
    private val routeIconPrefix = Regex("""^(?:(?:e |ad )?h |d |n |t |H )""")
    private val moneyToken = """([0-9lLI|Oo][0-9lLI|Oo.,]*)"""
    private val modernMoneyRegex = Regex("""(?:(?i:MX[N$])\s*\$?|\$)\s*$moneyToken(?:\s+(?i:MXN))?|$moneyToken\s+(?i:MXN)""")
    /** Deterministic for any input order, identical rectangles included. */
    private val blockOrder = compareBy<OcrBlock>({ it.bounds.top }, { it.bounds.left }, { it.bounds.bottom },
        { it.bounds.right }, { it.text })

    fun parse(blocks: List<OcrBlock>, cardBounds: OcrBounds? = null, chipRect: ScreenRect? = null,
        hostConfirmed: Boolean = false): OfferCard? {
        val cardBlocks = cardBlocks(blocks, cardBounds, chipRect)
        if (!hasCardSignature(cardBlocks, cardBounds, hostConfirmed = hostConfirmed)) return null
        val evidence = instanceEvidence(cardBlocks, ChipMask.maskOwnChip(blocks, chipRect))
        // A recognized TOTAL row selects the modern path; it never falls back to the legacy
        // extractor, which could refill a contradictory frame.
        val modern = if (modernTotals) modernCard(cardBlocks, evidence.selectionCertain) else null
        val fareHit = if (modern == null) extractFare(cardBlocks, cardBounds, hostConfirmed) else null
        val trip = if (modern == null) extractTripLeg(cardBlocks, hostConfirmed) else null
        val pickup = if (modern == null) extractPickupLeg(cardBlocks, hostConfirmed) else null

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

        if (modern != null) {
            val result = modern.result
            return OfferCard(
                platform = UBER_PACKAGE,
                variant = variant,
                fare = result.fare,
                captureSource = "OCR",
                // A recognized TOTAL row is the known current card: its amounts estimate driver payment.
                paymentBasis = if (result.fare != null || result.tripKm != null) "DRIVER_PAYMENT" else "UNCONFIRMED",
                pickupDistanceKm = result.pickupKm,
                pickupEtaMin = result.pickupMin,
                tripDistanceKm = result.tripKm,
                tripDurationMin = result.tripMin,
                surge = surge,
                paymentType = null,
                raw = result.fare?.let { mapOf("fare" to String.format(Locale.US, "%.2f", it)) } ?: emptyMap(),
                // Rows and visible money protect their pixels even when a value is withheld.
                contentBounds = unionBounds(modern.protectedFare, *modern.rows.toTypedArray()),
                fareBounds = modern.protectedFare?.let { OfferContentBounds(it.left, it.top, it.right, it.bottom) },
                instanceEvidence = evidence.copy(routeWellFormed = result.routeWellFormed),
                validation = result.evidence,
            )
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
            fareBounds = fareHit?.bounds?.let { OfferContentBounds(it.left, it.top, it.right, it.bottom) },
            instanceEvidence = evidence.copy(
                routeWellFormed = cardBlocks.sumOf { legRegex.findAll(it.text).count() } >= 2),
        )
    }

    /** [protectedFare]: the unique headline, or the union of competing prices whose value is withheld. */
    private class ModernCard(val protectedFare: OcrBounds?, val rows: List<OcrBounds>, val result: UberCardChecksum.Result)

    /**
     * The current card read by geometry before arithmetic: product heading -> headline -> TOTAL ->
     * two route rows, inside one card band (never across another product heading or action label).
     * The headline is bounded by its own heading and TOTAL row and must overlap the TOTAL's content
     * column, so a side fragment elsewhere on screen can never end it (39 of 42 real fare misses).
     * Null without a product heading and TOTAL row: legacy formats keep the labeled-route path.
     */
    private fun modernCard(blocks: List<OcrBlock>, selectionCertain: Boolean): ModernCard? {
        val ordered = blocks.filter { it.bounds.valid }.sortedWith(blockOrder)
        fun product(b: OcrBlock) = productRegex.containsMatchIn(b.text) && !radarPillRegex.containsMatchIn(b.text)
        val heading = ordered.firstOrNull(::product) ?: return null
        val below = ordered.filter { it !== heading && it.bounds.top >= heading.bounds.bottom }
        val boundary = below.firstOrNull { product(it) || OfferActionLabel.from(it.text) != null }?.bounds?.top
        fun inBand(b: OcrBlock) = boundary == null || b.bounds.bottom <= boundary
        val totals = below.filter(::inBand).mapNotNull { b -> totalRow(b.text)?.let { b to it } }
        val (total, totalRow) = totals.firstOrNull() ?: return null
        val routeRows = below.filter { inBand(it) && it.bounds.top >= total.bounds.bottom }
            .mapNotNull { b -> routeRow(b.text.trim())?.let { b to it } }
        val legs = routeRows.takeIf { it.size == 2 && it[0].first.bounds.bottom <= it[1].first.bounds.top }

        // The headline: a unique money token between this heading and this TOTAL, in its column, at
        // least 1.4x the TOTAL row (the structural body line beside it; real ratios 2.05-2.61).
        val bodyHeight = total.bounds.height.toDouble()
        val visualRows = visualRows(ordered)
        val amounts = currencyAmounts(below.filter { it.bounds.bottom <= total.bounds.top }, modernMoneyRegex)
        val candidates = amounts?.filter { amount ->
            val b = amount.block.bounds
            // Like the legacy path, a zero amount is no price; an unreadable token still competes. Since WP-AC
            // every separatorless token reads: `$0` is zero (no price), `$5` is 5.00 and competes as before.
            val readings = headlineMoney(amount.block.text)
                ?: amountOnlyRegex.matchEntire(amount.block.text.trim())?.let { UberCardChecksum.money(it.groupValues[1]) }
            b.left < total.bounds.right && b.right > total.bounds.left &&
                !isContext(amount, ordered, visualRows, bodyHeight) &&
                (readings.isNullOrEmpty() || readings.any { it.units > 0 })
        }
        // A shared currency fragment (null) or two prices: the checksum never chooses between them.
        val fareCandidates = candidates?.size ?: 2
        val headline = candidates?.singleOrNull()?.takeIf {
            modernMoneyRegex.matches(it.block.text) && it.amount.bounds.height > bodyHeight &&
                it.amount.bounds.height >= (bodyHeight * 1.4).toInt()
        }
        // Competing prices withhold the value, never the pixels (spec D.3): their union stays protected.
        val competing = candidates?.takeIf { it.size > 1 }?.map { it.block.bounds }
            ?: if (amounts == null) below.filter {
                it.bounds.bottom <= total.bounds.top && it.bounds.left < total.bounds.right && it.bounds.right > total.bounds.left &&
                    (modernMoneyRegex.matches(it.text.trim()) || amountOnlyRegex.matches(it.text.trim()) ||
                        currencyFragmentRegex.matches(it.text.trim()))
            }.map { it.bounds } else emptyList()
        val protectedFare = headline?.block?.bounds ?: competing.takeIf { it.isNotEmpty() }?.let { b ->
            OcrBounds(b.minOf { it.left }, b.minOf { it.top }, b.maxOf { it.right }, b.maxOf { it.bottom })
        }
        fun bounds(b: OcrBounds) = OfferContentBounds(b.left, b.top, b.right, b.bottom)
        fun row(index: Int) = legs?.get(index)?.second
        val pickupRow = legs?.get(0)?.first?.bounds?.let(::bounds)
        val tripRow = legs?.get(1)?.first?.bounds?.let(::bounds)
        val totalBounds = bounds(total.bounds)
        val slots = listOf(
            UberCardChecksum.Slot(headline?.let { headlineMoney(it.block.text) }.orEmpty(), headline?.block?.bounds?.let(::bounds)),
            UberCardChecksum.Slot(UberCardChecksum.money(totalRow.rate), totalBounds),
            UberCardChecksum.Slot(totalRow.minutes, totalBounds),
            UberCardChecksum.Slot(UberCardChecksum.distance(totalRow.distance), totalBounds),
            UberCardChecksum.Slot(row(0)?.minutes.orEmpty(), pickupRow),
            UberCardChecksum.Slot(row(0)?.km.orEmpty(), pickupRow),
            UberCardChecksum.Slot(row(1)?.minutes.orEmpty(), tripRow),
            UberCardChecksum.Slot(row(1)?.km.orEmpty(), tripRow),
        )
        return ModernCard(protectedFare, routeRows.map { it.first.bounds },
            UberCardChecksum.solve(slots, rowsIdentified = legs != null, selectionCertain = selectionCertain,
                fareCandidates = fareCandidates, structuralConflict = totals.size > 1))
    }

    /** A recognized TOTAL row: its decoded duration and its loose distance and rate tokens. */
    private class TotalRow(val minutes: List<UberCardChecksum.Candidate>, val distance: String, val rate: String)

    /**
     * The minute envelope first (an unread duration still makes it a TOTAL row); only without one, the
     * hour-only `1 h` row — and only when its hour decodes and the token after it reads as a distance:
     * an hour followed by an unreadable or unknown token is no TOTAL (`lh5 • 7.8 km` lost its minute
     * unit; it never becomes 60 with the 5 swallowed).
     */
    private fun totalRow(text: String): TotalRow? {
        totalRegex.find(text)?.let { m -> return TotalRow(UberCardChecksum.duration(m.groupValues[1]), m.groupValues[2], m.groupValues[3]) }
        val m = hourOnlyTotalRegex.find(text) ?: return null
        val hours = UberCardChecksum.hourOnly(m.groupValues[1]) ?: return null
        if (UberCardChecksum.distance(m.groupValues[2]).isEmpty()) return null
        return TotalRow(listOf(hours), m.groupValues[2], m.groupValues[3])
    }

    /** A recognized route row's decoded duration and distance. */
    private class RouteRow(val minutes: List<UberCardChecksum.Candidate>, val km: List<UberCardChecksum.Candidate>)

    /**
     * A whole route row. Removing an allowed icon repairs nothing; the whole remaining duration is
     * decoded once. Without a minute unit the row exists only as an hour-only duration directly followed
     * by a readable `(X km)`.
     */
    private fun routeRow(text: String): RouteRow? {
        fun km(token: String, unit: String) = UberCardChecksum.distance(token, unit.equals("knm", true) || unit.equals("kmn", true))
        routeRowRegex.matchEntire(text)?.let { m ->
            val (duration, minuteUnit, distance, unit) = m.destructured
            return RouteRow(UberCardChecksum.duration(routeIconPrefix.replaceFirst(duration, ""), minuteUnit.equals("nmin", true)),
                km(distance, unit))
        }
        val m = hourOnlyRouteRowRegex.matchEntire(text) ?: return null
        val hours = UberCardChecksum.hourOnly(routeIconPrefix.replaceFirst(m.groupValues[1], "")) ?: return null
        return km(m.groupValues[2], m.groupValues[3]).takeIf { it.isNotEmpty() }?.let { RouteRow(listOf(hours), it) }
    }

    /** The headline's money readings: null when the block is not currency-anchored (a competing price). */
    private fun headlineMoney(text: String): List<UberCardChecksum.Candidate>? =
        modernMoneyRegex.matchEntire(text.trim())?.let { m ->
            UberCardChecksum.money(m.groupValues.drop(1).firstOrNull { it.isNotEmpty() })
        }

    /**
     * A card survives missing money/time, but idle bonuses and explicit zero-wallet pills do not.
     * A route leg is required: host chrome plus an action label alone (an idle Trip Radar pill, a
     * post-tap state) must never hold a chip.
     */
    fun hasCardSignature(blocks: List<OcrBlock>, cardBounds: OcrBounds? = null, chipRect: ScreenRect? = null,
        hostConfirmed: Boolean = false): Boolean {
        val cardBlocks = cardBlocks(blocks, cardBounds, chipRect)
        val fare = extractFare(cardBlocks, cardBounds, hostConfirmed)
        val text = cardBlocks.joinToString(" ") { it.text }
        val trip = extractTripLeg(cardBlocks, hostConfirmed)
        val action = cardBlocks.any { OfferActionLabel.from(it.text) != null }
        val host = hostRegex.containsMatchIn(text)
        // A zero wallet pill cannot erase an independently recognized offer with an unread fare.
        if (fare == null && !(action && (trip != null || host)) &&
            cardBlocks.any { standaloneFare(it, host) == 0.0 }) return false
        return (fare != null && (trip != null || cardBlocks.any { legRegex.containsMatchIn(it.text) })) ||
            // Recognition may survive weak/missing glyph geometry; payment never bypasses the
            // headline checks. This preserves incomplete legacy MX offers without inventing a fare.
            (cardBlocks.any { it.text.trim().startsWith("MX", true) && (standaloneFare(it, host) ?: 0.0) > 0 } &&
                cardBlocks.any { legRegex.containsMatchIn(it.text) }) ||
            (trip != null && (action || host)) ||
            (host && action && cardBlocks.any { legRegex.containsMatchIn(it.text) })
    }

    private data class FareHit(
        val value: Double,
        val bounds: OcrBounds,
        val amountHeight: Int,
        val currencyAnchored: Boolean,
    )

    private data class CurrencyAmount(val amount: OcrBlock, val members: List<OcrBlock>) {
        val block = OcrBlock(members.sortedBy { it.bounds.left }.joinToString(" ") { it.text.trim() },
            OcrBounds(members.minOf { it.bounds.left }, members.minOf { it.bounds.top },
                members.maxOf { it.bounds.right }, members.maxOf { it.bounds.bottom }))
    }

    /** Observed card bounds take precedence; the heading/action band is the OCR-only fallback. */
    private fun cardBlocks(blocks: List<OcrBlock>, bounds: OcrBounds? = null, chip: ScreenRect? = null): List<OcrBlock> {
        if (KomparaUiGuard.isOwnUi(blocks.joinToString(" ") { it.text })) return emptyList()
        val ordered = ChipMask.maskOwnChip(blocks, chip)
            .filter { bounds == null || (bounds.valid && bounds.contains(it.bounds)) }
            .sortedWith(compareBy<OcrBlock> { it.bounds.top }.thenBy { it.bounds.left })
        val heading = ordered.firstOrNull { hostRegex.containsMatchIn(it.text) } ?: return ordered
        val action = ordered.firstOrNull {
            it.bounds.top >= heading.bounds.top && OfferActionLabel.from(it.text) != null
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

    /**
     * Join only adjacent, same-row currency glyphs; never merge two amounts or infer a currency.
     * [money] is the currency-anchored amount syntax: the legacy fare grammar, or the modern headline
     * token whose digits only [UberCardChecksum] may read.
     */
    private fun currencyAmounts(blocks: List<OcrBlock>, money: Regex = fareRegex): List<CurrencyAmount>? {
        val amounts = blocks.filter {
            money.matches(it.text.trim()) || amountOnlyRegex.matches(it.text.trim())
        }
        val fragments = blocks.filter { currencyFragmentRegex.matches(it.text.trim()) }
        val owners = mutableMapOf<OcrBlock, OcrBlock>()
        for (fragment in fragments) {
            val matches = amounts.filter { amount ->
                val a = amount.bounds
                val f = fragment.bounds
                val gap = maxOf(a.left - f.right, f.left - a.right, 0)
                sameRow(a, f) && gap <= minOf(a.height, f.height) &&
                    money.matches(listOf(amount, fragment).sortedBy { it.bounds.left }
                        .joinToString(" ") { it.text.trim() }) &&
                    // A label between the glyph and amount is context, not part of a currency.
                    blocks.none { other -> other != amount && other != fragment &&
                        sameRow(a, other.bounds) && other.bounds.left >= minOf(a.right, f.right) &&
                        other.bounds.right <= maxOf(a.left, f.left) && other !in fragments }
            }
            // A shared currency fragment cannot establish which neighboring number is payment.
            if (matches.size > 1) return null
            matches.singleOrNull()?.let { owners[fragment] = it }
        }
        return amounts.map { amount ->
            val token = CurrencyAmount(amount, listOf(amount) + owners.filterValues { it == amount }.keys)
            // A clean decimal without a recovered currency is still competing-price evidence.
            // It can withhold payment, but can never establish a fare by itself.
            if (!money.matches(token.block.text) && !amountOnlyRegex.matches(token.block.text)) return null
            token
        }
    }

    /**
     * Bonus, rate, goal and original-price context, or a "+" prefix in the amount's visual row,
     * attached fragments or immediate left neighbour: such an amount is never payment.
     */
    private fun isContext(amount: CurrencyAmount, positioned: List<OcrBlock>, rows: List<List<OcrBlock>>,
        bodyHeight: Double): Boolean {
        val b = amount.block.bounds
        val row = (rows.first { amount.amount in it } + amount.members).distinct()
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
        return excludedContext.containsMatchIn(context) ||
            immediatelyLeft?.text?.trimStart()?.startsWith("+") == true ||
            row.any { it.text.trimStart().startsWith("+") } ||
            attached.any { it.text.trimStart().startsWith("+") }
    }

    /** Context and headline evidence are prerequisites, never tie-breakers for arbitrary amounts. */
    private fun extractFare(blocks: List<OcrBlock>, cardBounds: OcrBounds? = null, hostConfirmed: Boolean = false): FareHit? {
        val hasHost = hostConfirmed || blocks.any { hostRegex.containsMatchIn(it.text) }
        val positioned = blocks.filter { it.bounds.valid }
        val firstLeg = positioned.filter(::isRouteLine).minOfOrNull { it.bounds.top } ?: return null
        // Currency can be a separate OCR line. Associate it before measuring body text or the
        // headline boundary: a terminal MXN must never cut its own amount out of ambiguity checks.
        val amounts = currencyAmounts(positioned) ?: return null
        val moneyBlocks = amounts.flatMap { it.members }.toSet()
        val body = positioned.filterNot {
            hostRegex.containsMatchIn(it.text) || OfferActionLabel.from(it.text) != null ||
                it.text.trim().length <= 1 || it.text.equals("Exclusivo", true) ||
                it in moneyBlocks || currencyFragmentRegex.matches(it.text.trim())
        }
        val bodyHeight = medianHeight(body) ?: return null
        // Real card edges outrank the text envelope. OCR-only cards can have narrow, indented
        // supporting lines even with an Uber heading; allow two body glyphs around those anchors.
        val anchors = positioned.filterNot { it in moneyBlocks }
        if (anchors.isEmpty()) return null
        val inset = (bodyHeight * 2).toInt()
        val left = cardBounds?.left ?: (anchors.minOf { it.bounds.left } - inset)
        val right = cardBounds?.right ?: (anchors.maxOf { it.bounds.right } + inset)
        val headingBottom = positioned.filter { hostRegex.containsMatchIn(it.text) }
            .maxOfOrNull { it.bounds.bottom }
        // A fare belongs before the first body/metadata row, not somewhere between bonuses and legs.
        val headlineEnd = body.filter { headingBottom == null || it.bounds.top >= headingBottom }
            .minOfOrNull { it.bounds.top } ?: firstLeg
        val rows = visualRows(positioned)
        val candidates = amounts.mapNotNull { amount ->
            val anchoredValue = standaloneFare(amount.block, hasHost)
            val value = (standaloneFare(amount.block, true)
                ?: amountOnlyRegex.matchEntire(amount.block.text)?.groupValues?.get(1)
                    ?.let(::cleanNumber)?.toDoubleOrNull())
                ?.takeIf { it > 0 && it.isFinite() } ?: return@mapNotNull null
            val b = amount.block.bounds
            if (b.bottom > firstLeg || b.bottom > headlineEnd) return@mapNotNull null
            if (isContext(amount, positioned, rows, bodyHeight)) return@mapNotNull null
            FareHit(value, b, amount.amount.bounds.height, anchoredValue != null)
        }
        // A strike is not represented by ML Kit's text/bounds. If two clean headlines survive, no
        // height or reading-order guess can prove which is current driver payment: withhold both.
        val candidate = candidates.singleOrNull() ?: return null
        // Check ambiguity before height: a smaller current amount beside a tall original price is
        // still a second price. Integer rounding tolerates subpixel OCR measurement of the 1.4 ratio.
        return candidate.takeIf {
            it.currencyAnchored && it.bounds.left >= left && it.bounds.right <= right &&
                it.amountHeight > bodyHeight && it.amountHeight >= (bodyHeight * 1.4).toInt()
        }
    }

    private data class Leg(val min: Double?, val km: Double?, val bounds: OcrBounds?)

    /** Per-block first (precise, keeps the block's bounds); joined-text fallback has no single block. */
    private fun extractTripLeg(blocks: List<OcrBlock>, hostConfirmed: Boolean = false): Leg? {
        blocks.firstNotNullOfOrNull { b ->
            if (b.text.contains("Viaje", ignoreCase = true)) partialLeg(b) else null
        }?.let { return it }
        return tripLegRegex.find(blocks.joinToString(" ") { it.text })?.let { toLeg(it, null) }
            ?: unlabeledRouteLegs(blocks, hostConfirmed).getOrNull(1)
    }

    /** Current cards have two standalone legs; legacy cards label the trip leg. */
    private fun extractPickupLeg(blocks: List<OcrBlock>, hostConfirmed: Boolean = false): Leg? =
        unlabeledRouteLegs(blocks, hostConfirmed).firstOrNull() ?: blocks
            .asSequence()
            .filterNot { it.text.contains("Viaje", ignoreCase = true) }
            .filterNot { it.text.contains("•") || it.text.contains("/km", ignoreCase = true) }
            .filter { it.text.contains("min", ignoreCase = true) || it.text.contains("(") }
            .firstNotNullOfOrNull(::partialLeg)

    private fun unlabeledRouteLegs(blocks: List<OcrBlock>, hostConfirmed: Boolean = false): List<Leg> {
        if (!hostConfirmed && blocks.none { hostRegex.containsMatchIn(it.text) }) return emptyList()
        // Exactly two standalone lines are the observed current shape. A garbled legacy "Viaje"
        // label must not be silently promoted into a known trip/payment structure.
        return blocks.filter { unlabeledLegRegex.matches(it.text.trim()) }
            .mapNotNull(::partialLeg).takeIf { it.size == 2 } ?: emptyList()
    }

    private fun partialLeg(block: OcrBlock): Leg? {
        val minutes = legacyMinutes(block.text)
        val km = distanceRegex.find(block.text)?.groupValues?.get(1)
            ?.let { recoverDroppedDistanceDecimal(cleanNumber(it)).toDoubleOrNull() }
        return if (minutes == null && km == null) null else Leg(minutes, km, block.bounds)
    }

    /**
     * The legacy leg's minutes. The duration span is the text immediately before the minute unit: after
     * a `Viaje` label, everything from the label to the unit (the label anchors the slot, so `xh 18`
     * there is unread); without one, only the trailing whitespace-separated tokens made of duration
     * characters, so an unrelated `$243/h` earlier in the block is outside it. An hour marker in the span
     * requires a complete hour duration ("Viaje: 1 h 18 min" is 78), and a span that fails stays unread —
     * never searched for its trailing minutes, and a leading `h` is no icon to strip here. Spans without
     * an hour marker keep the existing first-minutes rule.
     */
    private fun legacyMinutes(text: String): Double? {
        val label = viajeLabel.find(text)
        val start = label?.range?.let { it.last + 1 } ?: 0
        val span = minuteUnit.find(text, start)?.let { unit ->
            val region = text.substring(start, unit.range.first).trim()
            if (label != null) region
            else region.split(whitespace).takeLastWhile { token -> token.isNotEmpty() && token.all { it in DURATION_CHARS } }
                .joinToString(" ")
        }
        if (span != null && UberCardChecksum.hasHourMarker(span)) return cleanMinutes(span)
        return minutesRegex.find(text)?.groupValues?.get(1)?.let(::cleanMinutes)
    }

    private fun toLeg(m: MatchResult, bounds: OcrBounds?): Leg? {
        val min = cleanMinutes(m.groupValues[1]) ?: return null
        // Distances render with one decimal; reinsert a point OCR dropped ("130"→13.0, "L0"→1.0) so a
        // separator-less leg can't read 10× high. This recovery is for the leg, never the fare: the legacy
        // fare grammar (fareRegex) accepts only two-decimal amounts. Uber does print whole fares without
        // decimals (`$109`, WP-AC); only the modern path reads them, through the card's money identity.
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
     * A token with an hour marker ("1 h l", "4h46") is a whole duration (0.2.16): strict 60·H + M via
     * [UberCardChecksum.hours], never the first two digits ("1 h 61" is unread, not 16 or 61).
     */
    private fun cleanMinutes(raw: String): Double? {
        if (UberCardChecksum.hasHourMarker(raw)) return UberCardChecksum.hours(raw.trim())?.units?.toDouble()
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
        /** What a legacy duration token is made of: digits, their OCR confusables and the hour marker. */
        private const val DURATION_CHARS = "0123456789lLI|OohH"
    }
}
