package mx.kompara.ui.contract.v4

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Every v4 kit frame is not built, proven by a named test, or has a reviewed baseline; nothing is pending. */
class V4ContractTest {
    private val android = File("..").canonicalFile
    private val baselines = listOf("ui", "overlay").flatMap { File(android, "$it/src/test/snapshots/v4").listFiles().orEmpty().toList() }
    private fun baselinesOf(id: String) = baselines.filter { Regex("(?:^|_)${id}(?:_|\\.)").containsMatchIn(it.name) }

    @Test fun `the pinned kit has thirty-eight frames in seven sections each owned by one package`() {
        assertEquals(38, V4Contract.strings.size)
        val owners = V4Contract.packages.flatMap { it.sections.keys }
        assertEquals("Each section has exactly one owner", owners.sorted(), owners.distinct().sorted())
        assertEquals(V4Contract.strings.keys.map(V4Contract::section).toSet(), owners.toSet())
    }

    @Test fun `every frame has exactly one status`() {
        val claims = V4Contract.packages.flatMap { it.sections.entries }
        for ((letter, section) in claims) {
            section.native.keys.forEach { id ->
                assertTrue("$id is not a kit frame", id in V4Contract.strings)
                assertEquals("$id is not in section $letter", letter, V4Contract.section(id))
                assertFalse("$id is not built (${V4Contract.notBuilt[id]}); nothing claims it", id in V4Contract.notBuilt)
            }
        }
        for ((id, strings) in V4Contract.notBuiltStrings) {
            assertFalse("$id is not built at all; its strings need no exception", id in V4Contract.notBuilt)
            strings.keys.forEach { assertTrue("\"$it\" is not in $id's contract line", it in V4Contract.strings.getValue(id)) }
        }
        val native = claims.flatMap { it.value.native.entries }.associate { it.key to it.value }
        for (id in V4Contract.strings.keys) {
            val reviewed = baselinesOf(id)
            when {
                id in V4Contract.notBuilt -> assertTrue("$id is not built but has baselines $reviewed", reviewed.isEmpty())
                id in native -> {
                    val owner = File(android, native.getValue(id))
                    assertTrue("Missing native owner $owner for $id", owner.isFile)
                    assertTrue("$owner does not name $id", Regex("\\b$id\\b").containsMatchIn(owner.readText()))
                }
                else -> {
                    assertTrue("Missing reviewed v4 frame $id: record a baseline in snapshots/v4 or name a native owner test", reviewed.isNotEmpty())
                    if (V4Contract.section(id) in V4Contract.screens) {
                        assertTrue("$id needs a day baseline", reviewed.any { "_day_" in it.name })
                        assertTrue("$id needs a night baseline", reviewed.any { "_night_" in it.name })
                    }
                }
            }
        }
    }

    @Test fun `no launch-set code links an after-launch route`() {
        val ui = File(android, "ui/src/main/java/mx/kompara/ui")
        // Premium, statement import, the share card, referrals and the IMSS PDF (plan §3) may link only to each other.
        val dormant = listOf("paywall", "imports", "referral", "share", "fiscal").map { File(ui, it) }
        val routes = Regex("PAYWALL_ROUTE|paywallRoute|PLAN_ROUTE|IMPORT_ROUTE|REFERRAL_ROUTE|SHARE_CARD_ROUTE|" +
            "\"(?:paywall|tu-plan|plan-subscribe|import|import-review|referral|share-card)\\b")
        val sources = android.listFiles().orEmpty().flatMap { File(it, "src/main").walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
        assertTrue("No sources found under $android", sources.isNotEmpty())
        for (file in sources.filterNot { f -> dormant.any { f.startsWith(it) } }) {
            file.readLines().forEachIndexed { index, line ->
                val call = line.substringAfter("navigate(", "")
                assertFalse("${file.relativeTo(android)}:${index + 1} links an after-launch route: ${line.trim()}", routes.containsMatchIn(call))
            }
            // MainActivity is exported, so the NavHost would open any nav deep link from another app's intent.
            assertFalse("${file.relativeTo(android)} declares a nav deep link", "navDeepLink" in file.readText())
        }
        // The notices that remain open live routes; a 0.2.x import reminder still in the shade opens Ganancias.
        for (kind in mx.kompara.ui.notifications.NoticeKind.entries) {
            val target = mx.kompara.ui.notifications.NoticeTarget(kind, if (kind == mx.kompara.ui.notifications.NoticeKind.MONTH) "2026-09" else "2026-10-12")
            val destination = mx.kompara.ui.notifications.NoticeNavigation.destination(target)
            assertFalse("$kind opens $destination", routes.containsMatchIn("\"$destination"))
        }
    }

    @Test fun `after-launch entry points stay dormant in the manifest`() {
        val manifest = File(android, "app/src/main/AndroidManifest.xml").readText()
        val share = Regex("<activity\\s[^>]*android:name=\"\\.ShareImportActivity\"[^>]*>").find(manifest)!!.value
        assertTrue("ShareImportActivity must stay disabled", "android:enabled=\"false\"" in share)
        assertFalse("kompara://referral must not reach MainActivity", Regex("android:host=\"referral\"").containsMatchIn(manifest))
        val main = File(android, "app/src/main/java/mx/kompara/app/MainActivity.kt").readText()
        assertFalse("MainActivity must not read the share-target extra", Regex("getBooleanExtra\\(EXTRA_OPEN_IMPORT").containsMatchIn(main))
        assertFalse("MainActivity must not read the share-card extra", "OPEN_SHARE_CARD" in main)
        assertFalse("Nothing but the share sheet may start ShareImportActivity", sourcesMention("ShareImportActivity::class"))
    }

    private fun sourcesMention(token: String) = android.listFiles().orEmpty().any { module ->
        File(module, "src/main").walkTopDown().any { it.isFile && it.extension == "kt" && token in it.readText() }
    }
}
