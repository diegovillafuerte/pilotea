package mx.kompara.ocr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 0.2.19: on a dimmed DiDi card ML Kit can merge the round row icon into a leg row as a leading digit
 * (`925 min (7.4 km)` for 25). Only a leg of at least 60 minutes implying under 2 km/h is questioned: an
 * icon-glued row whose minutes begin its block drops that digit when the result is plausible, any other
 * questioned row's minutes stay unread. Real frames are sanitized S25 captures (`didi/icon-glue/`).
 */
class DidiIconGlueTest {
    private val parser = DidiOcrParser()
    private val fare = block("\$81.90", 84, 972, 481, 1086)

    private fun frame(name: String): List<OcrBlock> {
        val root = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/didi/icon-glue/$name")) { "missing $name" }.readText())
        val rows = if (root is JsonArray) root else root.jsonObject.getValue("owned").jsonArray
        return rows.map { row ->
            val o = row.jsonObject
            fun edge(key: String) = o.getValue(key).jsonPrimitive.int
            block(o.getValue("text").jsonPrimitive.content, edge("left"), edge("top"), edge("right"), edge("bottom"))
        }
    }

    private fun card(vararg rows: OcrBlock) = parser.parse(listOf(fare) + rows)!!

    private fun leg(text: String, left: Int, top: Int = 1700, height: Int = 56) = block(text, left, top, left + 420, top + height)

    /** An address line 14 px below a 56 px row at the default top. */
    private fun address(left: Int, top: Int = 1770) = block("Calle Inventada 123", left, top, 900, top + 48)

    @Test fun `both frames of the expired card read the true legs and the fare is unchanged`() {
        for (name in listOf("ocr_1794168170556.json", "ocr_1794168171357.json")) {
            val card = parser.parse(frame(name))!!
            assertEquals(name, 81.90, card.fare!!, 0.001)
            assertEquals(name, 4.0, card.pickupEtaMin!!, 0.001)
            assertEquals(name, 0.555, card.pickupDistanceKm!!, 0.001)
            assertEquals(name, 25.0, card.tripDurationMin!!, 0.001)
            assertEquals(name, 7.4, card.tripDistanceKm!!, 0.001)
        }
    }

    /** Each June frame shows one leg row. A fare block supplies the signature: the first frame's fare row is unreadable. */
    @Test fun `June frames read the true minutes`() {
        for ((name, min, km) in listOf(Triple("ocr_1781565520456.json", 14.0, 2.9),
            Triple("ocr_1781565787714.json", 35.0, 7.2), Triple("ocr_1781578834833.json", 32.0, 12.8))) {
            val card = parser.parse(frame(name) + fare)!!
            assertEquals(name, min, card.pickupEtaMin!!, 0.001)
            assertEquals(name, km, card.pickupDistanceKm!!, 0.001)
        }
    }

    @Test fun `the slowest genuine leg at the text column stays as read`() {
        assertEquals(44.0, card(leg("44 min (1.6 km)", 156), address(152)).pickupEtaMin!!, 0.001)
    }

    @Test fun `a spaced icon prefix keeps reading the row's own minutes`() {
        assertEquals(7.0, card(leg("9 7 min (1.5 km)", 97), address(152)).pickupEtaMin!!, 0.001)
    }

    @Test fun `a plausible speed is never questioned, even left of the column`() {
        assertEquals(91.0, card(leg("91 min (6.1 km)", 120), address(152)).pickupEtaMin!!, 0.001)
    }

    @Test fun `a legitimately long trip stays as read at the column and left of it`() {
        for (left in listOf(156, 97)) for (text in listOf("125 min (90 km)", "125 min (90.0 km)"))
            assertEquals("$text at $left", 125.0, card(leg(text, left), address(152)).pickupEtaMin!!, 0.001)
    }

    /** A June $139.39 card, 24 frames: `13km` is DiDi's whole kilometres, reads 13 km (0.2.20), and its 55 minutes stand. */
    @Test fun `a leg under an hour is never questioned when its distance has no decimal`() {
        val read = card(leg("55min (13km)", 140), address(138))
        assertEquals(55.0, read.pickupEtaMin!!, 0.001)
        assertEquals(13.0, read.pickupDistanceKm!!, 0.001)
    }

    /** A real June row: at 1.4 km it would be questioned, at the printed 14 km it is not, and it reads 14 (0.2.20). */
    @Test fun `a long leg with no decimal is judged at its printed distance`() {
        val read = card(leg("243min (14km)", 156), address(152))
        assertEquals(243.0, read.pickupEtaMin!!, 0.001)
        assertEquals(14.0, read.pickupDistanceKm!!, 0.001)
    }

    @Test fun `a questioned leg at the text column has unread minutes and keeps its distance`() {
        val read = card(leg("125 min (0.5 km)", 156), address(152))
        assertNull(read.pickupEtaMin)
        assertEquals(0.5, read.pickupDistanceKm!!, 0.001)
    }

