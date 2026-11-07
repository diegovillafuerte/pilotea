package mx.kompara.ocr

import kotlinx.serialization.json.Json
import mx.kompara.parsers.model.OfferCard

/**
 * The complete parse serialization of one real frame: the card signature and every field of the
 * production [OfferCard] (bounds, instance evidence, validation evidence with provenance, repairs,
 * operands, identity checks and grade) plus the legacy-only baseline parser's card, each as the
 * model's own kotlinx JSON. Parser output only: numbers, bounds, enums and recognized tokens, never
 * block text. The 190-frame golden file was written by this exact function at f8aaab8 (0.2.15) and
 * must never be regenerated from newer code.
 */
internal object UberS25ParseGolden {
    const val FILE = "parse-golden-f8aaab8.jsonl"
    private val json = Json { encodeDefaults = true }
    private val production = UberOcrParser()
    private val legacy = UberOcrParser(modernTotals = false)

    fun line(id: String, blocks: List<OcrBlock>): String {
        val signature = production.hasCardSignature(blocks, hostConfirmed = true)
        val card = encode(production.parse(blocks, hostConfirmed = true))
        val baseline = encode(legacy.parse(blocks, hostConfirmed = true))
        return """{"id":"$id","signature":$signature,"card":$card,"legacy":$baseline}"""
    }

    private fun encode(card: OfferCard?) = card?.let { json.encodeToString(OfferCard.serializer(), it) } ?: "null"
}
