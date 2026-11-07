package mx.kompara.ocr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Privacy guard for the OCR fixtures (0.2.16 round 3; they are this module's test resources — :parsers
 * keeps node snapshots without OCR geometry). No number-only block — a postal code, a street number —
 * may sit in a card's address area: below the first route row (`N min (`) and above the first action
 * button (`Me interesa` / `Aceptar`), the same rule as the sanitizer that regenerated the 0.2.15 exports.
 * Failures name the file, list and block index, never the text.
 */
class FixtureAddressPrivacyTest {
    private val numberOnly = Regex("""^[^A-Za-zÁÉÍÓÚáéíóúñÑ]*\d[^A-Za-zÁÉÍÓÚáéíóúñÑ]*$""")
    private val routeRow = Regex("""min\s*\(""")
    private val action = Regex("""(?i)me interesa|aceptar""")

    @Test fun `no OCR fixture keeps a number-only block in a card's address area`() {
        val root = File(requireNotNull(javaClass.getResource("/uber")) { "no OCR fixtures on the classpath" }.toURI()).parentFile
        val files = root.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.path }.toList()
        val failures = mutableListOf<String>()
        var cards = 0
        for (file in files) for ((where, blocks) in blockLists(Json.parseToJsonElement(file.readText()), file.relativeTo(root).path)) {
            val first = blocks.filter { routeRow.containsMatchIn(it.first) }.minOfOrNull { it.second } ?: continue
            val button = blocks.filter { action.containsMatchIn(it.first) }.minOfOrNull { it.second } ?: Int.MAX_VALUE
            cards++
            blocks.forEachIndexed { i, (text, top) ->
                val t = text.trim()
                if (top > first && top < button && numberOnly.matches(t) && '$' !in t) failures += "$where[$i]"
            }
        }
        // Every recorded card frame of the four real corpora and the three 2026-10 fixtures is scanned.
        assertEquals("card frames scanned", 367, cards)
        assertTrue(failures.size.toString() + " number-only address blocks:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Every list of OCR blocks (objects with `text` and `top`) in a fixture, as (text, top) with its JSON path. */
    private fun blockLists(e: JsonElement, path: String): List<Pair<String, List<Pair<String, Int>>>> = when (e) {
        is JsonArray -> {
            val own = e.takeIf { a -> a.isNotEmpty() && a.all { it is JsonObject && "text" in it && "top" in it } }?.let { a ->
                listOf(path to a.map { (it as JsonObject).let { o -> o.getValue("text").jsonPrimitive.content to o.getValue("top").jsonPrimitive.int } })
            }.orEmpty()
            own + e.flatMapIndexed { i, x -> blockLists(x, "$path[$i]") }
        }
        is JsonObject -> e.entries.flatMap { (k, v) -> blockLists(v, "$path.$k") }
        else -> emptyList()
    }
}
