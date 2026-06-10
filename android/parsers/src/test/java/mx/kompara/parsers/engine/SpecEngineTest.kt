package mx.kompara.parsers.engine

import mx.kompara.parsers.normalize.Normalizer
import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.snapshot.RectBox
import mx.kompara.parsers.spec.CardDetector
import mx.kompara.parsers.spec.ExtractStrategy
import mx.kompara.parsers.spec.FieldExtractor
import mx.kompara.parsers.spec.FieldNames
import mx.kompara.parsers.spec.ListMode
import mx.kompara.parsers.spec.ParserSpec
import mx.kompara.parsers.spec.TextPattern
import mx.kompara.parsers.spec.VariantRule
import mx.kompara.parsers.spec.VersionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecEngineTest {

    private val engine = SpecEngine()
    private val pkg = "com.kompara.demo"

    private fun node(
        text: String?,
        viewId: String? = null,
        index: Int = 0,
        depth: Int = 1,
        bounds: RectBox = RectBox(0, index * 100, 600, index * 100 + 50),
    ) = ParserNode(text = text, viewId = viewId, bounds = bounds, depth = depth, index = index)

    private fun snapshot(vararg nodes: ParserNode, version: Long? = 200L) =
        ParserSnapshot(packageName = pkg, timestampMs = 0L, versionCode = version, nodes = nodes.toList())

    private val baseDetector = CardDetector(allOf = listOf(TextPattern("Nuevo viaje")))

    // --- Detection ------------------------------------------------------------------------------

    @Test
    fun `wrong package returns null`() {
        val spec = ParserSpec(targetPackage = "other.app", cardDetector = baseDetector)
        assertNull(engine.evaluate(snapshot(node("Nuevo viaje")), spec))
    }

    @Test
    fun `version code outside range returns null`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            versionCodeRange = VersionRange(min = 1000),
            cardDetector = baseDetector,
        )
        assertNull(engine.evaluate(snapshot(node("Nuevo viaje"), version = 200L), spec))
    }

    @Test
    fun `unknown version code is not excluded`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            versionCodeRange = VersionRange(min = 1000),
            cardDetector = baseDetector,
        )
        assertEquals(pkg, engine.evaluate(snapshot(node("Nuevo viaje"), version = null), spec)?.platform)
    }

    @Test
    fun `allOf must all match`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = CardDetector(allOf = listOf(TextPattern("Nuevo viaje"), TextPattern("Aceptar"))),
        )
        assertNull(engine.evaluate(snapshot(node("Nuevo viaje")), spec))
        assertEquals(
            pkg,
            engine.evaluate(snapshot(node("Nuevo viaje"), node("Aceptar", index = 1)), spec)?.platform,
        )
    }

    @Test
    fun `noneOf is a negative guard`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector.copy(noneOf = listOf(TextPattern("Viaje en curso"))),
        )
        assertNull(
            engine.evaluate(snapshot(node("Nuevo viaje"), node("Viaje en curso", index = 1)), spec),
        )
    }

    @Test
    fun `empty detector matches nothing`() {
        val spec = ParserSpec(targetPackage = pkg, cardDetector = CardDetector())
        assertNull(engine.evaluate(snapshot(node("Nuevo viaje")), spec))
    }

    // --- Variants -------------------------------------------------------------------------------

    @Test
    fun `base variant applies when no rule matches`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector.copy(baseVariant = "standard"),
        )
        assertEquals("standard", engine.evaluate(snapshot(node("Nuevo viaje")), spec)?.variant)
    }

    @Test
    fun `first matching variant rule wins and can set surge`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector.copy(baseVariant = "standard"),
            variants = listOf(
                VariantRule(tag = "surge", whenAnyOf = listOf(TextPattern("din[aá]mica")), setsSurge = true),
                VariantRule(tag = "multi_stop", whenAnyOf = listOf(TextPattern("paradas"))),
            ),
        )
        val card = engine.evaluate(
            snapshot(node("Nuevo viaje"), node("Tarifa dinámica", index = 1), node("2 paradas", index = 2)),
            spec,
        )!!
        // surge rule is listed first → wins over multi_stop even though both anchors are present.
        assertEquals("surge", card.variant)
        assertTrue(card.surge)
    }

    @Test
    fun `surge defaults false for non-surge variant`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            variants = listOf(VariantRule(tag = "multi_stop", whenAnyOf = listOf(TextPattern("paradas")))),
        )
        val card = engine.evaluate(snapshot(node("Nuevo viaje"), node("3 paradas", index = 1)), spec)!!
        assertEquals("multi_stop", card.variant)
        assertFalse(card.surge)
    }

    // --- Extraction chains ----------------------------------------------------------------------

    @Test
    fun `same-node regex extracts and normalizes fare`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.FARE,
                    normalizer = Normalizer.CURRENCY,
                    strategies = listOf(ExtractStrategy(regexOnSameNode = """\$\s*([0-9.,]+)""")),
                ),
            ),
        )
        val card = engine.evaluate(snapshot(node("Nuevo viaje"), node("$ 99.90", index = 1)), spec)!!
        assertEquals(99.9, card.fare!!, 1e-9)
        assertEquals("99.90", card.raw[FieldNames.FARE])
    }

    @Test
    fun `viewId hint scopes the candidate then regex extracts`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.FARE,
                    normalizer = Normalizer.CURRENCY,
                    strategies = listOf(
                        ExtractStrategy(viewIdContains = "fare_value", regexOnSameNode = """\$\s*([0-9.,]+)"""),
                    ),
                ),
            ),
        )
        val card = engine.evaluate(
            snapshot(
                node("Nuevo viaje"),
                node("$ 11.00", viewId = "x:id/decoy", index = 1),
                node("$ 50.25", viewId = "x:id/fare_value", index = 2),
            ),
            spec,
        )!!
        // The viewId hint must pick the fare_value node, not the decoy that appears first.
        assertEquals(50.25, card.fare!!, 1e-9)
    }

    @Test
    fun `afterAnchor takes the next node in reading order`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.PICKUP_DISTANCE_KM,
                    normalizer = Normalizer.DISTANCE_KM,
                    strategies = listOf(
                        ExtractStrategy(
                            afterAnchor = TextPattern("^Recoger$"),
                            extractRegex = """([0-9.,]+\s*(?:km|m)\b)""",
                        ),
                    ),
                ),
            ),
        )
        val card = engine.evaluate(
            snapshot(node("Nuevo viaje"), node("Recoger", index = 1), node("1.2 km", index = 2)),
            spec,
        )!!
        assertEquals(1.2, card.pickupDistanceKm!!, 1e-9)
    }

    @Test
    fun `belowAnchor uses geometry as the spatial signal`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.PAYMENT_TYPE,
                    normalizer = Normalizer.NONE,
                    strategies = listOf(ExtractStrategy(belowAnchor = TextPattern("^Pago$"))),
                ),
            ),
        )
        // 'Efectivo' sits directly below 'Pago'; 'Lejos' is far below and horizontally offset.
        val card = engine.evaluate(
            snapshot(
                node("Nuevo viaje"),
                node("Pago", index = 1, bounds = RectBox(0, 100, 200, 150)),
                node("Efectivo", index = 2, bounds = RectBox(0, 160, 200, 210)),
                node("Lejos", index = 3, bounds = RectBox(400, 600, 600, 650)),
            ),
            spec,
        )!!
        assertEquals("Efectivo", card.paymentType)
    }

    @Test
    fun `strategies fall through in priority order`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.FARE,
                    normalizer = Normalizer.CURRENCY,
                    strategies = listOf(
                        // First strategy can't match (no such viewId) → falls through to the regex.
                        ExtractStrategy(viewIdEquals = "nope", regexOnSameNode = """\$([0-9.]+)"""),
                        ExtractStrategy(regexOnSameNode = """\$\s*([0-9.,]+)"""),
                    ),
                ),
            ),
        )
        val card = engine.evaluate(snapshot(node("Nuevo viaje"), node("$ 42.00", index = 1)), spec)!!
        assertEquals(42.0, card.fare!!, 1e-9)
    }

    @Test
    fun `missing fields yield nulls not exceptions`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.FARE,
                    normalizer = Normalizer.CURRENCY,
                    strategies = listOf(ExtractStrategy(regexOnSameNode = """\$\s*([0-9.,]+)""")),
                ),
                FieldExtractor(
                    field = FieldNames.TRIP_DURATION_MIN,
                    normalizer = Normalizer.DURATION_MIN,
                    strategies = listOf(ExtractStrategy(afterAnchor = TextPattern("Duración"))),
                ),
            ),
        )
        val card = engine.evaluate(snapshot(node("Nuevo viaje"), node("$ 30.00", index = 1)), spec)!!
        assertEquals(30.0, card.fare!!, 1e-9)
        assertNull(card.tripDurationMin)
    }

    @Test
    fun `malformed regex in spec does not throw`() {
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.FARE,
                    strategies = listOf(ExtractStrategy(regexOnSameNode = "([unclosed")),
                ),
            ),
        )
        // Should degrade to "no fare" rather than blowing up the hot path.
        val card = engine.evaluate(snapshot(node("Nuevo viaje"), node("$ 10.00", index = 1)), spec)
        assertEquals(pkg, card?.platform)
        assertNull(card?.fare)
    }

    @Test
    fun `empty snapshot is not an offer card`() {
        val spec = ParserSpec(targetPackage = pkg, cardDetector = baseDetector)
        assertNull(engine.evaluate(ParserSnapshot(packageName = pkg, timestampMs = 0L), spec))
    }

    // --- List mode (B-035) ----------------------------------------------------------------------

    private fun bidNode(amount: String, index: Int) =
        node("$$amount", viewId = "x:id/bid_price", index = index, bounds = RectBox(0, index * 100, 600, index * 100 + 50))

    private val bidSpec = ParserSpec(
        targetPackage = pkg,
        cardDetector = baseDetector.copy(baseVariant = "single_bid"),
        listMode = ListMode(cardAnchor = TextPattern("""\$\s*[0-9][0-9.,]*""")),
        extractors = listOf(
            FieldExtractor(
                field = FieldNames.FARE,
                normalizer = Normalizer.CURRENCY,
                strategies = listOf(ExtractStrategy(viewIdContains = "bid", regexOnSameNode = """\$\s*([0-9.,]+)""")),
            ),
        ),
    )

    @Test
    fun `non-list spec evaluateAll returns exactly the single card evaluate would`() {
        // Backward compat: a spec without listMode must behave identically through both entry points.
        val spec = ParserSpec(
            targetPackage = pkg,
            cardDetector = baseDetector,
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.FARE,
                    normalizer = Normalizer.CURRENCY,
                    strategies = listOf(ExtractStrategy(regexOnSameNode = """\$\s*([0-9.,]+)""")),
                ),
            ),
        )
        val snap = snapshot(node("Nuevo viaje"), node("$ 42.00", index = 1))
        val all = engine.evaluateAll(snap, spec)
        assertEquals(1, all.size)
        assertEquals(engine.evaluate(snap, spec), all.single())
    }

    @Test
    fun `list mode evaluateAll segments by the repeating card anchor`() {
        val snap = snapshot(
            node("Nuevo viaje"),
            bidNode("100", index = 1),
            bidNode("80", index = 2),
            bidNode("60", index = 3),
        )
        val cards = engine.evaluateAll(snap, bidSpec)
        assertEquals(3, cards.size)
        assertEquals(listOf(100.0, 80.0, 60.0), cards.map { it.fare })
    }

    @Test
    fun `list mode evaluate returns the top bid and stamps additional_bids`() {
        val snap = snapshot(
            node("Nuevo viaje"),
            bidNode("100", index = 1),
            bidNode("80", index = 2),
        )
        val top = engine.evaluate(snap, bidSpec)!!
        assertEquals(100.0, top.fare!!, 1e-9)
        assertEquals("1", top.raw[FieldNames.ADDITIONAL_BIDS])
    }

    @Test
    fun `list mode with a single matching anchor is one card without additional_bids`() {
        val snap = snapshot(node("Nuevo viaje"), bidNode("90", index = 1))
        val top = engine.evaluate(snap, bidSpec)!!
        assertEquals(90.0, top.fare!!, 1e-9)
        assertNull(top.raw[FieldNames.ADDITIONAL_BIDS])
        assertEquals(1, engine.evaluateAll(snap, bidSpec).size)
    }

    @Test
    fun `list mode segments do not bleed fields across cards`() {
        // Each card has its OWN bid_price node; segment isolation must keep them separate.
        val snap = snapshot(
            node("Nuevo viaje"),
            bidNode("100", index = 1),
            bidNode("70", index = 2),
        )
        val cards = engine.evaluateAll(snap, bidSpec)
        assertEquals(100.0, cards[0].fare!!, 1e-9)
        assertEquals(70.0, cards[1].fare!!, 1e-9)
    }
}
