package mx.kompara.data.db.entity

/** A payment record explicitly attributed to one local trip. An aggregate statement is not this type. */
data class ItemizedPaymentEvidence(
    val tripId: Long,
    val sourceRecordId: String,
    val paymentMxn: Double,
    val confirmedAt: Long,
) {
    init {
        require(tripId > 0 && sourceRecordId.isNotBlank())
        require(paymentMxn.isFinite() && paymentMxn >= 0)
    }
}
