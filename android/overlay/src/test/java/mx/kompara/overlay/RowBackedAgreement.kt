package mx.kompara.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.flowOf
import mx.kompara.capture.OfferEvent
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.RateBasis
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.metrics.counterOffers
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Round 3 (A3): production paints the chip from the ledger row ([RowBackedVerdict]), not from the
 * published card. A replay that evaluates the published card checks every evaluation here too, so a
 * row that diverges from the published economics (Codex R2 #1/#2) fails it instead of hiding.
 * Round 4 (B7): every check reads the session's row back from the ledger (an empty ledger fails);
 * a payment-bearing card's row carries exactly its economics; a payment-less OCR card paints no
 * economics whatever the row holds, and never rewrites the row's (a new row is minted fare-less from
 * its own legs).
 */
internal class RowBackedAgreement {
    private val store = object : DataStore<Preferences> {
        override val data = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No settings writes")
    }
    private val offers = BoundaryOfferDao()
    private val recorder = OfferRecorder(offers, CostProfileRepository(BoundaryCostProfileDao()),
        SettingsRepository(store), NetProfitEngine())

    suspend fun check(event: OfferEvent.Parsed) {
        val card = event.card
        val session = card.sessionId ?: "${event.packageName}:${event.timestampMs}"
        val before = offers.forSession(session)
        val shown = RowBackedVerdict.evaluate(recorder, event)
        val row = offers.forSession(session)
        assertNotNull("The evaluation wrote and reads the session's ledger row", row)
        assertEquals("The chip is painted from the session's ledger row", row!!.id, shown.row.id)
        fun economics(r: OfferEntity) = listOf(r.fareMxn, r.pickupKm, r.pickupMin, r.tripKm, r.tripMin)
        val published = listOf(card.fare, card.pickupDistanceKm, card.pickupEtaMin, card.tripDistanceKm, card.tripDurationMin)
        if (card.captureSource == "OCR" && card.fare == null) {
            assertEquals("A payment-less frame paints no economics, whatever the row holds",
                ReglaBand.Unavailable(ReglaUnavailable.FARE), shown.state.regla.band)
            assertEquals("A payment-less frame never rewrites the row's economics",
                before?.let(::economics) ?: listOf(null) + published.drop(1), economics(row))
        } else {
            assertEquals("The chip's row carries the published economics", published, economics(row))
            // v4: the chip says the basis the row was judged in; only a Pon Tu Precio card carries counter-offers.
            assertEquals("The chip's basis is the row's", RateBasis.fromName(row.evaluation?.rateBasis), shown.state.regla.basis)
            assertEquals("Counter-offers come from the row's frozen limits, on a bid card only",
                if (card.variant == "bid") row.counterOffers() else null, shown.state.regla.counterOffers)
        }
    }
}