    @Test fun `low speed alone proves nothing under an hour`() {
        for ((text, min, km) in listOf(Triple("2 min (50 m)", 2.0, 0.05), Triple("5 min (50 m)", 5.0, 0.05),
            Triple("55 min (1.3 km)", 55.0, 1.3), Triple("25 min (0.5 km)", 25.0, 0.5))) {
            val read = card(leg(text, 156), address(152), leg("25 min (7.4 km)", 156, top = 1903))
            assertEquals(text, min, read.pickupEtaMin!!, 0.001)
            assertEquals(text, km, read.pickupDistanceKm!!, 0.001)
            assertEquals(text, 25.0, read.tripDurationMin!!, 0.001)
        }
    }

    @Test fun `a glued single digit under an hour is not questioned`() {
        val read = card(leg("9 min (0.1 km)", 97), address(152))
        assertEquals(9.0, read.pickupEtaMin!!, 0.001)
        assertEquals(0.1, read.pickupDistanceKm!!, 0.001)
    }

    @Test fun `a separately read icon leaves two-digit minutes under an hour alone`() {
        val read = card(leg("9 12 min (300 m)", 97), address(152), leg("25 min (7.4 km)", 156, top = 1903))
        assertEquals(12.0, read.pickupEtaMin!!, 0.001)
        assertEquals(0.3, read.pickupDistanceKm!!, 0.001)
        assertEquals(25.0, read.tripDurationMin!!, 0.001)
    }

    @Test fun `a questioned row with the icon read separately is never stripped`() {
        for (text in listOf("9 94 min (555 m)", "O 94 min (555 m)", "9 925 min (7.4 km)")) {
            assertNull(text, card(leg(text, 97), address(152)).pickupEtaMin)
        }
        assertEquals(0.555, card(leg("9 94 min (555 m)", 97), address(152)).pickupDistanceKm!!, 0.001)
    }

    @Test fun `leading whitespace does not separate the minutes from the block's start`() {
        assertEquals(4.0, card(leg("  94 min (555 m)", 97), address(152)).pickupEtaMin!!, 0.001)
    }

    @Test fun `a row is questioned only from 60 minutes and below 2 km per hour`() {
        assertEquals("59 minutes", 59.0, card(leg("59 min (0.5 km)", 156), address(152)).pickupEtaMin!!, 0.001)
        assertNull("60 minutes", card(leg("60 min (0.5 km)", 156), address(152)).pickupEtaMin)
        assertEquals("exactly 2 km/h", 60.0, card(leg("60 min (2.0 km)", 156), address(152)).pickupEtaMin!!, 0.001)
        assertNull("just under 2 km/h", card(leg("60 min (1.9 km)", 156), address(152)).pickupEtaMin)
    }

    @Test fun `a strip that stays implausible leaves the minutes unread`() {
        for (text in listOf("919 min (0.1 km)", // 19 min is still 0.3 km/h
            "901 min (30.0 km)", // 1 min would be 1,800 km/h
            "90 min (0.1 km)")) { // nothing left to read
            assertNull(text, card(leg(text, 97), address(152)).pickupEtaMin)
        }
    }

    @Test fun `no reference row means not icon-glued`() {
        assertNull(card(leg("925 min (7.4 km)", 93)).pickupEtaMin)
    }

    @Test fun `the other leg is the fallback reference when no address line was read`() {
        val glued = card(leg("94 min (555 m)", 97, top = 1691), leg("25 min (7.4 km)", 156, top = 1903))
        assertEquals(4.0, glued.pickupEtaMin!!, 0.001)
        assertEquals(25.0, glued.tripDurationMin!!, 0.001)
        val gluedTrip = card(leg("4 min (555 m)", 156, top = 1691), leg("925 min (7.4 km)", 93, top = 1903))
        assertEquals(4.0, gluedTrip.pickupEtaMin!!, 0.001)
        assertEquals(25.0, gluedTrip.tripDurationMin!!, 0.001)
    }

    @Test fun `two glued legs and no address line prove nothing`() {
        val read = card(leg("94 min (555 m)", 97, top = 1691), leg("925 min (7.4 km)", 93, top = 1903))
        assertNull(read.pickupEtaMin)
        assertNull(read.tripDurationMin)
    }

    @Test fun `text beyond the row's address lines or beside it is no reference`() {
        assertNull("the action button two rows down", card(leg("925 min (7.4 km)", 93),
            block("Aceptar \$81.90", 357, 1700 + 56 + 2 * 56 + 1, 717, 1950)).pickupEtaMin)
        assertNull("a map label to the right", card(leg("925 min (7.4 km)", 93),
            block("Calle Inventada", 600, 1770, 900, 1818)).pickupEtaMin)
    }

    @Test fun `an address line below an unread first line still sets the column`() {
        assertEquals(25.0, card(leg("925 min (7.4 km)", 93), address(152, top = 1700 + 56 + 2 * 56)).pickupEtaMin!!, 0.001)
    }

    @Test fun `icon-glued is relative to the row's own height`() {
        fun read(column: Int, left: Int = 66, height: Int = 40) =
            card(leg("925 min (7.4 km)", left, height = height), block("Calle Inventada 123", column, 1700 + height + 10, 900, 1800)).pickupEtaMin
        assertEquals("exactly half the row's height", 25.0, read(86)!!, 0.001)
        assertNull("one pixel short of it", read(85))
        assertEquals("half of a taller row", 25.0, read(94, height = 56)!!, 0.001)
        assertNull("one pixel short of that", read(93, height = 56))
    }

    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) = OcrBlock(text, OcrBounds(l, t, r, b))
}
