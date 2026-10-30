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
    val bands = buttons.count { end -> visible.any { start ->
        heading.containsMatchIn(start.text) && start.bounds.top < end.bounds.top &&
            buttons.none { it.bounds.top > start.bounds.top && it.bounds.top < end.bounds.top }
    } }
    fun OcrBlock.rect() = HostRect(bounds.left, bounds.top, bounds.right, bounds.bottom).takeIf { it.valid }
    return OfferInstanceEvidence(
        product = ordered.firstNotNullOfOrNull { product.find(it.text)?.value }
            ?.uppercase(Locale.ROOT)?.replace(Regex("""\s+"""), ""),
        action = button?.let { action.find(it.text)?.value?.uppercase(Locale.ROOT) },
        headingBounds = header?.rect(), actionBounds = button?.rect(),
        visibleCards = bands.takeIf { it > 0 },
    )
}
