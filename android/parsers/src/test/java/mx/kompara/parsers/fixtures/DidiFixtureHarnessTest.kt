package mx.kompara.parsers.fixtures

import mx.kompara.parsers.engine.SpecEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Regression harness for the DiDi Conductor MX corpus (B-030). Runs the `didi-mx` spec against
 * every fixture under `resources/fixtures/com.didiglobal.driver/` and asserts the engine reproduces
 * each fixture's expected [mx.kompara.parsers.model.OfferCard].
 *
 * Parameterized so each fixture is its own named test case — a spec regression fails loudly,
 * naming the exact fixture that broke. Mirrors [DemoFixtureHarnessTest]; the wiring is identical,
 * only the spec name and package change.
 *
 * NOTE: the corpus is a reasoned model of DiDi MX cards (countdown ring, fare like "$54.20",
 * "Efectivo"/"Tarjeta" chips, pickup "a 3 min (1.2 km)", trip "15 min (7.8 km)", bonus chips like
 * "+$15 extra", stacked/back-to-back variants), NOT on-device captures — no physical device was
 * available in the build environment. On-device validation is tracked as tech debt (see
 * techdebt.md) and must replace these synthetic fixtures before the parser ships.
 */
@RunWith(Parameterized::class)
class DidiFixtureHarnessTest(
    private val named: NamedFixture,
) {
    private val engine = SpecEngine()
    private val spec = FixtureCorpus.loadSpec("didi-mx")

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
        if (card.fare != null) assertTrue("fare missing from raw in '${named.name}'", "fare" in card.raw)
        if (card.paymentType != null) {
            assertTrue("paymentType missing from raw in '${named.name}'", "paymentType" in card.raw)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<NamedFixture> {
            val corpus = FixtureCorpus.loadFixtures("com.didiglobal.driver")
            // Task B-030 requires >= 15 fixtures covering all variants + edge cases.
            require(corpus.size >= 15) {
                "Expected >= 15 DiDi MX fixtures, found ${corpus.size}"
            }
            return corpus
        }
    }
}
