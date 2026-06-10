package mx.kompara.parsers.fixtures

import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.spec.BundledSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-parameterized coverage assertions for the Uber Driver MX corpus (B-029 acceptance criteria):
 * every required variant is represented, the surge flag is orthogonal to the structural variant tag,
 * Uber's native per-km badge is captured into `raw`, and the spec is loadable as a *bundled* spec
 * (the runtime path `:capture` uses), not just off the test fixtures dir.
 */
class UberDriverCoverageTest {

    private val engine = SpecEngine()
    private val spec = FixtureCorpus.loadSpec("uber-driver")
    private val cards = FixtureCorpus.loadFixtures("com.ubercab.driver").map { named ->
        named.name to engine.evaluate(named.fixture.snapshot, spec)
    }

    @Test
    fun `every fixture is recognized as an offer card`() {
        // Synthetic corpus, so we expect 100% (acceptance gate is >= 95%).
        val recognized = cards.count { it.second != null }
        assertEquals("all Uber fixtures should parse", cards.size, recognized)
    }

    @Test
    fun `corpus covers all required variant tags`() {
        val variants = cards.mapNotNull { it.second?.variant }.toSet()
        for (required in listOf("standard", "exclusive", "trip_radar", "multi_stop", "reservation")) {
            assertTrue("variant '$required' not covered by the Uber corpus", required in variants)
        }
    }

    @Test
    fun `surge flag is set independently of the structural variant tag`() {
        val multiStopSurge = cards.first { it.first == "08_multi_stop_surge" }.second!!
        assertEquals("multi_stop", multiStopSurge.variant)
        assertTrue("surge must flip even when a structural variant owns the tag", multiStopSurge.surge)

        val exclusiveSurge = cards.first { it.first == "14_exclusive_surge" }.second!!
        assertEquals("exclusive", exclusiveSurge.variant)
        assertTrue(exclusiveSurge.surge)
    }

    @Test
    fun `uber native per-km badge is captured into the raw map`() {
        val badged = cards.first { it.first == "11_per_km_badge_no_rating" }.second!!
        assertEquals("MX$8.50/km", badged.raw["uberPerKmBadge"])
        // The badge must not be mistaken for the trip fare.
        assertEquals(95.0, badged.fare!!, 1e-6)
    }

    @Test
    fun `passenger rating is captured into raw when present`() {
        val withRating = cards.first { it.first == "01_standard" }.second!!
        assertEquals("4.92", withRating.raw["passengerRating"])
    }

    @Test
    fun `the uber spec is loadable as a bundled runtime spec`() {
        val registry = BundledSpecs.registry()
        val uber = registry.all().firstOrNull { it.targetPackage == "com.ubercab.driver" }
        assertNotNull("uber-driver spec must be bundled for the runtime registry", uber)
        assertEquals(spec.targetPackage, uber!!.targetPackage)
    }
}
