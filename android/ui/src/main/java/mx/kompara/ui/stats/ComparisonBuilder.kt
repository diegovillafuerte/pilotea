package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import mx.kompara.metrics.WeekMoneySnapshot
import mx.kompara.metrics.compare.*
import java.math.BigDecimal

/** Each answer uses one app/period/source/basis, before display rounding. No blended city ranking. */
object ComparisonBuilder {
    fun build(current: WeekMoneySnapshot, previous: WeekMoneySnapshot?, choice: CompareChoice,
        statements: List<ConfirmedStatement> = emptyList(), cohorts: List<StatementCohort> = emptyList(),
        premium: Boolean = false): WeeklyComparison {
        val money = current.money
        val apps = (money.contributions.map { it.record.app } + current.statements.map { it.app }).distinct()
            .filter { it in listOf("UBER", "DIDI") }
        val single = apps.singleOrNull()
        val resolved = if (single != null && choice.scope == CompareScope.APPS) choice.copy(scope = CompareScope.CITY, app = single) else choice
        return if (resolved.scope == CompareScope.APPS) apps(current, resolved, statements).copy(singleApp = single)
            else city(current, previous, resolved, statements, cohorts, premium).copy(singleApp = single)
    }

    private fun apps(snapshot: WeekMoneySnapshot, choice: CompareChoice, statements: List<ConfirmedStatement>): WeeklyComparison {
        val money = snapshot.money
        val captured = money.contributions.filter { it.record.source == MoneySource.CAPTURED_ESTIMATE }
        val valuesByMetric = ComparisonMetric.entries.associateWith { metric -> listOf("UBER", "DIDI").map { app ->
            val rows = captured.filter { it.record.app == app }
            val value = if (metric == ComparisonMetric.PER_HOUR) {
                // Hours belong to the statement, never to duplicated shift rollups or captured trip time.
                val proof = statements.singleOrNull { it.record.app == app && it.value(money.window.start, app, metric) != null }
                val contribution = money.contributions.singleOrNull { it.record.id == proof?.record?.id }
                proof?.onlineMinutes?.takeIf { it.signum() > 0 }?.let { minutes ->
                    contribution?.tripContribution?.multiply(BigDecimal(60))?.divide(minutes, MONEY_CONTEXT)
                }
            } else if (rows.isEmpty() || rows.any { it.tripContribution == null }) null else {
                val denominator = if (metric == ComparisonMetric.PER_KM) rows.map { it.record.km }.sumKnown()
                    else rows.sumOf { it.record.trips }.toBigDecimal()
                denominator?.takeIf { it.signum() > 0 }?.let { rows.map { it.tripContribution }.sumKnown()?.divide(it, MONEY_CONTEXT) }
            }
            ComparisonValue(appName(app), value, qualifier = "sin el coche")
        } }
        val unavailable = valuesByMetric.filterValues { rows -> rows.any { it.value == null } }.mapValues { (metric, _) ->
            when (metric) {
                ComparisonMetric.PER_KM -> "Falta distancia para comparar por km"
                ComparisonMetric.PER_TRIP -> "Faltan viajes o costos para comparar por viaje"
                ComparisonMetric.PER_HOUR -> {
                    val missing = listOf("UBER", "DIDI").filter { app -> statements.none { it.value(money.window.start, app, metric) != null } }
                    if (missing.isEmpty()) "Faltan kilómetros o costos de tus resúmenes"
                    else "falta tu resumen de ${missing.joinToString(" y ", transform = ::appName)}"
                }
            }
        }
        val values = valuesByMetric.getValue(choice.metric).sortedByDescending { it.value }.mapIndexed { index, v -> v.copy(own = index == 0) }
        val empty = if (choice.metric == ComparisonMetric.PER_HOUR) money.contributions.isEmpty() else captured.isEmpty()
        val finding = if (empty) "Todavía no hay viajes esta semana" else unavailable[choice.metric] ?: CompareUiText.appFinding(values, choice.metric)
        val detailRows = if (choice.metric == ComparisonMetric.PER_HOUR) money.contributions.filter { row -> statements.any { it.record.id == row.record.id } } else captured
        val details = detailRows.groupBy { it.record.app }.map { (app, rows) ->
            "${appName(app)} · ${rows.sumOf { it.record.trips }} viajes · ${rows.map { it.record.km }.sumKnown()?.stripTrailingZeros()?.toPlainString() ?: "sin"} km"
        } + listOf(if (choice.metric == ComparisonMetric.PER_HOUR) "Fuente: tus resúmenes confirmados, con horas en línea de cada app." else "Fuente: lo que registró Kompara de tus viajes.", "Incluye los kilómetros para llegar por el pasajero y los del viaje.",
            "Restamos ${money.energy()} y desgaste con los costos de cada viaje. No restamos lo del coche.",
            "Las horas del lector no se pueden repartir entre apps que usaste a la vez.", "Solo entran viajes tomados; los cancelados y sin lectura suficiente no se comparan.")
        return WeeklyComparison(money.window.start, money.window.asOf.atZone(money.window.zone).toLocalDate(), choice,
            if (empty) ComparisonStatus.EMPTY else if (choice.metric in unavailable) ComparisonStatus.UNAVAILABLE else ComparisonStatus.OWN_APPS,
            "${values.joinToString(" y ") { it.label }} · ${choice.metric.label} · aprox.", finding,
            "Manejaste cada app a distintas horas", values,
            "Ya restamos ${money.energy()} y desgaste;\nlo del coche cuesta igual en las dos apps.", details, unavailable)
    }

