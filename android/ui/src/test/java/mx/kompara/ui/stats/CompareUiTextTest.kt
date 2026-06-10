package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.metrics.compare.CompareMetric
import mx.kompara.metrics.compare.CompareVerdict
import mx.kompara.metrics.compare.CompareWinner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** es-MX presentation for the Comparar tab (B-047): the verdict sentence and per-metric formatting. */
class CompareUiTextTest {

    private val resolve: (String) -> Platform? = { name ->
        runCatching { Platform.valueOf(name) }.getOrNull()
    }

    @Test
    fun `verdict sentence reads the headline example`() {
        val verdict = CompareVerdict(
            metric = CompareMetric.EARNINGS_PER_KM,
            winner = CompareWinner.B,
            winnerPlatform = "DIDI",
            loserPlatform = "UBER",
            pctDifference = 0.12,
        )
        assertEquals(
            "Esta semana DiDi te pagó 12 % más por km que Uber.",
            CompareUiText.verdictSentence(verdict, resolve),
        )
    }

    @Test
    fun `verdict rounds the percent`() {
        val verdict = CompareVerdict(
            metric = CompareMetric.EARNINGS_PER_HOUR,
            winner = CompareWinner.A,
            winnerPlatform = "UBER",
            loserPlatform = "DIDI",
            pctDifference = 0.176,
        )
        assertTrue(
            CompareUiText.verdictSentence(verdict, resolve).contains("18 % más por hora"),
        )
    }

    @Test
    fun `verdict with no percent states the win without a misleading number`() {
        val verdict = CompareVerdict(
            metric = CompareMetric.NET_EARNINGS,
            winner = CompareWinner.B,
            winnerPlatform = "DIDI",
            loserPlatform = "UBER",
            pctDifference = null,
        )
        val s = CompareUiText.verdictSentence(verdict, resolve)
        assertTrue(s.contains("DiDi te pagó más"))
        assertTrue(!s.contains("%"))
    }

    @Test
    fun `tie verdict names neither platform`() {
        val verdict = CompareVerdict(
            metric = CompareMetric.EARNINGS_PER_KM,
            winner = CompareWinner.TIE,
            winnerPlatform = null,
            loserPlatform = null,
            pctDifference = 0.0,
        )
        val s = CompareUiText.verdictSentence(verdict, resolve)
        assertTrue(s.contains("casi lo mismo"))
    }

    @Test
    fun `metric values format per metric type`() {
        assertEquals("$8.40/km", CompareUiText.metricValue(CompareMetric.EARNINGS_PER_KM, 8.4))
        assertEquals("$185.50/h", CompareUiText.metricValue(CompareMetric.EARNINGS_PER_HOUR, 185.5))
        assertEquals("83 %", CompareUiText.metricValue(CompareMetric.ACCEPTANCE_RATE, 0.834))
        assertEquals("42", CompareUiText.metricValue(CompareMetric.TOTAL_TRIPS, 42.0))
    }

    @Test
    fun `platform names are proper nouns`() {
        assertEquals("Uber", CompareUiText.platformName(Platform.UBER))
        assertEquals("DiDi", CompareUiText.platformName(Platform.DIDI))
        assertEquals("inDrive", CompareUiText.platformName(Platform.INDRIVE))
    }

    @Test
    fun `pct label rounds to whole percent`() {
        assertEquals("12 %", CompareUiText.pctLabel(0.124))
        assertEquals("13 %", CompareUiText.pctLabel(0.125))
    }
}
