package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.metrics.WeekMoneySnapshot
import mx.kompara.metrics.compare.*
import mx.kompara.ui.stats.*
import java.math.BigDecimal
import java.time.LocalDate

/** All numbers are computed from the reference JSON. Proofs are debug scenarios, never live cache. */
class CompareFixture(context: Context) {
    val fixture = V3Fixture(context)
    val current = WeekFixture(context).week().snapshot
    val previous: WeekMoneySnapshot = LocalDate.parse("2026-10-05").let { date ->
        val apps = fixture.raw.getJSONObject("weeks").getJSONObject(date.toString()).getJSONObject("by_app")
        val records = listOf("uber", "didi").map { fixture.record("previous-$it", apps.getJSONObject(it), date, date.plusDays(6), it.uppercase()) }
        WeekMoneySnapshot(fixture.period(date, date.plusDays(6), records), List(7) { null })
    }
    val statement: ConfirmedStatement = current.statements.single().let {
        ConfirmedStatement(it.copy(source = MoneySource.SERVER_CONFIRMED_STATEMENT), "cdmx", "debug-statement",
            setOf(MoneyField.PAYMENT, MoneyField.TRIPS))
    }
    val cohort: StatementCohort = fixture.raw.getJSONObject("cohort").let { raw ->
        val quantiles = raw.getJSONObject("per_trip_deciles")
        StatementCohort(raw.getString("city").lowercase(), raw.getString("app").uppercase(), LocalDate.parse(raw.getString("week")),
            ComparisonMetric.PER_TRIP, ComparisonBasis.DRIVER_PAYMENT_BEFORE_COSTS, MoneySource.SERVER_CONFIRMED_STATEMENT,
            "debug-cohort", true, false, raw.getInt("drivers"), BigDecimal(raw.get("sum_driver_per_trip").toString()).divide(raw.getInt("drivers").toBigDecimal(), MONEY_CONTEXT),
            quantiles.keys().asSequence().associate { it.toInt() to BigDecimal(quantiles.get(it).toString()) })
    }
    fun comparison(frame: String = "F1"): WeeklyComparison = when (frame) {
        "F2", "F3", "N7" -> ComparisonBuilder.build(current, previous, CompareChoice(scope = CompareScope.CITY),
            listOf(statement), listOf(cohort), premium = frame != "F3")
        "F4" -> ComparisonBuilder.build(current, previous, CompareChoice(scope = CompareScope.CITY, app = "DIDI"))
        "F5" -> ComparisonBuilder.build(previous, null, CompareChoice(scope = CompareScope.CITY))
        else -> ComparisonBuilder.build(current, previous, CompareChoice())
    }
    fun state(frame: String = "F1") = comparison(frame).let { CompareUiState(it.weekStart, it.today, it.choice, false, it,
        weeks = listOf(it.today.monday(), fixture.weekStart, previous.money.window.start)) }
}
