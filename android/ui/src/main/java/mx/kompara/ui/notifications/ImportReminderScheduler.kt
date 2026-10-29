package mx.kompara.ui.notifications

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.share.WeekCloseScheduler
import mx.kompara.ui.stats.AppClock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Local weekly import routine. Observing persisted choices also handles changes outside G7 and
 * process restart. Work is unique; disabling removes queued work and any posted reminder. */
@Singleton
class ImportReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val poster: NoticePoster,
    private val clock: AppClock,
    private val zone: ZoneId,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            settings.settings.map { it.onboardingCompleted && it.importReminderEnabled }
                .distinctUntilChanged().collect { enabled ->
                    val manager = WorkManager.getInstance(context)
                    if (enabled) {
                        val periodic = PeriodicWorkRequestBuilder<ImportReminderWorker>(WeekCloseScheduler.PERIOD)
                            .setInitialDelay(Duration.ofMillis(WeekCloseScheduler.initialDelayMillis(clock.nowMs(), zone)))
                            .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMinutes(15)).build()
                        manager.enqueueUniquePeriodicWork(ImportReminderWorker.PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic)
                        val catchup = OneTimeWorkRequestBuilder<ImportReminderWorker>()
                            .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMinutes(15)).build()
                        manager.enqueueUniqueWork(ImportReminderWorker.ONE_OFF_WORK_NAME, ExistingWorkPolicy.KEEP, catchup)
                    } else {
                        manager.cancelUniqueWork(ImportReminderWorker.PERIODIC_WORK_NAME)
                        manager.cancelUniqueWork(ImportReminderWorker.ONE_OFF_WORK_NAME)
                        poster.cancelSummaries(NoticeKind.IMPORT)
                    }
                }
        }
    }
}
