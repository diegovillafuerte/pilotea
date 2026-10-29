package mx.kompara.ui.contract

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import java.io.File

/** Read the pinned kit contract, not captions or a competing hand-entered set of fixture totals. */
object FrameContract {
    private val twins = mapOf("N1" to "B1", "N2" to "B2", "N3" to "D1", "N4" to "E1", "N5" to "E3",
        "N6" to "F1", "N7" to "F2", "N8" to "C1", "N9" to "C2", "N10" to "C4")
    val strings: Map<String, List<String>> = File("src/debug/assets/v3/_frame-contract.md").readLines()
        .mapNotNull { line -> Regex("^- \\*\\*(\\w+)\\*\\*: (.*)").find(line)?.let { match ->
            match.groupValues[1] to Regex("`([^`]+)`").findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
        } }.toMap()

    // Documented capability gates, never permission to insert illustrative values into live data.
    private val gated = mapOf("G9" to setOf("$7.70/km")) // TD-054: no certified city proposal.
    fun normalize(text: String): String = text.replace("\u2060", "").replace('\u00a0', ' ')
        .replace(Regex("\\s+"), " ").replace(Regex(" +/"), "/").trim()

    fun assertFrame(compose: ComposeTestRule, id: String) {
        if (id !in strings) return // Additional native error/empty/editor scenarios have their own assertions.
        fun visibleText() = compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.map { it.layoutInput.text.text } +
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text) +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }.joinToString(" ").let(::normalize)
        var text = visibleText()
        if (id in setOf("D1", "D1a", "D1b", "N3")) {
            // The offer log is lazy: inspect its entire actual list, then restore the screenshot origin.
            val list = compose.onNodeWithTag("day-offers")
            val count = if (id == "D1" || id == "N3") 13 else 10
            for (index in 1..count) {
                list.performScrollToIndex(index)
                text += " " + visibleText()
            }
            list.performScrollToIndex(0)
        }
        val expected = strings.getValue(twins[id] ?: id) + strings.getValue(id)
        val semanticRates = mapOf("$9.49/km" to "9.49 pesos netos por km", "$7.16/km" to "7.16 pesos netos por km",
            "$2.20/km" to "2.20 pesos netos por km", "−$1.05/km" to "menos 1.05 pesos netos por km")
        fun nativeEquivalent(value: String): Boolean = when {
            // The band is one phrase-first accessible image; its visual text and drawn cues are decorative.
            value in semanticRates -> semanticRates.getValue(value) in text
            value == "▲ desde" -> "Tu meta" in text && "desde" in text
            value == "▼ abajo de" -> "Tu mínimo" in text && "abajo de" in text
            id == "H5d" && value == "$5,340" -> "5,340" in text && "Te pagó la app" in text && "$" in text
            id == "H5d" && value == "26 h" -> "26" in text && "Horas en línea" in text && Regex("\\bh\\b").containsMatchIn(text)
            else -> false
        }
        val missing = expected.distinct().filterNot { it in gated[id].orEmpty() || normalize(it) in text || nativeEquivalent(it) }
        val output = File("build/outputs/v3-contract/$id.txt").apply { parentFile!!.mkdirs() }
        output.writeText(text)
        assertTrue("$id missing $missing; rendered text: $output", missing.isEmpty())
        val retired = Regex("(?i)\\b(?:no conviene|conviene|regular|tu rojo|te quedan|ganancia neta|de verdad|umbral|lector funcionando)\\b")
        assertTrue("$id has retired vocabulary: ${retired.find(text)?.value}", !retired.containsMatchIn(text))
    }
}
