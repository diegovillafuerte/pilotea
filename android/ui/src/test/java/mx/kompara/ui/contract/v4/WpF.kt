package mx.kompara.ui.contract.v4

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import mx.kompara.ui.contract.FrameContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File

/** WP-F, Ajustes and first run (plan §4.8, §4.9). Only WP-F edits this file. */
internal object WpF : V4Package {
    override val sections = mapOf(
        "P" to Section(),
        "J" to Section(),
    )
    override val v3Retired = setOf(
        // Ajustes, redrawn as J1 (six rows; Lo del coche, Avisos, Invita y gana and Tema gone).
        "G1", "G1b", "G1e", "G1n",
        // Tu meta y tu mínimo, redrawn as J2/J7.
        "G2", "G2b", "G2c", "G2d", "G2e",
        // Tus costos, redrawn as J3; the car sheet now opens from it (G5 drew it over Ajustes).
        "G3", "G5",
        // Semáforo, redrawn as J4: no pause, no suppressed-offer list.
        "G4", "G4b", "G4c", "G4d", "G4e", "G4f", "G4g", "G4h", "G4i",
        // Tus datos and Ayuda, redrawn as J5 and J6.
        "G6", "G6b", "G8",
        // The first run, redrawn as P1 and restyled as P2–P4.
        "A1", "A2", "A3", "A3n", "A4", "A4b", "A4r",
    )
    override val wordingAllowlist = setOf<String>()

    /**
     * Strings a frame's contract line names that this build does not draw, with the reason. The kit's J5
     * draws the switch that would send averages to the city; it waits on the founder (plan §8), so no new
     * data leaves the phone. [assertFrame] requires these strings to be absent.
     */
    val notBuiltStrings: Map<String, Map<String, String>> = mapOf(
        "J5" to mapOf("Sumar mis promedios a mi ciudad" to "founder decision 1"),
    )

    /**
     * [V4Contract.assertFrame] for WP-F's frames, minus [notBuiltStrings], which must not be on screen. It reads
     * each text node once: [FrameContract.visibleText] repeats every Text (its layout and its semantics text), so a
     * contract string spanning three nodes, such as J2's "Muestra Neto Bruto", could never match there.
     */
    fun assertFrame(compose: ComposeTestRule, id: String) {
        val text = visibleText(compose)
        val output = File("build/outputs/v4-contract/$id.txt").apply { parentFile!!.mkdirs() }
        output.writeText(text)
        val notBuilt = notBuiltStrings[id].orEmpty()
        val missing = V4Contract.strings.getValue(id).filterNot { it in notBuilt || FrameContract.normalize(it) in text }
        assertTrue("$id missing $missing; rendered text: $output", missing.isEmpty())
        notBuilt.forEach { (string, reason) -> assertFalse("$id draws \"$string\", not built ($reason)", FrameContract.normalize(string) in text) }
        assertTrue("$id has retired wording: ${V4Contract.retired.find(text)?.value}", !V4Contract.retired.containsMatchIn(text))
    }

    private fun visibleText(compose: ComposeTestRule): String = compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.map { it.layoutInput.text.text }.ifEmpty { node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } } +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text) +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }.joinToString(" ").let(FrameContract::normalize)
}
