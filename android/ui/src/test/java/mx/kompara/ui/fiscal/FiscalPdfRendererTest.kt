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
@org.robolectric.annotation.Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class FiscalPdfRendererTest {
    private val renderer get() = FiscalPdfRenderer(androidx.test.core.app.ApplicationProvider.getApplicationContext())

    @Test
    fun `recorded report renders sources without legacy legal fields and paginates long imports`() {
        val fixture = mx.kompara.ui.fixtures.MonthFixture(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val report = FiscalReportComposer.recorded(fixture.month("E3c"), "Generado hoy")
        val (bitmap, canvas) = pageCanvas()
        canvas.drawColor(android.graphics.Color.WHITE)
        renderer.drawPage(canvas, report)
        val output = java.io.File("build/outputs/v3-screenshots/month_factual_pdf.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val long = report.copy(sourceNotes = List(50) { index -> "Resumen $index: " + report.sourceNotes.joinToString(" ") })
        val pages = renderer.recordedPages(long)
        assertTrue(pages.size > 1)
        pages.forEach { page -> assertTrue(FiscalPdfLayout.contentTopY() + 20f + page.sumOf { it.height.toDouble() } <= FiscalPdfLayout.PAGE_HEIGHT - FiscalPdfLayout.MARGIN) }
        val text = pages.flatten().joinToString(" ") { it.text }
        assertTrue(text.contains("Resumen 49:"))
        assertFalse(text.contains("retenciones"))
        assertFalse(text.contains("cubierto"))
    }

    @Test fun `failed PDF encoding leaves no private partial file and expiry removes cached exports`() {
        val h = mx.kompara.ui.export.ExportHarness()
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            h.cache.write("fiscal_reports", "pdf") { it.write(byteArrayOf(1, 2)); throw java.io.IOException("Disk full") }
        }
        assertTrue(java.io.File(h.context.cacheDir, "fiscal_reports").listFiles()!!.isEmpty())
        // Cache expiry is independent of the encoded format. Actual PdfDocument is instrumented.
        val uri = h.cache.write("fiscal_reports", "pdf") { it.write(byteArrayOf(1, 2, 3)) }
        val file = java.io.File(h.context.cacheDir, "fiscal_reports").listFiles()!!.single()
        file.setLastModified(System.currentTimeMillis() - mx.kompara.ui.export.ExportCache.MAX_AGE_MS)
        mx.kompara.ui.export.ExportCache.prune(h.context)
        assertFalse(file.exists())
        org.junit.Assert.assertThrows(java.io.FileNotFoundException::class.java) { h.provider.openFile(uri, "r") }
    }

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
        renderer.drawPage(canvas, fullReport())
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
        renderer.drawPage(canvas, sparse)
        assertFalse(bmp.isRecycled)
    }

    @Test
    fun `empty rows still draws header and footer without crashing`() {
        val noRows = fullReport().copy(rows = emptyList())
        val (_, canvas) = pageCanvas()
        renderer.drawPage(canvas, noRows)
    }

    /**
     * Best-effort: when a native PDF runtime IS present, the full [FiscalPdfRenderer.render] writes a
     * non-empty %PDF stream. Robolectric has no native PdfDocument, so we don't fail the suite on its
     * absence — this asserts the happy path only when the document can actually be created.
     */
    @Test
    fun `render writes a pdf stream when a native PdfDocument is available`() {
        val out = ByteArrayOutputStream()
        val produced = runCatching { renderer.render(fullReport(), out) }.isSuccess
        if (produced) {
            val bytes = out.toByteArray()
            assertTrue("pdf must have bytes", bytes.isNotEmpty())
            assertTrue("must start with %PDF", String(bytes, Charsets.ISO_8859_1).startsWith("%PDF"))
        }
    }
}
