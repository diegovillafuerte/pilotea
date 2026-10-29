package mx.kompara.ui.share

import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.WeekFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekShareDataTest {
    @Test fun `week entry carries selected period after-car money and hides hours without inventing a rank`() {
        val money = WeekFixture(ApplicationProvider.getApplicationContext()).week().money
        val hidden = weekShareData(money, true)
        assertEquals(SharePeriodKind.WEEK, hidden.periodKind)
        assertEquals("12 – 18 oct", hidden.periodLabel)
        assertEquals("62", hidden.trips)
        assertNull(hidden.netEarnings); assertNull(hidden.hours); assertFalse(hidden.confirmed); assertTrue(hidden.bars.isEmpty())
        assertEquals("$4,980", weekShareData(money, false).netEarnings)
        assertEquals("5 – 11 oct", weekShareData(WeekFixture(ApplicationProvider.getApplicationContext()).week("E2").money, true).periodLabel)
    }
}
