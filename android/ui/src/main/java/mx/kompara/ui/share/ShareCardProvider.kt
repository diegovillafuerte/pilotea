package mx.kompara.ui.share

import mx.kompara.data.accounting.PeriodMoney
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.ui.stats.monday
import java.time.*
import javax.inject.Inject

/** Explicit route scope; legacy notification links resolve to the last closed week, never a month. */
data class SharePeriod(val kind: SharePeriodKind, val start: LocalDate) {
    val end: LocalDate get() = if (kind == SharePeriodKind.WEEK) start.plusDays(6) else YearMonth.from(start).atEndOfMonth()
    companion object {
        fun resolve(week: String?, month: String?, today: LocalDate): SharePeriod {
            require(week.isNullOrBlank() || month.isNullOrBlank())
            return when {
                !month.isNullOrBlank() -> SharePeriod(SharePeriodKind.MONTH, YearMonth.parse(month).atDay(1))
                !week.isNullOrBlank() -> SharePeriod(SharePeriodKind.WEEK, LocalDate.parse(week).also { require(it == it.monday()) })
                else -> SharePeriod(SharePeriodKind.WEEK, today.monday().minusWeeks(1))
            }.also { require(it.start <= today) }
        }
    }
}

/** One frozen accounting snapshot per preview. A privacy toggle never re-queries a different period. */
class ShareCardProvider @Inject constructor(private val repository: PeriodMoneyRepository) {
    suspend fun load(period: SharePeriod, asOf: Instant, zone: ZoneId): PeriodMoney {
        val window = PeriodWindow(period.start, period.end, asOf, zone)
        // The import API cannot yet supply ConfirmedStatement proof. Never promote local imports or
        // legacy percentile DTOs to a standing; S11/S16's certified seam remains gated in production.
        return if (period.kind == SharePeriodKind.MONTH) repository.monthSnapshot(window).money
            else repository.weekSnapshot(window).money
    }
}
