package mx.kompara.parsers.model

import kotlin.math.roundToInt

/** A capture-lane-neutral text box; no Android or OCR types cross this boundary. */
data class HostTextBounds(val text: String, val bounds: HostRect, val viewId: String? = null)

/**
 * Derives a safe card envelope from the offer text the reader already sees. A measured card node
 * takes precedence; otherwise the envelope includes the fare/legs, nearby offer heading and buttons,
 * with explicit padding above the text. It does not infer a card top from a screen percentage.
 * Unknown map markers remain unknown; the caller supplies every marker it can actually observe.
 */
object HostGeometryDeriver {
    const val CARD_TEXT_PADDING_DP = 16
    private const val HEADING_DISTANCE_DP = 96
    private val fare = Regex("""(?:MX[N$]|\$)\s*\$?\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)
    private val leg = Regex("""(?:\d[\d.,]*\s*(?:km|min|m)\b|\bviaje\s*:)""", RegexOption.IGNORE_CASE)
    private val accept = Regex("""\baceptar\b""", RegexOption.IGNORE_CASE)
    private val button = Regex("""\b(?:aceptar|rechazar)\b""", RegexOption.IGNORE_CASE)
    private val heading = Regex("""\b(?:uber\w*|didi|exclusivo|reserva|programado|radar de solicitud|pon tu precio)\b""", RegexOption.IGNORE_CASE)
    private val chrome = Regex("""\b(?:en l[ií]nea|buscando viajes|men[uú]|buscar|gira|gire|dobla)\b""", RegexOption.IGNORE_CASE)

    fun offer(
        packageName: String,
        capturedAtMs: Long,
        displayBounds: HostRect,
        density: Float,
        textBounds: List<HostTextBounds>,
        source: HostGeometrySource,
        contentBounds: OfferContentBounds? = null,
        topChrome: HostRect? = null,
        observedCardBounds: HostRect? = null,
        protectedControls: List<HostRect> = emptyList(),
        markers: List<MapMarker> = emptyList(),
        navigation: Boolean = false,
        recognizedOffer: Boolean = false,
    ): HostGeometry? {
        if (packageName !in HostGeometryAdapter.SUPPORTED_HOSTS || !displayBounds.valid ||
            !density.isFinite() || density <= 0f) return null
        val boxes = textBounds.filter { displayBounds.contains(it.bounds) }
        val measuredCard = observedCardBounds?.takeIf(displayBounds::contains)
        val content = contentBounds?.let { HostRect(it.left, it.top, it.right, it.bottom) }
            ?.takeIf(displayBounds::contains)
        val fares = boxes.filter { fareValue(it.text) != null }
        val accepts = boxes.filter { accept.containsMatchIn(it.text) }
        val anchors = if (content != null) {
            // DiDi's amount extractor prefers "Aceptar $X". Its text union can start at the
            // pickup row, below the large fare; recover that matching fare instead of overlapping it.
            val buttonAmounts = accepts.filter { content.contains(it.bounds) }.mapNotNull { fareValue(it.text) }
            // The two OCR amounts may disagree by a digit. Geometry still protects the nearest
            // standalone fare above pickup; this never changes the parser's selected money value.
            val upperFare = if (buttonAmounts.isNotEmpty()) fares.filter {
                it.bounds.bottom <= content.top && fare.matches(it.text.trim())
            }.maxByOrNull { it.bounds.bottom } else null
            listOf(content) + fares.filter { fareValue(it.text) in buttonAmounts }.map { it.bounds } +
                listOfNotNull(upperFare?.bounds)
        } else {
            val legs = boxes.filter { leg.containsMatchIn(it.text) }
            // Recognition belongs to the parser, but absent card evidence must never invent a band.
            if (!recognizedOffer && measuredCard == null &&
                (legs.isEmpty() || fares.isEmpty() && accepts.isEmpty())) return null
            val unreadableHeading = if (recognizedOffer && fares.isEmpty() && legs.isEmpty())
                boxes.filter { heading.containsMatchIn(it.text) } else emptyList()
            (fares + legs + accepts + unreadableHeading).map { it.bounds }
        }
        if (measuredCard == null && anchors.isEmpty()) return null
        val firstText = anchors.minOfOrNull { it.top }
        val headingDistance = (HEADING_DISTANCE_DP * density).roundToInt()
        val headers = if (firstText != null) boxes.filter {
            heading.containsMatchIn(it.text) && it.bounds.bottom <= firstText &&
                firstText - it.bounds.bottom <= headingDistance
        } else emptyList()
        val currentContent = anchors + headers.map { it.bounds } + accepts.map { it.bounds }
        val card = if (measuredCard != null && currentContent.all(measuredCard::contains)) measuredCard else run {
            // Node evidence may precede this OCR frame by one polling interval. A shorter old card
            // cannot overrule the newly visible fare/header/legs above it when a tall offer arrives.
            val earliest = requireNotNull(firstText)
            val textTop = minOf(earliest, headers.minOfOrNull { it.bounds.top } ?: earliest)
            val paddedTop = textTop - (CARD_TEXT_PADDING_DP * density).roundToInt()
            val cardTop = maxOf(displayBounds.top, minOf(measuredCard?.top ?: paddedTop, paddedTop))
            // Width and foot cover the whole sheet; no chip may slip onto an unread part of it.
            HostRect(displayBounds.left, cardTop, displayBounds.right, displayBounds.bottom)
        }
        val measuredChrome = topChrome?.takeIf(displayBounds::contains)
        val chromeBottom = boxes.filter {
            it.bounds.bottom <= card.top && chrome.containsMatchIn(it.text)
        }.maxOfOrNull { it.bounds.bottom }
        val top = HostRect(displayBounds.left, displayBounds.top, displayBounds.right,
            maxOf(measuredChrome?.bottom ?: displayBounds.top + 1, chromeBottom ?: displayBounds.top + 1))
        val controls = (protectedControls + boxes.filter { button.containsMatchIn(it.text) }.map { it.bounds })
            .mapNotNull { it.intersection(displayBounds) }.distinct()
        return HostGeometry(packageName, "derived-${source.name.lowercase()}", capturedAtMs,
            displayBounds, orientation(displayBounds), top, card, controls,
            clipMarkers(markers, displayBounds), navigation, source)
    }

    /** A fresh host viewport can place the stopped tab without a stale or imaginary offer. */
    fun idleHost(
        packageName: String,
        capturedAtMs: Long,
        displayBounds: HostRect,
        topChrome: HostRect,
        protectedControls: List<HostRect> = emptyList(),
        markers: List<MapMarker> = emptyList(),
        navigation: Boolean = false,
    ): HostGeometry? {
        if (packageName !in HostGeometryAdapter.SUPPORTED_HOSTS ||
            !displayBounds.contains(topChrome)) return null
        return HostGeometry(packageName, "idle-host", capturedAtMs, displayBounds, orientation(displayBounds),
            topChrome, HostRect(displayBounds.left, displayBounds.bottom - 1, displayBounds.right, displayBounds.bottom),
            protectedControls.mapNotNull { it.intersection(displayBounds) }, clipMarkers(markers, displayBounds),
            navigation, HostGeometrySource.IDLE_HOST, isIdleHost = true)
    }

    private fun orientation(bounds: HostRect) =
        if (bounds.width > bounds.height) HostOrientation.LANDSCAPE else HostOrientation.PORTRAIT

    private fun clipMarkers(markers: List<MapMarker>, display: HostRect) = markers.mapNotNull { marker ->
        marker.outerBounds.intersection(display)?.let { marker.copy(outerBounds = it) }
    }

    private fun fareValue(text: String): Double? {
        if (text.trimStart().startsWith("+") || text.contains("/km", true) || text.contains("/h", true)) return null
        val amount = fare.find(text)?.groupValues?.get(1) ?: return null
        val normalized = if ('.' in amount) amount.replace(",", "") else amount.replace(',', '.')
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 }
    }
}
