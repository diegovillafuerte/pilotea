package mx.kompara.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDiagnosticsTest {
    @Test fun `changing geometry does not bypass the per reason log limit`() {
        var time = 0L
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ time }, messages::add)
        diagnostics.blocked("geometry missing", "ageMs=1")
        time = 1_000
        diagnostics.blocked("geometry missing", "ageMs=1001")
        diagnostics.blocked("incomplete reading")
        assertEquals(2, messages.size)
        time = 5_000
        diagnostics.blocked("geometry missing", "ageMs=5001")
        assertEquals(3, messages.size)
        assertEquals("offer visibility: geometry missing; ageMs=5001", messages.last())
    }

    @Test fun `all initial rejection reasons are reported without a preceding offer`() {
        val cases = listOf(
            OcrReadingEvidence(0, false, false, false, false) to "EMPTY_OCR",
            OcrReadingEvidence(2, false, false, false, false) to "NO_OFFER_SIGNATURE",
            OcrReadingEvidence(2, false, true, false, false) to "SIGNATURE_UNPARSED",
            OcrReadingEvidence(2, true, false, false, false) to "OWN_UI",
        )
        cases.forEach { (evidence, reason) ->
            val messages = mutableListOf<String>()
            val diagnostics = OcrDiagnostics({ 0L }, messages::add)
            diagnostics.frame(evidence.blocks, false)
            diagnostics.reading(evidence)
            diagnostics.endBurst()
            assertTrue(messages.first().startsWith("offer visibility: $reason;"))
            assertTrue(messages.last().contains("hidden{$reason=1}"))
            assertTrue(messages.last().contains("frames=1"))
        }
    }

    @Test fun `fare and accept without host or route explain the initial rejected frame without logging text`() {
        val blocks = listOf(
            OcrBlock("MXN148.00", OcrBounds(0, 0, 100, 20)),
            OcrBlock("Aceptar", OcrBounds(0, 25, 100, 45)),
            OcrBlock("Alice Example, Calle Privada 123", OcrBounds(0, 50, 100, 70)),
        )
        val uber = UberOcrParser()
        val didi = DidiOcrParser()
        val parsed = uber.parse(blocks) ?: didi.parse(blocks)
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ 0L }, messages::add)
        diagnostics.frame(blocks.size, false)
        diagnostics.reading(OcrReadingEvidence(blocks.size,
            KomparaUiGuard.isOwnUi(blocks.joinToString(" ") { it.text }),
            uber.hasCardSignature(blocks) || didi.hasCardSignature(blocks),
            parsed != null, parsed?.fare != null))
        diagnostics.endBurst()
        assertTrue(messages.first().contains("NO_OFFER_SIGNATURE"))
        assertTrue(messages.first().contains("signature=false parsed=false"))
        assertFalse(messages.any { "148.00" in it || "Aceptar" in it || "Alice" in it || "Calle Privada" in it })
    }

    @Test fun `own UI rejection precedes signature and parser evidence`() {
        assertEquals(OcrRejectionReason.OWN_UI,
            OcrReadingEvidence(5, true, true, true, true).rejection())
    }

    @Test fun `recognized but unread payment is also explained`() {
        assertEquals(OcrRejectionReason.SIGNATURE_UNPARSED,
            OcrReadingEvidence(2, false, true, true, false).rejection())
        assertEquals(null, OcrReadingEvidence(4, false, true, true, true).rejection())
    }

    @Test fun `reason throttling retains every rejected frame in the burst summary`() {
        var time = 0L
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ time }, messages::add)
        repeat(3) {
            diagnostics.frame(0, false)
            diagnostics.reading(OcrReadingEvidence(0, false, false, false, false))
            time += 1_000
        }
        assertEquals(1, messages.size)
        time = 5_000
        diagnostics.frame(0, false)
        diagnostics.reading(OcrReadingEvidence(0, false, false, false, false))
        assertEquals(2, messages.size)
        diagnostics.endBurst()
        assertEquals("burst: frames=4 ocrBlocks=0 signature=0 parsed=0 publishedOffers=0 " +
            "chipVisibleFrames=0 hidden{EMPTY_OCR=4}", messages.last())
    }

    @Test fun `zero frame burst records capture failures and closes only once`() {
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ 0L }, messages::add)
        diagnostics.beginBurst()
        diagnostics.blocked("SCREENSHOT_RATE_LIMIT", "count=1")
        diagnostics.beginBurst()
        diagnostics.blocked("SCREENSHOT_RATE_LIMIT", "count=2")
        diagnostics.endBurst()
        diagnostics.endBurst()
        assertEquals(2, messages.size)
        assertEquals("burst: frames=0 ocrBlocks=0 signature=0 parsed=0 publishedOffers=0 " +
            "chipVisibleFrames=0 hidden{SCREENSHOT_RATE_LIMIT=2}", messages.last())
    }

    @Test fun `withheld parsed frames have a reason on every frame while messages remain throttled`() {
        var time = 0L
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ time }, messages::add)
        repeat(4) {
            diagnostics.frame(8, false)
            diagnostics.reading(OcrReadingEvidence(8, false, true, true, true))
            diagnostics.unpublishedOffer(OcrPublicationRejectionReason.PARSED_SESSION_UNCONFIRMED)
            time += 1_000
        }
        assertEquals(listOf("offer visibility: PARSED_SESSION_UNCONFIRMED"), messages)
        diagnostics.endBurst()
        assertEquals("burst: frames=4 ocrBlocks=32 signature=4 parsed=4 publishedOffers=0 " +
            "chipVisibleFrames=0 hidden{PARSED_SESSION_UNCONFIRMED=4}", messages.last())
        diagnostics.beginBurst()
        diagnostics.endBurst()
        assertTrue(messages.last().endsWith("hidden{}"))
    }

    @Test fun `new host burst resets counts and distinguishes published offers from visible chips`() {
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ 0L }, messages::add)
        diagnostics.frame(4, false)
        diagnostics.reading(OcrReadingEvidence(4, false, true, true, true))
        diagnostics.publishedOffer()
        diagnostics.frame(4, true)
        diagnostics.reading(OcrReadingEvidence(4, false, true, true, true))
        diagnostics.publishedOffer()
        diagnostics.endBurst()
        assertEquals("burst: frames=2 ocrBlocks=8 signature=2 parsed=2 publishedOffers=2 " +
            "chipVisibleFrames=1 hidden{}", messages.last())
        diagnostics.beginBurst()
        diagnostics.endBurst()
        assertEquals("burst: frames=0 ocrBlocks=0 signature=0 parsed=0 publishedOffers=0 " +
            "chipVisibleFrames=0 hidden{}", messages.last())
    }
}
