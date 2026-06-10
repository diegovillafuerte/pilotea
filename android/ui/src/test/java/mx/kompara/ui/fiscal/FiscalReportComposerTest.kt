package mx.kompara.ui.fiscal

import mx.kompara.metrics.fiscal.FiscalMonthSummary
import mx.kompara.metrics.fiscal.FiscalTotals
import mx.kompara.metrics.fiscal.PlatformFiscalSummary
import mx.kompara.metrics.imss.CoverageStatus
import mx.kompara.metrics.imss.MonthPhase
import mx.kompara.metrics.imss.PlatformImssStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure composition of the fiscal PDF content (B-052): formatting, labels, the IMSS coverage line. */
class FiscalReportComposerTest {

    private val strings = FiscalReportComposer.ReportStrings(
        title = "Resumen fiscal mensual",
        columnHeaders = listOf("Plataforma", "Bruto", "Neto", "ISR ret.", "IVA ret."),
        monthTotalsLabel = "Totales del mes",
        ytdTotalsLabel = "Acumulado del año",
        approximationNote = "Aproximación de comisión.",
        disclaimer = "No es asesoría fiscal.",
        ratesNote = "ISR 2.1%, IVA 8%.",
        imssCoveredPrefix = "IMSS: cobertura en",
        imssNoneCovered = "IMSS: ninguna cubierta.",
        imssNoData = "IMSS: sin actividad.",
    )

    private fun platform(name: String, approx: Boolean) = PlatformFiscalSummary(
        platform = name,
        grossMxn = 10_000.0,
        fiscalNetMxn = if (approx) 10_000.0 else 7_500.0,
        commissionMxn = if (approx) 0.0 else 2_500.0,
        commissionApproximated = approx,
        estimatedIsrMxn = 210.0,
        estimatedIvaMxn = 800.0,
    )

    private fun summary(platforms: List<PlatformFiscalSummary>, approx: Boolean) = FiscalMonthSummary(
        month = "2026-06",
        year = 2026,
        ratesYear = 2026,
        platforms = platforms,
        monthTotals = FiscalTotals(10_000.0, 7_500.0, 210.0, 800.0),
        ytdTotals = FiscalTotals(60_000.0, 45_000.0, 1_260.0, 4_800.0),
    )

    private fun imss(name: String, net: Double, status: CoverageStatus) = PlatformImssStatus(
        platform = name, netSoFarMxn = net, thresholdMxn = 8364.0, remainingMxn = 0.0,
        progress = 1.0, daysRemaining = 0, projectedMonthEndMxn = net, status = status,
        phase = MonthPhase.PAST,
    )

    @Test
    fun `money columns are formatted as pesos`() {
        val data = FiscalReportComposer.compose(
            summary = summary(listOf(platform("UBER", approx = false)), approx = false),
            monthLabel = "Junio 2026",
            imssStatuses = listOf(imss("UBER", 9000.0, CoverageStatus.COVERED)),
            driverName = "Ana",
            generatedOnLabel = "Generado el 10 jun 2026",
            strings = strings,
        )
        val uber = data.rows.single()
        assertEquals("Uber", uber.label)
        assertEquals("$10,000.00", uber.grossMxn)
        assertEquals("$7,500.00", uber.netMxn)
        assertEquals("$210.00", uber.isrMxn)
        assertEquals("$800.00", uber.ivaMxn)
        assertEquals("Ana", data.driverName)
        assertNull(data.approximationNote) // not approximated
    }

    @Test
    fun `approximated platform gets a star and the approximation note appears`() {
        val data = FiscalReportComposer.compose(
            summary = summary(listOf(platform("DIDI", approx = true)), approx = true),
            monthLabel = "Junio 2026",
            imssStatuses = emptyList(),
            driverName = null,
            generatedOnLabel = "x",
            strings = strings,
        )
        assertEquals("DiDi *", data.rows.single().label)
        assertNotNull(data.approximationNote)
        assertNull(data.driverName)
    }

    @Test
    fun `imss line lists the covered platforms`() {
        val data = FiscalReportComposer.compose(
            summary = summary(listOf(platform("UBER", false), platform("DIDI", false)), approx = false),
            monthLabel = "Junio 2026",
            imssStatuses = listOf(
                imss("UBER", 9000.0, CoverageStatus.COVERED),
                imss("DIDI", 5000.0, CoverageStatus.UNLIKELY),
            ),
            driverName = null,
            generatedOnLabel = "x",
            strings = strings,
        )
        assertTrue(data.imssCoverageLine.contains("Uber"))
        assertTrue(!data.imssCoverageLine.contains("DiDi"))
    }

    @Test
    fun `imss line notes when none covered and when no data`() {
        val none = FiscalReportComposer.compose(
            summary = summary(listOf(platform("UBER", false)), false),
            monthLabel = "x", imssStatuses = listOf(imss("UBER", 3000.0, CoverageStatus.UNLIKELY)),
            driverName = null, generatedOnLabel = "x", strings = strings,
        )
        assertEquals(strings.imssNoneCovered, none.imssCoverageLine)

        val empty = FiscalReportComposer.compose(
            summary = summary(emptyList(), false), monthLabel = "x", imssStatuses = emptyList(),
            driverName = null, generatedOnLabel = "x", strings = strings,
        )
        assertEquals(strings.imssNoData, empty.imssCoverageLine)
    }

    @Test
    fun `platform labels are display-cased`() {
        assertEquals("Uber", FiscalReportComposer.platformLabel("UBER"))
        assertEquals("DiDi", FiscalReportComposer.platformLabel("DIDI"))
        assertEquals("inDrive", FiscalReportComposer.platformLabel("INDRIVE"))
    }

    @Test
    fun `blank driver name is omitted`() {
        val data = FiscalReportComposer.compose(
            summary = summary(listOf(platform("UBER", false)), false),
            monthLabel = "x", imssStatuses = emptyList(),
            driverName = "   ", generatedOnLabel = "x", strings = strings,
        )
        assertNull(data.driverName)
    }
}
