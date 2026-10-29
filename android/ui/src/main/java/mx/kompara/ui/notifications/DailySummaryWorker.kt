package mx.kompara.ui.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import mx.kompara.ui.notifications.NoticeKind
import mx.kompara.ui.notifications.SummaryDelivery

/** Calendar-scoped, amount-free delivery; retries retain their original dated target. */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val delivery: SummaryDelivery,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (delivery.deliver(NoticeKind.DAY)) Result.retry() else Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (_: Exception) { Result.retry() }

    companion object {
        const val PERIODIC_WORK_NAME = "kompara_day_summary_periodic"
        const val ONE_OFF_WORK_NAME = "kompara_day_summary_oneoff"
    }
}
