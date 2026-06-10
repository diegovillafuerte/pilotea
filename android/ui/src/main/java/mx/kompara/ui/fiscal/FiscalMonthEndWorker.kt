package mx.kompara.ui.fiscal

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.imss.MonthEndSummary
import mx.kompara.sync.fiscal.FiscalConfigRepository
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.stats.FiscalMonth
import mx.kompara.ui.stats.ImssTracker
import java.time.format.DateTimeFormatter

/**
 * Posts the month-end IMSS summary (B-051): for the **just-ended** month, one notification per
 * platform — covered / not covered — on the "fiscal" channel.
 *
 * Triggered both by a day-1 periodic schedule and on the next app open (see [FiscalMonthEndScheduler]),
 * so it must be idempotent: the pure [MonthEndSummary] short-circuits when the just-ended month was
 * already notified ([SettingsRepository.fiscalLastNotifiedMonth]), and the worker stamps the watermark
 * only after a successful decide. It also short-circuits when the user's toggle is off.
 *
 * The economic computation reuses the exact same pure path as the Fiscal tab ([ImssTracker] →
 * [mx.kompara.metrics.imss.ImssCalculator]) so the notification and the tab can never disagree.
 */
@HiltWorker
class FiscalMonthEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val aggregateDao: AggregateDao,
    private val settings: SettingsRepository,
    private val fiscalConfigRepository: FiscalConfigRepository,
    private val notifier: FiscalMonthEndNotifier,
    private val fiscalMonth: FiscalMonth,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val enabled = settings.isFiscalMonthlySummaryEnabled()
        if (!enabled) return Result.success()

        val now = clock.nowMs()
        // The month that just ended = 1 month before the current month.
        val month = fiscalMonth.monthAt(now, 1)
        val monthKey = month.format(MONTH_KEY)

        val alreadyNotified = settings.fiscalLastNotifiedMonth()
        if (alreadyNotified == monthKey) return Result.success()

        val daily = aggregateDao
            .observeDailyInRange(fiscalMonth.monthStartDay(month), fiscalMonth.monthEndDay(month))
            .first()
        val weekly = aggregateDao
            .observeWeeklyInRange(fiscalMonth.weekRangeStart(month), fiscalMonth.weekRangeEnd(month))
            .first()

        val config = fiscalConfigRepository.current()
        val enabledPlatforms = settings.settings.first().enabledPlatforms

        val statuses = ImssTracker.sectionsFor(
            month = month,
            thresholdMxn = config.imssMonthlyThresholdMxn,
            daily = daily,
            weekly = weekly,
            today = fiscalMonth.today(now),
            enabledPlatforms = enabledPlatforms,
        )

        val decisions = MonthEndSummary.decide(
            monthKey = monthKey,
            enabled = true,
            statuses = statuses,
            alreadyNotifiedMonth = alreadyNotified,
        )

        notifier.post(decisions)
        // Stamp the watermark even when there was nothing to post (e.g. an idle month), so we don't
        // re-evaluate the same closed month on every app open.
        settings.setFiscalLastNotifiedMonth(monthKey)
        return Result.success()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "kompara_fiscal_month_end_periodic"
        const val ONE_OFF_WORK_NAME = "kompara_fiscal_month_end_oneoff"
        private val MONTH_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
