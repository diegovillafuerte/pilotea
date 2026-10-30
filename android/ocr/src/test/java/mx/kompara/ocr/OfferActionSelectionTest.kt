package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S25 offer transcription with fake address/background prose, never a measured new screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferActionSelectionTest {
    private val parser = UberOcrParser()

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return ChipMask.maskOwnChip(List(json.length()) { i -> json.getJSONObject(i).let {
            OcrBlock(it.getString("text"), OcrBounds(it.getInt("left"), it.getInt("top"),
                it.getInt("right"), it.getInt("bottom")))
        } }, ScreenRect(325, 242, 1040, 572))
    }

    @Test fun `background swipe prose cannot veto first offer admission`() {
        val blocks = fixture() + OcrBlock("Desliza para aceptar", OcrBounds(96, 140, 980, 220))
        val parsed = parser.parse(blocks, hostConfirmed = true)!!
        assertEquals(79.45, parsed.fare!!, 0.001)
        assertEquals(24.0, parsed.tripDurationMin!!, 0.001)
        val admitted = OcrOfferSessionTracker().update(parsed, 1_000).card
        assertNotNull("Background prose is not an obscured preceding card action", admitted)
        assertEquals(1, admitted!!.instanceEvidence!!.visibleCards)
    }

    @Test fun `fake address containing swipe prose cannot truncate route or action`() {
        val blocks = fixture().map {
            if (it.text.startsWith("Calle Falsa")) it.copy(text = "Calle Falsa 123, Desliza para aceptar") else it
        }
        val parsed = parser.parse(blocks, hostConfirmed = true)!!
        assertEquals(79.45, parsed.fare!!, 0.001)
        assertEquals("The actual destination leg remains inside the offer", 24.0, parsed.tripDurationMin)
        assertEquals(1989, parsed.instanceEvidence!!.actionBounds!!.top)
        assertNotNull(OcrOfferSessionTracker().update(parsed, 1_000).card)
    }

    @Test fun `heading and instructional prose alone are not an offer action signature`() {
        val blocks = listOf(OcrBlock("Uber Priority", OcrBounds(96, 650, 500, 746)),
            OcrBlock("Desliza para aceptar", OcrBounds(96, 140, 980, 220)))
        assertFalse(parser.hasCardSignature(blocks, hostConfirmed = true))
        assertNull(parser.parse(blocks, hostConfirmed = true))
    }

    @Test fun `real standalone and amount-bearing action labels still admit the offer`() {
        for (label in listOf("Aceptar", "COINCIDIR", "Aceptar $79.45", "Aceptar\nMXN 79.45")) {
            val parsed = parser.parse(fixture().map { if (it.text == "Aceptar") it.copy(text = label) else it },
                hostConfirmed = true)!!
            assertNotNull(label, OcrOfferSessionTracker().update(parsed, 1_000).card)
            assertEquals(label, 1, parsed.instanceEvidence!!.visibleCards)
            assertEquals(label, 1989, parsed.instanceEvidence!!.actionBounds!!.top)
        }
    }

    @Test fun `genuine preceding card action still withholds ambiguous selection`() {
        val parsed = parser.parse(fixture() + OcrBlock("Aceptar", OcrBounds(96, 140, 400, 220)),
            hostConfirmed = true)!!
        assertFalse(parsed.instanceEvidence!!.selectionCertain)
        assertNull(OcrOfferSessionTracker().update(parsed, 1_000).card)
    }

    @Test fun `background instructional prose is not protected as an offer button`() {
        val background = OcrBlock("Desliza para aceptar", OcrBounds(96, 140, 980, 220))
        val parsed = selectOfferCard(parser, DidiOcrParser(), fixture() + background,
            capturedAtMs = 1_000, displayBounds = HostRect(0, 0, 1080, 2340), density = 3f,
            foregroundPackage = UberOcrParser.UBER_PACKAGE)!!
        assertFalse(parsed.hostGeometry!!.protectedControls.contains(HostRect(96, 140, 980, 220)))
        assertTrue(parsed.hostGeometry!!.protectedControls.contains(HostRect(442, 1989, 640, 2046)))
    }
}
