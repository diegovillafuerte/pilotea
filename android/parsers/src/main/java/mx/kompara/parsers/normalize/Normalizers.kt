package mx.kompara.parsers.normalize

/**
 * Unit normalizers for es-MX host-app text (android-technical-design.md §1). Pure functions: a
 * raw string in, a typed value (or `null` on no-parse) out — never throw.
 *
 * Mexican locale typically uses `,` as the thousands separator and `.` as the decimal point
 * (`$1,234.56`), but some surfaces — and inDrive / DiDi screens — render the European style
 * (`1.234,56`). [parseNumberEsMx] detects which convention a token uses and guards against
 * mis-reading `1.234` as `1234` versus `1.234`.
 */
enum class Normalizer {
    /** Money or bare number → Double (MXN). Strips currency symbols and grouping separators. */
    CURRENCY,

    /** Bare number → Double, locale-aware grouping/decimal handling. */
    NUMBER,

    /** Distance string ("3.2 km", "850 m") → kilometers. */
    DISTANCE_KM,

    /** Duration string ("12 min", "1 h 5 min", "90 s") → minutes. */
    DURATION_MIN,

    /** Pass the raw text through unchanged (still usable as a key in `raw`). */
    NONE,
    ;

    companion object {
        /**
         * Apply a normalizer, returning a Double for numeric kinds or `null` when nothing parses.
         * [NONE] always returns `null` (it carries no numeric value — the raw text is kept in
         * the card's `raw` map instead).
         */
        fun applyNumeric(normalizer: Normalizer, raw: String): Double? = when (normalizer) {
            CURRENCY, NUMBER -> parseNumberEsMx(raw)
            DISTANCE_KM -> parseDistanceKm(raw)
            DURATION_MIN -> parseDurationMin(raw)
            NONE -> null
        }
    }
}

private val LEADING_NUMBER = Regex("""[-+]?[0-9][0-9.,   ]*[0-9]|[-+]?[0-9]""")

/**
 * Parse a number written in either es-MX (`1,234.56`) or European (`1.234,56`) grouping. Returns
 * `null` if no number is present.
 *
 * Strategy: isolate the first number-shaped run, then decide which of `.`/`,` is the decimal
 * separator by looking at the *last* separator in the token (the decimal always comes last) and
 * how many digits follow it. Three trailing digits after a single separator (`1.234`, `1,234`)
 * is treated as grouping, not a decimal, to avoid the classic `$1.234` → `1.234` bug.
 */
fun parseNumberEsMx(raw: String): Double? {
    val match = LEADING_NUMBER.find(raw)?.value ?: return null
    // Normalize exotic spaces used as thousands separators, then drop spaces entirely.
    var token = match.replace(' ', ' ').replace(' ', ' ').replace(" ", "")
    val sign = if (token.startsWith("-")) -1.0 else 1.0
    token = token.trimStart('+', '-')
    if (token.isEmpty()) return null

    val lastDot = token.lastIndexOf('.')
    val lastComma = token.lastIndexOf(',')

    val normalized: String = when {
        lastDot == -1 && lastComma == -1 -> token
        // Only one kind of separator present.
        lastComma == -1 -> resolveSingleSeparator(token, '.')
        lastDot == -1 -> resolveSingleSeparator(token, ',')
        // Both present: the later one is the decimal separator, the other is grouping.
        lastDot > lastComma -> token.replace(",", "") // 1,234.56 (es-MX)
        else -> token.replace(".", "").replace(',', '.') // 1.234,56 (European)
    }
    return normalized.toDoubleOrNull()?.let { it * sign }
}

/**
 * Decide whether a lone separator is a decimal point or a thousands separator. A single separator
 * followed by exactly three digits and no other separators is grouping (`1.234` -> 1234); anything
 * else (`12.5`, `1.2345`, `0.5`) is a decimal.
 */
private fun resolveSingleSeparator(token: String, sep: Char): String {
    val idx = token.indexOf(sep)
    val occurrences = token.count { it == sep }
    val trailing = token.length - idx - 1
    val grouping = occurrences > 1 || (occurrences == 1 && trailing == 3 && idx > 0)
    return if (grouping) token.replace(sep.toString(), "") else token.replace(sep, '.')
}

private val KM_HINT = Regex("""\bkm\b|kil[oó]metro""", RegexOption.IGNORE_CASE)
private val METER_HINT = Regex("""\bm\b|\bmts?\b|metro""", RegexOption.IGNORE_CASE)

/** "3.2 km" -> 3.2, "850 m" -> 0.85, "1.2 km" -> 1.2. Meters convert to km. */
fun parseDistanceKm(raw: String): Double? {
    val value = parseNumberEsMx(raw) ?: return null
    return when {
        KM_HINT.containsMatchIn(raw) -> value
        METER_HINT.containsMatchIn(raw) -> value / 1000.0
        else -> value // assume km when unitless
    }
}

private val HOURS = Regex("""(\d+)\s*(?:h|hr|hrs|hora|horas)\b""", RegexOption.IGNORE_CASE)
private val MINUTES = Regex("""(\d+)\s*(?:min|mins|m|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
private val SECONDS = Regex("""(\d+)\s*(?:s|seg|segundo|segundos)\b""", RegexOption.IGNORE_CASE)

/**
 * "12 min" -> 12.0, "1 h 5 min" -> 65.0, "2 h" -> 120.0, "90 s" -> 1.5. When no unit token is
 * present, falls back to interpreting the bare number as minutes.
 */
fun parseDurationMin(raw: String): Double? {
    var total = 0.0
    var matched = false
    HOURS.find(raw)?.let { total += it.groupValues[1].toDouble() * 60.0; matched = true }
    MINUTES.find(raw)?.let { total += it.groupValues[1].toDouble(); matched = true }
    SECONDS.find(raw)?.let { total += it.groupValues[1].toDouble() / 60.0; matched = true }
    if (matched) return total
    // No unit token — treat the leading number as minutes.
    return parseNumberEsMx(raw)
}
