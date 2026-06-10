package mx.kompara.parsers.spec

import mx.kompara.parsers.snapshot.ParserSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecRegistryTest {

    private fun spec(pkg: String, version: Int, range: VersionRange) = ParserSpec(
        targetPackage = pkg,
        versionCodeRange = range,
        specVersion = version,
        cardDetector = CardDetector(allOf = listOf(TextPattern("Nuevo viaje"))),
    )

    private fun snap(pkg: String, version: Long?) =
        ParserSnapshot(packageName = pkg, timestampMs = 0L, versionCode = version)

    @Test
    fun `selects spec by package and version range`() {
        val registry = SpecRegistry(
            listOf(
                spec("com.a", 1, VersionRange(min = 1, max = 100)),
                spec("com.b", 1, VersionRange()),
            ),
        )
        assertEquals("com.a", registry.specFor(snap("com.a", 50L))?.targetPackage)
        assertEquals("com.b", registry.specFor(snap("com.b", 9999L))?.targetPackage)
        assertNull(registry.specFor(snap("com.a", 500L))) // out of range
        assertNull(registry.specFor(snap("com.c", 1L)))   // no spec for package
    }

    @Test
    fun `highest spec version wins on overlapping ranges`() {
        val registry = SpecRegistry(
            listOf(
                spec("com.a", 1, VersionRange(min = 1, max = 1000)),
                spec("com.a", 5, VersionRange(min = 1, max = 1000)),
            ),
        )
        assertEquals(5, registry.specFor(snap("com.a", 50L))?.specVersion)
    }

    @Test
    fun `empty registry selects nothing`() {
        assertNull(SpecRegistry().specFor(snap("com.a", 1L)))
    }
}
