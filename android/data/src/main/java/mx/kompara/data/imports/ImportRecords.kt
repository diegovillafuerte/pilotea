package mx.kompara.data.imports

import androidx.room.Entity
import androidx.room.PrimaryKey
import mx.kompara.data.accounting.*
import java.time.LocalDate

/** A retained input set. Raw preview never changes when the driver corrects a field. */
@Entity(tableName = "import_drafts")
data class ImportDraftEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val uploadType: String,
    val createdAt: Long,
    val consentAt: Long? = null,
    val previewJson: String? = null,
    val correctionJson: String? = null,
    val stage: String = "READY",
    val error: String? = null,
    val parentId: String? = null,
)

@Entity(tableName = "import_files", primaryKeys = ["draftId", "position"])
data class ImportFileEntity(val draftId: String, val position: Int, val name: String, val mime: String, val bytes: ByteArray)

/** Saved locally, never equivalent to a server confirmation or an itemized payment receipt. */
@Entity(tableName = "import_statements")
data class ImportStatementEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val start: String,
    val endInclusive: String,
    val payment: String?,
    val trips: Int?,
    val km: String?,
    val hours: String?,
    val source: String,
    val uploadType: String,
    val savedAt: Long,
) {
    fun income() = IncomeRecord("statement:$id", platform,
        Coverage(LocalDate.parse(start), LocalDate.parse(endInclusive), dated = false),
        MoneySource.valueOf(source), payment?.toBigDecimal(), km?.toBigDecimal(), trips ?: 0,
        minutes = hours?.toBigDecimal()?.multiply(60.toBigDecimal()),
        paymentBasis = PaymentBasis.DRIVER_PAYMENT, tripsKnown = trips != null)
}

/** One explicit field choice per app/period; the original captured and parsed rows are untouched. */
@Entity(tableName = "import_selections", primaryKeys = ["platform", "start"])
data class ImportSelectionEntity(val platform: String, val start: String, val statementId: String, val fields: String) {
    fun domain() = StatementSelection("statement:$statementId", fields.split(',').map(MoneyField::valueOf).toSet())
}
