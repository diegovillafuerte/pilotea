package mx.kompara.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotRateLimitsTest {
    @Test fun `consecutive interval refusals increment and another response resets the run`() {
        val limits = ScreenshotRateLimits()
        assertEquals(1, limits.onRateLimited())
        assertEquals(2, limits.onRateLimited())
        assertEquals(3, limits.onRateLimited())
        limits.reset()
        assertEquals(1, limits.onRateLimited())
        assertEquals(2, limits.onRateLimited())
    }

    @Test fun `reset before the first refusal and repeated burst resets remain empty`() {
        val limits = ScreenshotRateLimits()
        limits.reset()
        limits.reset()
        assertEquals(1, limits.onRateLimited())
        limits.reset()
        limits.reset()
        assertEquals(1, limits.onRateLimited())
    }

    @Test fun `repeated refusals are throttled but next diagnostic reports the current consecutive count`() {
        var time = 0L
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ time }, messages::add)
        val limits = ScreenshotRateLimits()
        repeat(5) {
            diagnostics.blocked("SCREENSHOT_RATE_LIMIT", "consecutive=${limits.onRateLimited()}")
            time += 1_000L
        }
        assertEquals(listOf("offer visibility: SCREENSHOT_RATE_LIMIT; consecutive=1"), messages)
        diagnostics.blocked("SCREENSHOT_RATE_LIMIT", "consecutive=${limits.onRateLimited()}")
        assertEquals(2, messages.size)
        assertEquals("offer visibility: SCREENSHOT_RATE_LIMIT; consecutive=6", messages.last())
        limits.reset()
        time += 5_000L
        diagnostics.blocked("SCREENSHOT_RATE_LIMIT", "consecutive=${limits.onRateLimited()}")
        assertEquals("offer visibility: SCREENSHOT_RATE_LIMIT; consecutive=1", messages.last())
    }

    @Test fun `zero-frame burst summary preserves every rate refusal including suppressed diagnostics`() {
        var time = 0L
        val messages = mutableListOf<String>()
        val diagnostics = OcrDiagnostics({ time }, messages::add)
        val limits = ScreenshotRateLimits()
        diagnostics.beginBurst()
        repeat(4) {
            diagnostics.blocked("SCREENSHOT_RATE_LIMIT", "consecutive=${limits.onRateLimited()}")
            time += 1_000L
        }
        diagnostics.endBurst()
        assertEquals(2, messages.size)
        assertTrue(messages.last().startsWith("burst: frames=0 ocrBlocks=0"))
        assertTrue(messages.last().endsWith("hidden{SCREENSHOT_RATE_LIMIT=4}"))
    }
}
