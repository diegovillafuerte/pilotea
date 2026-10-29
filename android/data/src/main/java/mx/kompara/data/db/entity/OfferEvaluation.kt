package mx.kompara.data.db.entity

/** Immutable judgement, captured once per offer. Decimal strings are raw values, not display text. */
data class OfferEvaluation(
    val evaluatedAt: Long,
    val unit: String,
    val goal: String,
    val minimum: String,
    val costVersionId: Long?,
    val source: String,
    val paymentBasis: String,
    val contribution: String?,
    val perKm: String?,
    val perHour: String?,
    val unavailableReason: String?,
)
