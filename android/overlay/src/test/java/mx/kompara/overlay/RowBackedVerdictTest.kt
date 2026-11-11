package mx.kompara.overlay

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mx.kompara.capture.OfferEvent
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.CounterOffers
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal

/**
 * v4 (WP-B): the chip painted from the ledger row carries the row's frozen basis and, on a Pon Tu
 * Precio card, the counter-offers that reach the row's frozen limits. Kit figures: cost 24.50 ÷ 12 +
 * 0.80 per km; limits $8 / $6 neto, $11 / $9 bruto.
 */
class RowBackedVerdictTest {
    @get:Rule val folder = TemporaryFolder()
    private val kitCost = RunningCostInputs(RunningCostMode.GAS, BigDecimal("0.8"), BigDecimal("24.5"), BigDecimal("12"))

    private fun evaluate(card: OfferCard, threshold: PlatformThreshold, basis: RateBasis) = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
                java.io.File(folder.root, "settings-${System.nanoTime()}.preferences_pb") })
            settings.saveLimits(threshold, PreferredMetric.IPK, basis)
            val recorder = OfferRecorder(BoundaryOfferDao(), CostProfileRepository(BoundaryCostProfileDao(kitCost)), settings, NetProfitEngine())
            RowBackedVerdict.evaluate(recorder, OfferEvent.Parsed(card.platform, 1_000, card))
        } finally { scope.cancel() }
    }

    private fun card(fare: Double, pickupKm: Double, pickupMin: Double, tripKm: Double, tripMin: Double, platform: String, variant: String? = null) =
        OfferCard(platform, variant, fare, pickupKm, pickupMin, tripKm, tripMin, paymentBasis = "DRIVER_PAYMENT",
            captureSource = "OCR", sessionId = "s-$fare-$variant")

    @Test fun `a Pon Tu Precio card carries the counter-offers of the row's frozen limits, any other card none`() {
        val neto = PlatformThreshold(8.0, 200.0, 6.0, 150.0)
        // S5: $90.05 for 22.9 km, 1.09 neto per km: from $203 the mínimo, from $249 the meta.
        val bid = evaluate(card(90.05, 2.0, 8.0, 20.9, 36.0, RealGeometryFixtures.DIDI, "bid"), neto, RateBasis.NET)
        assertEquals(CounterOffers(203, 249), bid.state.regla.counterOffers)
        assertEquals(203, bid.state.regla.trayMinimum)
        assertEquals("$1.09", bid.state.regla.rateText)
        assertNull(evaluate(card(90.05, 2.0, 8.0, 20.9, 36.0, RealGeometryFixtures.DIDI), neto, RateBasis.NET).state.regla.counterOffers)
        assertNull(evaluate(card(90.05, 2.0, 8.0, 20.9, 36.0, RealGeometryFixtures.DIDI, "surge"), neto, RateBasis.NET).state.regla.counterOffers)
    }

    @Test fun `a row judged in bruto paints the gross rate and the bruto counter-offers`() {
        val bruto = PlatformThreshold(11.0, 280.0, 9.0, 230.0)
        // S10: $148 for 12 km, 12.33 bruto per km, green against $11.
        val uber = evaluate(card(148.0, 2.0, 6.0, 10.0, 18.0, RealGeometryFixtures.UBER), bruto, RateBasis.GROSS)
        assertEquals(RateBasis.GROSS, uber.state.regla.basis)
        assertEquals("$12.33", uber.state.regla.rateText)
        assertEquals("GROSS", uber.row.evaluation!!.rateBasis)
        // The S5 bid in bruto: 3.93 per km; 9 × 22.9 = 206.1 → $207, 11 × 22.9 = 251.9 → $252.
        val bid = evaluate(card(90.05, 2.0, 8.0, 20.9, 36.0, RealGeometryFixtures.DIDI, "bid"), bruto, RateBasis.GROSS)
        assertEquals(CounterOffers(207, 252), bid.state.regla.counterOffers)
        assertEquals("bruto", bid.state.regla.basisWord)
    }
}
