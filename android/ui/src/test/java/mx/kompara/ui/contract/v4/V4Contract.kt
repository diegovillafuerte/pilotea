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

/**
 * One package's share of the v4 contract (docs/v4-simple-launch-plan.md §5, §6): WpB.kt, WpC.kt, WpD.kt, WpE.kt,
 * WpF.kt. The contract is closed: nothing is pending; only K3/K4 and J5's switch are not built.
 */
internal interface V4Package {
    /** The kit sections this package builds, by letter. */
    val sections: Map<String, Section>

    /** v3 frames whose screens this package deleted; their v3 baselines go with them. */
    val v3Retired: Set<String>

    /**
     * Production files (paths under android/) that still say wording v4 retired. Drop a file when you
     * rewrite or delete it; the contract fails on an entry that no longer needs it.
     */
    val wordingAllowlist: Set<String>
}

/** [native]: frames proven by a test instead of a baseline: id → that test file (under android/), which names the id. */
internal data class Section(val native: Map<String, String> = emptyMap())

/** Reads the pinned v4 kit contract (src/debug/assets/v4) and applies it to rendered frames. */
internal object V4Contract {
    val packages: List<V4Package> = listOf(WpB, WpC, WpD, WpE, WpF)

    /** Drawn in the kit, waiting on the founder; nothing claims them. */
    val notBuilt: Map<String, String> = mapOf("K3" to "founder decision 1", "K4" to "founder decision 1")

    /**
     * Strings a built frame's contract line names that this build does not draw, with the reason. J5 draws the
     * switch that would add the driver's averages to the city; it waits on founder decision 1 (plan §8), so no
     * new data leaves the phone. [assertFrame] requires these strings to be absent.
     */
    val notBuiltStrings: Map<String, Map<String, String>> = mapOf(
        "J5" to mapOf("Sumar mis promedios a mi ciudad" to "founder decision 1"),
    )

    /** Frame id → the strings that frame must show, in kit order. */
    val strings: Map<String, List<String>> = File("src/debug/assets/v4/_frame-contract.md").readLines()
        .mapNotNull { line -> Regex("^- \\*\\*(\\w+)\\*\\*: (.*)").find(line)?.let { match ->
            match.groupValues[1] to Regex("`([^`]+)`").findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
        } }.toMap()

    val v3Retired: Set<String> get() = packages.flatMap { it.v3Retired }.toSet()
    val wordingAllowlist: Set<String> get() = packages.flatMap { it.wordingAllowlist }.toSet()

    /** S, P, L, K, M, J or V. */
    fun section(id: String): String = Regex("^[A-Z]+").find(id)!!.value

    /** Sections drawn as app screens; each of their baselines comes in day and night. */
    val screens = setOf("P", "L", "K", "M", "J")

    /** v3's retired words and the kit's (check.py `RETIRED`). */
    val retired = Regex("(?i)(?:\\b(?:no conviene|conviene|regular|tu rojo|te quedan|te quedaron|ganancia neta|de verdad|umbral|" +
        "lector funcionando|sin el coche|con el coche|cuadrar|premium|lo del coche|en pausa)\\b|\\baprox)")

    /**
     * IMSS coverage claims. Only the app and the IMSS certify coverage (plan §4.7), so neither the IMSS frames (M)
     * nor the strings of [imssSources] say them. Semana's car bill ("El coche, cubierto el martes", L4) is not
     * about IMSS and may.
     */
    val coverage = Regex("(?i)\\b(?:cubiert[oa]s?|ya te toca)\\b")

    /** The IMSS tab's sources, under ui/src/main/java/mx/kompara/ui. */
    val imssSources = listOf("screens/ImssScreen.kt", "stats/ImssViewModel.kt", "stats/ImssPresentation.kt")

    /**
     * Asserts frame [id]'s contract strings are on screen, minus its [notBuiltStrings], which must not be; and that
     * no retired word is, nor, on an IMSS frame, a coverage claim.
     */
    fun assertFrame(compose: ComposeTestRule, id: String) {
        val text = visibleText(compose)
        val output = File("build/outputs/v4-contract/$id.txt").apply { parentFile!!.mkdirs() }
        output.writeText(text)
        val notBuilt = notBuiltStrings[id].orEmpty()
        val missing = strings.getValue(id).filterNot { it in notBuilt || FrameContract.normalize(it) in text }
        assertTrue("$id missing $missing; rendered text: $output", missing.isEmpty())
        notBuilt.forEach { (string, reason) -> assertFalse("$id draws \"$string\", not built ($reason)", FrameContract.normalize(string) in text) }
        val claim = retired.find(text) ?: coverage.find(text)?.takeIf { section(id) == "M" }
        assertTrue("$id has retired wording: ${claim?.value}", claim == null)
    }

    /**
     * Every text node once, in tree order: a Text's layout, else its semantics text, plus edit values and content
     * descriptions. [FrameContract.visibleText] repeats each Text (layout and semantics), so a contract string
     * spanning three nodes, such as J2's "Muestra Neto Bruto", could never match there.
     */
    private fun visibleText(compose: ComposeTestRule): String = compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.map { it.layoutInput.text.text }.ifEmpty { node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } } +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text) +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }.joinToString(" ").let(FrameContract::normalize)
}
