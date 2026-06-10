package mx.kompara.data.rollup

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Consecutive-weeks streak (B-039), ported from the web MVP's `streak_weeks` concept.
 *
 * Pure and deterministic. A "streak" is the count of consecutive ISO weeks — counting **back from
 * the most recent week that has data** — in which the driver had at least one week-row with data
 * (captured OR imported; source is irrelevant to a streak). The chain breaks at the first missing
 * Monday: a week with no data at all ends the streak.
 *
 * Why anchor on the latest *data* week rather than "this" week: a driver who took the current week
 * off shouldn't see their streak read as 0 the instant Monday rolls over — the streak describes their
 * run of active weeks, and a fresh rollup of the current week extends it. (If the product later wants
 * "streak as of today, broken by an idle current week", pass `today`'s Monday in as an extra present
 * week — kept out of here so the calculator stays a pure function of the data.)
 */
class StreakCalculator {

    /**
     * @param weekStarts ISO Monday dates (yyyy-MM-dd) of every week that has data, any order, dupes
     *   allowed (multiple platforms per week collapse to one). Empty ⇒ streak 0.
     * @return the number of consecutive weeks ending at the most recent present week.
     */
    fun streak(weekStarts: Collection<String>): Int {
        val mondays = weekStarts
            .mapNotNull { runCatching { LocalDate.parse(it, ISO_DATE) }.getOrNull() }
            .toSortedSet()
        if (mondays.isEmpty()) return 0

        var streak = 0
        var cursor = mondays.last()
        while (cursor in mondays) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    private companion object {
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
