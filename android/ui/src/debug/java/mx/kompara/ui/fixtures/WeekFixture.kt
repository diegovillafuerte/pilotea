package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.metrics.WeekMoneySnapshot
import mx.kompara.ui.stats.*
import java.math.BigDecimal
import java.time.LocalDate

/** Scoped aggregate fixture records; no invented trips/days for weeks supplied only as aggregates. */
class WeekFixture(context: Context) {
    val fixture = V3Fixture(context)
    private val day = DayFixture(context)
    fun week(frame: String = "E1"): WeekPresentation {
        var money = fixture.weekMoney
        var days: List<PeriodMoney?> = fixture.dayMoney
        var statements = listOf(fixture.week.getJSONObject("statement_uber").let {
            IncomeRecord("statement-uber", "UBER", Coverage(fixture.weekStart, fixture.weekStart.plusDays(6), false),
                MoneySource.PARSED_STATEMENT, it.getInt("paid").toBigDecimal(), null, it.getInt("trips"))
        })
        when (frame) {
            "E1c" -> {
                money = fixture.noSundayMoney
                days = fixture.dayMoney.dropLast(1) + fixture.period(fixture.weekStart.plusDays(6), fixture.weekStart.plusDays(6), emptyList())
                statements = emptyList()
            }
            "E8" -> {
                val today = day.day().money
                money = today.copy(window = today.window.copy(endInclusive = fixture.date.plusDays(6)))
                days = listOf(today) + List(6) { null }
                statements = emptyList()
            }
            "E2" -> {
                val start = LocalDate.parse("2026-10-05")
                val raw = fixture.raw.getJSONObject("weeks").getJSONObject(start.toString())
                val records = listOf("uber", "didi").map { app -> fixture.record("old-$app", raw.getJSONObject("by_app").getJSONObject(app), start, start.plusDays(6), app.uppercase()) }
                money = PeriodAccounting.calculate(PeriodWindow(start, start.plusDays(6), LocalDate.parse("2026-11-02").atTime(9, 41).atZone(fixture.zone).toInstant(), fixture.zone), records, fixture.schedule)
                days = List(7) { null }
                statements = emptyList()
            }
        }
        val history = fixture.raw.getJSONObject("weeks").keys().asSequence().map { key ->
            val raw = fixture.raw.getJSONObject("weeks").getJSONObject(key)
            WeeklyAggregateEntity("UBER", key, if (raw.optBoolean("imported")) "IMPORTED" else "CAPTURED", 0.0,
                raw.optDouble("gross", raw.optDouble("paid", 0.0)), raw.getInt("trips"), raw.optDouble("km", 0.0), raw.getDouble("hours"), null, null, null, null, null, 0)
        }.toList()
        return WeekPresentation(WeekMoneySnapshot(money, days, history, statements))
    }
}
