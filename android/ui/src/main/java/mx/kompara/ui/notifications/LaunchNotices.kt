package mx.kompara.ui.notifications

import androidx.work.WorkManager
import mx.kompara.ui.fiscal.FiscalMonthEndWorker
import mx.kompara.ui.share.WeekCloseScheduler

/**
 * The launch set keeps two notices (docs/v4-simple-launch-plan.md §3, §4.9): "Se apagó el semáforo",
 * posted by ServiceWatchdog, and "Tu semana está lista" on Monday morning. Daily summaries, the import
 * reminder and the month-end IMSS notice wait until after launch: their workers stay, nothing
 * schedules them, and startup cancels what 0.2.x enqueued.
 */
object LaunchNotices {
    /** Summaries delivered on schedule and on app open. */
    val summaries: List<NoticeKind> = listOf(NoticeKind.WEEK)

    /** Unique work 0.2.x enqueued for the summaries that wait. */
    val waitingWork: List<String> = listOf(
        DailySummaryWorker.PERIODIC_WORK_NAME, DailySummaryWorker.ONE_OFF_WORK_NAME,
        ImportReminderWorker.PERIODIC_WORK_NAME, ImportReminderWorker.ONE_OFF_WORK_NAME,
        FiscalMonthEndWorker.PERIODIC_WORK_NAME, FiscalMonthEndWorker.ONE_OFF_WORK_NAME,
    )

    /** At app start: cancel the waiting summaries, keep the Monday summary enqueued and catch up on it now. */
    fun schedule(workManager: WorkManager, weekClose: WeekCloseScheduler) {
        waitingWork.forEach(workManager::cancelUniqueWork)
        weekClose.ensureScheduled()
        weekClose.runNow()
    }
}
