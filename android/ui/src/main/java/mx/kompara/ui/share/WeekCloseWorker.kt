package mx.kompara.ui.share

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.stats.WeekClock

/**
 * Posts the Monday week-close share reminder (B-055): "Tu resumen de la semana está listo", which
 * opens the share-card preview. Triggered by a Monday-ish periodic schedule plus a next-app-open
 * one-off (see [WeekCloseScheduler]), so it must be idempotent — the pure [WeekCloseDecision]
 * short-circuits when the just-closed week was already reminded ([SettingsRepository.shareLastReminderWeek]),
 * when the toggle is off, or when that week had no captured data. The worker stamps the watermark on
 * any non-SKIP outcome so a handled week is never re-evaluated.
 */
@HiltWorker
class WeekCloseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val aggregateDao: AggregateDao,
    private val settings: SettingsRepository,
    private val notifier: WeekCloseNotifier,
    private val weekClock: WeekClock,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = clock.nowMs()
        // The week that just closed = the previous Monday's week. We summarise last week, not the
        // in-progress one — Monday morning the driver wants their finished week.
        val closedWeekStart = weekClock.weekStartIso(now - SEVEN_DAYS_MS)

        val enabled = settings.isShareWeeklyReminderEnabled()
        val lastReminderWeek = settings.shareLastReminderWeek()

        val hasData = aggregateDao.observeWeekly().first().any {
            it.weekStart == closedWeekStart && it.source == AggregateSource.CAPTURED.name
        }

        return when (WeekCloseDecision.decide(enabled, closedWeekStart, lastReminderWeek, hasData)) {
            WeekCloseAction.POST -> {
                notifier.post()
                settings.setShareLastReminderWeek(closedWeekStart)
                Result.success()
            }
            WeekCloseAction.STAMP_ONLY -> {
                settings.setShareLastReminderWeek(closedWeekStart)
                Result.success()
            }
            WeekCloseAction.SKIP -> Result.success()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "kompara_week_close_periodic"
        const val ONE_OFF_WORK_NAME = "kompara_week_close_oneoff"
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
