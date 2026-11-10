package mx.kompara.ui.notifications

import android.app.*
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.R
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rMX")
class NoticeContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsRepository(NoticePreferences())
    private val channels = NoticeChannels(context, settings)
    private val poster = NoticePoster(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    @Before fun setup() { allowNotices(context) }

    @Test fun `H6 exact copy contains counts duration and period but never money`() {
        val date = LocalDate.parse("2026-10-19")
        assertEquals("Tu resumen de hoy está listo" to "9 viajes · 8 h 10 min en turno",
            summaryCopy(context, NoticeTarget(NoticeKind.DAY, date.toString()), SummaryFacts(9, 490), date).let { it.first to it.second.replace('\u00a0', ' ') })
        assertEquals("Tu semana está lista" to "62 viajes · 12 – 18 oct",
            summaryCopy(context, NoticeTarget(NoticeKind.WEEK, "2026-10-12"), SummaryFacts(62, 0), date))
        assertEquals("Trae tu resumen" to "12 – 18 oct",
            summaryCopy(context, NoticeTarget(NoticeKind.IMPORT, "2026-10-12"), SummaryFacts(62, 490), date))
        assertEquals("Octubre: revisa tus registros del mes" to "Lo que te pagó cada app y tu vigencia en el IMSS.",
            summaryCopy(context, NoticeTarget(NoticeKind.MONTH, "2026-10"), SummaryFacts(null, 0), date))
        val late = summaryCopy(context, NoticeTarget(NoticeKind.DAY, date.toString()), SummaryFacts(1, 60), date.plusDays(1))
        assertEquals("Tu resumen del 19 oct está listo", late.first)
        assertEquals("1 viaje · 1 h 0 min en turno", late.second.replace('\u00a0', ' '))
    }
    @Test fun `public versions have titles only and distinct dated immutable activity intents`() = runTest {
        channels.migrate()
        val targets = listOf(NoticeTarget(NoticeKind.READER), NoticeTarget(NoticeKind.DAY, "2026-10-19"),
            NoticeTarget(NoticeKind.DAY, "2026-10-20"), NoticeTarget(NoticeKind.WEEK, "2026-10-12"), NoticeTarget(NoticeKind.MONTH, "2026-10"),
            NoticeTarget(NoticeKind.IMPORT, "2026-10-12"))
        val taps = targets.map { target ->
            val notice = poster.build(target, "Título", "9 viajes · 8 h 10 min en turno", 12345)!!
            assertEquals(12345L, notice.`when`)
            assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility)
            val public = notice.publicVersion
            assertEquals("Título", public.extras.getString(Notification.EXTRA_TITLE))
            assertNull(public.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertNull(public.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            assertNull(public.contentIntent)
            assertEquals(target, NoticeTarget.parse(shadowOf(notice.contentIntent).savedIntent.data))
            assertTrue(notice.contentIntent.isImmutable)
            notice.contentIntent
        }
        assertEquals(targets.size, taps.toSet().size)
    }
    @Test fun `only valid period payloads parse and recovery target contains no fabricated state`() {
        for (bad in listOf("kompara://notice/day/2026-02-30", "kompara://notice/week/2026-10-13",
            "kompara://notice/month/2026-13", "kompara://notice/reader/permission_lost", "kompara://notice/reader?off=true",
            "https://notice/day/2026-10-19", "kompara://notice/day/2026-10-19/extra", "kompara://notice/import/2026-10-13")) assertNull(bad, NoticeTarget.parse(Uri.parse(bad)))
        assertEquals("ganancias", NoticeNavigation.destination(NoticeTarget(NoticeKind.READER)))
        assertEquals("day/2026-10-19", NoticeNavigation.destination(NoticeTarget(NoticeKind.DAY, "2026-10-19")))
        assertEquals("week/2026-10-12", NoticeNavigation.destination(NoticeTarget(NoticeKind.WEEK, "2026-10-12")))
        // Statement import waits until after launch: a 0.2.x import reminder still in the shade opens Ganancias.
        assertEquals("ganancias", NoticeNavigation.destination(NoticeTarget(NoticeKind.IMPORT, "2026-10-12")))
        assertEquals("imss", NoticeNavigation.destination(NoticeTarget(NoticeKind.MONTH, "2026-09")))
    }
    @Test fun `channel migration renames in place and preserves blocks and disabled monthly preference`() = runTest {
        manager.createNotificationChannel(NotificationChannel(NoticeChannels.READER, "Estado del lector", NotificationManager.IMPORTANCE_NONE))
        manager.createNotificationChannel(NotificationChannel(NoticeChannels.SUMMARIES, "Resúmenes", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("fiscal", "Fiscal", NotificationManager.IMPORTANCE_NONE))
        manager.createNotificationChannel(NotificationChannel("ocr_reader_state_v2", "Lector detenido", NotificationManager.IMPORTANCE_NONE))
        channels.migrate()
        assertEquals("Estado del semáforo", manager.getNotificationChannel(NoticeChannels.READER).name)
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(NoticeChannels.READER).importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(NoticeChannels.SUMMARIES).importance)
        assertFalse(settings.settings.first().fiscalMonthlySummaryEnabled)
        assertFalse(settings.settings.first().readerAlertsEnabled)
        assertNull(manager.getNotificationChannel("fiscal"))
        assertNull(manager.getNotificationChannel("ocr_reader_state_v2"))
        settings.setFiscalMonthlySummaryEnabled(true)
        channels.migrate()
        assertTrue(settings.settings.first().fiscalMonthlySummaryEnabled)
    }
    @Test fun `runtime app and channel denial prevent posting without claiming delivery`() = runTest {
        channels.migrate()
        val target = NoticeTarget(NoticeKind.DAY, "2026-10-19")
        shadowOf(context as Application).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(poster.post(target, "Resumen", "", 1))
        allowNotices(context)
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(poster.post(target, "Resumen", "", 1))
        shadowOf(manager).setNotificationsEnabled(true)
        manager.deleteNotificationChannel(NoticeChannels.SUMMARIES)
        assertFalse(poster.post(target, "Resumen", "", 1))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun `month route updates shared selection before opening its own month`() = runTest {
        val selected = mx.kompara.ui.stats.SelectedPeriod(settings)
        val dispatcher = NoticeNavigation(selected, mx.kompara.ui.stats.AppClock { Instant.parse("2026-11-02T18:00:00Z").toEpochMilli() }, ZoneId.of("America/Mexico_City"))
        assertEquals("imss", dispatcher.route(NoticeTarget(NoticeKind.MONTH, "2026-09")))
        assertEquals("2026-09", settings.selectedMonth.first())
    }
}
