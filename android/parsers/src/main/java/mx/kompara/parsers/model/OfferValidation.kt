package mx.kompara.parsers.model

import kotlinx.serialization.Serializable

/** How one numeric slot of a checksum-validated card was obtained. */
@Serializable
enum class FieldProvenance { READ, RECOVERED, REPAIRED }

/** Precedence: CONTRADICTORY > UNVERIFIABLE > REPAIRED > CONSISTENT. */
@Serializable
enum class ConsistencyGrade { CONSISTENT, REPAIRED, UNVERIFIABLE, CONTRADICTORY }

/**
 * One numeric slot in integer units: minutes, tenths of a km, cents, or cents per km. [units] is
 * null when unread or unresolved; [provenance] is null exactly when [units] is. A RECOVERED value
 * names its operand slots in [operands] (bits of [OfferValidationEvidence] slot indices) and is never
 * an independent observation. [bounds] is the slot's row, kept even when its value is not.
 */
@Serializable
data class ValidatedField(
    val units: Long? = null,
    val provenance: FieldProvenance? = null,
    val bounds: OfferContentBounds? = null,
    /** [REPAIR_CONFUSABLE] | [REPAIR_DECIMAL] | [REPAIR_UNIT]. */
    val repairs: Int = 0,
    val operands: Int = 0,
) {
    companion object {
        const val REPAIR_CONFUSABLE = 1
        const val REPAIR_DECIMAL = 2
        const val REPAIR_UNIT = 4
    }
}

/**
 * One of the card's own identities. [evaluated]: every operand was independently available (a
 * recovered operand does not count). [residual]: minutes, tenths of a km, or for money the
 * dimensionless |F − R·TK| / F in parts per million — never a monetary amount.
 */
@Serializable
data class IdentityCheck(val evaluated: Boolean = false, val holds: Boolean = false, val residual: Long? = null)

/**
 * The modern Uber card's own arithmetic (TOTAL minutes = pickup + trip; TOTAL km = pickup + trip;
 * fare ≈ rate × TOTAL km within half a cent per km) and how each published value was established.
 * Optional on [OfferCard]: null for DiDi and for Uber formats without a recognized TOTAL row.
 */
@Serializable
data class OfferValidationEvidence(
    val fare: ValidatedField = ValidatedField(),
    val rate: ValidatedField = ValidatedField(),
    val totalMinutes: ValidatedField = ValidatedField(),
    val totalKm: ValidatedField = ValidatedField(),
    val pickupMinutes: ValidatedField = ValidatedField(),
    val pickupKm: ValidatedField = ValidatedField(),
    val tripMinutes: ValidatedField = ValidatedField(),
    val tripKm: ValidatedField = ValidatedField(),
    val minutes: IdentityCheck = IdentityCheck(),
    val distance: IdentityCheck = IdentityCheck(),
    val money: IdentityCheck = IdentityCheck(),
    val selectionCertain: Boolean = true,
    /** Currency-anchored or competing amounts between the product heading and the TOTAL row. */
    val fareCandidates: Int = 0,
    /** Both legs established and both route identities resolved. */
    val routeValidated: Boolean = false,
    /** The headline fare satisfied the money identity. */
    val moneyValidated: Boolean = false,
    val grade: ConsistencyGrade = ConsistencyGrade.UNVERIFIABLE,
) {
    /** Every identity resolved with one assignment (CONSISTENT, or uniquely REPAIRED). */
    val identityValidated: Boolean get() = grade == ConsistencyGrade.CONSISTENT || grade == ConsistencyGrade.REPAIRED

    /** Slot order shared by [ValidatedField.operands] and the diagnostic provenance bits. */
    val slots: List<ValidatedField> get() = listOf(fare, rate, totalMinutes, totalKm, pickupMinutes, pickupKm,
        tripMinutes, tripKm)

    companion object {
        const val FARE = 0
        const val RATE = 1
        const val TOTAL_MINUTES = 2
        const val TOTAL_KM = 3
        const val PICKUP_MINUTES = 4
        const val PICKUP_KM = 5
        const val TRIP_MINUTES = 6
        const val TRIP_KM = 7
    }
}
