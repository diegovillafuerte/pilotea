package mx.kompara.parsers.fixtures

import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.spec.FieldNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Regression harness for the inDrive MX bid-card corpus (B-035). Runs the `indrive-mx` spec against
 * every fixture under `resources/fixtures/sinet.startup.inDriver/` and asserts [SpecEngine.evaluate]
 * reproduces each fixture's expected [mx.kompara.parsers.model.OfferCard] — the TOP bid for list
 * fixtures, the single card for single-bid fixtures.
 *
 * Parameterized so each fixture is its own named test case; a spec regression names the broken
 * fixture. Mirrors [DidiFixtureHarnessTest]; list-mode segmentation behavior is covered separately in
 * [InDriveListModeTest].
 *
 * NOTE: inDrive uses a passenger-BID model — drivers pick from a list of custom-priced offers, so
 * per-offer $/km is especially valuable. PACKAGE ID NEEDS ON-DEVICE VERIFICATION
 * ('sinet.startup.inDriver') and this corpus is a reasoned model of inDrive MX bid cards, NOT
 * on-device captures (no physical device in the build environment). On-device validation is tracked
 * as tech debt (see techdebt.md) and must replace these synthetic fixtures before the parser ships.
 */
@RunWith(Parameterized::class)
class InDriveFixtureHarnessTest(
    private val named: NamedFixture,
) {
    private val engine = SpecEngine()
    private val spec = FixtureCorpus.loadSpec("indrive-mx")

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
    fun `raw map records the fare that produced the typed column`() {
        val card = engine.evaluate(named.fixture.snapshot, spec)!!
        if (card.fare != null) assertTrue("fare missing from raw in '${named.name}'", FieldNames.FARE in card.raw)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<NamedFixture> {
            val corpus = FixtureCorpus.loadFixtures("sinet.startup.inDriver")
            // Task B-035 requires >= 15 fixtures (single bids, 2-3 item lists, edge cases).
            require(corpus.size >= 15) {
                "Expected >= 15 inDrive MX fixtures, found ${corpus.size}"
            }
            return corpus
        }
    }
}
