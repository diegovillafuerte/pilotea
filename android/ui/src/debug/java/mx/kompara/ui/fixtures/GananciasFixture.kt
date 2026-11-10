package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferEvaluation
import mx.kompara.data.rollup.capturedIncomeRecords
import mx.kompara.data.service.*
import mx.kompara.data.settings.Settings
import mx.kompara.metrics.CarWeek
import mx.kompara.metrics.DayMoneySnapshot
import mx.kompara.metrics.TodayMoneySnapshot
import mx.kompara.metrics.WeekMoneySnapshot
import mx.kompara.ui.stats.*
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The v4 kit's Ganancias (L1–L8) from the pinned fixture: v3's 19 Oct timeline (the v4 fixture keeps it
 * unchanged) plus the v4 block's 12:25 offer from the second app. Every figure comes from the production
 * calculators; nothing here is a display string.
 */
class GananciasFixture(context: Context) {
    private val days = DayFixture(context)
    val fixture = days.fixture
    val v4: JSONObject = JSONObject(context.assets.open("v4/fixture.json").bufferedReader().use { it.readText() }).getJSONObject("v4")
    val zone = fixture.zone
    val date: LocalDate = fixture.date
    fun at(day: LocalDate, time: String): Instant = day.atTime(LocalTime.parse(time.padStart(5, '0'))).atZone(zone).toInstant()
    /** L1, L4, L5, L7, L8: Monday 19 Oct, 14:40. */
    val afternoon = at(date, "14:40")
    /** L3, L6: the next morning, 8:05. */
    val nextMorning = at(date.plusDays(1), "8:05")

    private fun didi(): OfferEntity {
        val raw = v4.getJSONObject("today_second_app_offers").getJSONObject("12:25")
        val id = at(date, "12:25").toEpochMilli()
        val km = raw.getDouble("km").toBigDecimal()
        val net = raw.getDouble("fare").toBigDecimal() - km * fixture.cost
        val rate = net.divide(km, MONEY_CONTEXT)
        return OfferEntity(id, id, raw.getString("app").uppercase(), raw.getDouble("fare"), raw.getDouble("km"), null,
            verdict = if (rate >= 8.toBigDecimal()) "GREEN" else if (rate >= 6.toBigDecimal()) "YELLOW" else "RED",
            outcome = "NOT_TAKEN", sessionId = "fixture-12:25",
            evaluation = OfferEvaluation(id, "IPK", "8", "6", 1, "CAPTURED_ESTIMATE", "DRIVER_PAYMENT", net.toPlainString(), rate.toPlainString(), null, null))
    }

    /** 19 Oct as seen at [now]: the offers, trips and shifts up to then, with the 12:25 offer from the second app. */
    fun day(now: Instant = afternoon, typical: Boolean = false): DayMoneySnapshot {
        val base = days.day(late = now >= at(date, "21:10")).snapshot
        val offers = base.offers + listOfNotNull(didi().takeIf { it.seenAt <= now.toEpochMilli() })
        val money = PeriodAccounting.calculate(PeriodWindow(date, date, now, zone), capturedIncomeRecords(base.trips, offers, now.toEpochMilli(), zone),
            fixture.schedule, base.shifts.map { TimeInterval(it.startedAt, it.endedAt) })
        val cost = fixture.schedule.running.single().let { if (typical) it.copy(confirmation = CostConfirmation.TYPICAL) else it }
        return DayMoneySnapshot(money, offers.sortedByDescending { it.seenAt }, base.trips, base.shifts, emptyList(), cost, listOf(cost))
    }

    /** A day without any offer. */
    fun empty(day: LocalDate, now: Instant, typical: Boolean = false): DayMoneySnapshot {
        val cost = fixture.schedule.running.single().let { if (typical) it.copy(confirmation = CostConfirmation.TYPICAL) else it }
        return DayMoneySnapshot(PeriodAccounting.calculate(PeriodWindow(day, day, now, zone), emptyList(), fixture.schedule),
            emptyList(), emptyList(), emptyList(), emptyList(), cost, listOf(cost))
    }

    fun today(frame: String): TodayMoneySnapshot = when (frame) {
        "L2" -> TodayMoneySnapshot(empty(date, afternoon, typical = true), empty(date.minusDays(1), afternoon, typical = true), false)
        "L3", "empty" -> {
            val now = if (frame == "L3") nextMorning else at(date.plusDays(1), "7:10")
            TodayMoneySnapshot(empty(date.plusDays(1), now), day(now), true)
        }
        else -> TodayMoneySnapshot(day(afternoon), empty(date.minusDays(1), afternoon), true)
    }

    fun readiness(frame: String): ReaderReadiness {
        val phase = when (frame) { "L2", "empty" -> ReaderPhase.PREPARED; "L3" -> ReaderPhase.OFF; else -> ReaderPhase.WORKING }
        val readerDate = if (frame in setOf("L3", "empty")) date.plusDays(1) else date
        val proof = when (frame) { "L2" -> null; "L3", "empty" -> at(date, "20:10").toEpochMilli(); else -> at(date, "14:32").toEpochMilli() }
        return ReaderReadiness(phase, phase != ReaderPhase.OFF, phase != ReaderPhase.OFF, phase != ReaderPhase.OFF, false,
            linkedMapOf(ReaderReadinessReducer.UBER to AppReadiness(true, proof, phase == ReaderPhase.WORKING),
                ReaderReadinessReducer.DIDI to AppReadiness(true, at(date, "12:25").toEpochMilli().takeIf { phase == ReaderPhase.WORKING }, phase == ReaderPhase.WORKING),
                ReaderReadinessReducer.INDRIVE to AppReadiness(false, null, false)),
            if (frame == "L3") ReaderIncident(ReaderIncidentCause.PERMISSION_LOST, at(date, "20:31").toEpochMilli()) else null, readerDate, 0)
    }

    fun ganancias(frame: String, settings: Settings = Settings.DEFAULT): GananciasUiState = GananciasStats.of(today(frame), readiness(frame), settings)

    /** Hoy (L1), the empty morning, or a past day (L6). */
    fun dayPresentation(frame: String, settings: Settings = Settings.DEFAULT): DayPresentation = when (frame) {
        "L6" -> DayPresentation(day(nextMorning), settings)
        else -> DayPresentation(today(frame).today, settings)
    }

    /** L4: 12 – 18 oct, closed. L5: 19 – 25 oct on its Monday. "short": a closed week the car was not covered. */
    fun week(frame: String): WeekPresentation {
        val (money, daily) = when (frame) {
            "L5" -> {
                val today = day(afternoon).money
                today.copy(window = today.window.copy(endInclusive = date.plusDays(6))) to listOf(today) + List(6) { null }
            }
            "short" -> {
                val start = fixture.weekStart
                val week = fixture.period(start, start.plusDays(6), fixture.dayRecords.take(1))
                week to fixture.dayMoney.take(1) + List(6) { i -> fixture.period(start.plusDays(i + 1L), start.plusDays(i + 1L), emptyList()) }
            }
            else -> fixture.weekMoney to fixture.dayMoney
        }
        return WeekPresentation(WeekMoneySnapshot(money, daily, hasOffers = true,
            car = CarWeek.of(fixture.schedule.car, money.window, daily.map { it?.tripContribution })))
    }

    fun weekState(frame: String) = week(frame).let { WeekUiState(it.start, loading = false, week = it) }
}
