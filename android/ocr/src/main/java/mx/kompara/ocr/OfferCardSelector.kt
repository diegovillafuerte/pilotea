package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.HostGeometryAdapter
import mx.kompara.parsers.model.HostLayoutObservation
import mx.kompara.parsers.model.HostGeometryDeriver
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.HostTextBounds
import mx.kompara.parsers.model.HostOcrGeometryEvidence
import mx.kompara.parsers.model.OfferActionLabel
import mx.kompara.capture.HostWindowEvidence
import mx.kompara.data.service.ScreenRect

/** The OS foreground package selects exactly one parser. OCR never guesses or changes hosts. */
internal fun selectOfferCard(
    uber: UberOcrParser,
    didi: DidiOcrParser,
    blocks: List<OcrBlock>,
    capturedAtMs: Long = 0,
    observation: HostLayoutObservation? = null,
    geometryAdapter: HostGeometryAdapter = HostGeometryAdapter(),
    displayBounds: HostRect? = null,
    density: Float = 1f,
    accessibility: HostWindowEvidence? = null,
    appearance: HostAppearanceSamples? = null,
    chipRect: ScreenRect? = null,
    foregroundPackage: String? = mx.kompara.data.service.ScreenReaderState.freshForegroundHost(
        android.os.SystemClock.elapsedRealtime(), 1_500),
): OfferCard? {
    // Validate independently observed card edges before fare selection, so a large map label
    // outside the card cannot become payment. Derived OCR geometry remains the fallback below.
    val uberBounds = geometryAdapter.adapt(UberOcrParser.UBER_PACKAGE, capturedAtMs, observation)?.offerCard
        ?: accessibility?.takeIf {
            it.packageName == UberOcrParser.UBER_PACKAGE && it.displayBounds == displayBounds &&
                capturedAtMs - it.capturedAtMs in 0..1_000
        }?.currentUberCardBounds(blocks, capturedAtMs)
    val fareBounds = uberBounds?.let { OcrBounds(it.left, it.top, it.right, it.bottom) }
    val card = when (foregroundPackage) {
        UberOcrParser.UBER_PACKAGE -> uber.parse(blocks, cardBounds = fareBounds, hostConfirmed = true)
        DidiOcrParser.DIDI_PACKAGE -> didi.parse(blocks)
        else -> null
    } ?: return null
    val nodes = accessibility?.takeIf {
        it.packageName == card.platform && it.displayBounds == displayBounds &&
            capturedAtMs - it.capturedAtMs in 0..1_000
    }
    val derived = displayBounds?.let { display ->
        HostGeometryDeriver.offer(card.platform, capturedAtMs, display, density,
            blocks.map { HostTextBounds(it.text, HostRect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom)) } +
                nodes?.textBounds.orEmpty(),
            source = if (nodes != null) HostGeometrySource.OCR_AND_ACCESSIBILITY else HostGeometrySource.OCR_TEXT_BOUNDS,
            contentBounds = card.contentBounds, topChrome = nodes?.topChrome,
            observedCardBounds = nodes?.observedCardBounds,
            protectedControls = nodes?.protectedControls.orEmpty(), markers = nodes?.markers.orEmpty(),
            navigation = nodes?.navigation ?: false, recognizedOffer = true,
            fareBounds = card.fareBounds?.let { HostRect(it.left, it.top, it.right, it.bottom) })
    }
    val observed = geometryAdapter.adapt(card.platform, capturedAtMs, observation)
    val geometry = observed ?: derived
    // The modern card's associated route rows are route anchors even when OCR garbled a unit or the
    // minutes (knm, nmin, dlZ): placement protects every row the parser read the route from.
    val modernRows = listOfNotNull(card.validation?.pickupKm?.bounds, card.validation?.tripKm?.bounds)
        .map { HostRect(it.left, it.top, it.right, it.bottom) }
    val routes = (blocks.filter { uberRoute.containsMatchIn(it.text) }.map { it.hostRect() } + modernRows)
        .distinct().sortedBy { it.top }
    val firstRoute = routes.firstOrNull()?.top
    // Payment can remain perfectly stable while a slide-in heading/close is still missing.
    // Only upper-card semantics or independently measured controls can validate that boundary.
    // Node text remains observable even when our chip obscures the same pixels from OCR.
    val upper = (blocks.map { HostTextBounds(it.text, it.hostRect()) } + nodes?.textBounds.orEmpty())
        .filter { firstRoute != null && it.bounds.bottom <= firstRoute &&
            (offerUpperAnchor.containsMatchIn(it.text) || upperControl.matches(it.text.trim())) }
        .map { it.bounds }.distinct()
    return card.copy(hostGeometry = geometry?.let { g ->
        g.copy(appearance = appearance?.appearance(g) ?: g.appearance,
            // Keep an authoritative host-layout observation intact; only the OCR fallback needs
            // mask provenance to distinguish observed card edges from inferred whitespace.
            ocrEvidence = if (observed != null) g.ocrEvidence else HostOcrGeometryEvidence(
                chipRect?.let { HostRect(it.left, it.top, it.right, it.bottom).expanded(ChipMask.DEFAULT_MARGIN_PX) },
                upper, routes,
                independentlyMeasuredCard = nodes?.observedCardBounds == g.offerCard))
    })
}

private fun OcrBlock.hostRect() = bounds.let { HostRect(it.left, it.top, it.right, it.bottom) }
private val offerUpperAnchor = Regex("""\b(?:Uber\w*|Didi|Exclusivo|Reserva|Programado|Radar de solicitud|Pon tu precio)\b""", RegexOption.IGNORE_CASE)
private val upperControl = Regex("""(?:x|×|✕|✖|cerrar|close)""", RegexOption.IGNORE_CASE)
private val uberHeading = Regex("""\bUber\w*|Radar de solicitud""", RegexOption.IGNORE_CASE)
private val uberRoute = Regex("""(?:[0-9OlI]+|—|-)\s*min\s*\(\s*[0-9OlI][0-9OlI.,]*\s*km""", RegexOption.IGNORE_CASE)

/**
 * Node polling can still describe the preceding short card when OCR sees the new taller one.
 * Corroborate older edges with the current heading/route/action before clipping any OCR block.
 * Amounts cannot expand measured edges: a stray price outside an otherwise matching card remains
 * excluded. Without a current heading, the older card cannot be identified and OCR is the fallback.
 */
private fun HostWindowEvidence.currentUberCardBounds(blocks: List<OcrBlock>, atMs: Long): HostRect? {
    val measured = observedCardBounds?.takeIf(displayBounds::contains) ?: return null
    if (capturedAtMs == atMs) return measured
    val ordered = blocks.sortedBy { it.bounds.top }
    val heading = ordered.firstOrNull { uberHeading.containsMatchIn(it.text) } ?: return null
    val action = ordered.firstOrNull {
        it.bounds.top >= heading.bounds.top && OfferActionLabel.from(it.text) != null
    }
    val anchors = ordered.filter {
        it == heading || it.bounds.top >= heading.bounds.top &&
            (action == null || it.bounds.bottom <= action.bounds.bottom) &&
            (uberRoute.containsMatchIn(it.text) || OfferActionLabel.from(it.text) != null)
    }
    return measured.takeIf {
        anchors.all { block -> block.bounds.let { b -> measured.contains(HostRect(b.left, b.top, b.right, b.bottom)) } }
    }
}
