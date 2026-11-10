package mx.kompara.ui.stats

import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.metrics.DriverSlices
import mx.kompara.metrics.HourBlock
import mx.kompara.metrics.SliceTotals
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek

/** A sentence with one emphasised run, as the kit's `<b>`: [lead], then [strong] in bold, then [tail]. */
data class Emphasis(val lead: String, val strong: String = "", val tail: String = "") {
    val text: String get() = lead + strong + tail
}

/**
 * One group of a Comparar · Tú chart (K1). [fraction] is of the best group's rate; null draws a hairline
 * (under 3 trips, or no rate in this basis). [value] is printed only where the kit prints it.
 */
data class TuBar(val label: String, val fraction: Float?, val value: String?, val best: Boolean = false, val low: Boolean = false)

/** One question of K1: its sentence, its chart and the chart's accessible reading. */
data class TuQuestion(val finding: String, val bars: List<TuBar>, val description: String)

/** Comparar · Tú (plan 4.6): the driver's own taken trips of the last 28 days, compared with each other. */
sealed interface TuPresentation {
    /** K2: under [DriverSlices.MIN_TRIPS_TO_COMPARE] taken trips in the window. */
    data class Gathering(val trips: Int) : TuPresentation {
        val line: Emphasis get() = Emphasis("Con ${DriverSlices.MIN_TRIPS_TO_COMPARE} viajes empezamos a comparar tus apps, " +
            "tus días y tus horas. Llevas ", "$trips", ".")
        val progress: Float get() = (trips.toFloat() / DriverSlices.MIN_TRIPS_TO_COMPARE).coerceIn(0f, 1f)
    }

    /** K1, in the driver's unit and basis. */
    data class Ready(val header: String, val apps: TuQuestion, val days: TuQuestion, val hours: TuQuestion) : TuPresentation

    companion object {
        private val dayLetters = listOf("L", "M", "M", "J", "V", "S", "D")
        private val dayNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

        fun of(slices: DriverSlices, unit: PreferredMetric, basis: RateBasis): TuPresentation {
            if (!slices.ready) return Gathering(slices.trips)
            val perKm = unit == PreferredMetric.IPK
            val gross = basis == RateBasis.GROSS
            val per = if (perKm) "por km" else "por hora"
            // In bruto the trips pay ("te paga"); in neto they leave ("te deja"), as the chip's word says.
            val leaves = if (gross) "te paga" else "te deja"
            val leavesMany = if (gross) "te pagan" else "te dejan"
            val chart = TuChart(unit, basis)

            val apps = slices.byApp.entries.sortedBy { (app, _) -> Platform.entries.indexOfFirst { it.name == app }.let { if (it < 0) Int.MAX_VALUE else it } }
            val appChart = chart.rank(apps.map { appName(it.key) }, apps.map { it.value }, labelAll = true, labelLow = false)
            val appBars = appChart.bars.indices.sortedWith(compareBy<Int> { appChart.printed[it] == null }.thenByDescending { appChart.printed[it] })
                .map { appChart.bars[it] }
            val appGap = appChart.gap
            val appFinding = when {
                apps.size == 1 -> "Solo tienes viajes de ${appName(apps.single().key)} en estas semanas."
                appGap == null -> "Aún no podemos comparar tus apps."
                appGap.signum() == 0 -> "${together(appChart.drawnLabels)} $leavesMany lo mismo $per."
                else -> "${appChart.bestLabel} $leaves ${chart.show(appGap)} más $per."
            }

            val days = DayOfWeek.entries.map { slices.byWeekday[it] }
            val dayChart = chart.rank(dayLetters, days, labelAll = false, labelLow = true)
            val dayGap = dayChart.gap
            val dayFinding = when {
                dayGap == null -> "Aún no podemos comparar tus días."
                dayGap.signum() == 0 -> "Todos tus días $leavesMany lo mismo."
                else -> "El ${dayNames[dayChart.best!!]} $leaves ${chart.show(dayGap)} más que el ${dayNames[dayChart.worst!!]}."
            }

            val blocks = HourBlock.entries
            val hourChart = chart.rank(blocks.map { "${it.fromHour}–${it.untilHour}" }, blocks.map { slices.byHourBlock[it] }, labelAll = false, labelLow = false)
            val hourFinding = if (hourChart.drawn < 2) "Aún no podemos comparar tus horas."
                else "${blockName(blocks[hourChart.best!!])} es cuando más $leaves."

            return Ready("Tus viajes · últimas 4 semanas · $per, ${if (gross) "bruto" else "neto"}",
                // Read aloud in the order drawn, best first; every drawn app prints its rate.
                TuQuestion(appFinding, appBars, appBars.filter { it.value != null }.joinToString(", ") { "${it.label} ${it.value}" }),
                TuQuestion(dayFinding, dayChart.bars, chart.describe(dayNames, dayChart)),
                TuQuestion(hourFinding, hourChart.bars, chart.describe(blocks.map(::blockName), hourChart)))
        }

        private fun blockName(block: HourBlock) = "De ${block.fromHour} a ${block.untilHour} h"

        private fun together(names: List<String>) = names.dropLast(1).joinToString(", ") + " y " + names.last()
    }
}

/** Ranks groups by their printed rate (differences come from the printed figures) and scales bars to the best raw rate. */
private class TuChart(private val unit: PreferredMetric, private val basis: RateBasis) {
    private val scale = if (unit == PreferredMetric.IPK) 2 else 0

    fun show(value: BigDecimal): String = if (scale == 2) Formatters.formatMxn(value) else Formatters.formatMxnWhole(value)

    /** [worst] is null when every drawn group prints the same figure. */
    class Ranked(val labels: List<String>, val bars: List<TuBar>, val printed: List<BigDecimal?>, val best: Int?, val worst: Int?) {
        val drawn: Int get() = printed.count { it != null }
        val drawnLabels: List<String> get() = labels.filterIndexed { i, _ -> printed[i] != null }
        val bestLabel: String get() = labels[best!!]
        /** Best minus worst printed figure; null under two drawn groups. */
        val gap: BigDecimal? get() = if (drawn < 2) null else worst?.let { printed[best!!]!!.subtract(printed[it]!!) } ?: BigDecimal.ZERO
    }

    fun rank(labels: List<String>, groups: List<SliceTotals?>, labelAll: Boolean, labelLow: Boolean): Ranked {
        val rates = groups.map { group -> group?.takeIf { it.drawable }?.rate(unit, basis) }
        val printed = rates.map { it?.setScale(scale, RoundingMode.HALF_UP) }
        val drawn = printed.indices.filter { printed[it] != null }
        val best = drawn.maxByOrNull { printed[it]!! }
        val worst = drawn.minByOrNull { printed[it]!! }?.takeIf { it != best }
        val top = best?.let { rates[it] }
        val bars = labels.mapIndexed { i, label ->
            val rate = rates[i]
            val fraction = rate?.let { if (top!!.signum() > 0) it.divide(top, MONEY_CONTEXT).toFloat().coerceIn(0f, 1f) else 0f }
            val labelled = rate != null && (labelAll || i == best || (labelLow && i == worst))
            TuBar(label, fraction, if (labelled) show(rate!!) else null, best = i == best, low = i == worst)
        }
        return Ranked(labels, bars, printed, best, worst)
    }

    fun describe(names: List<String>, ranked: Ranked): String = names.indices.filter { ranked.printed[it] != null }
        .joinToString(", ") { "${names[it]} ${show(ranked.printed[it]!!)}" }
}
