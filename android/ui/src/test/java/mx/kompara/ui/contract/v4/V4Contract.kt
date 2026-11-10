package mx.kompara.ui.contract.v4

import androidx.compose.ui.test.junit4.ComposeTestRule
import mx.kompara.ui.contract.FrameContract
import org.junit.Assert.assertTrue
import java.io.File

/**
 * One package's share of the v4 contract (docs/v4-simple-launch-plan.md §5, §6). Each package edits
 * only its own file — WpB.kt, WpC.kt, WpD.kt, WpE.kt, WpF.kt — so parallel work never touches a shared line.
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

/**
 * [pending]: frames not built yet. Drop an id once its reviewed baseline is in `snapshots/v4`; the
 * contract fails on a pending frame that already has one.
 * [native]: frames proven by a test instead of a baseline: id → that test file (under android/), which names the id.
 */
internal data class Section(val pending: Set<String> = emptySet(), val native: Map<String, String> = emptyMap())

/** Reads the pinned v4 kit contract (src/debug/assets/v4) and applies it to rendered frames. */
internal object V4Contract {
    val packages: List<V4Package> = listOf(WpB, WpC, WpD, WpE, WpF)

    /** Drawn in the kit, waiting on the founder; nothing claims them. */
    val notBuilt: Map<String, String> = mapOf("K3" to "founder decision 1", "K4" to "founder decision 1")

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

    /** v3's retired words, the kit's (check.py `RETIRED`), and the coverage claims M must never make. */
    val retired = Regex("(?i)(?:\\b(?:no conviene|conviene|regular|tu rojo|te quedan|te quedaron|ganancia neta|de verdad|umbral|" +
        "lector funcionando|sin el coche|con el coche|cuadrar|premium|lo del coche|en pausa|cubierto|ya te toca)\\b|\\baprox)")

    /** Asserts frame [id]'s contract strings are on screen and no retired word is. */
    fun assertFrame(compose: ComposeTestRule, id: String) {
        val text = FrameContract.visibleText(compose)
        val output = File("build/outputs/v4-contract/$id.txt").apply { parentFile!!.mkdirs() }
        output.writeText(text)
        val missing = strings.getValue(id).filterNot { FrameContract.normalize(it) in text }
        assertTrue("$id missing $missing; rendered text: $output", missing.isEmpty())
        assertTrue("$id has retired wording: ${retired.find(text)?.value}", !retired.containsMatchIn(text))
    }
}
