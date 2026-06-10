package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.metrics.compare.CompareMetric
import mx.kompara.metrics.compare.CompareVerdict
import mx.kompara.metrics.compare.CompareWinner
import mx.kompara.ui.format.Formatters

/**
 * Pure es-MX presentation for the Comparar tab (B-047), kept Android-free so the verdict sentence and
 * per-metric labels/values are unit-tested on the JVM (the composable only consumes the strings).
 *
 * Platform display names live here (Uber / DiDi / inDrive) rather than going through `strings.xml`
 * because they're proper nouns — identical in every locale — and the verdict sentence interpolates
 * them inline.
 */
object CompareUiText {

    /** Display name for a platform (proper noun, locale-independent). */
    fun platformName(platform: Platform): String = when (platform) {
        Platform.UBER -> "Uber"
        Platform.DIDI -> "DiDi"
        Platform.INDRIVE -> "inDrive"
        Platform.UNKNOWN -> "Otra"
    }

    /** Short metric label for a comparison row, e.g. "$/km". */
    fun metricLabel(metric: CompareMetric): String = when (metric) {
        CompareMetric.EARNINGS_PER_HOUR -> "$/hora"
        CompareMetric.EARNINGS_PER_KM -> "$/km"
        CompareMetric.EARNINGS_PER_TRIP -> "$/viaje"
        CompareMetric.NET_EARNINGS -> "Ganancia neta"
        CompareMetric.TOTAL_TRIPS -> "Viajes"
        CompareMetric.ACCEPTANCE_RATE -> "Aceptación"
        CompareMetric.COMMISSION_PCT -> "Comisión"
    }

    /** The noun phrase the verdict sentence uses, e.g. "por km". */
    private fun metricPhrase(metric: CompareMetric): String = when (metric) {
        CompareMetric.EARNINGS_PER_HOUR -> "por hora"
        CompareMetric.EARNINGS_PER_KM -> "por km"
        CompareMetric.EARNINGS_PER_TRIP -> "por viaje"
        CompareMetric.NET_EARNINGS -> "en ganancia neta"
        CompareMetric.TOTAL_TRIPS -> "en viajes"
        CompareMetric.ACCEPTANCE_RATE -> "en aceptación"
        CompareMetric.COMMISSION_PCT -> "en comisión"
    }

    /** Format a metric value for its row, e.g. "$8.40/km", "83 %", "1,234.56" net. */
    fun metricValue(metric: CompareMetric, value: Double): String = when (metric) {
        CompareMetric.EARNINGS_PER_HOUR -> Formatters.formatPerHour(value)
        CompareMetric.EARNINGS_PER_KM -> Formatters.formatPerKm(value)
        CompareMetric.EARNINGS_PER_TRIP -> Formatters.formatMxn(value)
        CompareMetric.NET_EARNINGS -> Formatters.formatMxn(value)
        CompareMetric.TOTAL_TRIPS -> value.toInt().toString()
        CompareMetric.ACCEPTANCE_RATE -> Formatters.formatPercent(value)
        CompareMetric.COMMISSION_PCT -> Formatters.formatPercent(value)
    }

    /** Whole-number percent for a 0..n ratio, e.g. 0.12 → "12 %". */
    fun pctLabel(ratio: Double): String = "${Math.round(ratio * 100)} %"

    /**
     * The headline verdict sentence (B-047 req 4), e.g. "DiDi te pagó 12 % más por km que Uber esta
     * semana". Handles the ties and the loser-was-zero case so the screen always has a sentence.
     *
     * @param resolve maps a platform NAME (the calculator's opaque string) back to a [Platform] for
     *   the display name; falls back to the raw name when it can't.
     */
    fun verdictSentence(verdict: CompareVerdict, resolve: (String) -> Platform?): String {
        val phrase = metricPhrase(verdict.metric)
        val winnerName = verdict.winnerPlatform
        if (verdict.winner == CompareWinner.TIE || winnerName == null) {
            // A tie carries no winner/loser names; the two compared platforms show in the bars below.
            return "Esta semana las dos plataformas te pagaron casi lo mismo $phrase."
        }
        val winner = displayName(winnerName, resolve)
        val loser = verdict.loserPlatform?.let { displayName(it, resolve) } ?: "la otra plataforma"
        val pct = verdict.pctDifference
        return if (pct == null) {
            // Loser was zero — the lead is unbounded; state the win without a misleading "%".
            "Esta semana $winner te pagó más $phrase que $loser."
        } else {
            "Esta semana $winner te pagó ${pctLabel(pct)} más $phrase que $loser."
        }
    }

    private fun displayName(platformName: String, resolve: (String) -> Platform?): String =
        resolve(platformName)?.let { platformName(it) } ?: platformName
}
