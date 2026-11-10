package mx.kompara.ui.contract

import mx.kompara.ui.contract.v4.V4Contract
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source/build contracts complement the rendered-frame checks; they do not certify a real device. */
class V3SurfaceContractTest {
    private val android = File("..").canonicalFile
    private fun sourceFiles(module: String, sourceSet: String = "main") = File(android, "$module/src/$sourceSet")
        .walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList()

    @Test fun `every live pinned frame has a real screen golden or an explicit Android handoff test`() {
        assertEquals(118, FrameContract.strings.size) // The pinned file stays whole: original 116 plus G1e/G1n.
        // v4 packages retire the v3 frames of the screens they delete, each in its own contract/v4/Wp*.kt.
        val retired = V4Contract.v3Retired
        retired.forEach { assertTrue("$it is not a v3 frame", it in FrameContract.strings) }
        val native = mapOf(
            "A3s" to "ui/src/test/java/mx/kompara/ui/onboarding/OnboardingNavigationTest.kt",
            "C13" to "overlay/src/test/java/mx/kompara/overlay/OverlayStateMachineTest.kt",
            "H6" to "ui/src/test/java/mx/kompara/ui/notifications/NoticeContractTest.kt",
            "H6b" to "ui/src/test/java/mx/kompara/ui/onboarding/WatchdogStateMachineTest.kt",
        )
        val snapshots = listOf("ui", "overlay").flatMap { File(android, "$it/src/test/snapshots/v3").listFiles().orEmpty().toList() }
        fun reviewed(frame: String) = snapshots.filter { Regex("_${frame}(?:_|\\.)").containsMatchIn(it.name) }
        FrameContract.strings.keys.forEach { frame ->
            when (frame) {
                in retired -> assertTrue("Retired $frame keeps baselines ${reviewed(frame)}", reviewed(frame).isEmpty() && frame !in native)
                in native -> assertTrue("Missing native owner for $frame", File(android, native.getValue(frame)).isFile)
                else -> assertTrue("Missing reviewed frame $frame", reviewed(frame).isNotEmpty())
            }
        }
        // Night twins must keep all three sizes; the chip grows and is suppressed by placement when it cannot fit.
        for (n in 1..10) {
            if ("N$n" in retired) continue
            val names = reviewed("N$n").map { it.name }
            assertTrue("N$n at 1.3", names.any { "1.3" in it })
            assertTrue("N$n at 2.0", names.any { "2.0" in it })
        }
    }

    @Test fun `production strings cannot reintroduce retired vocabulary or unused resources`() {
        val files = android.listFiles().orEmpty().flatMap { module ->
            File(module, "src").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList()
        }
        val allText = files.associateWith { it.readText() }
        val retired = Regex("(?i)\\b(?:no conviene|conviene|regular|tu rojo|te quedan|ganancia neta|de verdad|lector funcionando|lector apagado)\\b")
        // Wording v4 retired (plan §4.1). A file a package still has to rewrite is allowlisted in its contract/v4/Wp*.kt.
        val allowed = V4Contract.wordingAllowlist
        for (module in listOf("ui", "overlay", "capture", "ocr")) {
            for (file in sourceFiles(module)) {
                val path = file.relativeTo(android).invariantSeparatorsPath
                visibleStrings(file).forEach {
                    assertFalse("${file.name}: obsolete copy $it", retired.containsMatchIn(it))
                    if (path !in allowed) assertFalse("$path: copy v4 retired $it", v4Retired.containsMatchIn(it))
                }
            }
            val resource = File(android, "$module/src/main/res/values/strings.xml")
            if (!resource.exists()) continue
            Regex("<(string|plurals) name=\"([^\"]+)\"").findAll(resource.readText()).forEach { match ->
                val kind = match.groupValues[1]; val name = match.groupValues[2]
                val reference = Regex("(?:R\\.$kind\\.|@$kind/)$name\\b")
                assertTrue("Unused $module $kind/$name", allText.any { (file, text) -> file != resource && reference.containsMatchIn(text) })
            }
        }
        for (path in allowed) {
            val file = File(android, path)
            assertTrue("$path is allowlisted but gone: drop it from its package's allowlist", file.isFile)
            assertTrue("$path no longer says retired wording: drop it from its package's allowlist", visibleStrings(file).any(v4Retired::containsMatchIn))
        }
    }

    private val v4Retired = Regex("(?i)(?:\\b(?:sin el coche|con el coche|te quedaron)\\b|\\baprox\\.)")
    private fun visibleStrings(file: File): Sequence<String> {
        val text = file.readText()
        return if (file.extension == "xml") Regex(">(.*?)<").findAll(text).map { it.groupValues[1] }
            else Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(text).map { it.value }
    }

    @Test fun `retired routes fonts palettes and card components are absent from production`() {
        val ui = File(android, "ui/src/main/java/mx/kompara/ui")
        for (path in listOf("HomeUiText.kt", "HomeViewModel.kt", "screens/HistoryScreen.kt", "stats/HistoryViewModel.kt",
            "screens/StatsCommon.kt", "components/Chip.kt", "components/MetricCard.kt", "components/VerdictBadge.kt",
            "components/StatusChip.kt", "components/RecommendationCard.kt", "components/WatchdogBanner.kt",
            "screens/InicioDashboardScreen.kt", "screens/MonthSummaryScreen.kt", "screens/AvisosScreen.kt")) {
            assertFalse("Retired $path", File(ui, path).exists())
        }
        val sources = (sourceFiles("ui") + sourceFiles("overlay")).joinToString("\n") { it.readText() }
        for (token in listOf("R.font.inter", "FontWeight.ExtraBold", "FontWeight.Black", "HISTORY_ROUTE", "SIMULATOR_ROUTE", "FilterChip(", "dynamicDarkColorScheme", "dynamicLightColorScheme")) {
            assertFalse("Retired token $token", token in sources)
        }
        assertFalse(File(android, "ui/src/main/res/font/inter.ttf").exists())
        assertFalse(File(android, "licenses/Inter-OFL.txt").exists())
        sourceFiles("overlay").forEach { assertFalse("Overlay owns palette: $it", Regex("Color\\(0x|#[0-9a-fA-F]{6}").containsMatchIn(it.readText())) }
    }
}
