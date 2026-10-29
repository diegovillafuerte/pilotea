package mx.kompara.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferFramePipelineDiagnosticsTest {
    private fun block(text: String, top: Int) = OcrBlock(text, OcrBounds(20, top, 373, top + 24))

    @Test fun `read offer without geometry logs reason but no recognized personal or payment data`() {
        ShadowLog.clear()
        OfferFramePipeline().process(listOf(
            block("UberX Exclusivo", 500), block("MXN148.00", 540),
            block("A 4 min (2.0 km)", 600), block("Viaje: 20 min (10.0 km)", 650),
            block("Alice Example, Calle Privada 123", 700), block("Aceptar", 780),
        ), null, null)
        val messages = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }
        assertTrue(messages.any { "geometry missing" in it })
        assertFalse(messages.any { "148.00" in it || "Alice" in it || "Calle Privada" in it })
    }

    @Test fun `recognized offer with unread payment logs incomplete reading`() {
        ShadowLog.clear()
        OfferFramePipeline().process(listOf(block("UberX", 500), block("Aceptar", 780)), null, null)
        assertTrue(ShadowLog.getLogsForTag("KomparaOCR").any { "incomplete reading" in it.msg })
    }
}
