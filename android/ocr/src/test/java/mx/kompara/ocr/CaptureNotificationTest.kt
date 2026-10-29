package mx.kompara.ocr

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureNotificationTest {
    @Test fun `mandatory capture notice has a real immutable service stop action and title only public version`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notice = captureNotification(context)
        assertEquals("Semáforo de Kompara activo", notice.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("ocr_capture", notice.channelId)
        assertTrue(notice.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val action = notice.actions.single()
        assertEquals("Detener", action.title)
        assertTrue(action.actionIntent.isImmutable)
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals(OcrCaptureService.ACTION_STOP, intent.action)
        assertEquals(OcrCaptureService::class.java.name, intent.component!!.className)
        assertEquals(context.packageName, intent.component!!.packageName)
        assertEquals("Semáforo de Kompara activo", notice.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertNull(notice.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertNull(notice.publicVersion.actions)
    }
}
