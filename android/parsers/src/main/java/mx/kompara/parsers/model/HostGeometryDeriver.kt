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
    private val close = Regex("""^(?:x|×|✕|✖|cerrar|close)$""", RegexOption.IGNORE_CASE)
    private val reject = Regex("""\brechazar\b""", RegexOption.IGNORE_CASE)
    private val heading = Regex("""\b(?:uber\w*|didi|exclusivo|reserva|programado|radar de solicitud|pon tu precio)\b""", RegexOption.IGNORE_CASE)
    // The floating "Radar de solicitud de viaje N" pill is host chrome above the sheet (it also
    // shows on the idle home as "... 0"). It remains an upper anchor and a soft obstacle, never
    // part of the card envelope: folding it in lowered the safe band to nothing (13:12:30).
    private val radarPill = Regex("""\bradar de solicitud\b""", RegexOption.IGNORE_CASE)
    private val cardHeading = Regex("""\b(?:uber\w*|didi|exclusivo|reserva|programado|pon tu precio)\b""", RegexOption.IGNORE_CASE)
    private val uberHeading = Regex("""\buber\w*\b""", RegexOption.IGNORE_CASE)
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
        fareBounds: HostRect? = null,
    ): HostGeometry? {
        if (packageName !in HostGeometryAdapter.SUPPORTED_HOSTS || !displayBounds.valid ||
            !density.isFinite() || density <= 0f) return null
        val boxes = textBounds.filter { displayBounds.contains(it.bounds) }
        val measuredCard = observedCardBounds?.takeIf(displayBounds::contains)
        val content = contentBounds?.let { HostRect(it.left, it.top, it.right, it.bottom) }
            ?.takeIf(displayBounds::contains)
        val fares = boxes.filter { fareValue(it.text) != null }
        val accepts = boxes.filter { OfferActionLabel.from(it.text) != null }
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
                boxes.filter { cardHeading.containsMatchIn(it.text) } else emptyList()
            (fares + legs + accepts + unreadableHeading).map { it.bounds }
        }
        if (measuredCard == null && anchors.isEmpty()) return null
        val firstText = anchors.minOfOrNull { it.top }
        val headingDistance = (HEADING_DISTANCE_DP * density).roundToInt()
        // On a recognized Uber offer, metadata can separate the heading from the route by much
        // more than 96dp. Losing only the fare must not move the inferred card top down to pickup:
        // that would place our chip over the headline, then mask the fare on subsequent captures.
        // This frame's host heading and action bound the sheet independently of payment extraction.
        val offerHeading = if (recognizedOffer && packageName == "com.ubercab.driver" && firstText != null &&
            accepts.any { it.bounds.top >= firstText }) boxes.filter {
            uberHeading.containsMatchIn(it.text) &&
                it.bounds.bottom <= firstText
        }.maxByOrNull { it.bounds.bottom } else null
        val headers = if (firstText != null) boxes.filter {
            cardHeading.containsMatchIn(it.text) && it.bounds.bottom <= firstText &&
                (firstText - it.bounds.bottom <= headingDistance || it == offerHeading)
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
        val controls = (protectedControls + boxes.filter {
            OfferActionLabel.from(it.text) != null || reject.containsMatchIn(it.text) || close.matches(it.text.trim())
        }.map { it.bounds })
            .mapNotNull { it.intersection(displayBounds) }.distinct()
        val routes = boxes.filter { leg.containsMatchIn(it.text) && fareValue(it.text) == null }
            .map { it.bounds }.sortedBy { it.top }
        val upperLimit = routes.firstOrNull()?.top ?: accepts.minOfOrNull { it.bounds.top }
        val upper = boxes.filter { upperLimit != null && it.bounds.bottom <= upperLimit &&
            (heading.containsMatchIn(it.text) || close.matches(it.text.trim())) }.map { it.bounds }
        // The pill text and its stack count are one floating control; treat it as tappable.
        val pills = boxes.filter { radarPill.containsMatchIn(it.text) }.map { pill ->
            boxes.filter { other -> other == pill || other.text.trim().matches(Regex("""\d{1,2}""")) &&
                minOf(other.bounds.bottom, pill.bounds.bottom) - maxOf(other.bounds.top, pill.bounds.top) >=
                minOf(other.bounds.height, pill.bounds.height) / 2 &&
                other.bounds.left - pill.bounds.right in 0..pill.bounds.height * 2
            }.map { it.bounds }.reduce { a, c -> HostRect(minOf(a.left, c.left), minOf(a.top, c.top),
                maxOf(a.right, c.right), maxOf(a.bottom, c.bottom)) }
        }
        return HostGeometry(packageName, "derived-${source.name.lowercase()}", capturedAtMs,
            displayBounds, orientation(displayBounds), top, card, controls,
            clipMarkers(markers, displayBounds), navigation, source,
            ocrEvidence = HostOcrGeometryEvidence(null, upper, routes, independentlyMeasuredCard = measuredCard == card),
            fareBounds = fareBounds?.takeIf(displayBounds::contains), softObstacles = pills)
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
