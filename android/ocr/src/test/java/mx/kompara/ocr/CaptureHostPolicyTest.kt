package mx.kompara.ocr

import mx.kompara.data.service.HostForegroundObservation
import mx.kompara.data.service.ScreenReaderState
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CaptureHostPolicyTest {
    @After fun clearHost() { ScreenReaderState.setForegroundHost(null, 0) }

    @Test fun `one unavailable root skips capture without ending the offer session`() {
        val unknown = HostForegroundObservation.fromWindow(null, true, setOf(UberOcrParser.UBER_PACKAGE))
        assertEquals(HostForegroundObservation.Unknown, unknown)
        assertFalse(CaptureHostPolicy.mustEnd(unknown, interactive = true, readingAllowed = true))
    }

    @Test fun `stale projection host evidence is uncertainty and cannot close or authorize parsing`() {
        ScreenReaderState.setForegroundHost(UberOcrParser.UBER_PACKAGE, 0)
        val unknown = ScreenReaderState.freshForegroundObservation(1_500, 1_500)
        assertNull(ScreenReaderState.freshForegroundHost(1_500, 1_500))
        assertFalse(CaptureHostPolicy.mustEnd(unknown, interactive = true, readingAllowed = true))
    }

    @Test fun `known app departure screen off and reader shutdown end capture`() {
        assertTrue(CaptureHostPolicy.mustEnd(HostForegroundObservation.NonHost, true, true))
        assertTrue(CaptureHostPolicy.mustEnd(HostForegroundObservation.Unknown, false, true))
        assertTrue(CaptureHostPolicy.mustEnd(HostForegroundObservation.Unknown, true, false))
        assertTrue(CaptureHostPolicy.mustEnd(HostForegroundObservation.Target(UberOcrParser.UBER_PACKAGE), false, true))
        assertFalse(CaptureHostPolicy.mustEnd(HostForegroundObservation.Target(UberOcrParser.UBER_PACKAGE), true, true))
    }
}
