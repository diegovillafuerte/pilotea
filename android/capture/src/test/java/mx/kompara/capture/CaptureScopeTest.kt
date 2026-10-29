package mx.kompara.capture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureScopeTest {
    @Test
    fun `snapshot failure stops its collector and reports it without cancelling sibling reader`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val failures = mutableListOf<Pair<String?, Throwable>>()
        val scope = pipelineScope(dispatcher) { context, error ->
            failures += context[CoroutineName]?.name to error
        }
        try {
            val failure = SecurityException("reader unavailable")
            var reads = 0
            val pipeline = EventPipeline(SnapshotReader { _, _ -> reads++; throw failure }, dispatcher, 1)
            pipeline.start(CoroutineScope(scope.coroutineContext + CoroutineName("snapshot-pipeline")))
            var siblingFinished = false
            scope.launch { delay(5); siblingFinished = true }
            testScheduler.runCurrent()
            pipeline.submit(CaptureEvent("com.ubercab.driver", 1))
            testScheduler.advanceTimeBy(10)
            testScheduler.runCurrent()
            // The failed subscription must remain stopped; the sibling and parent survive.
            pipeline.submit(CaptureEvent("com.ubercab.driver", 2))
            testScheduler.advanceTimeBy(10)
            testScheduler.runCurrent()

            assertEquals(listOf("snapshot-pipeline"), failures.map { it.first })
            val reported = failures.single().second
            assertEquals(failure.javaClass, reported.javaClass)
            // Coroutine stack-trace recovery may copy an exception, retaining the original cause.
            assertSame(failure, generateSequence(reported) { it.cause }.last())
            assertEquals(1, reads)
            assertTrue(siblingFinished)
            assertTrue(scope.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `normal component cancellation is not reported as failure`() = runTest {
        val failures = mutableListOf<Throwable>()
        val scope = pipelineScope(StandardTestDispatcher(testScheduler)) { _, error -> failures += error }
        try {
            scope.launch { throw CancellationException("service teardown") }
            testScheduler.runCurrent()
            assertTrue(failures.isEmpty())
            assertTrue(scope.isActive)
        } finally {
            scope.cancel()
        }
    }
}
