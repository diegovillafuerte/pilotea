package mx.kompara.ui.fiscal

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.kompara.ui.fixtures.MonthFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** PdfDocument JNI is absent in Robolectric. This test must run on authorized Android hardware. */
@RunWith(AndroidJUnit4::class)
class FiscalPdfInstrumentedTest {
    @Test fun historicalFactualReportWritesReopensAndRendersAllPages() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = FiscalReportComposer.recorded(MonthFixture(context).month("E3c"), "Generado el 29 oct 2026")
        assertEquals("Septiembre 2026", report.monthLabel)
        assertTrue(report.columnHeaders.isEmpty())
        val long = report.copy(sourceNotes = report.sourceNotes + List(80) { "Registro de la fuente $it: sin repartir los pagos entre meses." })
        val file = File(context.cacheDir, "s18-instrumented.pdf")
        try {
            file.outputStream().use { FiscalPdfRenderer(context).render(long, it) }
            assertTrue(file.inputStream().use { String(ByteArray(5).also { bytes -> assertEquals(5, it.read(bytes)) }, Charsets.US_ASCII) } == "%PDF-")
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    assertTrue(pdf.pageCount > 1)
                    for (index in 0 until pdf.pageCount) pdf.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        assertTrue(pixels.count { android.graphics.Color.alpha(it) > 0 && android.graphics.Color.red(it) < 180 } > 100)
                        bitmap.recycle()
                    }
                }
            }
        } finally { file.delete() }
    }
}
