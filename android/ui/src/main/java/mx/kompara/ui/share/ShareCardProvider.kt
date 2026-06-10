package mx.kompara.ui.share

import kotlinx.coroutines.flow.first
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.rollup.StreakCalculator
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.aggregate.PercentileRepository
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.stats.MetricPercentiles
import mx.kompara.ui.stats.PeriodStats
import mx.kompara.ui.stats.WeekClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles a render-ready [ShareCardData] for the current week (B-055): the share-preview screen and
 * the week-close path both ask this for "the card to show right now". It folds the captured weekly
 * aggregates into a [PeriodStats] (reusing the same pure helpers the dashboard uses), computes the
 * streak, pulls the percentile flex for the driver's best-earning platform, and runs it all through
 * the pure [ShareCardComposer].
 *
 * The percentile flex is NOT premium-gated here — on the share card it's the marketing hook and is
 * shown to every tier by design (see [ShareCardComposer.bestFlex]); the in-app dashboard percentiles
 * remain gated elsewhere.
 */
@Singleton
class ShareCardProvider @Inject constructor(
    private val aggregateDao: AggregateDao,
    private val settingsRepository: SettingsRepository,
    private val percentileRepository: PercentileRepository,
    private val weekClock: WeekClock,
    private val clock: AppClock,
) {

    /**
     * The card for the week that [weekStartIso] opens (defaults to the current week), redacting
     * amounts per [hideAmountsOverride] when provided, else the persisted default. Reads a snapshot of
     * each reactive source — the preview screen re-asks on toggle changes.
     */
    suspend fun currentWeekCard(
        weekStartIso: String = weekClock.weekStartIso(clock.nowMs()),
        hideAmountsOverride: Boolean? = null,
    ): ShareCardData {
        val settings = settingsRepository.settings.first()
        val hideAmounts = hideAmountsOverride ?: settings.shareHideAmounts

        val allWeekly = aggregateDao.observeWeekly().first()
        val capturedThisWeek = allWeekly.filter {
            it.weekStart == weekStartIso && it.source == AggregateSource.CAPTURED.name
        }
        // Card always summarises "Todas" — the driver shares their whole week, not one platform.
        val period = PeriodStats.fromWeekly(capturedThisWeek, platform = null)
        val streak = StreakCalculator().streak(allWeekly.map { it.weekStart }.distinct())

        val percentiles = bestPlatformPercentiles(capturedThisWeek, settings.city, settings.enabledPlatforms)

        return ShareCardComposer.compose(
            stats = period,
            periodKind = SharePeriodKind.WEEK,
            periodLabel = Formatters.formatWeekRangeLabel(weekStartIso),
            streakWeeks = streak,
            city = settings.city,
            percentiles = percentiles,
            hideAmounts = hideAmounts,
        )
    }

    /**
     * The percentile standings to feed the flex. We benchmark the single platform the driver earned
     * the most net on this week (a concrete platform is required — "Todas" has no benchmark cell), and
     * only when it's one they've enabled. Returns empty when benchmarks aren't cached yet, in which
     * case the card simply omits the flex.
     */
    private suspend fun bestPlatformPercentiles(
        capturedThisWeek: List<WeeklyAggregateEntity>,
        city: mx.kompara.data.model.City,
        enabledPlatforms: Set<Platform>,
    ): List<mx.kompara.metrics.percentile.PercentileResult> {
        val topRow = capturedThisWeek
            .filter { row -> runCatching { Platform.valueOf(row.platform) }.getOrNull() in enabledPlatforms }
            .maxByOrNull { it.netEarningsMxn }
            ?: return emptyList()
        val platform = runCatching { Platform.valueOf(topRow.platform) }.getOrNull() ?: return emptyList()
        if (platform == Platform.UNKNOWN) return emptyList()

        val platformPeriod = PeriodStats.fromWeekly(capturedThisWeek, platform)
        return percentileRepository
            .observe(city, platform.name.lowercase(), MetricPercentiles.metricValues(platformPeriod))
            .first()
    }
}
