package mx.kompara.ui.format

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * es-MX number/currency formatting for everything a chofer sees. Kept Android-free so the rules
 * are unit-testable on the JVM (the composables only ever consume the returned strings).
 *
 * All formatting is pinned to [MX] so the output is identical regardless of the device locale —
 * a driver in Mexico always sees "$1,234.56", "12.3 km", "$186/h".
 */
object Formatters {
    private val MX: Locale = Locale.forLanguageTag("es-MX")

    const val NBSP = "\u00a0"
    const val WORD_JOINER = "\u2060"

    private fun number(value: java.math.BigDecimal, digits: Int): String =
        NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = java.math.RoundingMode.HALF_UP
        }.format(value.setScale(digits, java.math.RoundingMode.HALF_UP))

    private fun money(value: java.math.BigDecimal, digits: Int): String {
        val rounded = value.setScale(digits, java.math.RoundingMode.HALF_UP)
        return (if (rounded.signum() < 0) "−" else "") + "$" + number(rounded.abs(), digits)
    }

    fun formatMxn(amount: Double): String = formatMxn(java.math.BigDecimal.valueOf(amount))
    fun formatMxn(amount: java.math.BigDecimal): String = money(amount, 2)
    fun formatMxnWhole(amount: Double): String = formatMxnWhole(java.math.BigDecimal.valueOf(amount))
    fun formatMxnWhole(amount: java.math.BigDecimal): String = money(amount, 0)
    fun formatPerKm(amount: Double): String = formatMxn(amount) + "/km"
    fun formatPerKm(amount: java.math.BigDecimal): String = formatMxn(amount) + "/km"
    fun formatPerHour(amount: Double): String = formatPerHourWhole(amount)
    fun formatPerHourWhole(amount: Double): String = formatMxnWhole(amount) + "/h"
    fun formatPerHourWhole(amount: java.math.BigDecimal): String = formatMxnWhole(amount) + "/h"
    fun formatKm(km: Double): String = number(java.math.BigDecimal.valueOf(km), 1) + NBSP + "km"
    fun formatHours(hours: Double): String = number(java.math.BigDecimal.valueOf(hours), 1) + NBSP + "h"
    fun formatPercent(fraction: Double): String =
        number(java.math.BigDecimal.valueOf(fraction.coerceIn(0.0, 1.0)).multiply(java.math.BigDecimal(100)), 0) + NBSP + "%"

    /** Text-boundary protection without changing the visible currency/rate. Use in prose. */
    fun unbroken(value: String): String = value.replace(WORD_JOINER, "")
        .replace(" ", NBSP).replace("/", WORD_JOINER + "/" + WORD_JOINER)

    fun formatDuration(minutes: Long): String {
        require(minutes >= 0)
        return unbroken(if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min")
    }

    /** A relative money label always dates its evidence, including across year boundaries. */
    fun formatDatedRelativeLabel(date: LocalDate, today: LocalDate): String {
        val dated = date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE d MMM" else "EEEE d MMM yyyy", MX))
        return when (date) {
            today -> "Hoy, $dated"
            today.minusDays(1) -> "Ayer, $dated"
            else -> dated.replaceFirstChar { it.titlecase(MX) }
        }
    }

    /** "12" placeholder dash for an unknown rate — every card uses this when its denominator is 0. */
    const val DASH: String = "—"

    /**
     * A short es-MX day label for a day-detail header, e.g. "mié 10 jun" for 2026-06-10. Falls back
     * to the raw ISO string if it can't be parsed.
     */
    fun formatDayLabel(dayIso: String): String =
        runCatching { LocalDate.parse(dayIso, ISO).format(DAY_LABEL) }.getOrDefault(dayIso)

    /**
     * A short es-MX week label, e.g. "Semana del 8 jun" for the Monday 2026-06-08. Falls back to the
     * raw ISO string if it can't be parsed.
     */
    fun formatWeekLabel(weekStartIso: String): String =
        runCatching { "Semana del " + LocalDate.parse(weekStartIso, ISO).format(WEEK_LABEL) }
            .getOrDefault(weekStartIso)

    /**
     * The es-MX range label for the week opening at [weekStartIso] (the Monday), spanning Mon–Sun,
     * e.g. "Semana del 1–7 jun" for 2026-06-01, or "Semana del 29 jun–5 jul" when the week straddles
     * two months. Used on the shareable earnings card (B-055). Falls back to [formatWeekLabel] if the
     * ISO string can't be parsed.
     */
    fun formatWeekRangeLabel(weekStartIso: String): String {
        val start = runCatching { LocalDate.parse(weekStartIso, ISO) }.getOrNull()
            ?: return formatWeekLabel(weekStartIso)
        val end = start.plusDays(6)
        val startDay = start.dayOfMonth
        val endDay = end.dayOfMonth
        return if (start.month == end.month) {
            // Same month: "Semana del 1–7 jun".
            "Semana del $startDay–$endDay ${start.format(MONTH_ABBREV)}"
        } else {
            // Straddles two months: "Semana del 29 jun–5 jul".
            "Semana del $startDay ${start.format(MONTH_ABBREV)}–$endDay ${end.format(MONTH_ABBREV)}"
        }
    }

    /**
     * The es-MX month-and-year label for the first-of-month [monthStartIso], e.g. "Junio 2026" for
     * 2026-06-01. Capitalised so it reads as a heading on the share card (B-055). Falls back to the
     * raw ISO string if it can't be parsed.
     */
    fun formatMonthLabel(monthStartIso: String): String =
        runCatching {
            LocalDate.parse(monthStartIso, ISO).format(MONTH_YEAR)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(MX) else it.toString() }
        }.getOrDefault(monthStartIso)

    /**
     * The capitalised es-MX weekday name for an ISO date, e.g. "Sábado" for 2026-06-13. Used for the
     * "Mejor día" brag-grid cell on the Tu Mes share card. Falls back to the raw ISO string if it
     * can't be parsed.
     */
    fun formatWeekdayLabel(dayIso: String): String =
        runCatching {
            LocalDate.parse(dayIso, ISO).format(WEEKDAY)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(MX) else it.toString() }
        }.getOrDefault(dayIso)

    /** An hour-of-day block range, e.g. `formatHourRange(18)` → "18:00–19:00". */
    fun formatHourRange(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        val next = (h + 1) % 24
        return "%02d:00–%02d:00".format(h, next)
    }

    /** The kit's 24-hour clock, without a leading hour zero, in the evidence's time zone. */
    /** The semáforo's "desde …" line, one format on every screen: "desde 13:02", "desde ayer 20:31", "desde el sáb 17 oct, 20:31". */
    fun formatSince(epochMs: Long, today: LocalDate, zone: java.time.ZoneId): String {
        val clock = formatClock(epochMs, zone)
        return "desde " + when (val date = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()) {
            today -> clock
            today.minusDays(1) -> "ayer $clock"
            else -> "el ${date.format(DAY_LABEL)}, $clock"
        }
    }

    fun formatClock(epochMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(epochMs)
            .atZone(zone)
            .format(CLOCK)

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", MX)
    private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", MX)
    private val MONTH_ABBREV: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", MX)
    private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", MX)
    private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", MX)
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", MX)
}
