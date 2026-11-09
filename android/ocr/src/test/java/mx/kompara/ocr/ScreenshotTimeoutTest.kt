package mx.kompara.ocr

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScreenshotTimeoutTest {
    private class Frame {
        var releases = 0
        fun release() { releases++; assertEquals("released exactly once", 1, releases) }
    }
    private val timedOut = Frame()

    private fun TestScope.request(block: (ScreenshotCompletion<Frame>) -> Unit) =
        async(start = CoroutineStart.UNDISPATCHED) {
            awaitScreenshotResultWithin(2_000L, timedOut, Frame::release, { throw AssertionError() }, block)
        }

    @Test fun `a callback that never arrives ends at exactly the bound`() = runTest {
        val result = request { }
        advanceTimeBy(1_999); runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertSame(timedOut, result.await())
        assertEquals(0, timedOut.releases)
    }

    @Test fun `a late frame is released once and never delivered`() = runTest {
        lateinit var callback: ScreenshotCompletion<Frame>
        val result = request { callback = it }
        advanceTimeBy(2_000); runCurrent()
        assertSame(timedOut, result.await())
        val late = Frame()
        callback.complete(late)
        runCurrent()
        assertEquals(1, late.releases)
    }

    @Test fun `a frame just before the bound wins and is not released`() = runTest {
        lateinit var callback: ScreenshotCompletion<Frame>
        val result = request { callback = it }
        advanceTimeBy(1_999); runCurrent()
        val frame = Frame()
        callback.complete(frame)
        runCurrent()
        assertSame(frame, result.await())
        assertEquals(0, frame.releases)
    }

    @Test fun `a frame resumed at the deadline but not yet dispatched is released`() = runTest {
        lateinit var callback: ScreenshotCompletion<Frame>
        val result = request { callback = it }
        advanceTimeBy(2_000) // the timer is due at 2,000 and has not run yet
        val frame = Frame()
        callback.complete(frame)
        runCurrent()
        assertSame(timedOut, result.await())
        assertEquals(1, frame.releases)
    }

    @Test fun `parent cancellation is not reported as a timeout`() = runTest {
        var returned = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitScreenshotResultWithin(2_000L, timedOut, Frame::release, { throw AssertionError() }) { }
            returned = true
        }
        job.cancel(); runCurrent(); assertFalse(returned)
    }
}
