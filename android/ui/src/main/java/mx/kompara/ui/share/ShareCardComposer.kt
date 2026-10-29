package mx.kompara.ui.share

import mx.kompara.data.accounting.*
import mx.kompara.data.model.City
import mx.kompara.metrics.compare.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.*
import java.math.BigDecimal
import java.time.YearMonth

/** The same source/basis/coverage eligibility as Comparar, before any disclosure or formatting. */
object ShareCardComposer {
    fun compose(money: PeriodMoney, kind: SharePeriodKind, hide: Boolean = true,
        app: String = "UBER", city: City = City.CDMX, metric: ComparisonMetric = ComparisonMetric.PER_TRIP,
        statements: List<ConfirmedStatement> = emptyList(), cohorts: List<StatementCohort> = emptyList()): ShareCardData {
        val today = money.window.asOf.atZone(money.window.zone).toLocalDate()
        val start = money.window.start
        val open = !money.window.endInclusive.isBefore(today)
        val period = if (kind == SharePeriodKind.WEEK) weekRange(start, today) else
            Formatters.formatMonthLabel(start.toString())
        // An open week, monthly aggregate, parsed import or synthetic cohort can never borrow a rank.
        val proof = statements.singleOrNull { kind == SharePeriodKind.WEEK && !open &&
            money.window.endInclusive == start.plusDays(6) && it.city == city.key &&
            it.value(start, app, metric) != null && it.value(start, app, ComparisonMetric.PER_TRIP) != null }
        val cohort = ComparisonEligibility.cohort(cohorts, city.key, app, start, metric)
        val rank = ComparisonEligibility.rank(proof, cohort)
        if (rank != null && proof != null && cohort != null) {
            val own = requireNotNull(proof.value(start, app, metric))
            val max = maxOf(own, cohort.mean)
            val bars = if (hide) emptyList() else listOf("Tú" to own, "Promedio" to cohort.mean)
                .sortedByDescending { it.second }.map { (label, amount) ->
                    ShareCardBar(label, CompareUiText.rate(amount, metric),
                        if (max.signum() == 0) 0f else amount.divide(max, MONEY_CONTEXT).toFloat(), label == "Tú")
                }
            val hours = if (hide || proof.value(start, app, ComparisonMetric.PER_HOUR) == null) null else
                proof.onlineMinutes?.let { shareDuration(it) }
            return ShareCardData(period, kind,
                CompareUiText.rankFinding(rank, app, CompareUiText.cityName(city), metric).replaceFirst("Te pagan", "Me pagan").replace(" %", "\u00a0%"),
                "Según los resúmenes de ${CompareUiText.count(cohort.drivers)} conductores", "Antes de gastos",
                proof.record.trips.toString(), confirmed = true, hoursLabel = "Horas en línea", hours = hours, bars = bars, hideAmounts = hide)
        }
        val timing = if (kind == SharePeriodKind.WEEK) (if (open) "esta semana" else "esa semana") else (if (open) "este mes" else "ese mes")
        val title = if (kind == SharePeriodKind.WEEK) {
            if (open) "Llevas esta semana" else "Te quedaron esa semana"
        } else "${if (open) "Llevas" else "Te quedaron"} en ${monthName(YearMonth.from(start))}"
        val sources = money.contributions.map { it.record.source }.distinct()
        val source = when {
            sources.isEmpty() -> "Todavía no hay viajes registrados"
            sources.all { it == MoneySource.CAPTURED_ESTIMATE } -> "Según lo que registró Kompara de tus viajes"
            sources.none { it == MoneySource.CAPTURED_ESTIMATE } -> "Según tus resúmenes importados"
            else -> "Según tus viajes registrados y resúmenes importados"
        }
        return ShareCardData(period, kind, "${money.trips} ${if (money.trips == 1) "viaje" else "viajes"} $timing", source,
            if (hide) "" else if (money.periodResult == null) "Faltan datos para calcular lo que te quedó" else
                "Ya restamos ${money.energy()} y desgaste${if (money.carConfigured) " y lo del coche" else ""}. Antes de impuestos.",
            money.trips.toString(), netLabel = "$title · aprox.",
            netEarnings = if (hide || money.contributions.isEmpty()) null else money.periodResult?.let(Formatters::formatMxnWhole),
            hoursLabel = "Tiempo en turno", hours = if (hide || money.hoursOnline.signum() == 0) null else shareDuration(money.hoursOnline.multiply(BigDecimal(60))),
            hideAmounts = hide)
    }
}

internal fun shareDuration(minutes: BigDecimal): String {
    val rounded = minutes.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    return if (rounded % 60L == 0L) Formatters.unbroken("${rounded / 60} h") else Formatters.formatDuration(rounded)
}
