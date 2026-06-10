package mx.kompara.parsers.fixtures

import mx.kompara.parsers.engine.SpecEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Regression harness for the production Uber Driver MX spec (B-029): runs
 * `specs/uber-driver.json` (bundled in `:parsers` main resources) against every fixture in the
 * `com.ubercab.driver` corpus and asserts the engine reproduces each fixture's expected
 * [mx.kompara.parsers.model.OfferCard].
 *
 * Mirrors [DemoFixtureHarnessTest] exactly, per the harness reuse the design calls for. Each fixture
 * is its own parameterized case, so a spec regression fails loudly and names the offending fixture.
 *
 * NOTE: the corpus is *synthetic* — hand-authored from the publicly documented Uber Driver MX
 * offer-card layout because no physical device was available in the build environment. Real on-device
 * fixture capture (and the >=95% acceptance gate against real screens) remains tracked in techdebt
 * (TD-006). These fixtures pin the spec's contract and prove the extraction chains end-to-end.
 */
@RunWith(Parameterized::class)
class UberDriverFixtureHarnessTest(
    private val named: NamedFixture,
) {
    private val engine = SpecEngine()
    private val spec = FixtureCorpus.loadSpec("uber-driver")

    @Test
    fun `engine reproduces the expected offer card`() {
        val card = engine.evaluate(named.fixture.snapshot, spec)
        assertNotNull("Fixture '${named.name}' should be detected as an offer card", card)
        assertEquals(
            "Fixture '${named.name}' (${named.fixture.description}) regressed",
            named.fixture.expected.copy(raw = emptyMap()),
            card!!.copy(raw = emptyMap()),
        )
    }

    @Test
    fun `fare is recorded in the raw map when extracted`() {
        val card = engine.evaluate(named.fixture.snapshot, spec)!!
        if (card.fare != null) {
            assertTrue("fare missing from raw in '${named.name}'", "fare" in card.raw)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<NamedFixture> {
            val corpus = FixtureCorpus.loadFixtures("com.ubercab.driver")
            // Acceptance criterion: >= 15 fixtures covering all variants + edge cases.
            require(corpus.size >= 15) {
                "Expected >= 15 Uber fixtures (all variants + edge cases), found ${corpus.size}"
            }
            return corpus
        }
    }
}
