package mx.kompara.ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.data.service.*
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.onboarding.ServiceWatchdog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncidentDeliveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val settings = SettingsRepository(NoticePreferences())
    private var now = Instant.parse("2026-10-20T02:31:00Z").toEpochMilli()
    private val clock = object : Clock() {
        override fun getZone() = ZoneId.of("America/Mexico_City")
        override fun withZone(zone: ZoneId) = this
        override fun instant() = Instant.ofEpochMilli(now)
    }
    private val store = object : ReaderMemoryStore {
        var value = ReaderMemory()
        override fun load() = value
        override fun save(memory: ReaderMemory) { value = memory }
    }
    private fun watchdog(reader: ReaderReadinessRepository) = ServiceWatchdog(context, object : ServiceStatusProvider {
        override val connected = MutableStateFlow(reader.state.value.bound)
        override val readiness = reader.state
    }, settings, reader, NoticeChannels(context, settings), NoticePoster(context))
    @Before fun setup() { allowNotices(context) }
    private fun outage(reader: ReaderReadinessRepository) { reader.bound(); reader.disconnected(false) }

    @Test fun `H6b overnight and recreated watchdog preserve original cause timestamp and one notice until repair`() = runTest {
        settings.setOnboardingCompleted(true)
        val reader = ReaderReadinessRepository(store, clock); outage(reader)
        val firstScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        watchdog(reader).start(firstScope); runCurrent()
        val first = shadowOf(manager).allNotifications.single()
        assertEquals("Se apagó el semáforo", first.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Falta el permiso de Accesibilidad. Abre Kompara al estacionarte.", first.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(now, first.`when`)
        assertTrue(store.value.incident!!.notified)
        firstScope.cancel()
        now += Duration.ofHours(11).toMillis()
        val restarted = ReaderReadinessRepository(store, clock)
        restarted.observeEnvironment(false, true)
        watchdog(restarted).start(backgroundScope); runCurrent(); advanceTimeBy(5001); runCurrent()
        assertSame(first, shadowOf(manager).allNotifications.single())
        assertEquals(now - Duration.ofHours(11).toMillis(), restarted.state.value.incident!!.firstSeenAt)
        val staleTap = shadowOf(first.contentIntent).savedIntent
        restarted.observeEnvironment(true, true); runCurrent()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertEquals(ReaderPhase.PREPARED, restarted.state.value.phase)
        assertEquals("inicio", NoticeNavigation.destination(NoticeTarget.parse(staleTap.data)!!))
        assertNull(restarted.state.value.incident)
    }
    @Test fun `Android denial is not delivered and permission return retries same unresolved incident`() = runTest {
        settings.setOnboardingCompleted(true)
        shadowOf(manager).setNotificationsEnabled(false)
        val reader = ReaderReadinessRepository(store, clock); outage(reader)
        watchdog(reader).start(backgroundScope); runCurrent()
        assertFalse(reader.state.value.incident!!.notified)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        shadowOf(manager).setNotificationsEnabled(true)
        advanceTimeBy(5001); runCurrent()
        assertTrue(reader.state.value.incident!!.notified)
        assertEquals(now, shadowOf(manager).allNotifications.single().`when`)
    }
    @Test fun `capture stopped and permission lost share one slot and paused incidents remain silent`() = runTest {
        settings.setOnboardingCompleted(true)
        val reader = ReaderReadinessRepository(store, clock)
        reader.bound(); reader.captureRunning(true); reader.pauseUntilTomorrow(); reader.captureRunning(false)
        watchdog(reader).start(backgroundScope); runCurrent()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        reader.resume(); runCurrent()
        assertEquals("Dejamos de leer tus ofertas. Abre Kompara al estacionarte.",
            shadowOf(manager).allNotifications.single().extras.getString(Notification.EXTRA_TEXT))
        now += 60_000; reader.disconnected(false); runCurrent()
        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertEquals(now, shadowOf(manager).allNotifications.single().`when`)
        reader.pauseUntilTomorrow(); runCurrent()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        reader.resume(); runCurrent()
        assertTrue(shadowOf(manager).allNotifications.isEmpty()) // already notified, no new incident
        reader.bound(); reader.disconnected(false); runCurrent()
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
    @Test fun `legacy delivered notice upgrades its tap without another timestamp or duplicate`() = runTest {
        settings.setOnboardingCompleted(true)
        val reader = ReaderReadinessRepository(store, clock); outage(reader)
        reader.markNotified(reader.state.value.incident!!)
        manager.createNotificationChannel(android.app.NotificationChannel(NoticeChannels.READER, "Estado del lector", NotificationManager.IMPORTANCE_HIGH))
        manager.notify(4036, Notification.Builder(context, NoticeChannels.READER).setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Se apagó el semáforo").setWhen(now).build())
        watchdog(reader).start(backgroundScope); runCurrent()
        val notice = shadowOf(manager).allNotifications.single()
        assertEquals(now, notice.`when`)
        assertTrue(notice.extras.getBoolean("notice_v3"))
        assertEquals(NoticeTarget(NoticeKind.READER), NoticeTarget.parse(shadowOf(notice.contentIntent).savedIntent.data))
    }
}
