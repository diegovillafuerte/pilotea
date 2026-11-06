package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 2026-10-29 live S25 transcription; all address text is deliberately fictitious. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberPriorityS25Test {
    private val parser = UberOcrParser()
    private val chipRect = ScreenRect(325, 242, 1040, 572)

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { index ->
            val b = json.getJSONObject(index)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }
    }

    private fun cardBlocks() = ChipMask.maskOwnChip(fixture(), chipRect)

    @Test fun `live Priority exclusive offer reads fare and both unlabeled route legs`() {
        val blocks = cardBlocks()
        val card = parser.parse(blocks)!!
        assertTrue(parser.hasCardSignature(blocks))
        assertEquals(UberOcrParser.UBER_PACKAGE, card.platform)
        assertEquals("exclusive", card.variant)
        assertEquals(79.45, card.fare!!, 0.001)
        assertEquals(1.0, card.pickupEtaMin!!, 0.001)
        assertEquals(0.2, card.pickupDistanceKm!!, 0.001)
        assertEquals(24.0, card.tripDurationMin!!, 0.001)
        assertEquals(2.4, card.tripDistanceKm!!, 0.001)
        assertEquals("DRIVER_PAYMENT", card.paymentBasis)
        assertEquals(770, card.contentBounds!!.top)
        assertEquals(1682, card.contentBounds!!.bottom)
    }

    @Test fun `bare dollar thousands and old MX formats all parse`() {
        for ((text, expected) in listOf("$79.45" to 79.45, "$1,234.56" to 1234.56,
            "MX$79.45" to 79.45, "MXN 79.45" to 79.45)) {
            // 0.2.15: the thousands-separated amount carries its own consistent TOTAL rate ($1,234.56/2.6 km).
            val blocks = cardBlocks().map { if (it.text == "$79.45") it.copy(text = text) else it }
                .let { if (expected > 1000) it.withTotalRow("25 min • 2.6 km • $474.83/km est.") else it }
            assertEquals(text, expected, parser.parse(blocks)!!.fare!!, 0.001)
        }
    }

    @Test fun `only bonuses rates and chip amounts never supply a fare`() {
        val raw = fixture().filterNot { it.text == "$79.45" }
        val masked = ChipMask.maskOwnChip(raw, chipRect)
        assertFalse(masked.any { it.text.startsWith("tu ") })
        assertNull(parser.parse(masked)!!.fare)
        assertTrue(parser.hasCardSignature(masked))
    }

    @Test fun `a standalone amount inside own chip region is masked before parsing`() {
        val raw = fixture().filterNot { it.text == "$79.45" } +
            OcrBlock("MX$999.99", OcrBounds(340, 250, 1000, 560))
        assertNull(parser.parse(ChipMask.maskOwnChip(raw, chipRect))!!.fare)
    }

    @Test fun `card with only plus prefixed amounts has no fare`() {
        val blocks = cardBlocks().filterNot { it.text.contains("$") && !it.text.startsWith("+") }
        assertTrue(blocks.filter { it.text.contains("$") }.all { it.text.startsWith("+") })
        assertTrue(parser.hasCardSignature(blocks))
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `fare selection uses glyph height then reading order rather than amount value`() {
        val blocks = cardBlocks() + listOf(
            OcrBlock("$999.99", OcrBounds(96, 1095, 499, 1120)),
            OcrBlock("$88.88", OcrBounds(96, 1220, 499, 1347)),
        )
        assertEquals(79.45, parser.parse(blocks.reversed())!!.fare!!, 0.001)
    }

    @Test fun `map currency outside the card is never its fare`() {
        val blocks = cardBlocks() + listOf(
            OcrBlock("$999.99", OcrBounds(96, 50, 499, 240)),
            OcrBlock("MX$888.88", OcrBounds(96, 2100, 499, 2290)),
        )
        assertEquals(79.45, parser.parse(blocks)!!.fare!!, 0.001)
    }

    @Test fun `only a whole amount block may be the fare`() {
        for (text in listOf("+$79.45", "+ $79.45", "+MX$79.45", "$79.45/km est.",
            "MX$79.45/km est.", "$79.45 incluido", "MX$79.45 incluido", "tu meta MX$79.45")) {
            val blocks = cardBlocks().map { if (it.text == "$79.45") it.copy(text = text) else it }
            assertNull(text, parser.parse(blocks)!!.fare)
        }
    }

    // 0.2.14: under hold-last-good a legless host+action signature would pin a stale chip on idle
    // frames; heading and action now need one visible leg (the fare still needs none of them).
    @Test fun `Priority heading and Accept action retain host signature with one leg but no readable fare`() {
        val anchors = cardBlocks().filter { it.text in setOf("Uber Priority", "Exclusivo", "Aceptar") }
        assertFalse(parser.hasCardSignature(anchors))
        val leg = cardBlocks().filter { it.text == "1 min (0.2 km)" }
        assertTrue(parser.hasCardSignature(anchors + leg))
        assertEquals("exclusive", parser.parse(anchors + leg)!!.variant)
        assertFalse(parser.hasCardSignature((anchors + leg).filterNot { it.text == "Aceptar" }))
        assertFalse(parser.hasCardSignature((anchors + leg).filterNot { it.text == "Uber Priority" }))
    }
}
