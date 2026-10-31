package mx.kompara.ocr

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Round 3 (B7): the floating Radar pill is host chrome, never instance identity (A3 (i)). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrInstanceEvidenceTest {
    private fun fixture(name: String): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/$name")!!.readText())
        return List(json.length()) { i ->
            val o = json.getJSONObject(i)
            OcrBlock(o.getString("text"), OcrBounds(o.getInt("left"), o.getInt("top"), o.getInt("right"), o.getInt("bottom")))
        }
    }

    @Test fun `the Radar pill is never the heading, a card-band start or a label`() {
        val blocks = fixture("radar_me_interesa_s25_2026_10_30.json")
        val pill = blocks.single { it.text.startsWith("Radar de solicitud") }.bounds
        val header = blocks.single { it.text == "UberX" }.bounds
        val evidence = instanceEvidence(blocks, blocks)
        assertEquals("The UberX header is the heading, not the pill above it",
            listOf(header.left, header.top, header.right, header.bottom),
            evidence.headingBounds!!.let { listOf(it.left, it.top, it.right, it.bottom) })
        assertEquals("The card band starts at the UberX header", header.top, evidence.cardBands.single().top)
        assertFalse("No card band starts at the pill", evidence.cardBands.any { it.top <= pill.bottom })
        assertFalse("The pill is not a label", evidence.labels.any { "radar" in it })
        assertEquals("UBERX", evidence.product)
        assertEquals("ME INTERESA", evidence.action)
    }
}
