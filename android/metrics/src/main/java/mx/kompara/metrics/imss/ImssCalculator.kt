package mx.kompara.metrics.imss

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Per-platform IMSS-coverage tracker for one calendar month (B-051).
 *
 * **Why this exists.** Mexico's 2025 platform-work reform ties IMSS social-security coverage to a
 * driver earning **≥ 1 monthly minimum wage (the threshold) per platform per calendar month**. The
 * rule is **per platform**, not on a multi-platform total: a driver at $7,000 on Uber and $7,000 on
 * DiDi is *not* covered on either, even though their combined income clears the bar. So this computes
 * one [PlatformImssStatus] per platform, never a blended total.
 *
 * **Pure and deterministic** — no Android, no IO, no static clock. "Now" and the target month are
 * passed in, so the whole matrix (under/over, projection, days-remaining across month lengths and
 * leap years, straddling-week pro-rate) is unit-testable on the JVM.
 *
 * ## Net income for the month
 * Net is summed from the [DailyMonthInput]s whose day falls in the target month, **preferring daily
 * rows** (B-039 daily aggregates bucket by local day, so a week straddling a month boundary splits
 * cleanly). For any ISO week with **no daily coverage** in the month, the calculator falls back to
 * **pro-rating that week's net by the fraction of its 7 days that land inside the month**
 * ([WeeklyMonthInput]) — a documented approximation used only when day-level data is unavailable
 * (e.g. an imported weekly summary, B-045). Daily always wins where present, so the pro-rate never
 * double-counts a day a daily row already covered.
 *
 * ## Pacing & projection
 *  - **Pacing** ("te faltan $X y quedan N días"): the remaining-to-threshold amount and the number of
 *    days left in the month *including today* (so on the last day, N = 1).
 *  - **Projection**: month-end net at the current run-rate, where the run-rate is the net earned so
 *    far divided by the number of **elapsed days that had any activity** is intentionally NOT used —
 *    a driver who rests half the month would look like they'll miss. Instead the run-rate is
 *    `netSoFar / elapsedCalendarDays` (today inclusive), projected over the full month length. This
 *    matches the honest "if you keep going at this average pace" reading the UI promises.
 *
 * ## Coverage status
 *  - [CoverageStatus.COVERED] — net already ≥ threshold this month (locked in).
 *  - [CoverageStatus.ON_TRACK] — not yet over, but the projection clears the threshold.
 *  - [CoverageStatus.UNLIKELY] — projection falls short.
 * For a **past** month (the target month is entirely before "now"), there is no projecting: the
 * status is COVERED iff net ≥ threshold, else UNLIKELY (it can no longer be reached).
 */
class ImssCalculator {

    /**
     * Compute the [PlatformImssStatus] for every platform that appears in [daily] or [weekly] for the
     * [month], against [thresholdMxn].
     *
     * @param month the target calendar month.
     * @param today the device-local "today" (drives days-remaining and the elapsed-days run-rate). For
     *   a past/future month this still anchors whether the month is current; only a current month uses
     *   it for pacing/projection.
     */
    fun statusesFor(
        month: YearMonth,
        thresholdMxn: Double,
        daily: List<DailyMonthInput>,
        weekly: List<WeeklyMonthInput>,
        today: LocalDate,
    ): List<PlatformImssStatus> {
        val platforms = (daily.map { it.platform } + weekly.map { it.platform }).distinct().sorted()
        return platforms.map { platform ->
            statusForPlatform(
                platform = platform,
                month = month,
                thresholdMxn = thresholdMxn,
                daily = daily.filter { it.platform == platform },
                weekly = weekly.filter { it.platform == platform },
                today = today,
            )
        }
    }

    /** Single-platform status (also the unit of the per-platform UI sections). */
    fun statusForPlatform(
        platform: String,
        month: YearMonth,
        thresholdMxn: Double,
        daily: List<DailyMonthInput>,
        weekly: List<WeeklyMonthInput>,
        today: LocalDate,
    ): PlatformImssStatus {
        val netSoFar = monthNet(month, daily, weekly)
        val remaining = (thresholdMxn - netSoFar).coerceAtLeast(0.0)
        val progress = if (thresholdMxn <= 0.0) 1.0 else (netSoFar / thresholdMxn).coerceIn(0.0, 1.0)

        val monthLength = month.lengthOfMonth()
        val phase = monthPhase(month, today)
        val daysRemaining = when (phase) {
            MonthPhase.CURRENT -> (monthLength - today.dayOfMonth + 1).coerceAtLeast(0)
            MonthPhase.FUTURE -> monthLength
            MonthPhase.PAST -> 0
        }

        val projectedMonthEnd: Double = when (phase) {
            MonthPhase.PAST -> netSoFar // the month is closed; "projection" is just the realized net.
            MonthPhase.FUTURE -> 0.0 // nothing earned yet; no basis to project.
            MonthPhase.CURRENT -> {
                val elapsedDays = today.dayOfMonth // today inclusive
                if (elapsedDays <= 0 || netSoFar <= 0.0) {
                    netSoFar
                } else {
                    val runRatePerDay = netSoFar / elapsedDays
                    runRatePerDay * monthLength
                }
            }
        }

        val covered = netSoFar >= thresholdMxn
        val status = when {
            covered -> CoverageStatus.COVERED
            phase == MonthPhase.PAST -> CoverageStatus.UNLIKELY
            projectedMonthEnd >= thresholdMxn -> CoverageStatus.ON_TRACK
            else -> CoverageStatus.UNLIKELY
        }

        return PlatformImssStatus(
            platform = platform,
            netSoFarMxn = netSoFar,
            thresholdMxn = thresholdMxn,
            remainingMxn = remaining,
            progress = progress,
            daysRemaining = daysRemaining,
            projectedMonthEndMxn = projectedMonthEnd,
            status = status,
            phase = phase,
        )
    }

