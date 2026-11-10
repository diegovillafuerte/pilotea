package mx.kompara.ui.format

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * The es-MX formatter for [pattern], with the app's own short month (`MMM`, `LLL`) and short weekday (`E`, `EE`,
 * `EEE`, all the short weekday in java.time) names: `ene feb mar abr may jun jul ago sep oct nov dic` and
 * `lun mar mié jue vie sáb dom`.
 *
 * The names are the app's own because the locale data that supplies them changes between releases: JDK 25
 * says "sep" for September and JDK 21 says "sept", and Android's ICU data differs between Android releases
 * the same way, so one driver would read "sep" and another "sept". These tables are what JDK 25 prints for
 * es-MX today. Full names (`MMMM`, `LLLL`, `EEEE`) and numbers stay with the platform; they are stable. The
 * narrow forms (`MMMMM`, `LLLLL`, `EEEEE`) and the localized short weekdays (`eee`, `ccc`) are not supplied: no screen uses
 * them. Every short-name date in main code goes through here (`SpanishDateTest` guards the sources).
 */
internal fun spanishDate(pattern: String): DateTimeFormatter {
    val builder = DateTimeFormatterBuilder()
    var start = 0
    for (token in PATTERN_TOKEN.findAll(pattern)) {
        val (field, names) = SHORT_NAMES[token.value] ?: continue
        builder.appendPattern(pattern.substring(start, token.range.first)).appendText(field, names)
        start = token.range.last + 1
    }
    return builder.appendPattern(pattern.substring(start)).toFormatter(Locale.forLanguageTag("es-MX"))
}

/** A quoted literal, or a run of one pattern letter: `MMM` and `E` here are tokens, `MMMM` and `EEEE` are not. */
private val PATTERN_TOKEN = Regex("'(?:[^']|'')*'|([A-Za-z])\\1*")
private val MONTHS = "ene feb mar abr may jun jul ago sep oct nov dic".split(" ")
    .withIndex().associate { (i, name) -> i + 1L to name }
private val WEEKDAYS = "lun mar mié jue vie sáb dom".split(" ")
    .withIndex().associate { (i, name) -> i + 1L to name }
private val SHORT_NAMES = mapOf(
    "MMM" to (ChronoField.MONTH_OF_YEAR to MONTHS),
    "LLL" to (ChronoField.MONTH_OF_YEAR to MONTHS),
    "E" to (ChronoField.DAY_OF_WEEK to WEEKDAYS),
    "EE" to (ChronoField.DAY_OF_WEEK to WEEKDAYS),
    "EEE" to (ChronoField.DAY_OF_WEEK to WEEKDAYS),
)
