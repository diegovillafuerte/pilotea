package mx.kompara.ocr

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ten sanitized June-2026 S25 legacy Uber frames whose trip lasts an hour or more, with a literal oracle. */
internal object UberJuneHourFrames {
    private const val DIR = "/uber/june-hours/"

    data class Expected(val file: String, val tripMin: Double, val tripKm: Double)

    val expected: List<Expected> by lazy {
        JSONObject(text("oracle.json")).getJSONArray("frames").let { a ->
            List(a.length()) { a.getJSONObject(it).let { o ->
                val trip = o.getJSONArray("trip")
                Expected(o.getString("file"), trip.getDouble(0), trip.getDouble(1))
            } }
        }
    }

    fun blocks(file: String): List<OcrBlock> = JSONArray(text(file)).let { a ->
        List(a.length()) { a.getJSONObject(it).let { x ->
            OcrBlock(x.getString("text"), OcrBounds(x.getInt("left"), x.getInt("top"), x.getInt("right"), x.getInt("bottom")))
        } }
    }

    private fun text(name: String) = requireNotNull(javaClass.getResource(DIR + name)) { "missing $name" }.readText()
}

/**
 * The legacy labeled route has dropped the hour since June (`Viaje: 1 h 18 min` booked 18): the
 * suffix search read the trailing minutes. Each frame keeps the legacy path (no TOTAL row, no
 * validation) and now books 60·H + M with its distance unchanged, in every block order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UberJuneHourFramesTest {
    private val parser = UberOcrParser()

    @Test fun `every June hour frame books its whole trip duration on the legacy path`() {
        val failures = mutableListOf<String>()
        for (e in UberJuneHourFrames.expected) {
            val blocks = UberJuneHourFrames.blocks(e.file)
            for ((name, order) in UberS25RealCorpus.orders(blocks)) {
                val card = parser.parse(order, hostConfirmed = true)
                if (card == null) { failures += "${e.file} $name: no card"; continue }
                if (card.tripDurationMin != e.tripMin || card.tripDistanceKm != e.tripKm)
                    failures += "${e.file} $name: trip ${card.tripDurationMin} / ${card.tripDistanceKm}"
                if (card.validation != null) failures += "${e.file} $name: left the legacy path"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
