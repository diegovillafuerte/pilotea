package mx.kompara.ui.contract.v4

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Every v4 kit frame is not built, pending, proven by a named test, or has a reviewed baseline. */
class V4ContractTest {
    private val android = File("..").canonicalFile
    private val baselines = listOf("ui", "overlay").flatMap { File(android, "$it/src/test/snapshots/v4").listFiles().orEmpty().toList() }
    private fun baselinesOf(id: String) = baselines.filter { Regex("_${id}(?:_|\\.)").containsMatchIn(it.name) }

    @Test fun `the pinned kit has thirty-eight frames in seven sections each owned by one package`() {
        assertEquals(38, V4Contract.strings.size)
        val owners = V4Contract.packages.flatMap { it.sections.keys }
        assertEquals("Each section has exactly one owner", owners.sorted(), owners.distinct().sorted())
        assertEquals(V4Contract.strings.keys.map(V4Contract::section).toSet(), owners.toSet())
    }

    @Test fun `every frame has exactly one status`() {
        val claims = V4Contract.packages.flatMap { it.sections.entries }
        for ((letter, section) in claims) {
            (section.pending + section.native.keys).forEach { id ->
                assertTrue("$id is not a kit frame", id in V4Contract.strings)
                assertEquals("$id is not in section $letter", letter, V4Contract.section(id))
                assertFalse("$id is not built (${V4Contract.notBuilt[id]}); nothing claims it", id in V4Contract.notBuilt)
            }
            assertTrue("A frame is both pending and natively owned in $letter", section.pending.intersect(section.native.keys).isEmpty())
        }
        val pending = claims.flatMap { it.value.pending }.toSet()
        val native = claims.flatMap { it.value.native.entries }.associate { it.key to it.value }
        for (id in V4Contract.strings.keys) {
            val reviewed = baselinesOf(id)
            when {
                id in V4Contract.notBuilt -> assertTrue("$id is not built but has baselines $reviewed", reviewed.isEmpty())
                id in pending -> assertTrue("$id has baselines $reviewed: drop it from pending", reviewed.isEmpty())
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

    @Test fun `after-launch entry points stay dormant in the manifest`() {
        val manifest = File(android, "app/src/main/AndroidManifest.xml").readText()
        val share = Regex("<activity\\s[^>]*android:name=\"\\.ShareImportActivity\"[^>]*>").find(manifest)!!.value
        assertTrue("ShareImportActivity must stay disabled", "android:enabled=\"false\"" in share)
        assertFalse("kompara://referral must not reach MainActivity", Regex("android:host=\"referral\"").containsMatchIn(manifest))
    }
}
