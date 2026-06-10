package mx.kompara.ui.share

import mx.kompara.data.model.City
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.PeriodStats

/**
 * The pure data step of the shareable earnings card (B-055): folds a period's [PeriodStats], the
 * streak, the driver's [City], and any [PercentileResult]s into a render-ready [ShareCardData].
 *
 * Heavily unit-tested and Android-free: every branch (hide-amounts redaction, best-percentile
 * selection including display inversion, streak/period/trip formatting, missing-data variants) is
 * decided here so the [ShareCardRenderer] only paints strings. No premium gate lives here — the
 * percentile flex is marketing and is shown to every tier by design (see [bestFlex]).
 */
object ShareCardComposer {

    /** A favorable percentile must be strictly better than the median to be worth bragging about. */
    private const val FAVORABLE_DISPLAY_THRESHOLD = 50

    /**
     * Build the card for [stats] over the [periodKind] period labelled [periodLabel].
     *
     * @param stats the folded period totals/rates.
     * @param periodKind WEEK or MONTH — drives only the headline copy.
     * @param periodLabel the es-MX heading already formatted by the caller (e.g.
     *   [Formatters.formatWeekRangeLabel] / [Formatters.formatMonthLabel]).
     * @param streakWeeks the consecutive-weeks streak (B-039); ≤ 0 ⇒ no streak line.
     * @param city the driver's benchmark city, used to localise the flex ("…en CDMX").
     * @param percentiles the per-metric standings for this period (may be empty); the BEST favorable
     *   one becomes the flex line. Not gated by entitlement — it is the card's marketing hook.
     * @param hideAmounts when true the net-earnings line is fully redacted (null) so no peso figure
     *   reaches the renderer; trips/hours/percentile/streak still show.
     */
    fun compose(
        stats: PeriodStats,
        periodKind: SharePeriodKind,
        periodLabel: String,
        streakWeeks: Int,
        city: City,
        percentiles: List<PercentileResult>,
        hideAmounts: Boolean,
    ): ShareCardData = ShareCardData(
        periodLabel = periodLabel,
        periodKind = periodKind,
        netEarnings = if (hideAmounts) null else Formatters.formatMxn(stats.netEarningsMxn),
        trips = formatTrips(stats.totalTrips),
        hours = if (stats.hoursOnline > 0.0) Formatters.formatHours(stats.hoursOnline) else null,
        percentileFlex = bestFlex(percentiles, city),
        streakLine = streakLine(streakWeeks),
        hideAmounts = hideAmounts,
    )

    /**
     * Pick the single best percentile to brag about and render it as "Top X% en <Ciudad> 🚀", or
     * null when nothing qualifies. A result is favorable only when its [PercentileResult
     * .displayPercentile] (already inverted for "lower is better" metrics like commission) is strictly
     * above [FAVORABLE_DISPLAY_THRESHOLD] — i.e. the driver is genuinely in the better half. Among the
     * favorable ones we take the highest display percentile (the strongest "Top X%"). Exposed for unit
     * tests of the selection/inversion math.
     */
    fun bestFlex(percentiles: List<PercentileResult>, city: City): String? {
        val best = percentiles
            .filter { it.displayPercentile > FAVORABLE_DISPLAY_THRESHOLD }
            .maxByOrNull { it.displayPercentile }
            ?: return null
        return "Top ${best.topPercent}% en ${shareCityLabel(city)} 🚀"
    }

    /**
     * A short, punchy city name for the flex line. [City.displayName] is the verbose Ajustes label
     * ("Valle de Mexico (CDMX + Edomex)", "Puebla-Tlaxcala"); on the card we want the compact name a
     * driver would say out loud ("CDMX", "Puebla"). Falls back to the display name for cities whose
     * label is already short.
     */
    fun shareCityLabel(city: City): String = when (city) {
        City.CDMX -> "CDMX"
        City.PUEBLA -> "Puebla"
        else -> city.displayName
    }

    /** "🔥 4 semanas seguidas" / "🔥 1 semana seguida", or null when there is no streak. */
    fun streakLine(streakWeeks: Int): String? {
        if (streakWeeks <= 0) return null
        return if (streakWeeks == 1) {
            "🔥 1 semana seguida"
        } else {
            "🔥 $streakWeeks semanas seguidas"
        }
    }

    /** "1 viaje" / "38 viajes". */
    fun formatTrips(trips: Int): String =
        if (trips == 1) "1 viaje" else "$trips viajes"
}
