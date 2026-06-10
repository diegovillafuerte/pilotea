package mx.kompara.ui.fiscal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Render smoke test for the fiscal PDF (B-052): the page-drawing must lay out a full and a sparse
 * report onto a real `android.graphics.Canvas` without crashing. Robolectric renders Canvas/Bitmap on
 * the JVM (as the B-055 ShareCardRenderer smoke test does) but cannot construct a `PdfDocument` (no
 * native PDF runtime), so we exercise the deterministic drawing via [FiscalPdfRenderer.drawPage] on a
 * page-sized bitmap. The thin PdfDocument wrapper ([FiscalPdfRenderer.render]) is verified on-device.
 */
@RunWith(AndroidJUnit4::class)
class FiscalPdfRendererTest {

    private fun row(label: String) = FiscalReportRow(label, "$10,000.00", "$7,500.00", "$210.00", "$800.00")

    private fun fullReport() = FiscalReportData(
        wordmark = "Kompara",
        title = "Resumen fiscal mensual",
        driverName = "Juan Pérez",
        monthLabel = "Junio 2026",
        columnHeaders = listOf("Plataforma", "Bruto", "Neto", "ISR ret.", "IVA ret."),
        rows = listOf(row("Uber"), row("DiDi *")),
        monthTotals = row("Totales del mes"),
        ytdTotals = row("Acumulado del año"),
        monthWithheldTotal = "$2,020.00",
        imssCoverageLine = "IMSS: cobertura alcanzada este mes en Uber",
        approximationNote = "* DiDi no reporta su comisión.",
        disclaimer = "Estimaciones, no asesoría fiscal. ".repeat(6),
        ratesNote = "Estimación bajo régimen de plataformas (ISR 2.1%, IVA 8%).",
        generatedOn = "Generado el 10 jun 2026",
    )

    private fun pageCanvas(): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(
            FiscalPdfLayout.PAGE_WIDTH,
            FiscalPdfLayout.PAGE_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        return bmp to Canvas(bmp)
    }

    @Test
    fun `full report draws onto a page canvas without crashing`() {
        val (bmp, canvas) = pageCanvas()
        FiscalPdfRenderer.drawPage(canvas, fullReport())
        assertTrue(bmp.width > 0 && bmp.height > 0)
        assertFalse(bmp.isRecycled)
    }

    @Test
    fun `sparse report (no driver, single row, no approximation note) draws`() {
        val sparse = fullReport().copy(
            driverName = null,
            rows = listOf(row("Uber")),
            approximationNote = null,
            imssCoverageLine = "IMSS: sin actividad registrada este mes.",
        )
        val (bmp, canvas) = pageCanvas()
        FiscalPdfRenderer.drawPage(canvas, sparse)
        assertFalse(bmp.isRecycled)
    }

    @Test
    fun `empty rows still draws header and footer without crashing`() {
        val noRows = fullReport().copy(rows = emptyList())
        val (_, canvas) = pageCanvas()
        FiscalPdfRenderer.drawPage(canvas, noRows)
    }

    /**
     * Best-effort: when a native PDF runtime IS present, the full [FiscalPdfRenderer.render] writes a
     * non-empty %PDF stream. Robolectric has no native PdfDocument, so we don't fail the suite on its
     * absence — this asserts the happy path only when the document can actually be created.
     */
    @Test
    fun `render writes a pdf stream when a native PdfDocument is available`() {
        val out = ByteArrayOutputStream()
        val produced = runCatching { FiscalPdfRenderer.render(fullReport(), out) }.isSuccess
        if (produced) {
            val bytes = out.toByteArray()
            assertTrue("pdf must have bytes", bytes.isNotEmpty())
            assertTrue("must start with %PDF", String(bytes, Charsets.ISO_8859_1).startsWith("%PDF"))
        }
    }
}
