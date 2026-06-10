package mx.kompara.ui.stats

import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.metrics.imss.CoverageStatus
import mx.kompara.metrics.imss.DailyMonthInput
import mx.kompara.metrics.imss.ImssCalculator
import mx.kompara.metrics.imss.PlatformImssStatus
import mx.kompara.metrics.imss.WeeklyMonthInput
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure glue between the rollup rows + fiscal config and the Fiscal tab's render state (B-051). Maps
 * Room entities into the [ImssCalculator]'s plain inputs, runs the per-platform calculation, and
 * keeps only the platforms the driver actually has enabled, so the screen has nothing to compute.
 *
 * Kept Android-free so the whole month→sections mapping is unit-tested without Room or Compose.
 */
object ImssTracker {

    private val calc = ImssCalculator()

    /**
     * Build the per-platform IMSS sections for [month] from [daily]/[weekly] rollup rows against
     * [thresholdMxn], anchored at [today]. Only platforms in [enabledPlatforms] are shown, and only
     * those that have any data this month (so an enabled-but-idle platform doesn't clutter the tab).
     * Sections are ordered by the enabled-platform declaration order for a stable UI.
     */
    fun sectionsFor(
        month: YearMonth,
        thresholdMxn: Double,
        daily: List<DailyAggregateEntity>,
        weekly: List<WeeklyAggregateEntity>,
        today: LocalDate,
        enabledPlatforms: Set<Platform>,
    ): List<PlatformImssStatus> {
        val dailyInputs = daily.map { DailyMonthInput(it.platform, it.day, it.netEarningsMxn) }
        val weeklyInputs = weekly.map { WeeklyMonthInput(it.platform, it.weekStart, it.netEarningsMxn) }

        val byPlatform = calc.statusesFor(month, thresholdMxn, dailyInputs, weeklyInputs, today)
            .associateBy { it.platform }

        // Show enabled platforms that have any data this month, in enabled-order.
        return enabledPlatforms
            .map { it.name }
            .mapNotNull { name -> byPlatform[name] }
            .filter { it.netSoFarMxn > 0.0 || it.daysRemaining > 0 }
    }

    /** Maps a [CoverageStatus] to the chip-label resource the screen renders. */
    fun statusLabelKey(status: CoverageStatus): CoverageLabel = when (status) {
        CoverageStatus.COVERED -> CoverageLabel.COVERED
        CoverageStatus.ON_TRACK -> CoverageLabel.ON_TRACK
        CoverageStatus.UNLIKELY -> CoverageLabel.UNLIKELY
    }
}

/** UI-facing coverage label (decoupled from the metrics enum so strings live in :ui). */
enum class CoverageLabel { COVERED, ON_TRACK, UNLIKELY }
