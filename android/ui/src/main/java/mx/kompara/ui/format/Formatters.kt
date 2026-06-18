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
 * a driver in Mexico always sees "$1,234.56", "12.3 km", "$185.50/h".
 */
object Formatters {
    private val MX: Locale = Locale.forLanguageTag("es-MX")

    /**
     * Pesos with the Mexican peso sign and two decimals, e.g. `formatMxn(1234.56)` → "$1,234.56".
     * Uses a plain "$" prefix (not "MX$") since the whole app is single-currency.
     */
    fun formatMxn(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$" + nf.format(amount)
    }

    /**
     * Pesos rounded to whole pesos (no cents), e.g. `formatMxnWhole(4200.49)` → "$4,200". Used by
     * the Comparar benchmark table where cents are noise and long numbers wrapped (S-024).
     */
    fun formatMxnWhole(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
        return "$" + nf.format(amount)
    }

    /**
     * Distance in kilometres with one decimal and a "km" suffix, e.g. `formatKm(12.34)` → "12.3 km".
     */
    fun formatKm(km: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return nf.format(km) + " km"
    }

    /**
     * A per-hour rate in pesos, e.g. `formatPerHour(185.5)` → "$185.50/h". Reuses [formatMxn] so
     * the money part stays consistent.
     */
    fun formatPerHour(amountPerHour: Double): String = formatMxn(amountPerHour) + "/h"

    /**
     * A per-hour rate rounded to whole pesos for glanceable surfaces (the overlay chip), e.g.
     * `formatPerHourWhole(185.5)` → "$186/h". Cent precision is noise at a one-second read.
     */
    fun formatPerHourWhole(amountPerHour: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
        return "$" + nf.format(amountPerHour) + "/h"
    }

    /**
     * A money-per-km rate, e.g. `formatPerKm(8.4)` → "$8.40/km". The $/viaje and $/km cards use this
     * shape (with a "/viaje" or "/km" suffix the caller picks via [formatMxn] when needed).
     */
    fun formatPerKm(amountPerKm: Double): String = formatMxn(amountPerKm) + "/km"

    /** A money-per-km rate with one decimal, e.g. `formatPerKmOneDecimal(8.43)` → "$8.4/km" (S-024). */
    fun formatPerKmOneDecimal(amountPerKm: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return "$" + nf.format(amountPerKm) + "/km"
    }

    /** A count-per-hour rate with one decimal, e.g. `formatPerHourCount(2.34)` → "2.3/h". */
    fun formatPerHourCount(countPerHour: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return nf.format(countPerHour) + "/h"
    }

    /** A 0..1 fraction as a whole-number percent, e.g. `formatPercent(0.834)` → "83 %". */
    fun formatPercent(fraction: Double): String {
        val pct = (fraction.coerceIn(0.0, 1.0) * 100).toInt()
        return "$pct %"
    }

    /** Hours with one decimal and an "h" suffix, e.g. `formatHours(7.5)` → "7.5 h". */
    fun formatHours(hours: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return nf.format(hours) + " h"
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

    /** A clock time (HH:mm) for a shift bound, in the device-local rendering of [epochMs]. */
    fun formatClock(epochMs: Long): String =
        java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
            .format(CLOCK)

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", MX)
    private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", MX)
    private val MONTH_ABBREV: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", MX)
    private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", MX)
    private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", MX)
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", MX)
}
