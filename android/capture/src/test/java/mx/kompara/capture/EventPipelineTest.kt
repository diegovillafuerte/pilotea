package mx.kompara.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debounce/coalescing behaviour of [EventPipeline] under virtual time. No Android types are needed
 * here: the fake [SnapshotReader] returns node-less snapshots, so these run as plain JVM tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventPipelineTest {

    private val debounceMs = 80L

    /** A reader that counts how many times the pipeline asked for a snapshot. */
    private class CountingReader : SnapshotReader {
        val reads = AtomicInteger(0)
        override fun read(packageName: String, timestampMs: Long): ScreenSnapshot? {
            reads.incrementAndGet()
            return ScreenSnapshot(packageName = packageName, timestampMs = timestampMs, nodes = emptyList())
        }
    }

    @Test
    fun `a burst of events coalesces into exactly one snapshot`() = runTest {
        val reader = CountingReader()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val pipeline = EventPipeline(reader, dispatcher, debounceMs)

        val collected = mutableListOf<ScreenSnapshot>()
        val job = launch(dispatcher) { pipeline.debouncedSnapshots.toList(collected) }

        // Storm of 10 content-changed events 10ms apart — well inside the 80ms window.
        repeat(10) { i ->
            pipeline.submit(CaptureEvent(packageName = "com.ubercab.driver", timestampMs = i * 10L))
            advanceTimeByVirtual(10)
        }
        // Quiet period longer than the debounce window flushes the single coalesced snapshot.
        advanceTimeByVirtual(debounceMs + 1)

        assertEquals("exactly one snapshot per burst", 1, collected.size)
        assertEquals(1, reader.reads.get())
        assertEquals("com.ubercab.driver", collected.single().packageName)

        job.cancel()
    }

    @Test
    fun `two bursts separated by a quiet gap produce two snapshots`() = runTest {
        val reader = CountingReader()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val pipeline = EventPipeline(reader, dispatcher, debounceMs)

        val collected = mutableListOf<ScreenSnapshot>()
        val job = launch(dispatcher) { pipeline.debouncedSnapshots.toList(collected) }

        // First burst.
        repeat(5) { pipeline.submit(CaptureEvent("com.didiglobal.driver", it.toLong())) }
        advanceTimeByVirtual(debounceMs + 1)

        // Gap, then second burst.
        repeat(5) { pipeline.submit(CaptureEvent("com.didiglobal.driver", 1000L + it)) }
        advanceTimeByVirtual(debounceMs + 1)

        assertEquals(2, collected.size)
        assertEquals(2, reader.reads.get())

        job.cancel()
    }

    @Test
    fun `null snapshot from the reader is filtered out`() = runTest {
        val nullReader = SnapshotReader { _, _ -> null }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val pipeline = EventPipeline(nullReader, dispatcher, debounceMs)

        val collected = mutableListOf<ScreenSnapshot>()
        val job = launch(dispatcher) { pipeline.debouncedSnapshots.toList(collected) }

        pipeline.submit(CaptureEvent("com.ubercab.driver", 0L))
        advanceTimeByVirtual(debounceMs + 1)

        assertEquals(0, collected.size)
        job.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.advanceTimeByVirtual(ms: Long) {
        testScheduler.advanceTimeBy(ms)
        testScheduler.runCurrent()
    }
}
