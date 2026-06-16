package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity

/**
 * The pure week/row helpers behind [CompararViewModel] (S-024). Kept tiny and Room-free so week
 * selection and the IMPORTED-beats-CAPTURED preference are unit-tested without a database. The
 * comparison itself is assembled by [ComparisonBuilder] from these rows plus benchmark/percentile data.
 */
object CompareState {

    /** The set of ISO Mondays (newest first) that have any data — what the week picker offers. */
    fun availableWeeks(allWeekly: List<WeeklyAggregateEntity>): List<String> =
        allWeekly.map { it.weekStart }.distinct().sortedDescending()

    /** The week's rows, one per platform, preferring IMPORTED (realized) over CAPTURED (estimate). */
    fun rowsForWeek(
        allWeekly: List<WeeklyAggregateEntity>,
        weekStart: String,
    ): List<WeeklyAggregateEntity> =
        allWeekly
            .filter { it.weekStart == weekStart }
            .groupBy { it.platform }
            .map { (_, rows) ->
                rows.firstOrNull { it.source == AggregateSource.IMPORTED.name }
                    ?: rows.first { it.source == AggregateSource.CAPTURED.name }
            }
}
