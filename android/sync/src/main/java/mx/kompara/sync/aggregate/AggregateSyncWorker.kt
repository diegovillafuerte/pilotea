package mx.kompara.sync.aggregate

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import mx.kompara.data.settings.SettingsRepository

/**
 * WorkManager worker for the B-043 consented data exchange.
 *
 * Runs both directions in one pass:
 *  1. **up** — [AggregateUploader.sync] pushes the driver's dirty weekly aggregates (only when
 *     signed-in AND consented; otherwise a no-op that touches nothing);
 *  2. **down** — [BenchmarksRepository.refresh] pulls the driver's city benchmarks into the local
 *     cache (TTL-gated, offline-tolerant), so percentiles (B-046) work offline after the first fetch.
 *     The benchmark refresh is NOT gated by consent — benchmarks are public, anonymous data the app
 *     needs regardless of whether the driver shares their own aggregates.
 *
 * A thin shell over the two collaborators (both fully unit-tested on the JVM), so the worker itself
 * needs no unit test. Returns [Result.retry] on a transient upload failure so WorkManager re-runs it
 * with backoff; a failed benchmark fetch is swallowed by the repository (last-known-good cache wins)
 * and never fails the worker on its own. Offline tolerance comes from the enqueue-time network
 * constraint plus retry.
 */
@HiltWorker
class AggregateSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploader: AggregateUploader,
    private val benchmarks: BenchmarksRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = runCatching { uploader.sync() }.getOrNull()
            ?: return Result.retry()

        // Pull this driver's city benchmarks into the cache (best-effort, TTL-gated).
        runCatching { benchmarks.refresh(settings.currentCity()) }

        return if (outcome.failed) Result.retry() else Result.success()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "kompara_aggregate_sync_periodic"
        const val ONE_OFF_WORK_NAME = "kompara_aggregate_sync_oneoff"
    }
}
