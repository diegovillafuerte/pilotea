package mx.kompara.data.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundHostFreshnessTest {
    @After fun clearHost() { ScreenReaderState.setForegroundHost(null, 0) }

    @Test fun `OS observations are usable only within a nonnegative monotonic age`() {
        ScreenReaderState.setForegroundHost("com.ubercab.driver", 10_000)
        assertNull(ScreenReaderState.freshForegroundHost(9_999, 1_500))
        assertEquals("com.ubercab.driver", ScreenReaderState.freshForegroundHost(10_000, 1_500))
        assertEquals("com.ubercab.driver", ScreenReaderState.freshForegroundHost(11_499, 1_500))
        assertNull(ScreenReaderState.freshForegroundHost(11_500, 1_500))
        ScreenReaderState.setForegroundHost(null, 11_501)
        assertNull(ScreenReaderState.freshForegroundHost(11_501, 1_500))
    }
}
