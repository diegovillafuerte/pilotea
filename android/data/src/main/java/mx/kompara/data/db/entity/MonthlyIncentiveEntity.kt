package mx.kompara.data.db.entity

import androidx.room.Entity

/**
 * Incentives and referral bonuses the driver typed for one app and month (v4 IMSS, M4). The reader
 * never sees them in an offer. Whole pesos; tips are never included.
 */
@Entity(tableName = "monthly_incentives", primaryKeys = ["platform", "month"])
data class MonthlyIncentiveEntity(
    /** [mx.kompara.data.model.Platform] name, as trips store it. */
    val platform: String,
    /** "yyyy-MM". */
    val month: String,
    val amountMxn: Int,
    val updatedAt: Long,
)
