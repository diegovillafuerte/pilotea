package mx.kompara.parsers.fixtures

import mx.kompara.parsers.spec.BundledSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Non-parameterized coverage assertion for the DiDi Conductor MX spec: the spec must be loadable
 * as a *bundled* spec (the runtime path `:capture` uses), not just off the test classpath.
 * Mirrors [UberDriverCoverageTest]'s bundled-runtime check; the fixture-level regressions live in
 * [DidiFixtureHarnessTest].
 */
class DidiCoverageTest {

    @Test
    fun `the didi spec is loadable as a bundled runtime spec`() {
        val registry = BundledSpecs.registry()
        val didi = registry.all().firstOrNull { it.targetPackage == "com.didiglobal.driver" }
        assertNotNull("didi-mx spec must be bundled for the runtime registry", didi)
        assertEquals(FixtureCorpus.loadSpec("didi-mx").targetPackage, didi!!.targetPackage)
    }
}
