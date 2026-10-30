package mx.kompara.ocr

import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.OfferInstanceEvidence
import java.util.Locale

/** Only recognized product/action tokens and bounds leave this extractor; addresses never do. */
internal fun instanceEvidence(selected: List<OcrBlock>, visible: List<OcrBlock>): OfferInstanceEvidence {
    val heading = Regex("""\b(?:Uber\w*|Radar de solicitud|DiDi|Pon Tu Precio)\b""", RegexOption.IGNORE_CASE)
    val product = Regex("""\b(Uber(?:X|XL|Priority|Black|Comfort|Green|Moto|Taxi|Van|Planet)|Uber\s+(?:Priority|Comfort|Black|Green|Moto|Taxi|Van|Planet)|DiDi)\b""", RegexOption.IGNORE_CASE)
    val action = Regex("""\b(Aceptar|Coincidir)\b""", RegexOption.IGNORE_CASE)
    val ordered = selected.sortedBy { it.bounds.top }
    val header = ordered.firstOrNull { heading.containsMatchIn(it.text) }
    val button = ordered.firstOrNull { action.containsMatchIn(it.text) }
    val buttons = visible.filter { action.containsMatchIn(it.text) }.sortedBy { it.bounds.top }
    val bands = buttons.mapNotNull { end ->
        visible.filter { start -> heading.containsMatchIn(start.text) && start.bounds.top < end.bounds.top &&
            buttons.none { it.bounds.top > start.bounds.top && it.bounds.top < end.bounds.top }
        }.minByOrNull { it.bounds.top }?.let { start ->
            HostRect(minOf(start.bounds.left, end.bounds.left), start.bounds.top,
                maxOf(start.bounds.right, end.bounds.right), end.bounds.bottom).takeIf { it.valid }
        }
    }
    val text = selected.joinToString(" ") { it.text }
    val labels = buildSet {
        if (text.contains("Exclusivo", true)) add("exclusive")
        if (text.contains("Radar de solicitud", true)) add("trip_radar")
        if (text.contains("Reserva", true) || text.contains("Programad", true)) add("reservation")
        if (text.contains("paradas", true)) add("multi_stop")
        if (text.contains("dinámica", true) || text.contains("dinamica", true)) add("surge")
        if (text.contains("Pon Tu Precio", true)) add("bid")
    }
    fun OcrBlock.rect() = HostRect(bounds.left, bounds.top, bounds.right, bounds.bottom).takeIf { it.valid }
    return OfferInstanceEvidence(
        product = ordered.firstNotNullOfOrNull { product.find(it.text)?.value }
            ?.uppercase(Locale.ROOT)?.replace(Regex("""\s+"""), ""),
        action = button?.let { action.find(it.text)?.value?.uppercase(Locale.ROOT) },
        headingBounds = header?.rect(), actionBounds = button?.rect(),
        visibleCards = buttons.size.takeIf { it > 0 },
        labels = labels, cardBands = bands,
        selectionCertain = header == null || buttons.none { it.bounds.bottom <= header.bounds.top },
    )
}
