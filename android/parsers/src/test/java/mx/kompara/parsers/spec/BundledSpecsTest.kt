package mx.kompara.parsers.spec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that every spec shipped in `:parsers/src/main/resources/specs/` actually loads at runtime
 * (B-033 extra scope: DiDi's spec was moved out of test resources into runtime resources alongside
 * Uber's, and registered in [BundledSpecs], so DiDi now parses on-device, not just in tests).
 */
class BundledSpecsTest {

    @Test
    fun `all bundled specs load at runtime`() {
        val specs = BundledSpecs.load()
        // uber-driver + didi-mx + indrive-mx (B-035)
        assertEquals(3, specs.size)
        val packages = specs.map { it.targetPackage }.toSet()
        assertTrue("uber spec missing", "com.ubercab.driver" in packages)
        assertTrue("didi spec missing", "com.didiglobal.driver" in packages)
        assertTrue("indrive spec missing", "sinet.startup.inDriver" in packages)
    }

    @Test
    fun `bundled registry resolves uber, didi and indrive by package`() {
        val registry = BundledSpecs.registry()
        assertNotNull(
            registry.all().firstOrNull { it.targetPackage == "com.ubercab.driver" },
        )
        assertNotNull(
            registry.all().firstOrNull { it.targetPackage == "com.didiglobal.driver" },
        )
        assertNotNull(
            registry.all().firstOrNull { it.targetPackage == "sinet.startup.inDriver" },
        )
    }

    @Test
    fun `every spec name in SPEC_NAMES resolves to a loadable resource`() {
        // No silent drops: the count of decoded specs equals the count of declared names.
        assertEquals(BundledSpecs.SPEC_NAMES.size, BundledSpecs.load().size)
    }
}
