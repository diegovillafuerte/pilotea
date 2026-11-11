package mx.kompara.ui.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.R
import mx.kompara.ui.contract.v4.V4Contract
import mx.kompara.ui.fiscal.FiscalMonthEndScheduler
import mx.kompara.ui.share.WeekCloseScheduler
import mx.kompara.ui.share.WeekCloseWorker
import mx.kompara.ui.stats.AppClock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** V1's native owner: the launch set's two notices, their words, and what is scheduled. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rMX")
class LaunchNoticesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.parse("2026-10-19").atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    private val clock = AppClock { now }
    private val manager get() = WorkManager.getInstance(context)

    @Before fun setup() {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                object : Worker(appContext, workerParameters) { override fun doWork() = Result.retry() }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
    }
    @After fun cleanup() {
        manager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }
    private fun active(name: String) = manager.getWorkInfosForUniqueWork(name).get().filterNot { it.state.isFinished }

    @Test fun `V1 reader and week notices say the kit's words and never an amount`() {
        val reader = listOf(R.string.notice_permission_lost, R.string.notice_capture_stopped)
            .map { context.getString(R.string.notice_reader_title) to context.getString(it) }
        val week = summaryCopy(context, NoticeTarget(NoticeKind.WEEK, "2026-10-12"), SummaryFacts(62, 490), LocalDate.parse("2026-10-19"))
        assertEquals("Se apagó el semáforo" to "Falta el permiso de Accesibilidad. Abre Kompara al estacionarte.", reader.first())
        assertEquals("Tu semana está lista" to "62 viajes · 12 – 18 oct", week)
        val shown = (reader + week).joinToString(" ") { "${it.first} ${it.second}" }
        V4Contract.strings.getValue("V1").forEach { assertTrue("V1 says $it", it in shown) }
        assertFalse("A notice shows an amount: $shown", '$' in shown)
    }

    @Test fun `startup cancels the summaries that wait and keeps the Monday summary`() = runTest {
        val settings = SettingsRepository(NoticePreferences()).apply { setOnboardingCompleted(true) }
        // What 0.2.x enqueued.
        DailySummaryScheduler(context, clock, zone).run { ensureScheduled(); runNow() }
        FiscalMonthEndScheduler(context).run { ensureScheduled(); runNow() }
        ImportReminderScheduler(context, settings, NoticePoster(context), clock, zone).start(backgroundScope); runCurrent()
        LaunchNotices.waitingWork.forEach { assertTrue("0.2.x enqueued $it", active(it).isNotEmpty()) }

        LaunchNotices.schedule(manager, WeekCloseScheduler(context, clock))

        LaunchNotices.waitingWork.forEach { assertTrue("$it still enqueued", active(it).isEmpty()) }
        assertEquals(WeekCloseScheduler.initialDelayMillis(now, ZoneId.systemDefault()),
            active(WeekCloseWorker.PERIODIC_WORK_NAME).single().initialDelayMillis)
        assertEquals(1, active(WeekCloseWorker.ONE_OFF_WORK_NAME).size)
        assertEquals(listOf(NoticeKind.WEEK), LaunchNotices.summaries)
    }
}
