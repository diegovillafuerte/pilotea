package mx.kompara.parsers.model

import java.util.Locale

/**
 * A complete offer-action label, optionally including its quoted amount (DiDi). A word mentioned
 * in an address or background instruction is not a button and cannot delimit or count offer cards.
 * This recognizes the label only; it never establishes the amount as driver payment.
 * "Me interesa" is the S25 Trip Radar button (2026-10-30); its countdown fill can split or drop
 * the label on some frames, so a missing action is uncertainty, never evidence.
 */
object OfferActionLabel {
    private val label = Regex(
        """(Aceptar|Coincidir|Me interesa)(?:\s+(?:MX[N$]\s*\$?|\$)\s*[0-9OlI][0-9OlI.,]*(?:\s+MXN)?)?""",
        RegexOption.IGNORE_CASE,
    )

    fun from(text: String): String? = label.matchEntire(text.trim())
        ?.groupValues?.get(1)?.uppercase(Locale.ROOT)
}
