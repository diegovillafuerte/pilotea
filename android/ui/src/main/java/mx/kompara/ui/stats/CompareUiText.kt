package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.data.model.City
import mx.kompara.metrics.compare.ComparisonMetric
import java.math.BigDecimal
import mx.kompara.ui.format.Formatters

/** Spanish presentation for one source and metric at a time. */
object CompareUiText {
    fun count(value: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(value)

    fun cityName(city: City): String = when (city) {
        City.CDMX -> "CDMX"
        City.LEON -> "León"
        City.QUERETARO -> "Querétaro"
        City.MERIDA -> "Mérida"
        City.CANCUN -> "Cancún"
        else -> city.displayName
    }

    fun rate(value: BigDecimal, metric: ComparisonMetric): String = when (metric) {
        ComparisonMetric.PER_KM -> Formatters.formatPerKm(value)
        ComparisonMetric.PER_TRIP -> Formatters.formatMxnWhole(value) + "/viaje"
        ComparisonMetric.PER_HOUR -> Formatters.formatPerHourWhole(value)
    }

    fun rankFinding(rank: Int, app: String, city: String, metric: ComparisonMetric): String =
        "Te pagan ${if (rank >= 50) "más" else "menos"} ${metric.label.lowercase()} que al ${if (rank >= 50) rank else 100 - rank} % de los de ${appName(app)} en $city"


    /** Display name for a platform (proper noun, locale-independent). */
    fun platformName(platform: Platform): String = when (platform) {
        Platform.UBER -> "Uber"
        Platform.DIDI -> "DiDi"
        Platform.INDRIVE -> "inDrive"
        Platform.UNKNOWN -> "Otra"
    }

}
