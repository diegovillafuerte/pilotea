package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.metrics.DriverSlices
import mx.kompara.metrics.HourBlock
import mx.kompara.metrics.SliceTotals
import org.json.JSONObject
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Comparar · Tú's kit figures (K1, K2) from the pinned v4 fixture: the window's weeks by app, weekday
 * and hour block (check.py's `win`). Each group's net is its fares minus the production cost per km × km.
 */
class TuFixture(context: Context) {
    val raw = JSONObject(context.assets.open("v4/fixture.json").bufferedReader().use { it.readText() })
    private val window = raw.getJSONObject("v4").getJSONObject("window")
    private val today = LocalDate.parse(raw.getString("today"))
    private fun JSONObject.decimal(key: String) = BigDecimal(get(key).toString())
    private val cost: BigDecimal = raw.getJSONObject("costs").let {
        requireNotNull(RunningCostInputs(RunningCostMode.GAS, it.decimal("wear_per_km"), it.decimal("fuel_price_per_l"), it.decimal("km_per_l")).costPerKm)
    }

    private fun totals(trips: Int, fare: BigDecimal, km: BigDecimal) =
        SliceTotals(trips, fare, fare.subtract(cost.multiply(km, MONEY_CONTEXT)), km, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private fun totals(group: JSONObject) = totals(group.getInt("trips"), group.decimal("gross"), group.decimal("km"))
    private fun sum(groups: List<JSONObject>) = totals(groups.sumOf { it.getInt("trips") },
        groups.fold(BigDecimal.ZERO) { s, g -> s + g.decimal("gross") }, groups.fold(BigDecimal.ZERO) { s, g -> s + g.decimal("km") })

    /** K1: the three weeks the reader saw (the window starts before the reader's first day). */
    fun k1(): DriverSlices {
        val weeks = (0 until window.getJSONArray("weeks").length()).map { raw.getJSONObject("weeks").getJSONObject(window.getJSONArray("weeks").getString(it)) }
        val days = window.getJSONObject("by_weekday")
        val blocks = window.getJSONObject("by_hour_block")
        return DriverSlices(DriverSlices.window(today).start, DriverSlices.window(today).endInclusive, sum(weeks),
            listOf("uber", "didi").associate { app -> app.uppercase() to sum(weeks.map { it.getJSONObject("by_app").getJSONObject(app) }) },
            listOf("L", "M", "X", "J", "V", "S", "D").mapIndexed { i, key -> DayOfWeek.of(i + 1) to totals(days.getJSONObject(key)) }.toMap(),
            HourBlock.entries.associateWith { totals(blocks.getJSONObject("${it.fromHour}–${it.untilHour}")) })
    }

    /** K2: the first days, under the 30 trips the comparison needs. */
    fun k2(): DriverSlices {
        val trips = window.getInt("first_days_trips")
        val group = totals(trips, BigDecimal(trips * 150), BigDecimal(trips * 12))
        return DriverSlices(DriverSlices.window(today).start, DriverSlices.window(today).endInclusive, group, mapOf("UBER" to group), emptyMap(), emptyMap())
    }
}
