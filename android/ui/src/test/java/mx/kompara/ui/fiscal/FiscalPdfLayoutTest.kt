package mx.kompara.ui.fiscal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure layout MATH for the fiscal PDF (B-052): A4 geometry, column edges, row stacking, pagination. */
class FiscalPdfLayoutTest {

    @Test
    fun `A4 page is the standard point size`() {
        assertEquals(595, FiscalPdfLayout.PAGE_WIDTH)
        assertEquals(842, FiscalPdfLayout.PAGE_HEIGHT)
    }

    @Test
    fun `content width is symmetric inside the margins`() {
        val content = FiscalPdfLayout.contentWidth()
        assertEquals(FiscalPdfLayout.PAGE_WIDTH - 2 * FiscalPdfLayout.MARGIN, content, 0.001f)
        assertEquals(FiscalPdfLayout.MARGIN, FiscalPdfLayout.leftX(), 0.001f)
        assertEquals(FiscalPdfLayout.PAGE_WIDTH - FiscalPdfLayout.MARGIN, FiscalPdfLayout.rightX(), 0.001f)
    }

    @Test
    fun `there are five column edges, increasing left to right, ending at the right margin`() {
        val edges = FiscalPdfLayout.columnRightEdges()
        assertEquals(5, edges.size)
        // Strictly increasing.
        for (i in 1 until edges.size) {
            assertTrue("edge $i must be > edge ${i - 1}", edges[i] > edges[i - 1])
        }
        // The last money column's right edge is the content right edge.
        assertEquals(FiscalPdfLayout.rightX(), edges.last(), 0.01f)
    }

    @Test
    fun `the four money columns are equal width`() {
        val edges = FiscalPdfLayout.columnRightEdges()
        val widths = (1 until edges.size).map { edges[it] - edges[it - 1] }
        // widths = [money1, money2, money3, money4] (after the platform col)
        val first = widths.first()
        widths.forEach { assertEquals(first, it, 0.01f) }
    }

    @Test
    fun `row baselines advance by row height`() {
        val baselines = FiscalPdfLayout.rowBaselines(startY = 100f, rowCount = 3)
        assertEquals(3, baselines.size)
        assertEquals(100f + FiscalPdfLayout.ROW_HEIGHT, baselines[0], 0.001f)
        assertEquals(baselines[0] + FiscalPdfLayout.ROW_HEIGHT, baselines[1], 0.001f)
        assertEquals(baselines[1] + FiscalPdfLayout.ROW_HEIGHT, baselines[2], 0.001f)
    }

    @Test
    fun `no rows yields no baselines`() {
        assertTrue(FiscalPdfLayout.rowBaselines(100f, 0).isEmpty())
    }

    @Test
    fun `a few platforms fit on one page before the footer`() {
        val top = FiscalPdfLayout.contentTopY()
        // The launch scope is 1–3 platforms; there's ample room above the footer for that.
        assertTrue(FiscalPdfLayout.maxRowsBeforeFooter(top) >= 3)
    }

    @Test
    fun `footer sits above the bottom margin`() {
        val footerTop = FiscalPdfLayout.footerTopY()
        assertTrue(footerTop < FiscalPdfLayout.PAGE_HEIGHT - FiscalPdfLayout.MARGIN + 0.001f)
        assertTrue(footerTop > FiscalPdfLayout.contentTopY())
    }
}
