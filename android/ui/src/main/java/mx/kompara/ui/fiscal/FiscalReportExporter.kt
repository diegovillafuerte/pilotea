package mx.kompara.ui.fiscal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.kompara.ui.format.spanishDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-call fiscal-PDF export (B-052): resolve the localized report strings from resources, compose the
 * render-ready [FiscalReportData] ([FiscalReportComposer]), write it to the FileProvider cache
 * ([FiscalPdfWriter]), fire the `application/pdf` share chooser ([FiscalPdfSharer]), and bump the
 * anonymous export-funnel counter. Holds the Android Context/string lookups so the calculator,
 * composer, and renderer stay pure and the viewmodel stays thin.
 */
@Singleton
class FiscalReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: FiscalPdfWriter,
    private val sharer: FiscalPdfSharer,
) {

    suspend fun exportRecorded(month: mx.kompara.ui.stats.MonthPresentation): Boolean {
        if (mx.kompara.ui.stats.ImssTracker.payments(month.money).isEmpty()) return false
        val data = FiscalReportComposer.recorded(month, "Generado el ${todayLabel()}")
        var uri: android.net.Uri? = null
        var launched = false
        try {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { uri = writer.write(data, month.month.toString()) }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            launched = sharer.share(requireNotNull(uri), "Resumen del mes · ${data.monthLabel}", "Registros del mes · Kompara")
            if (launched) sharer.recordExport()
            return launched
        } finally {
            if (!launched) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { uri?.let(writer::discard) }
        }
    }

    private fun todayLabel(): String =
        Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(DAY_LABEL)

    private companion object {
        val DAY_LABEL: DateTimeFormatter = spanishDate("d MMM yyyy")
    }
}
