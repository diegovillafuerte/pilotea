package mx.kompara.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDiagnosticThrottleTest {
    @Test fun `repeated frames are limited by reason while a new reason logs immediately`() {
        val throttle = OverlayDiagnosticThrottle()
        assertTrue(throttle.shouldLog("STALE_GEOMETRY", 100))
        assertFalse(throttle.shouldLog("STALE_GEOMETRY", 101))
        assertTrue(throttle.shouldLog("SUPPRESSED_NO_SAFE_SPACE", 101))
        assertFalse(throttle.shouldLog("STALE_GEOMETRY", 5_099))
        assertTrue(throttle.shouldLog("STALE_GEOMETRY", 5_100))
    }
}
