package mx.kompara.ui.share

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.ui.stats.AppClock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the [WeekCloseWorker] (B-055).
 *
 *  - [ensureScheduled] enqueues a **weekly** periodic check anchored to the next Monday 09:00 local
 *    (KEEP, so calling it on every app start is idempotent). The 7-day cadence fires each Monday
 *    morning; the worker's per-week watermark means an extra run is a cheap no-op. No network
 *    constraint — it reads local rollups only.
 *  - [runNow] enqueues a one-off check for the next-app-open path, REPLACE so repeated opens coalesce.
 *    Combined with the watermark this can't double-post; it covers a device that was off all Monday.
 *
 * The initial-delay math ([initialDelayMillis]) is a pure function so the Monday-09:00 anchoring is
 * unit-tested without WorkManager.
 */
@Singleton
class WeekCloseScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Enqueue (or keep) the weekly Monday-09:00 reminder. Idempotent. */
    fun ensureScheduled() {
        val delay = initialDelayMillis(clock.nowMs(), ZoneId.systemDefault())
        val request = PeriodicWorkRequestBuilder<WeekCloseWorker>(PERIOD)
            .setInitialDelay(Duration.ofMillis(delay))
            .build()
        workManager.enqueueUniquePeriodicWork(
            WeekCloseWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Run an immediate week-close check (the "next app open" path). Coalesces with any pending one. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<WeekCloseWorker>().build()
        workManager.enqueueUniqueWork(
            WeekCloseWorker.ONE_OFF_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        /** Weekly cadence — one reminder per closed week. */
        val PERIOD: Duration = Duration.ofDays(7)

        /** Local time the reminder should fire. */
        val FIRE_TIME: LocalTime = LocalTime.of(9, 0)

        /**
         * Millis from [nowMs] until the next Monday 09:00 in [zone]. If it's exactly Monday 09:00 now,
         * returns a full week (the next occurrence) so we never schedule a zero/negative delay. Pure.
         */
        fun initialDelayMillis(nowMs: Long, zone: ZoneId): Long {
            val now = Instant.ofEpochMilli(nowMs).atZone(zone)
            var target = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).with(FIRE_TIME)
            if (!target.isAfter(now)) {
                target = target.plusWeeks(1)
            }
            return Duration.between(now, target).toMillis()
        }
    }
}
