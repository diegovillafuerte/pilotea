package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.metrics.compare.*
import java.math.BigDecimal
import java.time.LocalDate

/** The share card's and the plan's proof inputs, computed from the reference JSON. Proofs are debug scenarios, never live cache. */
class CompareFixture(context: Context) {
    val fixture = V3Fixture(context)
    val current = WeekFixture(context).week().snapshot
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
}
