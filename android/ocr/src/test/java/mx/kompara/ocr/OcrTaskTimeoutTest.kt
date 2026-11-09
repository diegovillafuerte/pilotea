package mx.kompara.ocr

import android.os.Looper
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrTaskTimeoutTest {
    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun `a task that never finishes returns null at exactly the bound and ignores a late result`() = runTest {
        val source = TaskCompletionSource<String>()
        val result = async(start = CoroutineStart.UNDISPATCHED) { source.task.awaitWithin(OCR_TIMEOUT_MS) }
        advanceTimeBy(4_999); runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertNull(result.await())
        source.setResult("late")
        idleMain()
        runCurrent()
    }

    @Test fun `a result inside the bound is returned`() = runTest {
        val source = TaskCompletionSource<String>()
        val result = async(start = CoroutineStart.UNDISPATCHED) { source.task.awaitWithin(OCR_TIMEOUT_MS) }
        advanceTimeBy(4_999); runCurrent()
        source.setResult("read")
        idleMain()
        runCurrent()
        assertEquals("read", result.await())
    }

    @Test fun `a failed task throws its own error, not cancellation`() = runTest {
        val source = TaskCompletionSource<String>()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { source.task.awaitWithin(OCR_TIMEOUT_MS) }
        }
        source.setException(IllegalArgumentException("x"))
        idleMain()
        runCurrent()
        assertTrue(result.await().exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun `a canceled task fails promptly instead of waiting for the bound`() = runTest {
        val tokens = CancellationTokenSource()
        val source = TaskCompletionSource<String>(tokens.token)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { source.task.awaitWithin(OCR_TIMEOUT_MS) }
        }
        tokens.cancel()
        idleMain()
        runCurrent()
        val failure = result.await().exceptionOrNull() ?: return@runTest fail("must fail")
        assertTrue(failure is IllegalStateException)
    }
}
