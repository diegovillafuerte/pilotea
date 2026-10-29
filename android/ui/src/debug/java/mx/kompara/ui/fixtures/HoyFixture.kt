package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.*
import mx.kompara.data.service.*
import mx.kompara.data.settings.Settings
import mx.kompara.metrics.*
import mx.kompara.ui.stats.*
import org.json.JSONObject
import java.time.*

/** B-frame alternate worlds. Aggregate-only fixture amounts never become invented production trips. */
class HoyFixture(context: Context) {
    val fixture = V3Fixture(context)
    private val zone = fixture.zone
    private val date = fixture.date
    private fun at(day: LocalDate, time: String) = day.atTime(LocalTime.parse(time.padStart(5, '0'))).atZone(zone).toInstant().toEpochMilli()
    private val cost = fixture.schedule.running.single()
    private fun empty(day: LocalDate, instant: Instant) = PeriodAccounting.calculate(PeriodWindow(day, day, instant, zone), emptyList(), fixture.schedule)

    fun state(frame: String): InicioUiState {
        val id = when (frame) { "N1" -> "B1"; "N2" -> "B2"; else -> frame }
        val tomorrow = id in setOf("B2", "B2r")
        val day = if (tomorrow) date.plusDays(1) else date
        val time = when (id) { "B2" -> "08:05"; "B2r" -> "08:07"; "B3" -> "09:44"; "B3n" -> "12:30"; "B4" -> "07:30"; "B5" -> "13:10"; "B6" -> "10:20"; else -> "14:40" }
        val now = Instant.ofEpochMilli(at(day, time))
        val money = when (id) {
            "B5" -> fixture.pausedMoney.copy(hoursOnline = "4.5".toBigDecimal())
            "B6" -> fixture.partialSupportMoney.copy(hoursOnline = "2.333333333333333".toBigDecimal())
            "B8" -> fixture.negativeDayMoney
            "B1", "B7", "B9" -> fixture.todayMoney
            else -> empty(day, now)
        }.let { it.copy(window = it.window.copy(asOf = now)) }
        var nextId = 0L
        val trips = mutableListOf<TripEntity>()
        fun offer(time: String, data: JSONObject?, outcome: String, inProgress: Boolean = false): OfferEntity {
            val seen = at(date, time)
            val contribution = data?.let { fixture.chip(if (time == "14:32") "verde" else "rojo") }
            val rate = data?.let { (it.getDouble("fare").toBigDecimal() - it.getDouble("km").toBigDecimal() * fixture.cost).divide(it.getDouble("km").toBigDecimal(), MONEY_CONTEXT) }
            val verdict = rate?.let { if (it >= "8".toBigDecimal()) "GREEN" else if (it >= "6".toBigDecimal()) "YELLOW" else "RED" }
            val row = OfferEntity(++nextId, seen, "UBER", data?.getDouble("fare"), data?.getDouble("km"),
                data?.takeIf { it.has("min") }?.getDouble("min"), verdict = verdict, accepted = outcome == "ACCEPTED", outcome = outcome,
                sessionId = "fixture-$time", evaluation = OfferEvaluation(seen, "IPK", "8", "6", 1, "CAPTURED_ESTIMATE", "DRIVER_PAYMENT",
                    rate?.multiply(data!!.getDouble("km").toBigDecimal())?.toPlainString(), rate?.toPlainString(), contribution?.ratePerHour?.toPlainString(), if (data == null) "FARE" else null))
            if (outcome == "ACCEPTED") trips += TripEntity(id = row.id, offerId = row.id, startedAt = seen, platform = "UBER",
                endedAt = if (inProgress) null else seen + 1, grossMxn = row.fareMxn, distanceKm = row.distanceKm, durationMin = row.durationMin,
                lifecycle = if (inProgress) "IN_PROGRESS" else "FINISHED")
            return row
        }
        val offers = if (id in setOf("B1", "B5", "B7", "B8", "B9")) buildList {
            if (id != "B5") add(offer("14:32", fixture.raw.getJSONObject("chips").getJSONObject("verde"), "ACCEPTED", true))
            if (id != "B8") {
                add(offer("12:10", fixture.raw.getJSONObject("chips").getJSONObject("rojo"), "NOT_TAKEN"))
                add(offer("11:48", null, "UNKNOWN"))
                val morning = fixture.raw.getJSONObject("today_morning_trips")
                morning.keys().asSequence().filterNot { it.startsWith("_") }.forEach { add(offer(it, morning.getJSONObject(it), "ACCEPTED")) }
                val missed = fixture.raw.getJSONObject("today_morning_missed")
                missed.keys().asSequence().filterNot { it.startsWith("_") }.forEach { add(offer(it, missed.getJSONObject(it), "NOT_TAKEN")) }
            }
        } else emptyList()
        val yesterdayMoney = when {
            tomorrow -> fixture.closedDayMoney
            id == "B4" -> fixture.dayMoney.last()
            else -> empty(day.minusDays(1), now)
        }
        val typical = id in setOf("B3", "B4")
        val today = DayMoneySnapshot(money, offers, trips, emptyList(),
            if (id == "B9") listOf(OfferPresentationEntity("fixture-12:10", "SUPPRESSED", at(day, "12:10"), "NO_ROOM")) else emptyList(),
            cost.copy(confirmation = if (typical) CostConfirmation.TYPICAL else CostConfirmation.CONFIRMED))
        val yesterday = DayMoneySnapshot(yesterdayMoney, emptyList(), emptyList(), emptyList(), emptyList(), cost)
        val phase = when (id) { "B2", "B3n" -> ReaderPhase.OFF; "B2r", "B3", "B4" -> ReaderPhase.PREPARED; "B5" -> ReaderPhase.PAUSED; "B7" -> ReaderPhase.ATTENTION; else -> ReaderPhase.WORKING }
        val proof = if (id == "B4") at(day.minusDays(1), "20:10") else if (tomorrow) at(date, "20:10") else if (id in setOf("B3", "B3n")) null else now.toEpochMilli()
        val reader = ReaderReadiness(phase, phase != ReaderPhase.OFF, phase != ReaderPhase.OFF, phase == ReaderPhase.WORKING,
            id in setOf("B2", "B2r", "B7"), mapOf(
                ReaderReadinessReducer.UBER to AppReadiness(true, proof, phase == ReaderPhase.WORKING),
                ReaderReadinessReducer.DIDI to AppReadiness(id != "B6", proof, phase == ReaderPhase.WORKING)),
            if (id == "B2") ReaderIncident(ReaderIncidentCause.PERMISSION_LOST, at(date, "20:31")) else null,
            day, if (id == "B9") 1 else 0)
        val state = InicioStats.today(TodayMoneySnapshot(today, yesterday, id !in setOf("B3", "B3n")), reader, Settings.DEFAULT)
        // B6 supplies an aggregate net only; these are its explicitly drawn example rows.
        return if (id != "B6") state else state.copy(offers = listOf(
            HoyOffer(1, "La tomaste", "10:05 · Arriba de tu meta", "$9.49/km", mx.kompara.ui.components.OfferMarkState.ABOVE),
            HoyOffer(2, "La tomaste", "9:12 · Cumple tu mínimo", "$7.16/km", mx.kompara.ui.components.OfferMarkState.MEETS),
            HoyOffer(3, "La tomaste", "8:36 · Arriba de tu meta", "$8.62/km", mx.kompara.ui.components.OfferMarkState.ABOVE),
        ))
    }
}
