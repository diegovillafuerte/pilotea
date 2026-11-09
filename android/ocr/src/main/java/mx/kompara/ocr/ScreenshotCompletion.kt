package mx.kompara.ocr

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One result owns the continuation, including synchronous cancellation from the platform call.
 * A vendor can call back inline and then throw: throwing from the suspension block would bypass
 * delivery of that first result. Losing frames and frames discarded by prompt cancellation are
 * released here, so neither path relies on the capture loop receiving them.
 */
internal class ScreenshotCompletion<T>(
    private val continuation: CancellableContinuation<T>,
    private val discard: (T) -> Unit,
) {
    private val completed = AtomicBoolean(false)

    init {
        continuation.invokeOnCancellation { completed.compareAndSet(false, true) }
    }

    fun complete(result: T) {
        if (!completed.compareAndSet(false, true)) {
            discard(result)
            return
        }
        continuation.resume(result) { _, undelivered, _ -> discard(undelivered) }
    }

    fun cancel(cancellation: CancellationException) {
        if (completed.compareAndSet(false, true)) continuation.cancel(cancellation)
    }
}

internal suspend fun <T> awaitScreenshotResult(
    discard: (T) -> Unit,
    failureResult: (RuntimeException) -> T,
    request: (ScreenshotCompletion<T>) -> Unit,
): T = suspendCancellableCoroutine { continuation ->
    val completion = ScreenshotCompletion(continuation, discard)
    try {
        request(completion)
    } catch (cancellation: CancellationException) {
        // Cancellation is a result too. In particular, never throw after inline onSuccess won.
        completion.cancel(cancellation)
    } catch (error: RuntimeException) {
        completion.complete(failureResult(error))
    }
}

/** A platform callback may never arrive: the wait ends as [timedOut]; a late result is discarded. */
internal suspend fun <T : Any> awaitScreenshotResultWithin(
    timeoutMs: Long,
    timedOut: T,
    discard: (T) -> Unit,
    failureResult: (RuntimeException) -> T,
    request: (ScreenshotCompletion<T>) -> Unit,
): T = withTimeoutOrNull(timeoutMs) { awaitScreenshotResult(discard, failureResult, request) } ?: timedOut
