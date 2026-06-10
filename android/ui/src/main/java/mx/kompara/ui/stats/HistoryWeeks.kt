package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity

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

/**
 * Folds the flat weekly-aggregate table into the history list (B-040 req 3): one [HistoryWeek] per
 * Monday, newest first, summed across platforms, with a source badge.
 *
 * Per the B-039 reconciliation contract the same `(platform, weekStart)` can carry both a CAPTURED
 * and an IMPORTED row. When both exist for a week we prefer the IMPORTED (realized) numbers for the
 * summary and badge the week IMPORTADO — the captured estimate is provisional and the realized
 * figure wins for display.
 */
object HistoryWeeks {

    fun build(rows: List<WeeklyAggregateEntity>): List<HistoryWeek> {
        val byWeek = rows.groupBy { it.weekStart }
        return byWeek.entries
            .map { (weekStart, weekRows) ->
                val imported = weekRows.filter { it.source == AggregateSource.IMPORTED.name }
                val captured = weekRows.filter { it.source == AggregateSource.CAPTURED.name }
                val preferred = if (imported.isNotEmpty()) imported else captured
                val badge = if (imported.isNotEmpty()) WeekSourceBadge.IMPORTADO else WeekSourceBadge.CAPTURADO
                HistoryWeek(
                    weekStart = weekStart,
                    source = badge,
                    period = PeriodStats.fromWeekly(preferred, platform = null),
                )
            }
            .sortedByDescending { it.weekStart }
    }

    /**
     * Split the (newest-first) [weeks] into the free-tier visible window and the premium-locked tail
     * (B-050). The free tier sees the [FREE_WEEKS] most-recent weeks (current + previous week); older
     * rows are gated. When [locked] is false (premium / debug / kill-switch promo) nothing is truncated
     * — every week is visible and [HistoryPartition.locked] is empty.
     *
     * Pure + positional (no clock): "current + previous" is just the two newest weeks that have data, so
     * a driver who only drove every other week still sees their two most recent weeks free.
     */
    fun partition(weeks: List<HistoryWeek>, locked: Boolean): HistoryPartition {
        if (!locked) return HistoryPartition(visible = weeks, locked = emptyList())
        val sorted = weeks.sortedByDescending { it.weekStart }
        return HistoryPartition(
            visible = sorted.take(FREE_WEEKS),
            locked = sorted.drop(FREE_WEEKS),
        )
    }

    /** Free-tier history window: the current week + the previous week. */
    const val FREE_WEEKS = 2
}

/**
 * The history list split into the free-visible weeks and the premium-locked tail (B-050).
 *
 * @property visible weeks rendered normally (the free window, or all weeks when unlocked).
 * @property locked older weeks shown blurred behind the gate (empty when unlocked).
 */
data class HistoryPartition(
    val visible: List<HistoryWeek>,
    val locked: List<HistoryWeek>,
) {
    /** True when there is a locked tail to tease behind the gate. */
    val hasLocked: Boolean get() = locked.isNotEmpty()
}
