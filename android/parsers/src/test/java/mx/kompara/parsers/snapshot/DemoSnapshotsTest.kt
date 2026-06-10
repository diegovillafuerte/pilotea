package mx.kompara.parsers.snapshot

import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.spec.BundledSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled demo offer snapshots (B-037) must load from MAIN resources and parse through the same
 * bundled specs + spec engine the live reader uses — otherwise the in-app simulator would show a
 * blank chip. This guards against the demo fixtures drifting away from the shipped specs.
 */
class DemoSnapshotsTest {

    private val registry = BundledSpecs.registry()
    private val engine = SpecEngine()

    @Test
    fun thereAreThreeUberAndThreeDidiDemoOffers() {
        assertEquals(3, DemoSnapshots.UBER.size)
        assertEquals(3, DemoSnapshots.DIDI.size)
        // Each platform covers good / marginal / bad.
        for (deck in listOf(DemoSnapshots.UBER, DemoSnapshots.DIDI)) {
            assertEquals(
                setOf(
                    DemoSnapshots.Shape.GOOD,
                    DemoSnapshots.Shape.MARGINAL,
                    DemoSnapshots.Shape.BAD,
                ),
                deck.map { it.shape }.toSet(),
            )
        }
    }

    @Test
    fun everyDemoSnapshotLoadsFromMainResources() {
        for (offer in DemoSnapshots.all()) {
            val snapshot = DemoSnapshots.load(offer)
            assertTrue("nodes for ${offer.id}", snapshot.nodes.isNotEmpty())
        }
    }

    @Test
    fun everyDemoSnapshotIsRecognizedByABundledSpecAndYieldsAFare() {
        for (offer in DemoSnapshots.all()) {
            val snapshot = DemoSnapshots.load(offer)
            val spec = registry.specFor(snapshot)
            assertNotNull("a bundled spec recognizes ${offer.id}", spec)
            val card = engine.evaluate(snapshot, spec!!)
            assertNotNull("${offer.id} parses into an OfferCard", card)
            assertNotNull("${offer.id} has a fare", card!!.fare)
            assertNotNull("${offer.id} has a trip distance", card.tripDistanceKm)
        }
    }
}
