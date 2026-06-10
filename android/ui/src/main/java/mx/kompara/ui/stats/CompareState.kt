package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.metrics.compare.CompareCalculator
import mx.kompara.metrics.compare.CompareResult
import mx.kompara.metrics.compare.PlatformMetrics

/**
 * The pure core of [CompararViewModel] (B-047): given the full weekly-aggregate table and a chosen
 * week, decide the Comparar tab's mode — empty (0 platforms), single (1 platform + teaser), or a real
 * comparison (2+). Extracted like [InicioStats]/[HistoryWeeks] so the platform-pair selection and the
 * 0/1/2/3-platform branching are unit-tested without Room or the gatekeeper.
 *
 * Week selection mirrors [WeekSummaryViewModel]: a week's rows prefer the IMPORTED (realized) figures
 * when present, else the CAPTURED estimate. Platforms come from [PlatformSelection.platformsWithData]
 * so the order (UBER, DIDI, INDRIVE) and "has data" rule match the rest of the app.
 *
 * When exactly two platforms have data the pair is automatic. With three or more, the screen shows
 * platform chips and the caller passes the selected pair via [selectedPair]; absent a valid selection
 * the first two (declaration order) are compared so there's always something on screen.
 */
object CompareState {

    /**
     * Build the comparison state for [weekStart].
     *
     * @param allWeekly the whole weekly table (every week, both sources).
     * @param weekStart the ISO Monday to compare.
     * @param selectedPair the driver's chosen platform pair when 3+ platforms have data; ignored with
     *   0–2 platforms (the pair is then forced/automatic). An invalid pair falls back to the first two.
     */
    fun forWeek(
        allWeekly: List<WeeklyAggregateEntity>,
        weekStart: String,
        selectedPair: Pair<Platform, Platform>? = null,
    ): CompareUiData {
        val weekRows = rowsForWeek(allWeekly, weekStart)
        val platforms = PlatformSelection.platformsWithData(weekRows)

        return when (platforms.size) {
            0 -> CompareUiData(weekStart = weekStart, mode = CompareMode.Empty)
            1 -> CompareUiData(
                weekStart = weekStart,
                mode = CompareMode.SinglePlatform(platforms.first()),
            )
            else -> {
                val (a, b) = resolvePair(platforms, selectedPair)
                val result = CompareCalculator.compare(
                    metricsFor(weekRows, a),
                    metricsFor(weekRows, b),
                )
                CompareUiData(
                    weekStart = weekStart,
                    mode = CompareMode.Comparison(
                        platformA = a,
                        platformB = b,
                        platforms = platforms,
                        result = result,
                    ),
                )
            }
        }
    }

    /** The set of ISO Mondays (newest first) that have any data — what the week picker offers. */
    fun availableWeeks(allWeekly: List<WeeklyAggregateEntity>): List<String> =
        allWeekly.map { it.weekStart }.distinct().sortedDescending()

    /**
     * Pick the pair to compare when 3+ platforms have data: the driver's [selected] pair if both
     * sides still have data and differ, otherwise the first two platforms (declaration order).
     */
    private fun resolvePair(
        platforms: List<Platform>,
        selected: Pair<Platform, Platform>?,
    ): Pair<Platform, Platform> {
        if (selected != null &&
            selected.first != selected.second &&
            selected.first in platforms &&
            selected.second in platforms
        ) {
            return selected
        }
        return platforms[0] to platforms[1]
    }

    /** The week's rows, preferring IMPORTED over CAPTURED for the same platform (realized wins). */
    private fun rowsForWeek(
        allWeekly: List<WeeklyAggregateEntity>,
        weekStart: String,
    ): List<WeeklyAggregateEntity> {
        val forWeek = allWeekly.filter { it.weekStart == weekStart }
        return forWeek
            .groupBy { it.platform }
            .map { (_, rows) ->
                rows.firstOrNull { it.source == AggregateSource.IMPORTED.name }
                    ?: rows.first { it.source == AggregateSource.CAPTURED.name }
            }
    }

    /** Fold one platform's row for the week into [PlatformMetrics] for the calculator. */
    private fun metricsFor(weekRows: List<WeeklyAggregateEntity>, platform: Platform): PlatformMetrics {
        val row = weekRows.first { it.platform == platform.name }
        return PlatformMetrics.of(
            platform = platform.name,
            earningsPerHour = row.earningsPerHour,
            earningsPerKm = row.earningsPerKm,
            earningsPerTrip = row.earningsPerTrip,
            netEarnings = row.netEarningsMxn,
            totalTrips = row.totalTrips,
            acceptanceRate = row.acceptanceRate,
            // commission is null on the captured path; an imported week can supply it later.
            commissionPct = null,
        )
    }
}

/** The Comparar tab's render data for one week: which mode, and the comparison payload when present. */
data class CompareUiData(
    val weekStart: String,
    val mode: CompareMode,
)

/** The three Comparar states (B-047 req 3). */
sealed interface CompareMode {
    /** No platform captured data this week → empty state with a CTA to the Lector. */
    data object Empty : CompareMode

    /** Exactly one platform → "agrega otra para comparar" + a teaser (a static "ejemplo"). */
    data class SinglePlatform(val platform: Platform) : CompareMode

    /**
     * Two or more platforms → a real comparison. [platforms] is every platform with data (≥2) so the
     * screen can render selection chips when there are more than two; [platformA]/[platformB] are the
     * pair currently being compared.
     */
    data class Comparison(
        val platformA: Platform,
        val platformB: Platform,
        val platforms: List<Platform>,
        val result: CompareResult,
    ) : CompareMode {
        /** True when the driver has 3+ platforms, so the screen offers a platform-pair chooser. */
        val showsChips: Boolean get() = platforms.size > 2
    }
}
