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

/** Adversarial edits to the S25 transcription; its addresses remain fictitious. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberFareSafetyTest {
    private val parser = UberOcrParser()

    private fun fixture(fare: String = "$79.45"): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        val blocks = List(json.length()) { index ->
            val b = json.getJSONObject(index)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }.map { if (it.text == "$79.45") it.copy(text = fare) else it }
        return ChipMask.maskOwnChip(blocks, ScreenRect(325, 242, 1040, 572))
    }

    @Test fun `split rate at original height cannot become driver payment`() {
        val blocks = fixture("S79.45").filterNot { it.text.contains("/km") } + listOf(
            block("25 min • 2.6 km •", 96, 926, 510, 988),
            block("$30.56", 520, 926, 690, 988),
            block("/km est.", 700, 926, 858, 988),
        )
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `split bonus at original height cannot become driver payment`() {
        val blocks = fixture("S79.45").filterNot { it.text == "+$16.00 incluido" } + listOf(
            block("+", 476, 1123, 510, 1212),
            block("$16.00", 520, 1123, 700, 1212),
            block("incluido", 710, 1123, 959, 1212),
        )
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `bare dollar with terminal MXN remains a fare`() {
        assertEquals(79.45, parser.parse(fixture("$79.45 MXN"))!!.fare)
    }

    @Test fun `legacy MX dollar with terminal MXN remains a fare`() {
        assertEquals(79.45, parser.parse(fixture("MX$79.45 MXN"))!!.fare)
    }

    @Test fun `terminal MXN without currency prefix remains a fare`() {
        assertEquals(79.45, parser.parse(fixture("79.45 MXN"))!!.fare)
    }

    @Test fun `garbled fare with split rate and bonus never yields either amount`() {
        val blocks = fixture("unreadable").filterNot {
            it.text.contains("/km") || it.text == "+$16.00 incluido"
        } + listOf(
            block("$30.56", 520, 926, 690, 988),
            block("/km est.", 700, 926, 858, 988),
            block("+", 476, 1123, 510, 1212),
            block("$16.00", 520, 1123, 700, 1212),
            block("incluido", 710, 1123, 959, 1212),
        )
        val card = parser.parse(blocks)!!
        assertTrue(parser.hasCardSignature(blocks))
        assertNull(card.fare)
    }

    @Test fun `S currency OCR substitution is deliberately not recovered`() {
        for (text in listOf("S79.45", "S 79.45", "S1,234.56", "S79.45 MXN", "S79.45/km")) {
            assertNull(text, parser.parse(fixture(text))!!.fare)
        }
    }

    @Test fun `headline amount is rejected by labels in the same visual row`() {
        for (text in listOf("/km", "km est", "est.", "incluido", "para inicio", "tu meta", "tu mínimo")) {
            val blocks = fixture() + block(text, 520, 796, 800, 850)
            assertNull(text, parser.parse(blocks.reversed())!!.fare)
        }
    }

    @Test fun `a separate leading plus to the immediate left rejects the headline`() {
        assertNull(parser.parse(fixture() + block("+", 64, 810, 90, 858))!!.fare)
    }

    @Test fun `a vertically offset plus immediately left still rejects the headline`() {
        // Only 10px overlaps the fare: a strict visual-row overlap test must not hide the plus.
        val blocks = fixture() + block("+", 64, 740, 90, 780)
        assertNull(parser.parse(blocks)!!.fare)
        assertNull(parser.parse(blocks.reversed())!!.fare)
    }

    @Test fun `an offset rate suffix immediately right still rejects the headline`() {
        // The suffix overlaps the fare by only 7px and belongs to no full headline row.
        val blocks = fixture() + block("/km est.", 510, 890, 850, 915)
        assertNull(parser.parse(blocks)!!.fare)
        assertNull(parser.parse(blocks.reversed())!!.fare)
    }

    @Test fun `closely attached context above or below a headline rejects it`() {
        for (text in listOf("/km est.", "incluido", "para inicio", "tu mínimo")) {
            for (label in listOf(
                block(text, 96, 744, 450, 764),
                block(text, 96, 903, 450, 923),
            )) {
                assertNull("$text at ${label.bounds.top}", parser.parse(fixture() + label)!!.fare)
            }
        }
    }

    @Test fun `split own UI labels on the amount row reject payment`() {
        for (label in listOf("meta", "mínimo")) {
            val blocks = fixture() + listOf(
                block("tu", 520, 800, 565, 850),
                block(label, 575, 800, 800, 850),
            )
            assertNull(label, parser.parse(blocks)!!.fare)
        }
    }

    @Test fun `suffix fares retain split contextual exclusions`() {
        for (fare in listOf("$79.45 MXN", "MX$79.45 MXN", "79.45 MXN")) {
            assertNull(fare, parser.parse(fixture(fare) + block("/km est.", 520, 800, 850, 860))!!.fare)
        }
    }

    @Test fun `a body sized standalone amount cannot substitute for the missing headline`() {
        val blocks = fixture().map {
            if (it.text == "$79.45") it.copy(bounds = it.bounds.copy(bottom = it.bounds.top + 80)) else it
        }
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `missing geometry cannot establish a confident headline`() {
        val blocks = fixture().map { it.copy(bounds = OcrBounds(0, 0, 0, 0)) }
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `a headline amount below route legs cannot become payment`() {
        val blocks = fixture("unreadable") + block("$999.99", 96, 1730, 499, 1900)
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `two competing headline amounts with no distinguishing context are ambiguous`() {
        val blocks = fixture() + block("$99.99", 550, 770, 950, 897)
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `a larger original amount cannot win over a smaller amount on its headline row`() {
        // Strikethrough styling is absent from OcrBlock. Neither relative size nor amount value
        // establishes which of these two otherwise clean amounts is the current driver payment.
        for ((large, small) in listOf("$99.99" to "$79.45", "$79.45" to "$99.99")) {
            val blocks = fixture(large) + block(small, 550, 788, 950, 878)
            for (ordered in listOf(blocks, blocks.reversed(), blocks.sortedBy { it.text })) {
                val card = parser.parse(ordered)!!
                assertNull(card.fare)
                assertNull(card.raw["fare"])
            }
        }
    }

    @Test fun `separate amounts before metadata are ambiguous even when one is body sized`() {
        for ((large, small) in listOf("$99.99" to "$79.45", "$79.45" to "$99.99")) {
            val blocks = fixture(large).map {
                if (it.bounds.top >= 926) {
                    it.copy(bounds = it.bounds.copy(top = it.bounds.top + 150, bottom = it.bounds.bottom + 150))
                } else it
            } + block(small, 96, 927, 499, 1017)
            for (ordered in listOf(blocks, blocks.reversed())) {
                val card = parser.parse(ordered)!!
                assertNull(card.fare)
                assertNull(card.raw["fare"])
            }
        }
    }

    @Test fun `a larger labeled original price never wins`() {
        for (label in listOf("antes", "precio original")) {
            val blocks = fixture() + listOf(
                block("$999.99", 96, 1230, 499, 1380),
                block(label, 540, 1260, 850, 1320),
            )
            assertEquals(label, 79.45, parser.parse(blocks)!!.fare)
        }
    }

    @Test fun `a struck amount retaining its stroke glyphs is not an anchored fare`() {
        val struck = "$9\u03369\u03369\u0336.9\u03369\u0336"
        val blocks = fixture() + block(struck, 96, 1220, 499, 1400)
        assertEquals(79.45, parser.parse(blocks)!!.fare)
    }

    @Test fun `a chip rect over the headline removes it before fare selection`() {
        val blocks = ChipMask.maskOwnChip(fixture(), ScreenRect(90, 760, 510, 910))
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `explicit card bounds exclude a headline crossing a card edge`() {
        val bounds = OcrBounds(80, 640, 1000, 2080)
        for (fareBounds in listOf(
            OcrBounds(79, 770, 499, 897),
            OcrBounds(600, 770, 1001, 897),
            OcrBounds(96, 512, 499, 639),
            OcrBounds(96, 2081, 499, 2208),
        )) {
            val blocks = fixture().map {
                if (it.text == "$79.45") it.copy(bounds = fareBounds) else it
            }
            assertTrue(parser.hasCardSignature(blocks, cardBounds = bounds))
            assertNull(fareBounds.toString(), parser.parse(blocks, cardBounds = bounds)!!.fare)
        }
        assertEquals(79.45, parser.parse(fixture(), cardBounds = bounds)!!.fare)
    }

    @Test fun `a lateral map amount cannot expand the inferred card`() {
        val blocks = fixture("unreadable") + block("$999.99", 1050, 770, 1450, 897)
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `the direct parser masks a supplied chip rect inside the card`() {
        val chip = ScreenRect(90, 760, 510, 910)
        assertTrue(parser.hasCardSignature(fixture(), chipRect = chip))
        assertNull(parser.parse(fixture(), chipRect = chip)!!.fare)
    }

    @Test fun `Kompara own UI cannot supply a simulated Uber fare`() {
        val blocks = fixture() + listOf(
            block("Comparar", 100, 2200, 350, 2257),
            block("Ajustes", 700, 2200, 900, 2257),
        )
        assertFalse(parser.hasCardSignature(blocks))
        assertNull(parser.parse(blocks))
    }

    @Test fun `malformed money punctuation does not become a fare after cleanup`() {
        for (text in listOf("$7,9.45", "$79..45", "$1,23.45", "$1,,234.56", "MX$7,9.45 MXN")) {
            assertNull(text, parser.parse(fixture(text))!!.fare)
        }
    }

    @Test fun `hostless legacy MX dollar suffix retains its route signature`() {
        for (fare in listOf("MX$79.45", "MX$79.45 MXN")) {
            val blocks = listOf(
                block(fare, 96, 100, 499, 227),
                block("A 1 min (0.2 km)", 190, 300, 510, 357),
                block("Viaje: 24 min (2.4 km)", 190, 400, 641, 457),
                block("Aceptar", 442, 600, 640, 657),
            )
            assertTrue(parser.hasCardSignature(blocks))
            val card = parser.parse(blocks)!!
            assertEquals(fare, 79.45, card.fare)
            assertEquals("DRIVER_PAYMENT", card.paymentBasis)
        }
    }

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrBlock(text, OcrBounds(left, top, right, bottom))
}
