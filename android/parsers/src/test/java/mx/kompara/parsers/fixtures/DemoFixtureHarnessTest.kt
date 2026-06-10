package mx.kompara.parsers.fixtures

import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The regression harness (task requirement 4): runs the `com.kompara.demo` spec against every
 * fixture in its corpus and asserts the engine reproduces each fixture's expected [OfferCard].
 *
 * Parameterized so each fixture is its own test case — a spec regression fails loudly, naming the
 * exact fixture that broke. The same `(spec, corpus)` wiring is reused verbatim for the real Uber
 * and DiDi corpora arriving in B-029/B-030: add a spec under `resources/specs/` and fixtures under
 * `resources/fixtures/<package>/`, then point a parameterized class at them.
 *
 * Comparison ignores the card's [OfferCard.raw] map (debug-only, pre-normalization text) so
 * fixtures pin the typed contract, not incidental raw strings.
 */
@RunWith(Parameterized::class)
class DemoFixtureHarnessTest(
    private val named: NamedFixture,
) {
    private val engine = SpecEngine()
    private val spec = FixtureCorpus.loadSpec("demo")

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
    fun `raw map records every extracted field for the card`() {
        val card = engine.evaluate(named.fixture.snapshot, spec)!!
        // Every non-null typed field must have a corresponding raw entry (debuggability).
        if (card.fare != null) assertTrue("fare missing from raw in '${named.name}'", "fare" in card.raw)
        if (card.paymentType != null) {
            assertTrue("paymentType missing from raw in '${named.name}'", "paymentType" in card.raw)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<NamedFixture> {
            val corpus = FixtureCorpus.loadFixtures("com.kompara.demo")
            // Guard: the harness itself must have a corpus, or it would silently pass with zero cases.
            require(corpus.size >= 4) {
                "Expected >= 4 demo fixtures proving the harness, found ${corpus.size}"
            }
            return corpus
        }
    }
}
