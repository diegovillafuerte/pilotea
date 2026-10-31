package mx.kompara.ocr

import mx.kompara.capture.HostWindowOwnership
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/** TEMP DIAG recorder: debug-only offer frames with ownership provenance; other windows stay private. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferFixtureRecorderTest {
    private val display = HostRect(0, 0, 1080, 2340)
    private val notification = HostRect(0, 91, 1080, 300)

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { i -> json.getJSONObject(i).let {
            OcrBlock(it.getString("text"), OcrBounds(it.getInt("left"), it.getInt("top"), it.getInt("right"), it.getInt("bottom")))
        } }.filter { it.bounds.top >= 600 }
    }

    private fun run(record: Boolean, frames: List<List<OcrBlock>>): java.io.File {
        val dir = Files.createTempDirectory("fixtures").toFile()
        val pipeline = OfferFramePipeline(recordFixtures = record)
        frames.forEachIndexed { index, blocks ->
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(900))
            val at = 1_000_000L + index * 900
            pipeline.process(blocks, ScreenRect(36, 139, 756, 475), dir, capturedAtMs = at, displayBounds = display,
                density = 3f, foregroundPackage = UberOcrParser.UBER_PACKAGE,
                windowOwnership = HostWindowOwnership(UberOcrParser.UBER_PACKAGE, at, display, 1, display,
                    listOf(notification)))
        }
        return dir
    }

    // Round 2 (B4): a frame of a present session is always recorded (R8: the frames that decide
    // a soft hide or absence), so the signature-free frame between the two offers is kept too.
    @Test fun `debug recorder writes one provenance file per session frame and redacts other windows`() {
        val private = OcrBlock("Mensaje privado de prueba", OcrBounds(40, 120, 900, 200))
        val idle = listOf(OcrBlock("Buscando viajes", OcrBounds(240, 1940, 840, 2030)))
        val dir = run(true, listOf(fixture() + private, idle, fixture() + private))
        val files = java.io.File(dir, "ocr-fixtures").listFiles().orEmpty().sortedBy { it.name }
        assertEquals("The two signature frames and the session's signature-free frame",
            listOf("ocr_1000000.json", "ocr_1000900.json", "ocr_1001800.json"), files.map { it.name })
        assertFalse(JSONObject(files[1].readText()).getBoolean("signature"))
        val json = JSONObject(files.first().readText())
        for (key in listOf("capturedAtMs", "host", "chipRect", "ownership", "raw", "owned", "parsed", "signature",
            "decision", "shownFare")) assertTrue(key, json.has(key))
        assertTrue(json.getBoolean("signature"))
        assertEquals(1_000_000L, json.getLong("capturedAtMs"))
        assertEquals(1, json.getJSONObject("ownership").getJSONArray("excludedBounds").length())
        val raw = json.getJSONArray("raw")
        val redacted = (0 until raw.length()).map { raw.getJSONObject(it) }.filter { it.has("redacted") }
        assertEquals("The notification block is bounds only", 1, redacted.size)
        assertFalse(redacted.single().has("text"))
        assertFalse(files.first().readText().contains("Mensaje privado"))
        val owned = json.getJSONArray("owned")
        assertTrue((0 until owned.length()).all { owned.getJSONObject(it).has("text") })
        assertEquals("CONFIDENT", json.getJSONObject("decision").getString("confidence"))
    }

    @Test fun `a session-free lookalike is recorded at most once per five seconds`() {
        // The idle Uber home: an earnings pill, a surge pill and a wait pill, but no legs.
        val home = listOf(OcrBlock("$245.00", OcrBounds(380, 120, 700, 200)),
            OcrBlock("+$15", OcrBounds(100, 900, 260, 960)), OcrBlock("5-10 min", OcrBounds(380, 1900, 700, 1960)))
        val dir = run(true, List(12) { home })
        val files = java.io.File(dir, "ocr-fixtures").listFiles().orEmpty().map { it.name }.sorted()
        assertEquals("12 frames over 9.9 s: frames 0, 6 (5.4 s later)",
            listOf("ocr_1000000.json", "ocr_1005400.json"), files)
    }

    @Test fun `pruning keeps the newest fixture files and never throws`() {
        val dir = Files.createTempDirectory("fixtures").toFile()
        val fixtures = java.io.File(dir, "ocr-fixtures").apply { mkdirs() }
        listOf(5L, 40L, 300L, 2_000L, 1_000L).forEach { java.io.File(fixtures, "ocr_$it.json").writeText("{}") }
        OfferFramePipeline.pruneFixtures(dir, keep = 3)
        assertEquals(listOf("ocr_1000.json", "ocr_2000.json", "ocr_300.json"),
            fixtures.listFiles().orEmpty().map { it.name }.sorted())
        OfferFramePipeline.pruneFixtures(java.io.File(dir, "missing"), keep = 3)
    }

    @Test fun `nothing is written when the debug flag is off`() {
        val dir = run(false, listOf(fixture(), fixture()))
        assertTrue(java.io.File(dir, "ocr-fixtures").listFiles().isNullOrEmpty())
    }
}
