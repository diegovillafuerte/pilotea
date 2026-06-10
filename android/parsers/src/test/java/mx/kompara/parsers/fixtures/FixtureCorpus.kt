package mx.kompara.parsers.fixtures

import mx.kompara.parsers.spec.ParserSpec
import mx.kompara.parsers.spec.SpecJson
import java.io.File

/**
 * Loads specs and their fixture corpus off the test classpath. A package's spec lives at
 * `resources/specs/<specName>.json`; its fixtures live under `resources/fixtures/<package>/`.
 *
 * This is intentionally classpath/file based (not Android assets) so the regression harness runs
 * as a plain JVM unit test with no emulator. Real Uber/DiDi corpora land in B-029/B-030; the
 * `com.kompara.demo` corpus here proves the harness end-to-end.
 */
object FixtureCorpus {

    private fun resourceDir(path: String): File? =
        javaClass.classLoader?.getResource(path)?.toURI()?.let(::File)

    /** Decode a spec from `resources/specs/<name>.json`. */
    fun loadSpec(name: String): ParserSpec {
        val text = javaClass.classLoader
            ?.getResource("specs/$name.json")
            ?.readText()
            ?: error("Spec resource specs/$name.json not found on the test classpath")
        return SpecJson.decodeSpec(text)
    }

    /** Every fixture file for a package, sorted by filename for stable parameterized ordering. */
    fun loadFixtures(packageName: String): List<NamedFixture> {
        val dir = resourceDir("fixtures/$packageName")
            ?: error("Fixture directory fixtures/$packageName not found on the test classpath")
        return dir.listFiles { f -> f.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { NamedFixture(name = it.nameWithoutExtension, fixture = SpecJson.json.decodeFromString(Fixture.serializer(), it.readText())) }
    }
}

/** A fixture plus its file stem, so parameterized failures name the offending fixture. */
data class NamedFixture(val name: String, val fixture: Fixture) {
    // Parameterized uses this for the test-case display name ({0}); keep it just the stem.
    override fun toString(): String = name
}
