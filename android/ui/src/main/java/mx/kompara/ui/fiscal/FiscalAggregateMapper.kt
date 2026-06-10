package mx.kompara.ui.fiscal

import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.metrics.fiscal.FiscalMonthInput
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Pure glue between the Room rollup rows and the [mx.kompara.metrics.fiscal.FiscalCalculator]'s plain
 * [FiscalMonthInput]s (B-052). Mirrors B-051's [mx.kompara.ui.stats.ImssTracker]/ImssCalculator
 * month-net rule so the fiscal summary and the IMSS tracker count the same pesos:
 *
 *  - **Daily rows win.** Each daily row in-range becomes one [FiscalMonthInput] (gross + net) bucketed
 *    on its own day, so a week straddling a month boundary splits cleanly by day.
 *  - **Weekly rows pro-rate the uncovered days.** A weekly row contributes only for the days in its
 *    7-day span that fall in the target month AND aren't already covered by a daily row, pro-rated by
 *    that fraction — the same documented approximation B-051 uses for imported weekly summaries. The
 *    pro-rated week is emitted as a single synthetic input dated on the month's first in-week day.
 *
 * Kept Android-free (Room entities are plain data) so the mapping is unit-tested without a database.
 */
object FiscalAggregateMapper {

    /**
     * Map [daily] + [weekly] rollup rows into per-platform [FiscalMonthInput]s for [month]. Only rows
     * for the target month survive (daily filtered by day; weekly pro-rated into the month).
     */
    fun toInputs(
        month: YearMonth,
        daily: List<DailyAggregateEntity>,
        weekly: List<WeeklyAggregateEntity>,
    ): List<FiscalMonthInput> {
        val inputs = ArrayList<FiscalMonthInput>()

        // Days any daily row covers (across any month) — used to suppress double-counting from weekly.
        val coveredDays: Set<LocalDate> = daily.mapNotNull { parseDate(it.day) }.toSet()

        // Daily rows in the target month.
        daily.forEach { row ->
            val d = parseDate(row.day) ?: return@forEach
            if (YearMonth.from(d) == month) {
                inputs.add(FiscalMonthInput(row.platform, row.day, row.grossEarningsMxn, row.netEarningsMxn))
            }
        }

        // Weekly rows: pro-rate the in-month, daily-uncovered share.
        weekly.forEach { week ->
            val weekStart = parseDate(week.weekStart) ?: return@forEach
            val daysInWeek = (0 until DAYS_PER_WEEK).map { weekStart.plusDays(it.toLong()) }
            val inMonthUncovered = daysInWeek.filter { YearMonth.from(it) == month && it !in coveredDays }
            if (inMonthUncovered.isEmpty()) return@forEach
            val fraction = inMonthUncovered.size.toDouble() / DAYS_PER_WEEK
            // Date the synthetic input on the earliest in-month uncovered day so it lands in the month.
            val day = inMonthUncovered.min().format(ISO_DATE)
            inputs.add(
                FiscalMonthInput(
                    platform = week.platform,
                    day = day,
                    grossMxn = week.grossEarningsMxn * fraction,
                    netMxn = week.netEarningsMxn * fraction,
                ),
            )
        }

        return inputs
    }

    /**
     * The widened week-start range that covers every week which could straddle [month] — the Monday on
     * or before the 1st, through the last day of the month. Mirrors B-051's FiscalMonth range so the
     * YTD query (Jan→month) and the month query share semantics. Returned as ISO yyyy-MM-dd pair.
     */
    fun weekRange(month: YearMonth): Pair<String, String> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return start.format(ISO_DATE) to month.atEndOfMonth().format(ISO_DATE)
    }

    private fun parseDate(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso, ISO_DATE) }.getOrNull()

    private const val DAYS_PER_WEEK = 7
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
