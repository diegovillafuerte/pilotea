package mx.kompara.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short month and weekday names are the app's own on every JVM and Android release (`spanishDate`). */
class SpanishDateTest {
    private val wednesday = LocalDate.parse("2026-09-16")

    @Test fun `short months and weekdays are the app's own names`() {
        val months = (1..12).map { LocalDate.of(2026, it, 1) }
        assertEquals("ene feb mar abr may jun jul ago sep oct nov dic", months.joinToString(" ") { it.format(spanishDate("MMM")) })
        assertEquals("ene feb mar abr may jun jul ago sep oct nov dic", months.joinToString(" ") { it.format(spanishDate("LLL")) })
        val week = (0L..6L).map { LocalDate.parse("2026-11-09").plusDays(it) }
        assertEquals("lun mar mié jue vie sáb dom", week.joinToString(" ") { it.format(spanishDate("EEE")) })
        // In java.time E, EE and EEE all mean the short weekday.
        for (short in listOf("E", "EE")) assertEquals(week.map { it.format(spanishDate("EEE")) }, week.map { it.format(spanishDate(short)) })
    }

    @Test fun `the app's patterns keep full names and numbers from the platform`() {
        assertEquals("miércoles 16 sep", wednesday.format(spanishDate("EEEE d MMM")))
        assertEquals("mié 16 sep", wednesday.format(spanishDate("EEE d MMM")))
        assertEquals("16 sep 2026, 08:05", LocalDateTime.parse("2026-09-16T08:05").format(spanishDate("d MMM yyyy, HH:mm")))
        val full = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-MX"))
        assertEquals(wednesday.format(full), wednesday.format(spanishDate("d 'de' MMMM")))
        assertEquals("16 de septiembre", wednesday.format(spanishDate("d 'de' MMMM")))
    }

    /** A date added later with `ofPattern("d MMM", …)` would pass every other test until September. */
    @Test fun `no main source takes a short or narrow month or weekday name from the platform`() {
        val android = File("..").canonicalFile
        val helper = File(android, "ui/src/main/java/mx/kompara/ui/format/SpanishDate.kt")
        val sources = android.listFiles().orEmpty().flatMap { File(it, "src/main").walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
        assertTrue("Missing $helper", helper in sources)
        val literal = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
        val token = Regex("'(?:[^']|'')*'|([A-Za-z])\\1*")
        val checks = listOf(
            Regex("\\b(?:ofPattern|appendPattern|applyPattern|SimpleDateFormat)\\s*\\(") to (PLATFORM_NAMES to "with the platform's short or narrow names; use spanishDate"),
            Regex("\\bspanishDate\\s*\\(") to (UNSUPPLIED to "with a name spanishDate does not supply"),
        )
        for (file in sources - helper) {
            val text = file.readText()
            fun where(at: Int) = "${file.relativeTo(android)}:${text.substring(0, at).count { it == '\n' } + 1}"
            for ((call, rule) in checks) for (match in call.findAll(text)) {
                literal.findAll(arguments(text, match.range.last)).filter { pattern -> token.findAll(pattern.value).any { it.value in rule.first } }
                    .forEach { fail("${where(match.range.first)} formats ${it.value} ${rule.second}") }
            }
            Regex("TextStyle\\.(?:SHORT|NARROW)").find(text)?.let { fail("${where(it.range.first)} takes a short name from the platform (${it.value}); use spanishDate") }
        }
    }

    /** The text between the parenthesis at [open] and its match, string contents included. */
    private fun arguments(text: String, open: Int): String {
        var depth = 0
        var quoted = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '\\' -> i++
                c == '"' -> quoted = !quoted
                !quoted && c == '(' -> depth++
                !quoted && c == ')' -> if (--depth == 0) return text.substring(open + 1, i)
            }
            i++
        }
        return text.substring(open + 1)
    }

    private companion object {
        /** Narrow names and the localized short weekdays: spanishDate does not supply them, so no pattern may use them. */
        val UNSUPPLIED = setOf("eee", "ccc", "MMMMM", "LLLLL", "EEEEE", "eeeee", "ccccc")
        /** Every short or narrow month or weekday name a platform pattern would take from the locale data. */
        val PLATFORM_NAMES = setOf("MMM", "LLL", "E", "EE", "EEE") + UNSUPPLIED
    }
}
