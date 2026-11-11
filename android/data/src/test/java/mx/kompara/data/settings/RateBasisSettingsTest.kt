package mx.kompara.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.accounting.RunningCostMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal

/** Kit figures (fixture.json costs/floors/chip_basis): cost 24.5 ÷ 12 + 0.8 = 2.8417 per km. */
class RateBasisSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    private val cost = RunningCostInputs(RunningCostMode.GAS, BigDecimal("0.8"), BigDecimal("24.5"), BigDecimal("12")).costPerKm!!
    private val hourStep = BigDecimal.TEN // ThresholdEditor.step's per-hour step in :ui

    private fun convert(t: PlatformThreshold, from: RateBasis, to: RateBasis) = LimitConversion.convert(t, from, to, cost, hourStep)

    @Test fun `the kit's cost per km`() = assertEquals(2.8417, cost.toDouble(), 0.00005)

    @Test fun `8 and 6 neto are 11 and 9 bruto and back`() {
        // per km: 8 + 2.8417 = 10.84 → 11.0; 6 + 2.8417 = 8.84 → 9.0 (nearest half peso)
        // per hour: 200 × 11/8 = 275 → 280; 150 × 9/6 = 225 → 230 (step 10, half up)
        val gross = convert(PlatformThreshold(8.0, 200.0, 6.0, 150.0), RateBasis.NET, RateBasis.GROSS)
        assertEquals(PlatformThreshold(11.0, 280.0, 9.0, 230.0), gross)
        // back: 11 − 2.8417 = 8.16 → 8.0; 9 − 2.8417 = 6.16 → 6.0; 280 × 8/11 = 203.6 → 200; 230 × 6/9 = 153.3 → 150
        assertEquals(PlatformThreshold(8.0, 200.0, 6.0, 150.0), convert(gross, RateBasis.GROSS, RateBasis.NET))
    }

    @Test fun `8 and 6·50 neto are 11 and 9·50 bruto`() {
        // 6.5 + 2.8417 = 9.34 → 9.5; the hour minimum 150 × 9.5/6.5 = 219.2 → 220
        assertEquals(PlatformThreshold(11.0, 280.0, 9.5, 220.0),
            convert(PlatformThreshold(8.0, 200.0, 6.5, 150.0), RateBasis.NET, RateBasis.GROSS))
        // 9.5 − 2.8417 = 6.66 → 6.5
        assertEquals(6.5, convert(PlatformThreshold(11.0, 280.0, 9.5, 220.0), RateBasis.GROSS, RateBasis.NET).redPerKmMxn, 0.0)
    }

    @Test fun `the same basis changes nothing`() {
        val t = PlatformThreshold(8.13, 95.0, 6.07, 70.0)
        assertEquals(t, convert(t, RateBasis.GROSS, RateBasis.GROSS))
    }

    @Test fun `limits never go below zero and the goal stays above the minimum`() {
        // bruto 3.00 / 2.50 → 0.16 → 0.0 and −0.34 → 0.0 (clamped): equal, so the meta rises one step to 0.50.
        // hours scale with the per-km ratios: 100 × 0.5/3 = 16.7 → 20; 80 × 0/2.5 = 0
        assertEquals(PlatformThreshold(0.5, 20.0, 0.0, 0.0),
            convert(PlatformThreshold(3.0, 100.0, 2.5, 80.0), RateBasis.GROSS, RateBasis.NET))
        // a zero minimum scales its hour limit with the goal's ratio: 90 × 3.5/0.5 = 630; 60 × 3.5/0.5 = 420
        assertEquals(PlatformThreshold(3.5, 630.0, 3.0, 420.0),
            convert(PlatformThreshold(0.5, 90.0, 0.0, 60.0), RateBasis.NET, RateBasis.GROSS))
    }

    @Test fun `the basis decodes leniently and defaults to NET`() {
        assertEquals(RateBasis.NET, SettingsSerialization.decode(null, { null }).rateBasis)
        assertEquals(RateBasis.GROSS, SettingsSerialization.decode(null, { null }, lookupString = mapOf(SettingsSerialization.KEY_RATE_BASIS to "GROSS")::get).rateBasis)
        assertEquals(RateBasis.NET, SettingsSerialization.decode(null, { null }, lookupString = mapOf(SettingsSerialization.KEY_RATE_BASIS to "OTHER")::get).rateBasis)
    }

    @Test fun `switching the basis saves the converted limits with it, and saveLimits can carry a staged basis`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb").also { it.delete() } })
            repo.saveLimits(PlatformThreshold(8.0, 200.0, 6.0, 150.0), PreferredMetric.IPK)
            assertEquals(RateBasis.NET, repo.settings.first().rateBasis)

            repo.switchRateBasis(RateBasis.GROSS, cost, hourStep)
            var saved = repo.settings.first()
            assertEquals(RateBasis.GROSS, saved.rateBasis)
            assertEquals(PlatformThreshold(11.0, 280.0, 9.0, 230.0), saved.threshold)
            assertEquals(PreferredMetric.IPK, saved.preferredMetric)

            repo.switchRateBasis(RateBasis.GROSS, cost, hourStep) // already GROSS: no second conversion
            assertEquals(PlatformThreshold(11.0, 280.0, 9.0, 230.0), repo.settings.first().threshold)

            repo.saveLimits(PlatformThreshold(8.0, 200.0, 6.5, 150.0), PreferredMetric.IPH, RateBasis.NET) // J2's staged Guardar
            saved = repo.settings.first()
            assertEquals(RateBasis.NET, saved.rateBasis); assertEquals(PreferredMetric.IPH, saved.preferredMetric)
            assertEquals(6.5, saved.threshold!!.redPerKmMxn, 0.0)

            repo.saveLimits(PlatformThreshold(9.0, 200.0, 6.5, 150.0), PreferredMetric.IPK) // no basis: kept
            assertEquals(RateBasis.NET, repo.settings.first().rateBasis)
        } finally { scope.cancel() }
    }
}
