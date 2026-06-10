package mx.kompara.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * [WeekClock] maps "now" to the same ISO buckets the rollup wrote. UTC so the epoch↔date mapping is
 * unambiguous; the clock is zone-parametric so this exercises the device path.
 */
class WeekClockTest {

    private val clock = WeekClock(ZoneOffset.UTC)

    private fun at(y: Int, mo: Int, d: Int, h: Int = 12): Long =
        LocalDateTime.of(y, mo, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `week start is the Monday of the current ISO week`() {
        // 2026-06-10 is a Wednesday → Monday is 2026-06-08.
        assertEquals("2026-06-08", clock.weekStartIso(at(2026, 6, 10)))
        // 2026-06-08 (the Monday itself) → itself.
        assertEquals("2026-06-08", clock.weekStartIso(at(2026, 6, 8)))
        // 2026-06-07 (Sunday) → previous Monday 2026-06-01.
        assertEquals("2026-06-01", clock.weekStartIso(at(2026, 6, 7)))
    }

    @Test
    fun `day iso is the local calendar day`() {
        assertEquals("2026-06-10", clock.dayIso(at(2026, 6, 10)))
    }

    @Test
    fun `day window brackets the local day exactly`() {
        val start = clock.dayStartMs("2026-06-10")
        val end = clock.dayEndMs("2026-06-10")
        assertEquals(at(2026, 6, 10, 0), start)
        assertEquals(at(2026, 6, 11, 0), end)
        assertEquals(24L * 3600_000L, end - start)
    }
}
