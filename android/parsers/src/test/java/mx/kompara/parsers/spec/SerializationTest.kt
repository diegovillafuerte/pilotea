package mx.kompara.parsers.spec

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.normalize.Normalizer
import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.snapshot.RectBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerializationTest {

    @Test
    fun `spec round-trips through json`() {
        val spec = ParserSpec(
            targetPackage = "com.kompara.demo",
            versionCodeRange = VersionRange(min = 100, max = 999),
            specVersion = 3,
            cardDetector = CardDetector(
                allOf = listOf(TextPattern("Nuevo viaje")),
                anyOf = listOf(TextPattern("Aceptar")),
                noneOf = listOf(TextPattern("Viaje en curso")),
                baseVariant = "standard",
            ),
            extractors = listOf(
                FieldExtractor(
                    field = FieldNames.FARE,
                    normalizer = Normalizer.CURRENCY,
                    strategies = listOf(
                        ExtractStrategy(afterAnchor = TextPattern("^Tarifa$"), extractRegex = """\$([0-9.,]+)"""),
                        ExtractStrategy(regexOnSameNode = """\$([0-9.,]+)"""),
                    ),
                ),
            ),
            variants = listOf(
                VariantRule(tag = "surge", whenAnyOf = listOf(TextPattern("din[aá]mica")), setsSurge = true),
            ),
        )
        val decoded = SpecJson.decodeSpec(SpecJson.encodeSpec(spec))
        assertEquals(spec, decoded)
    }

    @Test
    fun `snapshot round-trips through json including bounds surrogate`() {
        val snapshot = ParserSnapshot(
            packageName = "com.kompara.demo",
            timestampMs = 1700000000000L,
            versionCode = 220L,
            nodes = listOf(
                ParserNode(
                    text = "Tarifa",
                    viewId = "x:id/fare",
                    className = "android.widget.TextView",
                    bounds = RectBox(40, 200, 200, 250),
                    depth = 3,
                    index = 1,
                ),
            ),
        )
        val decoded = SpecJson.decodeSnapshot(SpecJson.encodeSnapshot(snapshot))
        assertEquals(snapshot, decoded)
        assertEquals(160, decoded.nodes[0].bounds.width)
        assertEquals(50, decoded.nodes[0].bounds.height)
    }

    @Test
    fun `offer card round-trips through json`() {
        val card = OfferCard(
            platform = "com.kompara.demo",
            variant = "surge",
            fare = 1234.56,
            pickupDistanceKm = 0.85,
            pickupEtaMin = 3.0,
            tripDistanceKm = 12.0,
            tripDurationMin = 65.0,
            surge = true,
            paymentType = "Tarjeta",
            raw = mapOf("fare" to "$1,234.56"),
        )
        assertEquals(card, SpecJson.decodeOfferCard(SpecJson.encodeOfferCard(card)))
    }

    @Test
    fun `unknown keys are ignored for forward compatibility`() {
        val json = """
            {
              "targetPackage": "com.kompara.demo",
              "futureField": "ignored",
              "cardDetector": { "allOf": [ { "regex": "Nuevo viaje" } ] }
            }
        """.trimIndent()
        val spec = SpecJson.decodeSpec(json)
        assertEquals("com.kompara.demo", spec.targetPackage)
        assertEquals(1, spec.specVersion) // default applied
    }

    @Test
    fun `version range bounds behave inclusively and openly`() {
        assertEquals(true, VersionRange().contains(123L)) // open both ends
        assertEquals(true, VersionRange(min = 100, max = 200).contains(100L))
        assertEquals(true, VersionRange(min = 100, max = 200).contains(200L))
        assertEquals(false, VersionRange(min = 100, max = 200).contains(99L))
        assertEquals(false, VersionRange(min = 100, max = 200).contains(201L))
        assertEquals(true, VersionRange(min = 100).contains(null)) // unknown not excluded
    }
}
