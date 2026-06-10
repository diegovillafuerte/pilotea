package mx.kompara.ui.share

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ACTION_SEND intent shape for the share-card flow (B-055): correct action, PNG mime, image
 * stream + caption extras, and the read-grant flag. Runs under Robolectric for a real Android Intent.
 */
@RunWith(AndroidJUnit4::class)
class ShareCardSharerTest {

    @Test
    fun `builds an ACTION_SEND png intent with the image and caption`() {
        val uri = Uri.parse("content://mx.kompara.app.fileprovider/share_cards/tu_resumen_story.png")
        val intent = ShareCardSharer.sendIntent(uri, caption = "Mi semana 🚀")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(ShareCardSharer.MIME_PNG, intent.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("Mi semana 🚀", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(
            "must grant read permission to the share target",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }
}
