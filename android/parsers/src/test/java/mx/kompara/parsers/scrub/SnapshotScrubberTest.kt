package mx.kompara.parsers.scrub

import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.snapshot.RectBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotScrubberTest {

    private val scrubber = SnapshotScrubber()

    @Test
    fun `masks ten-digit mexican phone numbers in common formats`() {
        for (raw in listOf("55 1234 5678", "55-1234-5678", "5512345678", "+52 1 55 1234 5678")) {
            val out = scrubber.scrubText("Llama al $raw")
            assertFalse("phone leaked: $out", out.contains(Regex("""\d{4}""")))
            assertTrue(out.contains(SnapshotScrubber.PHONE_MASK))
        }
    }

    @Test
    fun `masks mexican license plates`() {
        for (plate in listOf("ABC-12-34", "ABC-1234", "XYZ1234", "123-ABC")) {
            val out = scrubber.scrubText("Placa $plate")
            assertTrue("plate not masked: $out", out.contains(SnapshotScrubber.PLATE_MASK))
            assertFalse("plate leaked: $out", out.contains(plate))
        }
    }

    @Test
    fun `masks exact street address but keeps colonia and zone`() {
        val out = scrubber.scrubText("Av. Reforma 222, Col. Juárez, Cuauhtémoc")
        assertFalse("street number leaked: $out", out.contains("222"))
        assertTrue("street-type word dropped: $out", out.contains("Av."))
        assertTrue("colonia must survive: $out", out.contains("Juárez"))
        assertTrue("zone must survive: $out", out.contains("Cuauhtémoc"))
        assertTrue(out.contains(SnapshotScrubber.ADDR_MASK))
    }

    @Test
    fun `masks labeled passenger name`() {
        val out = scrubber.scrubText("Pasajero: Juan Pérez García")
        assertFalse("name leaked: $out", out.contains("Juan"))
        assertTrue("label must survive: $out", out.contains("Pasajero"))
        assertTrue(out.contains(SnapshotScrubber.NAME_MASK))
    }

    @Test
    fun `masks standalone person-name run`() {
        val out = scrubber.scrubText("María López")
        assertEquals(SnapshotScrubber.NAME_MASK, out)
    }

    @Test
    fun `does not mask single capitalized word like a label`() {
        // 'Tarifa', 'Efectivo' etc. must survive — they're structural, not names.
        assertEquals("Tarifa", scrubber.scrubText("Tarifa"))
        assertEquals("Efectivo", scrubber.scrubText("Efectivo"))
    }

    @Test
    fun `preserves currency and distance text`() {
        // Offer-relevant numbers must not be eaten by the phone matcher.
        assertEquals("\$85.50", scrubber.scrubText("\$85.50"))
        assertEquals("3.2 km", scrubber.scrubText("3.2 km"))
        assertEquals("12 min", scrubber.scrubText("12 min"))
    }

    @Test
    fun `scrub walks every node and leaves structure intact`() {
        val snapshot = ParserSnapshot(
            packageName = "com.kompara.demo",
            timestampMs = 0L,
            nodes = listOf(
                ParserNode(text = "Pasajero: Ana Torres", viewId = "x:id/name", bounds = RectBox(0, 0, 10, 10), index = 0),
                ParserNode(text = "Tarifa $85.50", viewId = "x:id/fare", bounds = RectBox(0, 20, 10, 30), index = 1),
            ),
        )
        val scrubbed = scrubber.scrub(snapshot)
        assertFalse(scrubbed.nodes[0].text!!.contains("Ana"))
        // viewIds, bounds, index untouched.
        assertEquals("x:id/name", scrubbed.nodes[0].viewId)
        assertEquals(RectBox(0, 20, 10, 30), scrubbed.nodes[1].bounds)
        assertTrue(scrubbed.nodes[1].text!!.contains("85.50"))
    }

    @Test
    fun `null text node passes through unchanged`() {
        val node = ParserNode(text = null, viewId = "x:id/icon", bounds = RectBox(), index = 0)
        assertEquals(node, scrubber.scrubNode(node))
    }
}
