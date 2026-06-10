package mx.kompara.ui.fiscal

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires the Android share sheet for a rendered fiscal-report PDF (B-052) so the driver can send it to
 * an accountant or attach it to a credit application — over WhatsApp, email (Gmail/Outlook), Drive,
 * etc. Unlike the earnings card ([mx.kompara.ui.share.ShareCardSharer], which prefers WhatsApp), a
 * fiscal report usually goes to email, so this always presents the system chooser. Bumps an anonymous
 * local export-funnel counter so the export loop can be measured (mirrors the share-count pattern).
 *
 * The intent is `ACTION_SEND` with `application/pdf` and a read-grant for the [FileProvider]
 * [FiscalPdfWriter] URI.
 */
@Singleton
class FiscalPdfSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /** Share [pdfUri] (a PDF content URI) with a [subject] line and optional [body] text. */
    fun share(pdfUri: Uri, subject: String, body: String) {
        val base = sendIntent(pdfUri, subject, body)
        val chooser = Intent.createChooser(base, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    /** Record a completed export-share intent in the anonymous local funnel counter. */
    suspend fun recordExport() {
        settingsRepository.incrementFiscalExportCount()
    }

    companion object {
        const val MIME_PDF = "application/pdf"

        /**
         * Build the base `ACTION_SEND` PDF intent (subject + body + stream + read-grant). Pure of
         * instance state so the intent shape is unit-testable without constructing the sharer.
         */
        fun sendIntent(pdfUri: Uri, subject: String, body: String): Intent =
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_PDF
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}
