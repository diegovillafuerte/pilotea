package mx.kompara.data.db.entity

/** Lifecycle and payment confirmation are independent axes. */
enum class TripLifecycle { ACCEPTED_ESTIMATE, IN_PROGRESS, FINISHED, UNKNOWN, CANCELLED }

val TripEntity.hasItemConfirmation: Boolean get() = !confirmationEvidenceId.isNullOrBlank() &&
    confirmationSource == "ITEMIZED_PAYMENT" && confirmedAt != null

val TripEntity.isIncomeEligible: Boolean get() = lifecycle in setOf(
    TripLifecycle.ACCEPTED_ESTIMATE.name, TripLifecycle.IN_PROGRESS.name, TripLifecycle.FINISHED.name,
) && (offerId != null || hasItemConfirmation)
