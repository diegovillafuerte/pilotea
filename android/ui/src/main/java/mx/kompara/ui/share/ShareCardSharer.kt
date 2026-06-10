package mx.kompara.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires the Android share sheet for a rendered share-card image (B-055), preferring WhatsApp — the
 * channel the driver communities run on — and falling back to the system chooser when WhatsApp isn't
 * installed. Also bumps the anonymous local share-funnel counter so the loop can be measured.
 *
 * The intent is `ACTION_SEND` with `image/png` and a read-grant for the [FileProvider][ShareCardWriter]
 * URI. We try `com.whatsapp` directly; if it's resolvable we target it, otherwise we present a chooser
 * so the driver can pick Instagram, Telegram, Messenger, etc. — every path still shares the same image.
 */
@Singleton
class ShareCardSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Share [imageUri] (a PNG content URI) with an optional [caption] as the text body. Returns the
     * resolved [ShareTarget] so the caller/tests can assert WhatsApp-vs-chooser. The actual
     * `startActivity` is best-effort (a brand-new emulator may have no share targets at all).
     */
    fun share(imageUri: Uri, caption: String): ShareTarget {
        val base = sendIntent(imageUri, caption)
        val whatsappInstalled = isWhatsAppInstalled()
        val intent = if (whatsappInstalled) {
            Intent(base).setPackage(WHATSAPP_PACKAGE)
        } else {
            Intent.createChooser(base, caption).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return if (whatsappInstalled) ShareTarget.WHATSAPP else ShareTarget.CHOOSER
    }

    /** Record a completed share intent in the anonymous local funnel counter. */
    suspend fun recordShare() {
        settingsRepository.incrementShareCount()
    }

    private fun isWhatsAppInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, 0)
        true
    }.getOrDefault(false)

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val MIME_PNG = "image/png"

        /**
         * Build the base `ACTION_SEND` image intent (PNG, image stream + caption, read-grant). Pure of
         * any instance state so the intent shape is unit-tested without constructing the sharer.
         */
        fun sendIntent(imageUri: Uri, caption: String): Intent =
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_PNG
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}

/** Which target the share intent was aimed at (B-055) — for the funnel + tests. */
enum class ShareTarget { WHATSAPP, CHOOSER }
