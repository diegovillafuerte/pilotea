package mx.kompara.ui.imports

import mx.kompara.data.imports.ImportDraftEntity
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.api.ImportMetrics
import mx.kompara.data.accounting.IncomeRecord
import java.math.BigDecimal

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data object Picking : ImportUiState
    data class SignedOut(val draft: ImportDraftEntity? = null) : ImportUiState
    data class Ready(val draft: ImportDraftEntity, val files: List<ImportFile>) : ImportUiState
    data class Reading(val saving: Boolean = false) : ImportUiState
    data class Review(val draft: ImportDraftEntity, val overlap: ImportOverlap? = null, val partial: Boolean = false) : ImportUiState
    data class Reconcile(val review: Review) : ImportUiState
    data class Missing(val draft: ImportDraftEntity, val parsed: Boolean) : ImportUiState
    data class Correcting(val review: Review, val values: ImportCorrection, val error: String? = null) : ImportUiState
    data class Saved(val weekStart: String) : ImportUiState
    data class Error(val draft: ImportDraftEntity?, val files: List<ImportFile>, val message: String, val retryable: Boolean = true, val saving: Boolean = false) : ImportUiState
}

data class ImportOverlap(val records: List<IncomeRecord>, val runningExpense: BigDecimal?, val sourceLabel: String = "Lector · aprox.") {
    val trips get() = records.sumOf { it.trips }
    val payment get() = if (records.any { it.payment == null }) null else records.fold(BigDecimal.ZERO) { sum, r -> sum + r.payment!! }
    val km get() = if (records.any { it.km == null }) null else records.fold(BigDecimal.ZERO) { sum, r -> sum + r.km!! }
    val start get() = records.minOf { it.coverage.start }
    val end get() = records.maxOf { it.coverage.endInclusive }
    val active get() = records.any { it.inProgress > 0 }
}

/** The legacy endpoint needs a pair of DiDi images; it cannot incrementally parse a first image. */
enum class ImportPlatform(val wire: String, val uploadType: String, val fileCount: Int) {
    UBER_PDF("uber", "pdf", 1), UBER_SCREENSHOT("uber", "screenshot", 1), DIDI("didi", "screenshot", 2),
    INDRIVE("indrive", "screenshot", 1),
}

data class ImportCorrection(val payment: String?, val trips: String?, val hours: String?, val km: String?, val week: String) {
    companion object {
        fun from(m: ImportMetrics) = ImportCorrection(m.netEarnings?.toBigDecimal()?.stripTrailingZeros()?.toPlainString(),
            m.totalTrips?.toString(), m.hoursOnline?.toBigDecimal()?.stripTrailingZeros()?.toPlainString(),
            m.totalKm?.toBigDecimal()?.stripTrailingZeros()?.toPlainString(), m.weekStart)
    }
    fun apply(m: ImportMetrics): ImportMetrics {
        fun number(s: String?): Double? = s?.let {
            val raw = it.trim().replace("−", "-").replace("$", "").trim()
            val cleaned = if (raw.matches(Regex("-?[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?"))) raw.replace(",", "") else raw.replace(",", ".")
            require(cleaned.matches(Regex("-?[0-9]+(?:\\.[0-9]{1,2})?"))) { "Revisa este número" }
            cleaned.toBigDecimal().toDouble()
        }
        return m.copy(netEarnings = number(payment), totalTrips = trips?.let {
            require(it.trim().matches(Regex("[0-9]+"))) { "Revisa este número" }; it.trim().toInt()
        }, hoursOnline = number(hours), totalKm = number(km), weekStart = week.trim(),
            earningsPerTrip = null, earningsPerKm = null, earningsPerHour = null, tripsPerHour = null)
            .also { mx.kompara.sync.imports.validateImportMetrics(it) }
    }
}
