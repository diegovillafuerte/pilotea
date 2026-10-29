package mx.kompara.ui.fiscal

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Intent shape for the fiscal-PDF export (B-052): ACTION_SEND, application/pdf, stream + subject +
 * body + read-grant. Runs under Robolectric for android.content.Intent / Uri. Also covers the
 * per-month file name. The actual chooser launch is best-effort and not asserted here.
 */
@RunWith(AndroidJUnit4::class)
class FiscalPdfSharerTest {

    @Test
    fun `send intent is a pdf ACTION_SEND with stream subject body and read grant`() {
        val uri = Uri.parse("content://mx.kompara.fileprovider/fiscal_reports/kompara_fiscal_2026-06.pdf")
        val intent = FiscalPdfSharer.sendIntent(uri, subject = "Resumen Kompara", body = "Adjunto")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(FiscalPdfSharer.MIME_PDF, intent.type)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("Resumen Kompara", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Adjunto", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(
            "must grant read permission",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun `invalid month cannot create a report with a misleading period`() {
        val h = mx.kompara.ui.export.ExportHarness()
        val report = FiscalReportComposer.recorded(mx.kompara.ui.fixtures.MonthFixture(h.context).month(), "Generado hoy")
        org.junit.Assert.assertThrows(java.time.format.DateTimeParseException::class.java) {
            FiscalPdfWriter(h.context, h.cache).write(report, "2026-06/../etc")
        }
        assertTrue(java.io.File(h.context.cacheDir, "fiscal_reports").listFiles().orEmpty().isEmpty())
    }
}
