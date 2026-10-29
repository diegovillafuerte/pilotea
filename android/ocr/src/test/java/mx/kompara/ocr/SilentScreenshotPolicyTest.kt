package mx.kompara.ocr

import android.accessibilityservice.AccessibilityServiceInfo
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SilentScreenshotPolicyTest {
    @Test fun `security exception requires permanent stand-down`() {
        assertEquals(ScreenshotFailure.NO_CAPABILITY,
            classifyScreenshotFailure(SecurityException("screenshot capability missing")))
    }

    @Test fun `illegal state is a hard error eligible for bounded retries`() {
        assertEquals(ScreenshotFailure.HARD_ERROR, classifyScreenshotFailure(IllegalStateException()))
    }

    @Test fun `cancellation is propagated unchanged`() {
        val cancellation = CancellationException("service stopped")
        try {
            classifyScreenshotFailure(cancellation)
            fail("cancellation must not become a screenshot failure")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test fun `missing screenshot capability prevents lane start on supported APIs`() {
        for (api in listOf(30, 36)) {
            assertFalse(canStartSilentLane(api, 0))
            assertFalse(canStartSilentLane(api, AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT))
        }
    }

    @Test fun `screenshot capability permits start only on supported APIs`() {
        val capabilities = AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT or
            AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT
        assertFalse(canStartSilentLane(29, capabilities))
        assertTrue(canStartSilentLane(30, capabilities))
        assertTrue(canStartSilentLane(36, capabilities))
    }
}
