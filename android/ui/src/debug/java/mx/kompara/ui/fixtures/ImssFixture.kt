package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.metrics.imss.ImssMonth
import mx.kompara.metrics.imss.ImssRule
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** The IMSS tab's kit figures (M1–M4) from the pinned v4 fixture, as check.py derives them. */
class ImssFixture(context: Context) {
    val raw = JSONObject(context.assets.open("v4/fixture.json").bufferedReader().use { it.readText() })
    private val v4 = raw.getJSONObject("v4")
    val today: LocalDate = LocalDate.parse(raw.getString("today"))
    val month: YearMonth = YearMonth.from(today)
    private fun JSONObject.decimal(key: String) = BigDecimal(get(key).toString())
    /** The year's three numbers as the fixture states them; the screen's copy reads [ImssRule.Y2026], which must agree. */
    val rule: ImssRule = v4.getJSONObject("imss_2026").let { ImssRule(2026, it.decimal("min_monthly_wage_cdmx"), it.decimal("cost_deduction_vehicle"), it.decimal("iva")) }

    /** M1: each app's fares are the window's weeks plus today's at 14:40 (check.py `trips_m`); incentives as typed. */
    fun m1(): ImssMonth {
        val window = v4.getJSONObject("window").getJSONArray("weeks")
        val trips = listOf("uber", "didi").associate { app ->
            app.uppercase() to (0 until window.length()).fold(raw.getJSONObject("today_by_app_1440").decimal(app)) { sum, i ->
                sum + raw.getJSONObject("weeks").getJSONObject(window.getString(i)).getJSONObject("by_app").getJSONObject(app).decimal("gross")
            }
        }
        // The kit draws DiDi's 0 as "Incentivos: agregar": nothing typed yet.
        val typed = v4.getJSONObject("imss_incentives_entered").let { entered -> listOf("uber", "didi").filter { entered.getInt(it) > 0 }.associate { it.uppercase() to entered.getInt(it) } }
        return ImssMonth.of(month, today, trips, typed, rule)
    }

    /** M2: another driver, four days before the month ends, over the amount with Uber. */
    fun m2(): ImssMonth = v4.getJSONObject("examples").getJSONObject("m2_other_driver").let {
        ImssMonth.of(month, month.atEndOfMonth().minusDays(it.getLong("days_left")), mapOf("UBER" to it.decimal("uber_trips")),
            mapOf("UBER" to it.getInt("uber_incentives")), rule)
    }
}
