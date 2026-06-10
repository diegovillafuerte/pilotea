package mx.kompara.sync.telemetry

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that flushes the telemetry + fixture-report queues (B-034).
 *
 * A thin shell over [TelemetryUploader]: it delegates all logic so the worker
 * itself needs no unit test (the uploader is fully tested on the JVM). Returns
 * [Result.retry] on a transient failure so WorkManager re-runs it with backoff,
 * and [Result.success] when the queues drained (or there was nothing to send).
 * Offline tolerance comes from the enqueue-time network constraint plus retry.
 */
@HiltWorker
class TelemetryUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploader: TelemetryUploader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = runCatching { uploader.flush() }.getOrNull()
            ?: return Result.retry()
        return if (outcome.failed) Result.retry() else Result.success()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "kompara_telemetry_upload_periodic"
        const val ONE_OFF_WORK_NAME = "kompara_telemetry_upload_oneoff"
    }
}
