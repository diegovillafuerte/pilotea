package mx.kompara.parsers.fixtures

import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.spec.FieldNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies list-mode behavior for the inDrive MX spec (B-035): [SpecEngine.evaluateAll] segments a
 * multi-bid snapshot into one [mx.kompara.parsers.model.OfferCard] per bid, while [SpecEngine.evaluate]
 * keeps returning the single top bid (pipeline compatibility) and stamps the count of the other bids
 * into `raw["additional_bids"]`. Drives the real fixture corpus so the engine and the bundled spec
 * are exercised together, not a hand-built spec.
 */
class InDriveListModeTest {

    private val engine = SpecEngine()
    private val spec = FixtureCorpus.loadSpec("indrive-mx")

    private fun fixture(name: String) =
        FixtureCorpus.loadFixtures("sinet.startup.inDriver").first { it.name == name }.fixture

    @Test
    fun `evaluateAll returns one card per bid in a two-bid list`() {
        val cards = engine.evaluateAll(fixture("09_bid_list_two").snapshot, spec)
        assertEquals(2, cards.size)
        // Top bid first, in reading order.
        assertEquals(110.0, cards[0].fare!!, 1e-9)
        assertEquals(85.0, cards[1].fare!!, 1e-9)
        // Per-card fields don't bleed across segments.
        assertEquals(2.3, cards[0].pickupDistanceKm!!, 1e-9)
        assertEquals(3.1, cards[1].pickupDistanceKm!!, 1e-9)
        assertEquals(12.0, cards[0].tripDistanceKm!!, 1e-9)
        assertEquals(9.5, cards[1].tripDistanceKm!!, 1e-9)
        // The list variant is shared by every card.
        cards.forEach { assertEquals("bid_list", it.variant) }
    }

    @Test
    fun `evaluateAll returns one card per bid in a three-bid list`() {
        val cards = engine.evaluateAll(fixture("10_bid_list_three").snapshot, spec)
        assertEquals(3, cards.size)
        assertEquals(listOf(130.0, 100.0, 95.0), cards.map { it.fare })
    }

    @Test
    fun `evaluateAll segments lists with mixed units and a missing duration`() {
        val cards = engine.evaluateAll(fixture("11_bid_list_three_mixed_units").snapshot, spec)
        assertEquals(3, cards.size)
        // Top bid pickup in meters -> km.
        assertEquals(0.95, cards[0].pickupDistanceKm!!, 1e-9)
        // Middle bid has a distance-only trip line -> no duration.
        assertNull(cards[1].tripDurationMin)
        assertEquals(11.0, cards[1].tripDistanceKm!!, 1e-9)
    }

    @Test
    fun `evaluate returns the top bid and counts the rest in raw`() {
        val twoBid = engine.evaluate(fixture("09_bid_list_two").snapshot, spec)!!
        assertEquals(110.0, twoBid.fare!!, 1e-9)
        assertEquals("1", twoBid.raw[FieldNames.ADDITIONAL_BIDS])

        val threeBid = engine.evaluate(fixture("10_bid_list_three").snapshot, spec)!!
        assertEquals(130.0, threeBid.fare!!, 1e-9)
        assertEquals("2", threeBid.raw[FieldNames.ADDITIONAL_BIDS])
    }

    @Test
    fun `single-bid fixture has no additional_bids and one card from evaluateAll`() {
        val snapshot = fixture("01_single_bid_standard").snapshot
        val single = engine.evaluate(snapshot, spec)!!
        assertFalse("single bid should not carry additional_bids", FieldNames.ADDITIONAL_BIDS in single.raw)

        val all = engine.evaluateAll(snapshot, spec)
        assertEquals(1, all.size)
        assertEquals(75.0, all[0].fare!!, 1e-9)
    }

    @Test
    fun `passenger rating is captured into raw across rating formats`() {
        // Plain decimal, star glyph, and labelled forms all reach raw.passengerRating on the top bid.
        val plain = engine.evaluate(fixture("01_single_bid_standard").snapshot, spec)!!
        assertEquals("4.8", plain.raw["passengerRating"])

        val star = engine.evaluate(fixture("14_bid_list_star_rating").snapshot, spec)!!
        assertEquals("4.7", star.raw["passengerRating"])

        val comma = engine.evaluate(fixture("07_single_bid_rating_comma").snapshot, spec)!!
        assertEquals("4,9", comma.raw["passengerRating"])
    }

    @Test
    fun `evaluateAll on a non-matching snapshot is an empty list`() {
        val other = fixture("01_single_bid_standard").snapshot.copy(packageName = "com.other.app")
        assertTrue(engine.evaluateAll(other, spec).isEmpty())
    }
}
