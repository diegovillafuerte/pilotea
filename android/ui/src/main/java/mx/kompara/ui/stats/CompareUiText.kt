package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.data.model.City
import mx.kompara.metrics.compare.ComparisonMetric
import java.math.BigDecimal
import java.math.RoundingMode
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

    fun appFinding(values: List<ComparisonValue>, metric: ComparisonMetric): String {
        val sorted = values.filter { it.value != null }.sortedByDescending { it.value }
        if (sorted.size != 2) return "Faltan datos para comparar tus apps"
        val difference = sorted[0].value!!.subtract(sorted[1].value!!)
        val phrase = metric.label.lowercase()
        val decimals = if (metric == ComparisonMetric.PER_KM) 2 else 0
        if (difference.setScale(decimals, RoundingMode.HALF_UP).signum() == 0) return "Las dos apps te dejaron lo mismo $phrase"
        val amount = if (decimals == 2) Formatters.formatMxn(difference) else Formatters.formatMxnWhole(difference)
        return "${sorted[0].label} te dejó $amount más $phrase"
    }

    fun rankFinding(rank: Int, app: String, city: String, metric: ComparisonMetric): String =
        "Te pagan ${if (rank >= 50) "más" else "menos"} ${metric.label.lowercase()} que al ${if (rank >= 50) rank else 100 - rank} % de los de ${appName(app)} en $city"

    /** F4 deliberately explains the printed whole-peso pair, not hidden precision. */
    fun selfDifference(current: BigDecimal, previous: BigDecimal): String? {
        val before = previous.setScale(0, RoundingMode.HALF_UP)
        if (before.signum() <= 0) return null
        val delta = current.setScale(0, RoundingMode.HALF_UP).subtract(before)
            .multiply(BigDecimal(100)).divide(before, 0, RoundingMode.HALF_UP)
        return (if (delta.signum() > 0) "+" else "") + delta.toPlainString().replace('-', '−') + " %"
    }


    /** Display name for a platform (proper noun, locale-independent). */
    fun platformName(platform: Platform): String = when (platform) {
        Platform.UBER -> "Uber"
        Platform.DIDI -> "DiDi"
        Platform.INDRIVE -> "inDrive"
        Platform.UNKNOWN -> "Otra"
    }

}
