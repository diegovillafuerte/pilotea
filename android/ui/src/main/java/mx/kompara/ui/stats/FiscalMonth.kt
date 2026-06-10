package mx.kompara.ui.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Maps the Fiscal tab's month picker to the date keys the IMSS tracker needs (B-051): the target
 * [YearMonth], the inclusive ISO day-range to query daily aggregates, and the inclusive ISO
 * week-start (Monday) range that covers every week that could straddle the month — so the
 * [mx.kompara.metrics.imss.ImssCalculator] can pro-rate a straddling week.
 *
 * Pure aside from the injected [zone] (device-local, so month boundaries match what the rollup wrote
 * and the driver sees). Kept here so the month-selection logic is unit-testable without Room.
 */
class FiscalMonth(private val zone: ZoneId) {

    /** Today's local date for [epochMs]. */
    fun today(epochMs: Long): LocalDate = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /** The current calendar month for [epochMs]. */
    fun currentMonth(epochMs: Long): YearMonth = YearMonth.from(today(epochMs))

    /**
     * The month [monthsAgo] before the current month (0 = current, 1 = last month, …). Used by the
     * month picker, which offers the current month plus a fixed number of past months.
     */
    fun monthAt(epochMs: Long, monthsAgo: Int): YearMonth = currentMonth(epochMs).minusMonths(monthsAgo.toLong())

    /** First ISO day (yyyy-MM-dd) of [month]. */
    fun monthStartDay(month: YearMonth): String = month.atDay(1).format(ISO_DATE)

    /** Last ISO day (yyyy-MM-dd) of [month]. */
    fun monthEndDay(month: YearMonth): String = month.atEndOfMonth().format(ISO_DATE)

    /**
     * The earliest week-start (Monday) that could carry days in [month]: the Monday on/just before the
     * 1st. A week-start query from here through the last day of the month captures every week whose
     * 7-day span overlaps the month (the last possible overlapping week starts on or before the 31st).
     */
    fun weekRangeStart(month: YearMonth): String =
        month.atDay(1)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .format(ISO_DATE)

    /** The latest week-start to include: the last day of the month (a Monday on the 31st still overlaps). */
    fun weekRangeEnd(month: YearMonth): String = monthEndDay(month)

    /** A capitalized es-MX month label, e.g. "Junio 2026". Falls back to the ISO form if needed. */
    fun label(month: YearMonth): String =
        runCatching {
            month.atDay(1).format(MONTH_LABEL).replaceFirstChar { it.titlecase(MX) }
        }.getOrDefault(month.toString())

    private companion object {
        val MX: Locale = Locale.forLanguageTag("es-MX")
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", MX)
    }
}
