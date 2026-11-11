package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.metrics.monthMoneySnapshot
import mx.kompara.ui.stats.*
import java.time.LocalDate

/** Aggregate test evidence stays at its declared date scope, never seeds a real installation. */
class MonthFixture(context: Context) {
    val fixture = V3Fixture(context)
    fun month(frame: String = "E3"): MonthPresentation {
        if (frame !in listOf("E3b", "E3c")) return MonthPresentation(monthMoneySnapshot(fixture.monthMoney.window,
            fixture.monthRows.flatMap { it.contributions.map { c -> c.record } }, fixture.schedule,
            history = fixture.raw.getJSONObject("weeks").keys().asSequence().map { java.time.YearMonth.from(LocalDate.parse(it)) }.toList()))
        val start = LocalDate.parse("2026-09-01")
        val imported = IncomeRecord("sep-import", "UBER", Coverage(LocalDate.parse("2026-09-14"), LocalDate.parse("2026-09-20"), false),
            MoneySource.PARSED_STATEMENT, fixture.import.getInt("paid").toBigDecimal(), null, fixture.import.getInt("trips"))
        val raw = fixture.raw.getJSONObject("alt_scenarios").getJSONObject("E3c_sept_mixed").getJSONObject("weeks")
        val records = if (frame == "E3c") listOf(
            fixture.record("sep21", raw.getJSONObject("2026-09-21"), start.withDayOfMonth(21), start.withDayOfMonth(27)),
            fixture.record("sep28", raw.getJSONObject("2026-09-28_partial"), start.withDayOfMonth(28), start.withDayOfMonth(30))) else emptyList()
        return MonthPresentation(monthMoneySnapshot(fixture.monthMoney.window.copy(start = start, endInclusive = start.withDayOfMonth(30)),
            records + imported, fixture.schedule), premium = true)
    }
}
