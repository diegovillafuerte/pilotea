package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.metrics.compare.ComparisonMetric
import org.junit.Assert.*
import org.junit.Test

class CompareUiTextTest {
    private fun pair(a: String, b: String) = listOf(ComparisonValue("Uber", a.toBigDecimal()), ComparisonValue("DiDi", b.toBigDecimal()))
    @Test fun `difference names winning app and after running costs verb`() {
        assertEquals("DiDi te dejó $1.21 más por km", CompareUiText.appFinding(pair("9.1300", "10.3410"), ComparisonMetric.PER_KM))
    }
    @Test fun `hour finding preserves its chosen unit and whole pesos`() {
        assertEquals("Uber te dejó $32 más por hora", CompareUiText.appFinding(pair("211.6", "180"), ComparisonMetric.PER_HOUR))
    }
    @Test fun `zero loser is absolute difference with no infinite percent`() {
        assertEquals("DiDi te dejó $10.00 más por km", CompareUiText.appFinding(pair("0", "10"), ComparisonMetric.PER_KM))
    }
    @Test fun `ties after display rounding name neither app`() {
        assertEquals("Las dos apps te dejaron lo mismo por km", CompareUiText.appFinding(pair("8.001", "8.004"), ComparisonMetric.PER_KM))
    }
    @Test fun `metric formatting preserves negative signs km cents and whole hour and trip pay`() {
        assertEquals("$8.40/km", CompareUiText.rate("8.4".toBigDecimal(), ComparisonMetric.PER_KM))
        assertEquals("$186/h", CompareUiText.rate("185.5".toBigDecimal(), ComparisonMetric.PER_HOUR))
        assertEquals("$149/viaje", CompareUiText.rate("148.88235".toBigDecimal(), ComparisonMetric.PER_TRIP))
        assertEquals("−$1.05/km", CompareUiText.rate("-1.05".toBigDecimal(), ComparisonMetric.PER_KM))
    }
    @Test fun `platform names and cohort counts`() {
        assertEquals("Uber", CompareUiText.platformName(Platform.UBER)); assertEquals("DiDi", CompareUiText.platformName(Platform.DIDI))
        assertEquals("inDrive", CompareUiText.platformName(Platform.INDRIVE)); assertEquals("1,240", CompareUiText.count(1240))
    }
    @Test fun `self difference uses printed figures never zero or negative divisor`() {
        assertEquals("+12 %", CompareUiText.selfDifference("120.42857".toBigDecimal(), "106.89655".toBigDecimal()))
        assertNull(CompareUiText.selfDifference("120".toBigDecimal(), "0".toBigDecimal()))
        assertNull(CompareUiText.selfDifference("120".toBigDecimal(), "-10".toBigDecimal()))
        assertEquals("−50 %", CompareUiText.selfDifference("50".toBigDecimal(), "100".toBigDecimal()))
    }
    @Test fun `rank sentences specify actual app city and metric including below median`() {
        assertEquals("Te pagan más por viaje que al 78 % de los de Uber en CDMX", CompareUiText.rankFinding(78, "UBER", "CDMX", ComparisonMetric.PER_TRIP))
        assertEquals("Te pagan menos por viaje que al 70 % de los de DiDi en Monterrey", CompareUiText.rankFinding(30, "DIDI", "Monterrey", ComparisonMetric.PER_TRIP))
    }
}
