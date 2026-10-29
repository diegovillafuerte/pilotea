package mx.kompara.ui.share

import android.content.*
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import mx.kompara.data.settings.SettingsRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareCardSharerTest {
    private val uri = Uri.parse("content://mx.kompara.app.fileprovider/share_cards/resumen.png")
    private val preferences = SettingsRepository(object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    })
    @Test fun `PNG send grants only reading for the stream and ClipData`() {
        val intent = ShareCardSharer.sendIntent(uri, "Mi semana")
        assertEquals(Intent.ACTION_SEND, intent.action); assertEquals("image/png", intent.type)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals("Mi semana", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    @Test fun `missing WhatsApp falls back and More always opens chooser with same payload`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sharer = ShareCardSharer(context, preferences)
        assertEquals(ShareTarget.CHOOSER, sharer.share(uri, "12 – 18 oct"))
        var chooser = Shadows.shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(uri, chooser.clipData!!.getItemAt(0).uri)
        Shadows.shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = "com.whatsapp" })
        assertEquals(ShareTarget.WHATSAPP, sharer.share(uri, "12 – 18 oct"))
        assertEquals("com.whatsapp", Shadows.shadowOf(context).nextStartedActivity.`package`)
        assertEquals(ShareTarget.CHOOSER, sharer.share(uri, "12 – 18 oct", false))
        chooser = Shadows.shadowOf(context).nextStartedActivity
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertNull(send.`package`); assertEquals("12 – 18 oct", send.getStringExtra(Intent.EXTRA_TEXT))
    }
    @Test fun `removed or incompatible WhatsApp retries chooser and unavailable chooser reports failure`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(base.packageManager).installPackage(PackageInfo().apply { packageName = "com.whatsapp" })
        val started = mutableListOf<Intent>()
        var denyAll = false
        val context = object : ContextWrapper(base) {
            override fun startActivity(intent: Intent) {
                started += intent
                if (denyAll || intent.`package` == "com.whatsapp") throw ActivityNotFoundException()
            }
        }
        val sharer = ShareCardSharer(context, preferences)
        assertEquals(ShareTarget.CHOOSER, sharer.share(uri, "Mi semana"))
        assertEquals(listOf(Intent.ACTION_SEND, Intent.ACTION_CHOOSER), started.map { it.action })
        denyAll = true
        assertEquals(ShareTarget.FAILED, sharer.share(uri, "Mi semana", false))
    }
}
