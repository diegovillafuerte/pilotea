package mx.kompara.ocr

import org.junit.Assert.assertEquals
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
}
