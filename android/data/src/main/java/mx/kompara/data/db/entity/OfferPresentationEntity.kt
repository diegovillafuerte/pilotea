package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** S5 records measured presentation outcomes against the capture's immutable session identity. */
@Entity(tableName = "offer_presentations")
data class OfferPresentationEntity(
    @PrimaryKey val sessionId: String,
    val outcome: String,
    val recordedAt: Long,
    val cause: String? = null,
) {
    init {
        require(outcome in setOf("FULL", "COMPACT", "SUPPRESSED", "UNSUPPORTED"))
        require(outcome != "SUPPRESSED" || !cause.isNullOrBlank())
    }
}
