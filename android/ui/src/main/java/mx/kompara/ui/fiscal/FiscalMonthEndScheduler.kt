package mx.kompara.ui.fiscal

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the [FiscalMonthEndWorker] (B-051).
 *
 *  - [ensureScheduled] enqueues a unique **daily** periodic check (KEEP, so calling it on every app
 *    start is idempotent). A daily cadence reliably catches day 1 of a new month without needing a
 *    precise alarm, and the worker itself only posts once per month (watermark) — so an extra daily
 *    run is a cheap no-op. No network constraint: it reads local rollups + cached config.
 *  - [runNow] enqueues a one-off check for the **next app open** path (the task's "or next app open"),
 *    REPLACE so repeated opens coalesce. Combined with the watermark this can't double-post.
 */
@Singleton
class FiscalMonthEndScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Enqueue (or keep) the daily month-end check. Idempotent. */
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<FiscalMonthEndWorker>(PERIOD).build()
        workManager.enqueueUniquePeriodicWork(
            FiscalMonthEndWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Run an immediate month-end check (the "next app open" path). Coalesces with any pending one. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<FiscalMonthEndWorker>().build()
        workManager.enqueueUniqueWork(
            FiscalMonthEndWorker.ONE_OFF_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        val PERIOD: Duration = Duration.ofHours(24)
    }
}
