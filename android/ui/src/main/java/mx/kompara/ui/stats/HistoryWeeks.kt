package mx.kompara.ui.stats


/** Where a week's numbers came from, as the history badge reads it. */
enum class WeekSourceBadge {
    /** Computed on device from the capture stream. */
    CAPTURADO,

    /** Derived from an uploaded weekly summary (B-045). */
    IMPORTADO,
}

/** One row in the history list: a week, its summed metrics, and its source badge. */
data class HistoryWeek(
    val weekStart: String,
    val source: WeekSourceBadge,
    val period: PeriodStats,
)

/** Legacy list adapter: selection is per app, captured stays selected until explicit reconciliation. */
object HistoryWeeks {
    const val FREE_WEEKS = 4
    fun isFree(week: java.time.LocalDate, today: java.time.LocalDate): Boolean =
        mx.kompara.billing.FreeDetailWindow.week(week, today)

    /** Only breakdowns are gated. The picker and every own total remain visible. */
    fun partition(weeks: List<HistoryWeek>, locked: Boolean, today: java.time.LocalDate): HistoryPartition {
        val sorted = weeks.sortedByDescending { it.weekStart }
        return HistoryPartition(sorted, if (locked) sorted.filter { !isFree(java.time.LocalDate.parse(it.weekStart), today) } else emptyList())
    }
}

data class HistoryPartition(val visible: List<HistoryWeek>, val locked: List<HistoryWeek>) {
    val hasLocked: Boolean get() = locked.isNotEmpty()
}
