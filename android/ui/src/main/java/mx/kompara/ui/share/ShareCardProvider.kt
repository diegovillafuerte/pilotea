package mx.kompara.ui.share

import kotlinx.coroutines.flow.first
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.DailyAggregateEntity
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
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
     * The Wrapped-style "Tu mes" card for the month [monthStartIso] opens (defaults to the current
     * month). Mirrors [currentWeekCard] but sources the month's CAPTURED *daily* rows in the month's
     * calendar range — daily buckets give both the folded monthly totals and the per-weekday breakdown
     * for the "Mejor día" brag cell, and they don't straddle month boundaries the way Monday-anchored
     * weeks do. The percentile flex ("Tu lugar") and best-net platform ("Mejor app") reuse the same
     * benchmark/selection logic as the weekly card; the streak comes from the weekly history.
     */
    suspend fun currentMonthCard(
        monthStartIso: String = monthStartIso(clock.nowMs()),
        hideAmountsOverride: Boolean? = null,
    ): ShareCardData {
        val settings = settingsRepository.settings.first()
        val hideAmounts = hideAmountsOverride ?: settings.shareHideAmounts

        val monthStart = LocalDate.parse(monthStartIso, ISO_DATE)
        val monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth())
        val capturedThisMonth = aggregateDao
            .observeDailyInRange(monthStart.format(ISO_DATE), monthEnd.format(ISO_DATE))
            .first()
            .filter { it.source == AggregateSource.CAPTURED.name }

        // Card always summarises "Todas" — the driver shares their whole month, not one platform.
        // Use the monthly fold so online hours sum across days (per-day max, summed) rather than
        // collapsing to a single day's max, which would inflate $/hora and viajes/hora.
        val period = PeriodStats.fromDailyMonth(capturedThisMonth, platform = null)

        // Streak is a weekly concept (B-039), unchanged from the weekly card.
        val streak = StreakCalculator().streak(
            aggregateDao.observeWeekly().first().map { it.weekStart }.distinct(),
        )

        val bestApp = bestNetPlatform(capturedThisMonth)
        val bestDay = bestNetWeekday(capturedThisMonth)
        val percentiles = bestPlatformDailyPercentiles(capturedThisMonth, settings.city, settings.enabledPlatforms)

        return ShareCardComposer.compose(
            stats = period,
            periodKind = SharePeriodKind.MONTH,
            periodLabel = Formatters.formatMonthLabel(monthStartIso),
            streakWeeks = streak,
            city = settings.city,
            percentiles = percentiles,
            hideAmounts = hideAmounts,
            bestApp = bestApp,
            bestDay = bestDay,
        )
    }

    /** ISO date (yyyy-MM-dd) of the first day of the month [epochMs] falls in, device-local zone. */
    private fun monthStartIso(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(weekClock.zone)
            .toLocalDate()
            .withDayOfMonth(1)
            .format(ISO_DATE)

    /**
     * The platform the driver earned the most net on this month, from the month's *captured* data
     * (the "Mejor app" brag cell reflects real top-net, not what's toggled on in settings). Excludes
     * only unparseable platforms and [Platform.UNKNOWN]; null when none qualify so the cell omits.
     */
    private fun bestNetPlatform(capturedThisMonth: List<DailyAggregateEntity>): Platform? =
        capturedThisMonth
            .mapNotNull { row -> runCatching { Platform.valueOf(row.platform) }.getOrNull()?.let { it to row } }
            .filter { (platform, _) -> platform != Platform.UNKNOWN }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, rows) -> rows.sumOf { it.netEarningsMxn } }
            .maxByOrNull { it.value }
            ?.key

    /**
     * The platform to benchmark for "Tu lugar": the top-net platform *among those the driver enabled*,
     * since percentile eligibility is gated to enabled platforms. Null when none qualify.
     */
    private fun bestPlatformForPercentiles(
        capturedThisMonth: List<DailyAggregateEntity>,
        enabledPlatforms: Set<Platform>,
    ): Platform? =
        capturedThisMonth
            .filter { row -> runCatching { Platform.valueOf(row.platform) }.getOrNull() in enabledPlatforms }
            .groupBy { it.platform }
            .mapValues { (_, rows) -> rows.sumOf { it.netEarningsMxn } }
            .maxByOrNull { it.value }
            ?.key
            ?.let { runCatching { Platform.valueOf(it) }.getOrNull() }
            ?.takeUnless { it == Platform.UNKNOWN }

    /**
     * The localised weekday with the highest summed net across the month ("Sábado"), or null when
     * there are no parseable captured days (so the "Mejor día" cell gracefully omits). The top weekday
     * is returned even when its net is non-positive — a month with real daily rows still has a "best"
     * day; only genuinely no data omits the cell.
     */
    private fun bestNetWeekday(capturedThisMonth: List<DailyAggregateEntity>): String? {
        val parseable = capturedThisMonth
            .filter { runCatching { LocalDate.parse(it.day, ISO_DATE) }.getOrNull() != null }
        if (parseable.isEmpty()) return null
        val byWeekday = parseable
            .groupBy { LocalDate.parse(it.day, ISO_DATE).dayOfWeek }
            .mapValues { (_, rows) -> rows.sumOf { it.netEarningsMxn } }
        val best = byWeekday.maxByOrNull { it.value } ?: return null
        // Any date with that day-of-week formats to the weekday name; pick the first matching captured day.
        val sampleDay = parseable.first { LocalDate.parse(it.day, ISO_DATE).dayOfWeek == best.key }.day
        return Formatters.formatWeekdayLabel(sampleDay)
    }

    /**
     * Daily twin of [bestPlatformPercentiles]: benchmark the single platform the driver earned the
     * most net on this month (a concrete enabled platform), folding the month's daily rows into the
     * platform's [PeriodStats]. Returns empty when benchmarks aren't cached, so the card omits "Tu lugar".
     */
    private suspend fun bestPlatformDailyPercentiles(
        capturedThisMonth: List<DailyAggregateEntity>,
        city: mx.kompara.data.model.City,
        enabledPlatforms: Set<Platform>,
    ): List<mx.kompara.metrics.percentile.PercentileResult> {
        val platform = bestPlatformForPercentiles(capturedThisMonth, enabledPlatforms) ?: return emptyList()
        val platformPeriod = PeriodStats.fromDailyMonth(capturedThisMonth, platform)
        return percentileRepository
            .observe(city, platform.name.lowercase(), MetricPercentiles.metricValues(platformPeriod))
            .first()
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

    private companion object {
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
