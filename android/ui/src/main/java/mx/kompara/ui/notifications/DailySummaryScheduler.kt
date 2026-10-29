package mx.kompara.ui.notifications

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.ui.stats.AppClock
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailySummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
    private val zone: ZoneId,
) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(Duration.ofDays(1))
            .setInitialDelay(SummaryCalendar.dayDelay(Instant.ofEpochMilli(clock.nowMs()).atZone(zone)))
            .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMinutes(15)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DailySummaryWorker.PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMinutes(15)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(DailySummaryWorker.ONE_OFF_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
