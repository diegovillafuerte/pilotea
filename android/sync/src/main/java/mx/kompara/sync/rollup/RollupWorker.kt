package mx.kompara.sync.rollup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import mx.kompara.metrics.rollup.RollupRecomputer
import java.util.concurrent.TimeUnit

/**
 * Recomputes the captured daily/weekly aggregates (B-039) in the background.
 *
 * A thin shell over [RollupRecomputer]: no local logic, so it needs no unit test (the recomputer is
 * JVM-tested). Runs **daily** to keep aggregates fresh even when no trip closed (e.g. an open shift
 * crosses midnight), and is also enqueued **on demand** by the lifecycle tracker when a trip closes
 * so the ledger updates promptly. The recompute is idempotent (it rewrites only the trailing-window
 * CAPTURED rows), so overlapping runs are harmless. No network constraint — it's all local Room.
 */
@HiltWorker
class RollupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recomputer: RollupRecomputer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        runCatching { recomputer.recompute() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        const val PERIODIC_WORK_NAME = "kompara-rollup-periodic"
        const val ONE_OFF_WORK_NAME = "kompara-rollup-oneoff"
        private const val INTERVAL_HOURS = 24L
        private const val BACKOFF_SECONDS = 30L

        /** Enqueue the once-a-day periodic recompute, keeping any already-scheduled instance. */
        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<RollupWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Enqueue a one-shot recompute now (the on-trip-close / on-demand trigger). REPLACE so a burst
         * of closes coalesces into a single pending recompute rather than a queue of duplicates.
         */
        fun triggerOnce(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<RollupWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(ONE_OFF_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
