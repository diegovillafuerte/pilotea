package mx.kompara.ui.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** The same local outbox and driving guard as the other dated notices; never uploads a file. */
@HiltWorker
class ImportReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val delivery: SummaryDelivery,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (delivery.deliver(NoticeKind.IMPORT)) Result.retry() else Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (_: Exception) { Result.retry() }

    companion object {
        const val PERIODIC_WORK_NAME = "kompara_import_reminder_periodic"
        const val ONE_OFF_WORK_NAME = "kompara_import_reminder_oneoff"
    }
}
