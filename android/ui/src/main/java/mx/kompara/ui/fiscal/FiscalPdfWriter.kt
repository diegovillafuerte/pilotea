package mx.kompara.ui.fiscal

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.ui.export.ExportCache
import java.time.YearMonth
import javax.inject.Inject

class FiscalPdfWriter @Inject constructor(@ApplicationContext context: Context, private val cache: ExportCache) {
    private val renderer = FiscalPdfRenderer(context)
    fun write(data: FiscalReportData, monthKey: String): Uri {
        YearMonth.parse(monthKey)
        return cache.write(SUBDIR, "pdf") { renderer.render(data, it) }
    }
    fun discard(uri: Uri) = cache.discard(uri)
    companion object {
        const val SUBDIR = "fiscal_reports"
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
