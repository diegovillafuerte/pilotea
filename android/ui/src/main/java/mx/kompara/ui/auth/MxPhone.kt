package mx.kompara.ui.auth

/**
 * Pure normalization of Mexican phone numbers into the E.164 form the backend expects
 * (`+52` + 10 digits). Accepts what drivers actually type: bare 10 digits, with spaces/dashes/
 * parentheses, or with a `+52`/`52`/legacy `+521` mobile prefix. Anything else is rejected
 * (returns null) so the UI can ask for a correction instead of sending a doomed OTP request.
 */
object MxPhone {

    /** A Mexican subscriber number is exactly 10 digits (area code + line). */
    const val LOCAL_DIGITS: Int = 10

    /** Normalize [raw] to "+52XXXXXXXXXX", or null when it isn't a plausible MX number. */
    fun normalizeOrNull(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        val local = when {
            digits.length == LOCAL_DIGITS -> digits
            // "52" country prefix typed in (with or without the +, already stripped).
            digits.length == LOCAL_DIGITS + 2 && digits.startsWith("52") -> digits.drop(2)
            // Legacy "+52 1" mobile dialing prefix (retired 2019, still in many address books).
            digits.length == LOCAL_DIGITS + 3 && digits.startsWith("521") -> digits.drop(3)
            else -> return null
        }
        return "+52$local"
    }

    /** Pretty local form for display, e.g. "+52 55 1234 5678" from a normalized E.164 string. */
    fun display(e164: String): String {
        val local = e164.removePrefix("+52")
        if (local.length != LOCAL_DIGITS) return e164
        return "+52 ${local.substring(0, 2)} ${local.substring(2, 6)} ${local.substring(6)}"
    }
}
