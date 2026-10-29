package mx.kompara.ui.fiscal

import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.metrics.fiscal.FiscalMonthInput
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Compatibility adapter for dated rows. Undated weeks never become synthetic daily income. */
object FiscalAggregateMapper {

    /**
     * Map [daily] + [weekly] rollup rows into per-platform [FiscalMonthInput]s for [month]. Only dated rows
     * for the target month survive; weekly payments are handled by the explicit month scope model.
     */
    @Suppress("UNUSED_PARAMETER")
    fun toInputs(
        month: YearMonth,
        daily: List<DailyAggregateEntity>,
        weekly: List<WeeklyAggregateEntity>,
    ): List<FiscalMonthInput> {
        val inputs = ArrayList<FiscalMonthInput>()

        // Daily rows in the target month.
        daily.forEach { row ->
            val d = parseDate(row.day) ?: return@forEach
            if (YearMonth.from(d) == month) {
                inputs.add(FiscalMonthInput(row.platform, row.day, row.grossEarningsMxn, row.netEarningsMxn))
            }
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

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
