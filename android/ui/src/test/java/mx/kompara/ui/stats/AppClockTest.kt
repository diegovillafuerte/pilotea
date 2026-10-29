package mx.kompara.ui.stats

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppClockTest {
    @Test fun `ticks cross local midnight without recreating viewmodel`() = runTest {
        val zone = ZoneId.of("America/Mexico_City")
        val start = Instant.parse("2026-10-20T05:59:59.500Z").toEpochMilli()
        val values = mutableListOf<Instant>()
        val job = launch { AppClock { start + testScheduler.currentTime }.moments().take(3).toList(values) }
        advanceUntilIdle(); job.join()
        assertEquals(listOf("2026-10-19", "2026-10-20", "2026-10-20"), values.map { it.atZone(zone).toLocalDate().toString() })
        assertEquals(500L, values[1].toEpochMilli() - values[0].toEpochMilli())
    }
    @Test fun `clock is read again after wall time changes rather than incremented`() = runTest {
        var wall = 0L
        val values = mutableListOf<Instant>()
        val job = launch { AppClock { wall }.moments().take(2).toList(values) }
        runCurrent(); wall = 86_400_000L; advanceUntilIdle(); job.join()
        assertEquals(wall, values.last().toEpochMilli())
    }
}
