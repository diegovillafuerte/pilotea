package mx.kompara.data.rollup

import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Compatibility aggregate projection of the shared calculator. Per-app net excludes the car. */
class RollupCalculator(private val zone: ZoneId, private val costs: CostSchedule) {
    /** Explicit-cost adapter for existing callers/tests; production uses the dated schedule. */
    constructor(zone: ZoneId, marginalCostPerKm: Double) : this(zone, CostSchedule(listOf(
        RunningCostVersion(-1, Long.MIN_VALUE, 0, RunningCostInputs(RunningCostMode.DIRECT,
            directPerKm = marginalCostPerKm.decimal()), CostConfirmation.CONFIRMED),
    )))

    fun rollup(trips: List<TripEntity>, shifts: List<ShiftEntity>, offers: List<OfferEntity>, computedAt: Long): RollupResult {
        val records = capturedIncomeRecords(trips, offers, computedAt, zone)
        val intervals = shifts.map { TimeInterval(it.startedAt, it.endedAt) }
        fun bucket(rows: List<IncomeRecord>, start: LocalDate, end: LocalDate) = PeriodAccounting.calculate(
            PeriodWindow(start, end, Instant.ofEpochMilli(computedAt), zone), rows, costs, intervals)
        fun acceptance(platform: String, start: LocalDate, end: LocalDate): Double? {
            val resolved = offers.filter { it.platform == platform && it.seenAt <= computedAt &&
                localDate(it.seenAt) in start..end && it.outcome in listOf("ACCEPTED", "DECLINED", "EXPIRED", "NOT_TAKEN") }
            return resolved.takeIf { it.isNotEmpty() }?.let { list -> list.count { it.outcome == "ACCEPTED" }.toDouble() / list.size }
        }
        val daily = records.groupBy { it.app to it.coverage.start }.map { (key, rows) ->
            val (app, day) = key
            val money = bucket(rows, day, day)
            val value = money.byApp.getValue(app)
            val net = value.tripContribution?.toDouble()
            val hours = money.hoursOnline.toDouble()
            DailyAggregateEntity(app, day.toString(), AggregateSource.CAPTURED.name,
                net ?: 0.0, value.payment!!.toDouble(), value.trips, value.km!!.toDouble(), hours,
                net?.let { it / value.trips }, value.ratePerKm?.toDouble(), net?.let { perHour(it, hours) },
                perHour(value.trips.toDouble(), hours), acceptance(app, day, day), computedAt,
                runningCostKnown = net != null, runningExpenseDecimal = value.runningExpense?.toPlainString(),
                contributionDecimal = value.tripContribution?.toPlainString(), costVersionIds = versionIds(money))
        }
        val weekly = records.groupBy { it.app to monday(it.coverage.start) }.map { (key, rows) ->
            val (app, week) = key
            val money = bucket(rows, week, week.plusDays(6))
            val value = money.byApp.getValue(app)
            val net = value.tripContribution?.toDouble()
            val hours = money.hoursOnline.toDouble()
            WeeklyAggregateEntity(app, week.toString(), AggregateSource.CAPTURED.name,
                net ?: 0.0, value.payment!!.toDouble(), value.trips, value.km!!.toDouble(), hours,
                net?.let { it / value.trips }, value.ratePerKm?.toDouble(), net?.let { perHour(it, hours) },
                perHour(value.trips.toDouble(), hours), acceptance(app, week, week.plusDays(6)), computedAt,
                runningCostKnown = net != null, runningExpenseDecimal = value.runningExpense?.toPlainString(),
                contributionDecimal = value.tripContribution?.toPlainString(), costVersionIds = versionIds(money))
        }
        return RollupResult(daily, weekly)
    }

    fun weekStartIso(epochMs: Long): String = monday(localDate(epochMs)).toString()
    fun dayIso(epochMs: Long): String = localDate(epochMs).toString()
    private fun localDate(epochMs: Long) = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    private fun monday(day: LocalDate) = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private fun perHour(value: Double, hours: Double): Double? = if (hours > 0) value / hours else null
    private fun versionIds(money: PeriodMoney) = money.contributions.mapNotNull { it.runningCostVersion?.id }.distinct().sorted().joinToString(",")
}

data class RollupResult(val daily: List<DailyAggregateEntity>, val weekly: List<WeeklyAggregateEntity>)
