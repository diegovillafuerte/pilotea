package mx.kompara.ui.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Maps "now" to the ISO date keys the rollup tables are bucketed by — the Monday opening the current
 * week and today's local date — so the dashboard can ask the [mx.kompara.data.db.dao.AggregateDao]
 * for the right rows. Mirrors [mx.kompara.data.rollup.RollupCalculator]'s Monday-anchored week and
 * local-day conventions exactly, kept here as a tiny pure helper so the selection logic stays
 * unit-testable without Room.
 *
 * @param zone device-local zone, injected so day/week boundaries match what the driver sees (and so
 *   tests can pin a deterministic zone).
 */
class WeekClock(val zone: ZoneId) {

    /** ISO date (yyyy-MM-dd) of the Monday that opens the week [epochMs] falls in. */
    fun weekStartIso(epochMs: Long): String =
        localDate(epochMs)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .format(ISO_DATE)

    /** Epoch millis at the start of the Monday that opens the week containing [epochMs]. */
    fun weekStartMs(epochMs: Long): Long =
        localDate(epochMs)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /** Epoch millis at the start of the *next* Monday — the exclusive end of the current week window. */
    fun weekEndMs(epochMs: Long): Long =
        localDate(epochMs)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /** ISO date (yyyy-MM-dd) of the local day [epochMs] falls in. */
    fun dayIso(epochMs: Long): String = localDate(epochMs).format(ISO_DATE)

    /** Epoch millis at the start of the local day [dayIso] (yyyy-MM-dd). */
    fun dayStartMs(dayIso: String): Long =
        LocalDate.parse(dayIso, ISO_DATE).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Epoch millis at the start of the day *after* [dayIso] — the exclusive end of the day window. */
    fun dayEndMs(dayIso: String): Long =
        LocalDate.parse(dayIso, ISO_DATE).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun localDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private companion object {
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
