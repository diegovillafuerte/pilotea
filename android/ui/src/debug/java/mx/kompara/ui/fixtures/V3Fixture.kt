package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.ui.components.BarValue
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.stats.PeriodStats
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Raw reference aggregates at their declared scope; all money uses the production calculator. */
class V3Fixture(context: Context) {
    val raw = JSONObject(context.assets.open("v3/fixture.json").bufferedReader().use { it.readText() })
    val zone: ZoneId = ZoneId.of("America/Mexico_City")
    val date: LocalDate = LocalDate.parse(raw.getString("today"))
    val clock = AppClock { date.atTime(LocalTime.parse(raw.getString("now"))).atZone(zone).toInstant().toEpochMilli() }
    private fun JSONObject.decimal(key: String) = BigDecimal(get(key).toString())
    private val costInputs = raw.getJSONObject("costs").let {
        RunningCostInputs(RunningCostMode.GAS, it.decimal("wear_per_km"), it.decimal("fuel_price_per_l"), it.decimal("km_per_l"))
    }
    val schedule = CostSchedule(listOf(RunningCostVersion(1, 0, 0, costInputs, CostConfirmation.CONFIRMED,
        LocalDate.parse(raw.getJSONObject("costs").getString("confirmed_on")).atStartOfDay(zone).toInstant().toEpochMilli())),
        listOf(raw.getJSONObject("car_cost").let { CarCostVersion(1, LocalDate.parse(it.getString("from")), it.decimal("weekly"), 0) }))
    val cost: BigDecimal = requireNotNull(costInputs.costPerKm)
    val car: BigDecimal = requireNotNull(schedule.car.single().weeklyMxn).divide(BigDecimal(7), MONEY_CONTEXT)
    private val shifts = raw.getJSONArray("today_shifts")
    private val green = raw.getJSONObject("chips").getJSONObject("verde")
    val week = raw.getJSONObject("weeks").getJSONObject("2026-10-12")

    fun record(id: String, data: JSONObject, start: LocalDate, end: LocalDate = start, app: String = "UBER", inProgress: Int = 0): IncomeRecord =
        IncomeRecord(id, app, Coverage(start, end), MoneySource.CAPTURED_ESTIMATE,
            data.decimal(if (data.has("gross")) "gross" else "fare"), data.decimal("km"),
            data.optInt("trips", 1), minutes = if (data.has("min")) data.decimal("min") else null,
            inProgress = inProgress, costVersionId = 1)

    fun period(start: LocalDate, end: LocalDate, records: List<IncomeRecord>, time: String = raw.getString("now")): PeriodMoney =
        PeriodStats.calculate(PeriodWindow(start, end, date.atTime(LocalTime.parse(time)).atZone(zone).toInstant(), zone), records, schedule)

    private val morning = record("morning", shifts.getJSONObject(0), date)
    val todayRecords = listOf(morning, record("14:32", green, date, inProgress = 1))
    val todayMoney = period(date, date, todayRecords)
    val today: BigDecimal = requireNotNull(todayMoney.periodResult)
    val closedDayMoney = period(date, date, (0 until shifts.length()).map { record("shift$it", shifts.getJSONObject(it), date) }, "21:10")
    val closedDay: BigDecimal = requireNotNull(closedDayMoney.periodResult)
    val pausedMoney = period(date, date, listOf(morning), "13:10")
    val partialSupportMoney = period(date, date, listOf(IncomeRecord("partial-support", "UBER", Coverage(date, date),
        MoneySource.CAPTURED_ESTIMATE, null, null, raw.getJSONObject("b6_partial_support").getInt("trips"),
        declaredContribution = raw.getJSONObject("b6_partial_support").decimal("net"))), "10:20")
    val negativeDayMoney = period(date, date, listOf(record("only-trip", green, date, inProgress = 1)))
    val weekStart: LocalDate = LocalDate.parse("2026-10-12")
    val weekRecords = listOf("uber", "didi").map { app -> record(app, week.getJSONObject("by_app").getJSONObject(app), weekStart, weekStart.plusDays(6), app.uppercase()) }
    val weekMoney = period(weekStart, weekStart.plusDays(6), weekRecords)
    val weekNet: BigDecimal = requireNotNull(weekMoney.periodResult)
    val dayRecords = listOf("L", "M1", "M2", "J", "V", "S", "D").mapIndexed { index, key ->
        record(key, week.getJSONObject("days").getJSONObject(key), weekStart.plusDays(index.toLong()))
    }
    val dayMoney = dayRecords.map { period(it.coverage.start, it.coverage.endInclusive, listOf(it)) }
    val noSundayMoney = period(weekStart, weekStart.plusDays(6), dayRecords.dropLast(1))
    val days: List<BarValue> = dayMoney.mapIndexed { index, money ->
        val net = requireNotNull(money.periodResult)
        BarValue(listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")[index], net.toDouble(), Formatters.formatMxnWhole(net))
    }
    private fun appRecords(week: String, start: LocalDate, end: LocalDate) = listOf("uber", "didi").map { app ->
        record("$week-$app", raw.getJSONObject("weeks").getJSONObject(week).getJSONObject("by_app").getJSONObject(app), start, end, app.uppercase())
    }
    val monthRows = listOf(
        period(date.withDayOfMonth(1), date.withDayOfMonth(4), appRecords("2026-09-28", date.withDayOfMonth(1), date.withDayOfMonth(4))),
        period(date.withDayOfMonth(5), date.withDayOfMonth(11), appRecords("2026-10-05", date.withDayOfMonth(5), date.withDayOfMonth(11))),
        weekMoney, todayMoney)
    val monthMoney = period(date.withDayOfMonth(1), date.withDayOfMonth(31), monthRows.flatMap { it.contributions.map { row -> row.record } })
    fun rate(app: String): BigDecimal = requireNotNull(weekMoney.byApp.getValue(app.uppercase()).ratePerKm)
    fun chip(key: String): Contribution = PeriodAccounting.contribution(record(key, raw.getJSONObject("chips").getJSONObject(key), date), schedule, zone)
    fun chipRate(key: String): String = Formatters.formatPerKm(requireNotNull(chip(key).ratePerKm))
    val import = raw.getJSONObject("weeks").getJSONObject("2026-09-14")
    val goal: String = Formatters.formatMxn(raw.getJSONObject("floors").decimal("verde_min_per_km")).removePrefix("$")
    val minimum: String = Formatters.formatMxn(raw.getJSONObject("floors").decimal("rojo_below_per_km")).removePrefix("$")
}
