package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.*
import mx.kompara.data.rollup.capturedIncomeRecords
import mx.kompara.data.settings.Settings
import mx.kompara.metrics.DayMoneySnapshot
import mx.kompara.ui.stats.DayPresentation
import org.json.JSONObject
import java.time.LocalTime

/** Full dated offer evidence from the checked-in fixture, without inventing missing durations. */
class DayFixture(context: Context) {
    val fixture = V3Fixture(context)
    fun day(late: Boolean = false, suppressed: Boolean = false): DayPresentation {
        val date = fixture.date
        fun at(time: String) = date.atTime(LocalTime.parse(time.padStart(5, '0'))).atZone(fixture.zone).toInstant().toEpochMilli()
        val now = at(if (late) "21:10" else "14:40")
        val offers = mutableListOf<OfferEntity>()
        val trips = mutableListOf<TripEntity>()
        fun add(time: String, data: JSONObject?, taken: Boolean, pickup: Double? = data?.optDouble("pickup_km")?.takeUnless { it.isNaN() }) {
            if (at(time) > now) return
            val id = at(time)
            val km = data?.getDouble("km")
            val fare = data?.getDouble("fare")
            val net = if (fare != null && km != null) fare.toBigDecimal() - km.toBigDecimal() * fixture.cost else null
            val rate = net?.divide(km!!.toBigDecimal(), MONEY_CONTEXT)
            val minutes = data?.optDouble("min")?.takeUnless { it.isNaN() }
            val hour = minutes?.let { net?.multiply(60.toBigDecimal())?.divide(it.toBigDecimal(), MONEY_CONTEXT) }
            val verdict = when { rate == null -> "UNKNOWN"; rate >= 8.toBigDecimal() -> "GREEN"; rate >= 6.toBigDecimal() -> "YELLOW"; else -> "RED" }
            val outcome = if (taken) "ACCEPTED" else if (data == null) "UNKNOWN" else "NOT_TAKEN"
            offers += OfferEntity(id, id, "UBER", fare, km, minutes, pickupKm = pickup, verdict = verdict,
                accepted = taken, outcome = outcome, sessionId = "fixture-$time",
                evaluation = OfferEvaluation(id, "IPK", "8", "6", 1, "CAPTURED_ESTIMATE", "DRIVER_PAYMENT",
                    net?.toPlainString(), rate?.toPlainString(), hour?.toPlainString(), if (data == null) "FARE" else null))
            if (taken) trips += TripEntity(id = id, offerId = id, startedAt = id,
                // Only the fixture's 14:32 record has a prescribed live transition at 14:56.
                endedAt = if (!late && time == "14:32") null else at(time) + (minutes?.times(60_000)?.toLong() ?: 1),
                platform = "UBER", grossMxn = fare, distanceKm = km, durationMin = minutes)
        }
        val chips = fixture.raw.getJSONObject("chips")
        add("14:32", chips.getJSONObject("verde"), true, 2.0)
        add("14:50", chips.getJSONObject("amarillo"), true, 2.0)
        add("12:10", chips.getJSONObject("rojo"), false, 5.2)
        add("11:48", null, false)
        listOf("today_morning_trips", "today_morning_missed", "today_afternoon_trips").forEach { key ->
            val group = fixture.raw.getJSONObject(key)
            group.keys().asSequence().filterNot { it.startsWith("_") }.forEach { add(it, group.getJSONObject(it), key != "today_morning_missed") }
        }
        val shifts = (0..1).map { i -> fixture.raw.getJSONArray("today_shifts").getJSONObject(i).let {
            ShiftEntity(id = i + 1L, startedAt = at(it.getString("start")), endedAt = at(it.getString("end")).takeIf { end -> end <= now })
        } }
        val window = PeriodWindow(date, date, java.time.Instant.ofEpochMilli(now), fixture.zone)
        val money = PeriodAccounting.calculate(window, capturedIncomeRecords(trips, offers, now, fixture.zone), fixture.schedule,
            shifts.map { TimeInterval(it.startedAt, it.endedAt) })
        return DayPresentation(DayMoneySnapshot(money, offers, trips, shifts,
            if (suppressed) listOf(OfferPresentationEntity("fixture-12:10", "SUPPRESSED", at("12:10"), "NAVIGATION")) else emptyList(),
            fixture.schedule.running.single()), Settings.DEFAULT)
    }
}
