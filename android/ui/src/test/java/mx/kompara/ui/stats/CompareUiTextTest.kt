package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.metrics.compare.ComparisonMetric
import org.junit.Assert.*
import org.junit.Test

class CompareUiTextTest {
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
    @Test fun `rank sentences specify actual app city and metric including below median`() {
        assertEquals("Te pagan más por viaje que al 78 % de los de Uber en CDMX", CompareUiText.rankFinding(78, "UBER", "CDMX", ComparisonMetric.PER_TRIP))
        assertEquals("Te pagan menos por viaje que al 70 % de los de DiDi en Monterrey", CompareUiText.rankFinding(30, "DIDI", "Monterrey", ComparisonMetric.PER_TRIP))
    }
}