    /**
     * Net income for [month]: sum daily rows in-month, then add the in-month pro-rated share of any
     * weekly row whose ISO week is NOT already represented by a daily row. Daily wins where present.
     */
    private fun monthNet(
        month: YearMonth,
        daily: List<DailyMonthInput>,
        weekly: List<WeeklyMonthInput>,
    ): Double {
        // Days (in any month) that daily rows cover — used to suppress double-counting from weekly.
        val coveredDays: Set<LocalDate> = daily.mapNotNull { parseDate(it.day) }.toSet()

        val dailyNet = daily
            .filter { input -> parseDate(input.day)?.let { YearMonth.from(it) == month } == true }
            .sumOf { it.netEarningsMxn }

        val weeklyNet = weekly.sumOf { week ->
            val weekStart = parseDate(week.weekStart) ?: return@sumOf 0.0
            val daysInWeek = (0 until DAYS_PER_WEEK).map { weekStart.plusDays(it.toLong()) }
            // Only the week's days that fall in the target month AND aren't already covered by a daily
            // row count toward the pro-rate.
            val daysInMonthUncovered = daysInWeek.count { day ->
                YearMonth.from(day) == month && day !in coveredDays
            }
            if (daysInMonthUncovered == 0) 0.0 else week.netEarningsMxn * (daysInMonthUncovered.toDouble() / DAYS_PER_WEEK)
        }

        return dailyNet + weeklyNet
    }

    private fun monthPhase(month: YearMonth, today: LocalDate): MonthPhase {
        val currentMonth = YearMonth.from(today)
        return when {
            month == currentMonth -> MonthPhase.CURRENT
            month.isBefore(currentMonth) -> MonthPhase.PAST
            else -> MonthPhase.FUTURE
        }
    }

    private fun parseDate(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso, ISO_DATE) }.getOrNull()

    private companion object {
        const val DAYS_PER_WEEK = 7
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}

/** A daily-aggregate net for one platform/day (yyyy-MM-dd, local zone). The preferred input. */
data class DailyMonthInput(
    val platform: String,
    val day: String,
    val netEarningsMxn: Double,
)

/**
 * A weekly-aggregate net for one platform/week (weekStart = ISO Monday, yyyy-MM-dd). Used only to
 * pro-rate the in-month share of a week that has no daily coverage (documented approximation).
 */
data class WeeklyMonthInput(
    val platform: String,
    val weekStart: String,
    val netEarningsMxn: Double,
)

/** Where the target month sits relative to "today". */
enum class MonthPhase { PAST, CURRENT, FUTURE }

/** Coverage outlook for one platform in one month. */
enum class CoverageStatus {
    /** Net already ≥ threshold this month — coverage locked in ("cubierto este mes"). */
    COVERED,

    /** Not yet over, but projected to clear the threshold ("en camino"). */
    ON_TRACK,

    /** Projected to fall short, or a past month that ended under ("difícil este mes"). */
    UNLIKELY,
}

/**
 * The full IMSS picture for one platform in one month — the unit of the Fiscal-tab per-platform
 * section and the month-end notification decision.
 */
data class PlatformImssStatus(
    val platform: String,
    val netSoFarMxn: Double,
    val thresholdMxn: Double,
    /** Pesos still needed to reach the threshold; 0 once met. */
    val remainingMxn: Double,
    /** 0f..1f fill toward the threshold. */
    val progress: Double,
    /** Calendar days left in the month including today (0 for a past month). */
    val daysRemaining: Int,
    /** Net the run-rate projects for month-end (= realized net for a past month). */
    val projectedMonthEndMxn: Double,
    val status: CoverageStatus,
    val phase: MonthPhase,
) {
    /** Whether coverage is already secured this month (net ≥ threshold). */
    val covered: Boolean get() = status == CoverageStatus.COVERED

    /** Progress as a whole-number percent (0..100), handy for content descriptions. */
    val progressPercent: Int get() = (progress * 100).roundToInt()
}
