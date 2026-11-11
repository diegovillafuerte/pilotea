package mx.kompara.data.db.entity

/**
 * Immutable judgement, captured once per offer. Decimal strings are raw values, not display text.
 * [contribution], [perKm] and [perHour] are always the NET figures, whatever [rateBasis] judged the
 * verdict; gross rates come from the row's fare, distance and duration.
 */
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
    /** [mx.kompara.data.settings.RateBasis] name the verdict, [goal] and [minimum] are in. Rows from before v10 are NET. */
    val rateBasis: String = mx.kompara.data.settings.RateBasis.NET.name,
)
