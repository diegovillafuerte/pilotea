package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberFareFragmentTest {
    private val parser = UberOcrParser()

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return ChipMask.maskOwnChip(List(json.length()) { index ->
            val b = json.getJSONObject(index)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }, ScreenRect(325, 242, 1040, 572))
    }

    @Test fun `separate terminal currency cannot hide the competing current price`() {
        val blocks = fixture().map {
            when {
                it.text == "$79.45" -> it.copy(text = "$99.99")
                it.bounds.top >= 926 -> it.copy(bounds = it.bounds.copy(
                    top = it.bounds.top + 150, bottom = it.bounds.bottom + 150))
                else -> it
            }
        } + listOf(block("$79.45", 96, 927, 499, 1017), block("MXN", 520, 945, 650, 995))
        for (ordered in listOf(blocks, blocks.reversed())) {
            val guard = FrameOutlierGuard()
            val stabilizer = FareStabilizer()
            repeat(5) { frame ->
                val card = parser.parse(ordered)!!
                assertEquals("DRIVER_PAYMENT", card.paymentBasis)
                assertTrue(guard.accept(card, frame * 300L))
                assertNull(stabilizer.onParsed(card, frame * 300L).fare)
            }
        }
    }

    @Test fun `a separate terminal currency remains part of a single headline`() {
        assertEquals(79.45, parser.parse(fixture() + block("MXN", 520, 796, 650, 850))!!.fare)
    }

    @Test fun `missing or detached currency cannot hide a competing decimal headline`() {
        val prices = fixture().map {
            when {
                it.text == "$79.45" -> it.copy(text = "$99.99")
                it.bounds.top >= 926 -> it.copy(bounds = it.bounds.copy(
                    top = it.bounds.top + 150, bottom = it.bounds.bottom + 150))
                else -> it
            }
        } + block("79.45", 96, 927, 499, 1017)
        for (fragment in listOf(null,
            block("MXN", 520, 1007, 650, 1057), // Only 10px overlaps the amount.
            block("MXN", 720, 945, 850, 995), // Beyond a confident association gap.
            block("$", 20, 945, 70, 995), // Cropped by independently observed card bounds.
        )) {
            val blocks = prices + listOfNotNull(fragment)
            for (ordered in listOf(blocks, blocks.reversed())) {
                assertNull(parser.parse(ordered, OcrBounds(80, 640, 1000, 2340))!!.fare)
            }
        }
    }

    @Test fun `a bare decimal headline is ambiguity evidence but never payment`() {
        val blocks = fixture().map { if (it.text == "$79.45") it.copy(text = "79.45") else it }
        assertNull(parser.parse(blocks)!!.fare)
        // A rating below the summary is body metadata, not a competing headline.
        assertEquals(79.45, parser.parse(fixture().map {
            if (it.text == "4.81 (1141)") it.copy(text = "4.81") else it
        })!!.fare)
    }

    @Test fun `separate currency prefixes recover the amount without depending on input order`() {
        for (currency in listOf("MXN", "MX$", "$")) {
            val blocks = fixture().filterNot { it.text == "$79.45" } + listOf(
                block(currency, 96, 796, 215, 850),
                block("79.45", 230, 770, 633, 897),
            )
            for (ordered in listOf(blocks, blocks.reversed())) {
                assertEquals(currency, 79.45, parser.parse(ordered)!!.fare)
            }
        }
    }

    @Test fun `fragmented currencies preserve contextual exclusions`() {
        val money = fixture().filterNot { it.text == "$79.45" } + listOf(
            block("$", 96, 796, 125, 850), block("79.45", 140, 770, 499, 897),
        )
        for (label in listOf("/km est.", "incluido", "tu meta", "antes")) {
            assertNull(label, parser.parse(money + block(label, 520, 796, 850, 850))!!.fare)
        }
        assertNull(parser.parse(money + block("+", 64, 810, 90, 858))!!.fare)
    }

    @Test fun `a tall currency glyph cannot promote a body sized amount`() {
        val blocks = fixture().filterNot { it.text == "$79.45" } + listOf(
            block("$", 96, 770, 125, 897), block("79.45", 140, 796, 499, 850),
        )
        assertNull(parser.parse(blocks)!!.fare)
    }

    @Test fun `a second headline beyond inferred text width still prevents choosing the other price`() {
        assertNull(parser.parse(fixture() + block("$99.99", 550, 770, 1200, 897))!!.fare)
    }

    @Test fun `a wider captured headline survives a narrow Uber heading`() {
        assertEquals(137.28, parser.parse(wideHeadline())!!.fare)
    }

    @Test fun `validated card bounds take precedence over narrow supporting text`() {
        val blocks = wideHeadline() + block("Aceptar", 442, 1989, 640, 2046)
        assertEquals(137.28, parser.parse(blocks, OcrBounds(80, 640, 1000, 2080))!!.fare)
    }

    private fun wideHeadline() = listOf(
        block("Uber Priority", 96, 653, 502, 746),
        block("MXN137.28", 103, 785, 769, 881),
        block("A 5 min (1.0 km)", 192, 1339, 551, 1384),
        block("Viaje: 51 min (13.0 km)", 216, 1537, 687, 1590),
    )

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrBlock(text, OcrBounds(left, top, right, bottom))
}