    private fun city(current: WeekMoneySnapshot, previous: WeekMoneySnapshot?, choice: CompareChoice,
        statements: List<ConfirmedStatement>, cohorts: List<StatementCohort>, premium: Boolean): WeeklyComparison {
        val money = current.money
        val week = money.window.start
        val app = choice.app
        val city = CompareUiText.cityName(choice.city)
        val metric = choice.metric
        fun proof(metric: ComparisonMetric) = statements.singleOrNull { money.window.elapsedEnd == money.window.endInclusive && it.city == choice.city.key && it.value(week, app, metric) != null }
        val statement = proof(metric)
        val cohort = ComparisonEligibility.cohort(cohorts, choice.city.key, app, week, metric)
        val rank = ComparisonEligibility.rank(statement, cohort)
        val ownCaptured = capturedPayment(current, app, metric)
        val priorCaptured = previous?.takeIf { it.money.window.start == week.minusWeeks(1) }?.let { capturedPayment(it, app, metric) }
        val unavailable = buildMap {
            put(ComparisonMetric.PER_KM, if (app == "UBER") "El resumen de Uber no trae kilómetros" else "Aún no hay promedio por km de ${appName(app)} en $city")
            put(ComparisonMetric.PER_HOUR, if (proof(ComparisonMetric.PER_HOUR) == null) "falta tu resumen de ${appName(app)} con horas en línea"
                else "aún no hay promedio por hora de ${appName(app)} en $city")
            for (m in listOf(ComparisonMetric.PER_KM, ComparisonMetric.PER_HOUR)) {
                if (!(app == "UBER" && m == ComparisonMetric.PER_KM) && proof(m) != null &&
                    ComparisonEligibility.cohort(cohorts, choice.city.key, app, week, m) != null) remove(m)
            }
        }
        val hasOwn = ownCaptured != null || statement != null
        val selfComparison = cohort == null && ownCaptured != null && priorCaptured != null
        val status = when {
            metric in unavailable -> ComparisonStatus.UNAVAILABLE
            !hasOwn && money.contributions.any { it.record.app == app } -> ComparisonStatus.UNAVAILABLE
            !hasOwn -> ComparisonStatus.EMPTY
            rank != null -> ComparisonStatus.RANKED
            statement != null || selfComparison -> ComparisonStatus.NO_COHORT
            else -> ComparisonStatus.UNCONFIRMED
        }
        val own = if (selfComparison) ownCaptured else statement?.value(week, app, metric) ?: ownCaptured
        val count = if (statement != null && !selfComparison) statement.record.trips else current.money.contributions
            .filter { it.record.app == app && it.record.source == MoneySource.CAPTURED_ESTIMATE }.sumOf { it.record.trips }
        val finding = when (status) {
            ComparisonStatus.RANKED -> CompareUiText.rankFinding(rank!!, app, city, metric)
            ComparisonStatus.NO_COHORT -> "Aún juntamos conductores de ${appName(app)} para compararte"
            ComparisonStatus.UNCONFIRMED -> "Falta tu resumen de ${appName(app)} para ver tu lugar"
            ComparisonStatus.UNAVAILABLE -> unavailable[metric] ?: "Faltan datos del pago para comparar"
            else -> "Todavía no hay viajes de ${appName(app)} esta semana"
        }
        val qualifier = when (status) {
            ComparisonStatus.RANKED -> "Según los resúmenes de ${CompareUiText.count(cohort!!.drivers)} conductores"
            ComparisonStatus.NO_COHORT -> if (selfComparison || statement == null) "Según lo que registró Kompara de tus viajes" else "Según tu resumen de ${appName(app)}"
            else -> "Contamos $count viajes"
        }
        val values = when (status) {
            ComparisonStatus.RANKED -> listOf(ComparisonValue("Tú", own), ComparisonValue("Promedio", cohort!!.mean.takeIf { premium }, own = false, locked = !premium))
                .let { if (premium) it.sortedByDescending { row -> row.value } else it }
            ComparisonStatus.NO_COHORT -> listOf(ComparisonValue("Tú", own)) + if (selfComparison)
                listOf(ComparisonValue("Tú · ${weekRange(week.minusWeeks(1), week)}", priorCaptured, own = false)) else emptyList()
            ComparisonStatus.UNCONFIRMED -> listOf(ComparisonValue("Tú", own, qualifier = "· sin confirmar"),
                ComparisonValue("Promedio", null, own = false, waiting = "al confirmar tu semana"))
            else -> emptyList()
        }
        val details = buildList {
            add("${appName(app)} · $city · ${metric.label}")
            add(if (statement != null && !selfComparison) "Fuente: tu resumen confirmado de ${appName(app)}." else "Fuente: viajes registrados por Kompara, sin confirmar con tu resumen.")
            add("$count viajes · pago de la app antes de gastos.")
            val km = if (statement != null && !selfComparison) statement.record.km else money.contributions.filter { it.record.app == app }.map { it.record.km }.sumKnown()
            add(km?.let { "${it.stripTrailingZeros().toPlainString()} km de esa fuente." } ?: "Kilómetros no disponibles en esta fuente.")
            add(when (metric) {
                ComparisonMetric.PER_TRIP -> "Por viaje divide el pago entre los viajes de la misma fuente y semana; no cuenta la espera entre viajes."
                ComparisonMetric.PER_KM -> "Por km divide el pago entre los kilómetros de la misma fuente y semana."
                ComparisonMetric.PER_HOUR -> "Por hora divide el pago entre las horas en línea confirmadas de esa app y semana."
            })
            if (selfComparison) add("Semana del ${weekRange(week.minusWeeks(1), week)}: tus registros de Kompara, antes de gastos. La diferencia usa las cifras redondeadas que ves.")
            if (cohort != null) add("${CompareUiText.count(cohort.drivers)} conductores con resúmenes confirmados de esa app, ciudad y semana. Promedio es la media de sus pagos ${metric.label.lowercase()}, no la mediana.")
            else add("No hay un promedio con la misma fuente, cobertura y periodo. No usamos datos de muestra para calificarte.")
            add("No se incluyen semanas sin confirmar ni se mezclan pagos de una app con viajes de otra.")
        }
        return WeeklyComparison(week, money.window.asOf.atZone(money.window.zone).toLocalDate(), choice, status,
            "${appName(app)} · $city · ${metric.label}", finding, qualifier, values, "Antes de gastos", details, unavailable,
            difference = if (selfComparison) CompareUiText.selfDifference(ownCaptured!!, priorCaptured!!) else null, rank = rank.takeIf { status == ComparisonStatus.RANKED })
    }

    private fun capturedPayment(snapshot: WeekMoneySnapshot, app: String, metric: ComparisonMetric): BigDecimal? {
        val rows = snapshot.money.contributions.filter { it.record.app == app && it.record.source == MoneySource.CAPTURED_ESTIMATE }
        if (rows.isEmpty() || rows.any { it.record.paymentBasis != PaymentBasis.DRIVER_PAYMENT } || metric == ComparisonMetric.PER_HOUR) return null
        val denominator = if (metric == ComparisonMetric.PER_TRIP) rows.sumOf { it.record.trips }.toBigDecimal() else rows.map { it.record.km }.sumKnown()
        return denominator?.takeIf { it.signum() > 0 }?.let { rows.map { it.record.payment }.sumKnown()?.divide(it, MONEY_CONTEXT) }
    }
}

private fun List<BigDecimal?>.sumKnown(): BigDecimal? = if (isEmpty() || any { it == null }) null else fold(BigDecimal.ZERO) { sum, value -> sum + value!! }
