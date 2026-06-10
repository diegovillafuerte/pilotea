package mx.kompara.sync.spec

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic OTA parser-config refresh (B-033). Pulls the active signed bundle through
 * [SpecConfigRepository] every ~6h so a remotely-shipped spec fix or kill switch reaches a driver
 * within one refresh cycle even if the app is never reopened.
 *
 * Constraints: requires connectivity but NOT unmetered — a kill switch for a broken parser is
 * worth a few KB of cellular data, and the bundle is tiny. Exponential backoff handles backend
 * blips. The worker is idempotent: [SpecConfigRepository.refresh] no-ops when the bundle isn't
 * newer, and a failed fetch retains the last-known-good specs, so a missed cycle is harmless.
 */
@HiltWorker
class SpecConfigRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: SpecConfigRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        when (repository.refresh()) {
            is RefreshResult.Failed -> Result.retry()
            else -> Result.success()
        }

    companion object {
        const val UNIQUE_NAME = "kompara-spec-config-refresh"
        private const val REFRESH_INTERVAL_HOURS = 6L
        private const val BACKOFF_SECONDS = 30L

        /**
         * Enqueue the periodic refresh, keeping any already-scheduled instance (so re-enqueueing on
         * every app start doesn't reset the 6h clock). Connectivity-constrained, exponential backoff.
         */
        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                // CONNECTED, not UNMETERED: a kill switch must reach drivers even on cellular.
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SpecConfigRefreshWorker>(
                REFRESH_INTERVAL_HOURS, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
