package mx.kompara.ui.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.stats.AppClock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsRepository(NoticePreferences())
    private val zone = ZoneId.of("America/Mexico_City")
    private val now = LocalDate.parse("2026-10-19").atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    private val poster = NoticePoster(context)
    private val manager get() = WorkManager.getInstance(context)
    private val names = listOf(ImportReminderWorker.PERIODIC_WORK_NAME, ImportReminderWorker.ONE_OFF_WORK_NAME)

    @Before fun setup() {
        allowNotices(context)
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

    @Test fun `persisted choice schedules once without network and cancels then rearms both jobs`() = runTest {
        val scheduler = ImportReminderScheduler(context, settings, poster, AppClock { now }, zone)
        scheduler.start(backgroundScope); runCurrent()
        names.forEach { assertTrue(active(it).isEmpty()) }
        settings.setOnboardingCompleted(true); runCurrent()
        val first = names.map { active(it).single() }
        assertEquals(Duration.ofHours(1).toMillis(), first.first().initialDelayMillis)
        first.forEach { assertEquals(NetworkType.NOT_REQUIRED, it.constraints.requiredNetworkType) }
        scheduler.start(backgroundScope)
        settings.setDailySummaryEnabled(false); runCurrent()
        assertEquals(first.map { it.id }, names.map { active(it).single().id })
        NoticeChannels(context, settings).migrate()
        assertTrue(poster.post(NoticeTarget(NoticeKind.IMPORT, "2026-10-12"), "Trae tu resumen", "12 – 18 oct", now))
        settings.setImportReminderEnabled(false); runCurrent()
        names.forEach { assertTrue(active(it).isEmpty()) }
        assertTrue(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
        settings.setImportReminderEnabled(true); runCurrent()
        assertNotEquals(first.map { it.id }, names.map { active(it).single().id })
        settings.setOnboardingCompleted(false); runCurrent()
        names.forEach { assertTrue(active(it).isEmpty()) }
    }
}
