package mx.kompara.ocr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScreenshotCompletionTest {
    private class Frame {
        var releases = 0
        fun release() {
            releases++
            assertEquals("a frame must be released exactly once", 1, releases)
        }
    }

    @Test fun `inline success wins over synchronous cancellation and reaches caller`() = runBlocking {
        val frame = Frame()
        val actual = awaitScreenshotResult<Frame>(
            discard = Frame::release,
            failureResult = { throw AssertionError("cancellation must bypass failure classification") },
        ) { completion ->
            completion.complete(frame)
            throw CancellationException()
        }
        assertSame(frame, actual)
        assertEquals(0, frame.releases)
        actual.release()
    }

    @Test fun `synchronous cancellation first cancels continuation and releases late frame`() = runBlocking {
        lateinit var callback: ScreenshotCompletion<Frame>
        val cancellation = CancellationException()
        try {
            awaitScreenshotResult<Frame>(Frame::release, { throw AssertionError() }) {
                callback = it
                throw cancellation
            }
            fail("synchronous cancellation must cancel the suspension")
        } catch (actual: CancellationException) {
            // Coroutine stack-trace recovery may wrap the original cancellation.
            assertSame(cancellation, generateSequence(actual as Throwable) { it.cause }.last())
        }
        val late = Frame()
        callback.complete(late)
        assertEquals(1, late.releases)
    }

    @Test fun `callback cancellation shares first result gate with inline success`() = runBlocking {
        val frame = Frame()
        val result = awaitScreenshotResult<Frame>(Frame::release, { throw AssertionError() }) {
            it.complete(frame)
            it.cancel(CancellationException())
        }
        assertSame(frame, result)
        assertEquals(0, frame.releases)
        result.release()
    }

    @Test fun `callback cancellation wins over late success and synchronous platform failure`() = runBlocking {
        val frame = Frame()
        val failureFrame = Frame()
        try {
            awaitScreenshotResult(Frame::release, { failureFrame }) {
                it.cancel(CancellationException())
                it.complete(frame)
                throw IllegalStateException()
            }
            fail("callback cancellation must win")
        } catch (_: CancellationException) {
            assertEquals(1, frame.releases)
            assertEquals(1, failureFrame.releases)
        }
    }

    @Test fun `duplicate success releases only losing frame`() = runBlocking {
        val first = Frame()
        val duplicate = Frame()
        val result = awaitScreenshotResult<Frame>(Frame::release, { throw AssertionError() }) {
            it.complete(first)
            it.complete(duplicate)
        }
        assertSame(first, result)
        assertEquals(0, first.releases)
        assertEquals(1, duplicate.releases)
        result.release()
    }

    @Test fun `parent cancellation releases late frame without delivering it`() = runBlocking {
        lateinit var callback: ScreenshotCompletion<Frame>
        var delivered = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitScreenshotResult<Frame>(Frame::release, { throw AssertionError() }) { callback = it }
            delivered = true
        }
        job.cancel()
        val frame = Frame()
        callback.complete(frame)
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(delivered)
        assertEquals(1, frame.releases)
    }

    @Test fun `prompt parent cancellation releases resumed but undelivered frame once`() = runBlocking {
        lateinit var callback: ScreenshotCompletion<Frame>
        var delivered = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitScreenshotResult<Frame>(Frame::release, { throw AssertionError() }) { callback = it }
            delivered = true
        }
        val frame = Frame()
        callback.complete(frame)
        // runBlocking has not dispatched the resumed child yet.
        job.cancel()
        job.join()
        assertFalse(delivered)
        assertEquals(1, frame.releases)
    }

    @Test fun `inline success survives synchronous platform failure`() = runBlocking {
        val first = Frame()
        val failureFrame = Frame()
        val actual = awaitScreenshotResult(Frame::release, { failureFrame }) {
            it.complete(first)
            throw SecurityException()
        }
        assertSame(first, actual)
        assertEquals(0, first.releases)
        assertEquals(1, failureFrame.releases)
        actual.release()
    }
}
