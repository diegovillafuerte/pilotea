package mx.kompara.data.rollup

import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.*
import java.time.Instant
import java.time.ZoneId

/** Linked, readable estimates enter the ledger once; lifecycle never implies payment confirmation. */
fun capturedIncomeRecords(trips: List<TripEntity>, offers: List<OfferEntity>, computedAt: Long, zone: ZoneId): List<IncomeRecord> {
    val byId = offers.associateBy { it.id }
    return trips.distinct().distinctBy { it.offerId?.let { id -> "offer:$id" } ?: "trip:${it.id}" }.mapNotNull { trip ->
        val offer = byId[trip.offerId]
        if (trip.startedAt > computedAt || !trip.isIncomeEligible || trip.paymentBasis != "DRIVER_PAYMENT" ||
            trip.inputProvenance == "LEGACY_UNSPECIFIED" || trip.grossMxn == null || trip.distanceKm == null ||
            trip.grossMxn < 0 || trip.distanceKm < 0 || !trip.grossMxn.isFinite() || !trip.distanceKm.isFinite() ||
            (!trip.hasItemConfirmation && offer?.outcome != OfferOutcome.ACCEPTED.name)) return@mapNotNull null
        val date = Instant.ofEpochMilli(trip.startedAt).atZone(zone).toLocalDate()
        val inProgress = trip.endedAt == null || trip.endedAt > computedAt
        IncomeRecord("trip:${trip.id}", trip.platform, Coverage(date, date), MoneySource.CAPTURED_ESTIMATE,
            trip.grossMxn.decimal(), trip.distanceKm.decimal(), 1, startedAt = trip.startedAt,
            minutes = trip.durationMin?.takeIf { it.isFinite() && it >= 0 }?.decimal(),
            lifecycle = if (trip.hasItemConfirmation) MoneyLifecycle.ITEM_CONFIRMED
                else if (inProgress) MoneyLifecycle.IN_PROGRESS else MoneyLifecycle.TAKEN_ESTIMATE,
            inProgress = if (inProgress) 1 else 0,
            costVersionId = offer?.evaluation?.costVersionId, costVersionLocked = offer?.evaluation != null,
            fieldSources = if (trip.hasItemConfirmation) mapOf(MoneyField.PAYMENT to requireNotNull(trip.confirmationEvidenceId))
                else MoneyField.entries.associateWith { "offer:${offer?.id}" })
    }
}
