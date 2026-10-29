package mx.kompara.data.db.entity

/** Observed lifecycle outcome. Legacy DECLINED/EXPIRED names remain readable without new tap guesses. */
enum class OfferOutcome {
    PENDING,
    UNKNOWN,
    NOT_TAKEN,
    CANCELLED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
}
