package mx.kompara.ui.stats

import mx.kompara.data.db.entity.WeeklyAggregateEntity

/** History choices retain empty selected weeks. Raw sources must never be collapsed by preference. */
object CompareState {
    fun availableWeeks(allWeekly: List<WeeklyAggregateEntity>): List<String> =
        allWeekly.map { it.weekStart }.distinct().sortedDescending()
    fun rowsForWeek(allWeekly: List<WeeklyAggregateEntity>, weekStart: String): List<WeeklyAggregateEntity> =
        allWeekly.filter { it.weekStart == weekStart }
}
