package mx.kompara.sync.telemetry

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
 * Schedules the [TelemetryUploadWorker] (B-034).
 *
 *  - [ensurePeriodic] enqueues a unique 12-hour periodic upload (KEEP policy, so
 *    re-calling it on every app start is idempotent), constrained to run only
 *    when the network is connected and with exponential backoff on retry.
 *  - [flushNow] enqueues a one-off upload for an on-demand flush (e.g. right
 *    after the driver files a fixture report, or from a debug action), REPLACE so
 *    rapid taps coalesce into one run.
 *
 * Both honor the consent toggle indirectly: the worker calls [TelemetryUploader],
 * which short-circuits counter upload when telemetry is disabled.
 */
@Singleton
class TelemetryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Enqueue (or keep) the 12h periodic upload. Idempotent. */
    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(PERIOD)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
        workManager.enqueueUniquePeriodicWork(
            TelemetryUploadWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Request an immediate (network-permitting) flush. Coalesces with any pending one-off. */
    fun flushNow() {
        val request = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
        workManager.enqueueUniqueWork(
            TelemetryUploadWorker.ONE_OFF_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        val PERIOD: Duration = Duration.ofHours(12)
        val BACKOFF: Duration = Duration.ofMinutes(5)
    }
}
