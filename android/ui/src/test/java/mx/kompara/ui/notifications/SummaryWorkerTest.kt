package mx.kompara.ui.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.*
import mx.kompara.data.service.DrivingActivity
import mx.kompara.data.settings.*
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.ui.fiscal.FiscalMonthEndWorker
import mx.kompara.ui.share.WeekCloseWorker
import mx.kompara.ui.stats.AppClock
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rMX")
class SummaryWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("America/Mexico_City")
    private var now = LocalDate.parse("2026-10-19").atTime(21, 12).atZone(zone).toInstant().toEpochMilli()
    private val clock = AppClock { now }
    private val settings = SettingsRepository(NoticePreferences())
    private lateinit var db: KomparaDatabase
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun delivery() = SummaryDelivery(context, settings, NoticeChannels(context, settings), NoticePoster(context),
        SummarySafety(db.offerDao(), db.tripDao()), PeriodMoneyRepository(db, CostProfileRepository(db.costProfileDao()),
            CarCostRepository(db.carCostDao())), clock, zone)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        allowNotices(context); DrivingActivity.foreground(null)
    }
    @After fun cleanup() { db.close(); DrivingActivity.foreground(null) }
    private suspend fun seed(day: String = "2026-10-19"): Long {
        val start = LocalDate.parse(day).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val offer = db.offerDao().insert(OfferEntity(seenAt = start, platform = "UBER", fareMxn = 148.0,
            distanceKm = 12.0, durationMin = 24.0, outcome = "ACCEPTED"))
        return db.tripDao().insert(TripEntity(offerId = offer, startedAt = start, endedAt = start + 1_440_000,
            platform = "UBER", grossMxn = 148.0, distanceKm = 12.0, durationMin = 24.0, lifecycle = "FINISHED"))
    }
    private fun target() = NoticeTarget.parse(shadowOf(shadowOf(manager).allNotifications.single().contentIntent).savedIntent.data)

    private fun worker(kind: NoticeKind, delivery: SummaryDelivery): CoroutineWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker = when (kind) {
                NoticeKind.DAY -> DailySummaryWorker(appContext, params, delivery)
                NoticeKind.WEEK -> WeekCloseWorker(appContext, params, delivery)
                NoticeKind.MONTH -> FiscalMonthEndWorker(appContext, params, delivery)
                else -> error("summary")
            }
        }
        return when (kind) {
            NoticeKind.DAY -> TestListenableWorkerBuilder<DailySummaryWorker>(context).setWorkerFactory(factory).build()
            NoticeKind.WEEK -> TestListenableWorkerBuilder<WeekCloseWorker>(context).setWorkerFactory(factory).build()
            else -> TestListenableWorkerBuilder<FiscalMonthEndWorker>(context).setWorkerFactory(factory).build()
        }
    }
    @Test fun `daily worker defers in host and navigation with known offer or trip then delivers original day after restart`() = runTest {
        settings.setOnboardingCompleted(true); seed()
        val first = worker(NoticeKind.DAY, delivery())
        DrivingActivity.unknown()
        assertEquals(ListenableWorker.Result.retry(), first.doWork())
        DrivingActivity.foreground("com.ubercab.driver")
        assertEquals(ListenableWorker.Result.retry(), first.doWork())
        DrivingActivity.foreground("com.google.android.apps.maps")
        val offer = db.offerDao().insert(OfferEntity(seenAt = now, platform = "DIDI", fareMxn = null, distanceKm = null, durationMin = null))
        assertEquals(ListenableWorker.Result.retry(), first.doWork())
        val tripId = db.tripDao().accept(TripEntity(offerId = offer, startedAt = now, platform = "DIDI", grossMxn = null,
            distanceKm = null, durationMin = null, lifecycle = "IN_PROGRESS"))
        assertEquals(ListenableWorker.Result.retry(), first.doWork())
        now += Duration.ofHours(11).toMillis()
        assertEquals(ListenableWorker.Result.retry(), worker(NoticeKind.DAY, delivery()).doWork())
        db.tripDao().cancel(tripId, now)
        assertEquals(ListenableWorker.Result.success(), worker(NoticeKind.DAY, delivery()).doWork())
        assertEquals(NoticeTarget(NoticeKind.DAY, "2026-10-19"), target())
        assertEquals("Tu resumen del 19 oct está listo", shadowOf(manager).allNotifications.single().extras.getString(android.app.Notification.EXTRA_TITLE))
        manager.cancelAll()
        assertEquals(ListenableWorker.Result.success(), worker(NoticeKind.DAY, delivery()).doWork())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun `week worker keeps denied period across another Monday and never opens share preview`() = runTest {
        settings.setOnboardingCompleted(true); seed("2026-10-12")
        shadowOf(manager).setNotificationsEnabled(false)
        assertEquals(ListenableWorker.Result.retry(), worker(NoticeKind.WEEK, delivery()).doWork())
        now += Duration.ofDays(7).toMillis()
        shadowOf(manager).setNotificationsEnabled(true)
        assertEquals(ListenableWorker.Result.success(), worker(NoticeKind.WEEK, delivery()).doWork())
        assertEquals(NoticeTarget(NoticeKind.WEEK, "2026-10-12"), target())
    }
    @Test fun `month worker is factual without regulatory gate and defers until safe`() = runTest {
        settings.setOnboardingCompleted(true); seed("2026-09-20")
        DrivingActivity.foreground("com.didiglobal.driver")
        assertEquals(ListenableWorker.Result.retry(), worker(NoticeKind.MONTH, delivery()).doWork())
        DrivingActivity.foreground(null)
        assertEquals(ListenableWorker.Result.success(), worker(NoticeKind.MONTH, delivery()).doWork())
        assertEquals(NoticeTarget(NoticeKind.MONTH, "2026-09"), target())
        val extras = shadowOf(manager).allNotifications.single().extras
        val text = listOf(android.app.Notification.EXTRA_TITLE, android.app.Notification.EXTRA_TEXT,
            android.app.Notification.EXTRA_BIG_TEXT).joinToString { extras.getCharSequence(it).toString() }
        assertFalse(text.contains("cubierto")); assertFalse(text.contains("148")); assertFalse(text.contains("$"))
    }
    @Test fun `periodic and app open race posts only once and dismissing never replays`() = runTest {
        settings.setOnboardingCompleted(true); seed()
        val delivery = delivery()
        listOf(async { delivery.deliver(NoticeKind.DAY) }, async { delivery.deliver(NoticeKind.DAY) }).awaitAll()
        assertEquals(1, shadowOf(manager).allNotifications.size)
        manager.cancelAll()
        assertFalse(delivery.deliver(NoticeKind.DAY))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun `disabled no data and onboarding branches never generate empty sample summaries`() = runTest {
        val delivery = delivery()
        assertFalse(delivery.deliver(NoticeKind.DAY))
        settings.setOnboardingCompleted(true)
        assertFalse(delivery.deliver(NoticeKind.MONTH))
        seed("2026-10-12"); settings.setShareWeeklyReminderEnabled(false)
        assertFalse(delivery.deliver(NoticeKind.WEEK))
        settings.setShareWeeklyReminderEnabled(true)
        assertFalse(delivery.deliver(NoticeKind.WEEK))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun `empty live day stays pending so work after the evening check can still be summarized`() = runTest {
        settings.setOnboardingCompleted(true)
        val delivery = delivery()
        assertTrue(delivery.deliver(NoticeKind.DAY))
        seed()
        assertFalse(delivery.deliver(NoticeKind.DAY))
        assertEquals(NoticeTarget(NoticeKind.DAY, "2026-10-19"), target())
    }
    @Test fun `unknown open trip remains unsafe without assuming end from elapsed minutes`() = runTest {
        db.tripDao().insert(TripEntity(startedAt = now - Duration.ofDays(1).toMillis(), platform = "UBER",
            grossMxn = null, distanceKm = null, durationMin = 1.0, lifecycle = "UNKNOWN"))
        assertTrue(SummarySafety(db.offerDao(), db.tripDao()).busy())
    }
}
