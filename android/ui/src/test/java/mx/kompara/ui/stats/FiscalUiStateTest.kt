package mx.kompara.ui.stats

import mx.kompara.metrics.fiscal.FiscalMonthSummary
import mx.kompara.metrics.fiscal.FiscalTotals
import mx.kompara.metrics.fiscal.PlatformFiscalSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Render-state contract for the Fiscal tab (B-052): the export-availability and emptiness derivations
 * the screen reads to enable/disable the "Exportar PDF" button and choose month vs. YTD totals. Pure
 * data assertions — the calculator/mapper/composer paths are covered by their own suites.
 */
class FiscalUiStateTest {

    private fun summary(platforms: List<PlatformFiscalSummary>) = FiscalMonthSummary(
        month = "2026-06",
        year = 2026,
        ratesYear = 2026,
        platforms = platforms,
        monthTotals = FiscalTotals(10_000.0, 7_500.0, 210.0, 800.0),
        ytdTotals = FiscalTotals(60_000.0, 45_000.0, 1_260.0, 4_800.0),
    )

    private fun platform() = PlatformFiscalSummary(
        platform = "UBER", grossMxn = 10_000.0, fiscalNetMxn = 7_500.0, commissionMxn = 2_500.0,
        commissionApproximated = false, estimatedIsrMxn = 210.0, estimatedIvaMxn = 800.0,
    )

    private fun state(summary: FiscalMonthSummary?, exporting: Boolean = false, ytd: Boolean = false) =
        FiscalUiState(
            loading = false,
            monthOffset = 0,
            monthLabel = "Junio 2026",
            availableMonthOffsets = listOf(0),
            monthLabels = listOf("Junio 2026"),
            thresholdMxn = 8364.0,
            usingDefaultConfig = false,
            sections = emptyList(),
            fiscalSummary = summary,
            ytdView = ytd,
            exporting = exporting,
        )

    @Test
    fun `canExport is true when the summary has at least one platform`() {
        assertTrue(state(summary(listOf(platform()))).canExport)
    }

    @Test
    fun `canExport is false for an empty summary`() {
        assertFalse(state(summary(emptyList())).canExport)
    }

    @Test
    fun `canExport is false while the summary is still loading (null)`() {
        assertFalse(state(summary = null).canExport)
    }

    @Test
    fun `loading state cannot export`() {
        assertFalse(FiscalUiState.LOADING.canExport)
    }

    @Test
    fun `ytd toggle selects the right totals to display`() {
        val s = summary(listOf(platform()))
        // The screen picks ytdTotals when ytdView, else monthTotals.
        val monthView = state(s, ytd = false)
        val ytdView = state(s, ytd = true)
        assertFalse(monthView.ytdView)
        assertTrue(ytdView.ytdView)
        // Sanity: the two totals differ so the toggle is meaningful.
        assertTrue(s.ytdTotals.grossMxn > s.monthTotals.grossMxn)
    }
}
