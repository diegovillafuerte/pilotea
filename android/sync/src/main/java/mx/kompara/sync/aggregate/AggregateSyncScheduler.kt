package mx.kompara.sync.aggregate

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the [AggregateSyncWorker] (B-043).
 *
 *  - [ensurePeriodic] enqueues a unique 12-hour periodic sync (KEEP policy, so re-calling it on every
 *    app start is idempotent), constrained to run only when connected and with exponential backoff.
 *  - [syncNow] enqueues a one-off sync for an on-demand push (e.g. right after the driver opts in to
 *    sharing, or after a rollup recomputes a week), REPLACE so rapid triggers coalesce into one run.
 *
 * Both honor consent indirectly: the worker delegates to [AggregateUploader], which short-circuits
 * the upload when the driver is signed-out or hasn't consented. The benchmark download leg always
 * runs (benchmarks are public, anonymous data) and is TTL-gated inside the repository.
 */
@Singleton
class AggregateSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Enqueue (or keep) the 12h periodic sync. Idempotent. */
    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<AggregateSyncWorker>(PERIOD)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
        workManager.enqueueUniquePeriodicWork(
            AggregateSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Request an immediate (network-permitting) sync. Coalesces with any pending one-off. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<AggregateSyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
        workManager.enqueueUniqueWork(
            AggregateSyncWorker.ONE_OFF_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        val PERIOD: Duration = Duration.ofHours(12)
        val BACKOFF: Duration = Duration.ofMinutes(5)
    }
}
