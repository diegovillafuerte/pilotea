package mx.kompara.ui.fiscal

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a rendered fiscal-report PDF to a private cache file and hands back a [FileProvider] content
 * [Uri] grantable to a share target (B-052). Mirrors [mx.kompara.ui.share.ShareCardWriter]: the PDF
 * lives under a dedicated `fiscal_reports/` cache subdir (declared in `res/xml/file_paths.xml`) so the
 * FileProvider only ever exposes these reports, never the rest of the cache. The file name carries the
 * month so successive months don't clobber each other, while re-exporting the same month overwrites.
 */
@Singleton
class FiscalPdfWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Render [data] to a PDF in the fiscal-reports cache and return a shareable content URI for it.
     * @param monthKey "yyyy-MM" — used only to name the file.
     */
    fun write(data: FiscalReportData, monthKey: String): Uri {
        val dir = File(context.cacheDir, SUBDIR).apply { mkdirs() }
        val file = File(dir, fileNameFor(monthKey))
        FileOutputStream(file).use { out -> FiscalPdfRenderer.render(data, out) }
        return FileProvider.getUriForFile(context, authority(), file)
    }

    private fun authority(): String = "${context.packageName}$AUTHORITY_SUFFIX"

    companion object {
        /** Cache subdir declared in `file_paths.xml` — the only fiscal path the FileProvider exposes. */
        const val SUBDIR = "fiscal_reports"

        /** Appended to the applicationId to form the FileProvider authority (manifest match). */
        const val AUTHORITY_SUFFIX = ".fileprovider"

        /** Stable, per-month PDF file name (sanitized monthKey). */
        fun fileNameFor(monthKey: String): String =
            "kompara_fiscal_${monthKey.replace(Regex("[^0-9-]"), "")}.pdf"
    }
}
