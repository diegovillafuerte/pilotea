package mx.kompara.sync.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [sanitizeUploadFilename] (PR-D3): an untrusted filename can't break/inject the multipart header. */
class UploadFilenameSanitizerTest {

    @Test
    fun `a normal filename is preserved`() {
        assertEquals("Resumen-semanal_2.pdf", sanitizeUploadFilename("Resumen-semanal_2.pdf"))
    }

    @Test
    fun `quotes and backslashes are stripped`() {
        // A naive header build would let a quote close filename="" and inject attributes.
        assertEquals("evil.pdf", sanitizeUploadFilename("\"evil\".pdf")) // "evil".pdf -> evil.pdf
        assertEquals("apdfx", sanitizeUploadFilename("a\"pdf\\x")) // a"pdf\x -> apdfx
    }

    @Test
    fun `CR LF and control chars are stripped`() {
        val injected = "report.pdf\r\nContent-Type: text/html"
        val cleaned = sanitizeUploadFilename(injected)
        assertTrue("no CR", !cleaned.contains('\r'))
        assertTrue("no LF", !cleaned.contains('\n'))
        assertTrue(cleaned.startsWith("report.pdf"))
    }

    @Test
    fun `path separators are dropped`() {
        assertEquals("etcpasswd", sanitizeUploadFilename("/etc/passwd"))
    }

    @Test
    fun `an overlong name is capped`() {
        val long = "a".repeat(5000) + ".pdf"
        assertEquals(MAX_UPLOAD_FILENAME_LEN_FOR_TEST, sanitizeUploadFilename(long).length)
    }

    @Test
    fun `a name that sanitizes to blank falls back to a neutral label`() {
        assertEquals("import", sanitizeUploadFilename("\"\"\r\n"))
        assertEquals("import", sanitizeUploadFilename(""))
    }

    private companion object {
        // Mirrors the (private) MAX_UPLOAD_FILENAME_LEN in ApiClient.kt.
        const val MAX_UPLOAD_FILENAME_LEN_FOR_TEST = 100
    }
}
