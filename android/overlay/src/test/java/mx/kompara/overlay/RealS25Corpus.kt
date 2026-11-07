package mx.kompara.overlay

import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.parsers.model.HostRect
import org.json.JSONObject

/**
 * The sanitized S25 real-offer corpora and their literal oracles, read from the single repository copy
 * in :ocr's test resources (the overlay test source set includes that directory). Every address and
 * name in them is invented; numeric/label tokens and bounds are verbatim device OCR.
 * [groups]: 2026-10-30 and 2026-11-06 morning (190 frames). [pmGroups]: 2026-11-06 17:42–17:46, the
 * 0.2.15 run with the hour cards (126 frames). [lateGroups]: 2026-11-06 18:09–18:12, one group per card
 * appearance (60 frames): the hour-only `1 h` TOTAL, three more hour cards and the $78.08 Priority.
 */
internal object RealS25Corpus {
    private const val DIR = "/uber/s25/2026-11-06/"
    private const val PM_DIR = "/uber/s25/2026-11-06-pm/"
    private const val LATE_DIR = "/uber/s25/2026-11-06-late/"

    data class Frame(val group: Int, val index: Int, val tMs: Long, val signature: Boolean, val chip: HostRect?,
        val blocks: List<OcrBlock>, val capturedAtMs: Long? = null) {
        val id get() = "G$group[$index]"
    }

    /** One card appearance: fare, pickup and trip (minutes, km) for its inclusive frame range. */
    data class Appearance(val group: Int, val ordinal: Int, val from: Int, val to: Int, val fare: Double,
        val pickup: Pair<Double, Double>, val trip: Pair<Double, Double>, val card: String? = null) {
        val id get() = "G${group}a$ordinal"
        operator fun contains(index: Int) = index in from..to
    }

    data class Group(val group: Int, val frames: List<Frame>, val appearances: List<Appearance>) {
        fun appearance(index: Int) = appearances.singleOrNull { index in it }
    }

    val groups: List<Group> by lazy { load(DIR) }
    val pmGroups: List<Group> by lazy { load(PM_DIR) }
    val lateGroups: List<Group> by lazy { load(LATE_DIR) }

    private fun load(dir: String): List<Group> {
        val oracle = JSONObject(text(dir, "oracle.json")).getJSONArray("groups")
        return List(oracle.length()) { gi ->
            val g = oracle.getJSONObject(gi)
            val number = g.getInt("group")
            val frames = JSONObject(text(dir, g.getString("file"))).getJSONArray("frames").let { a ->
                List(a.length()) { i -> a.getJSONObject(i).let { o ->
                    val blocks = o.getJSONArray("blocks").let { b -> List(b.length()) { b.getJSONObject(it).let { x ->
                        OcrBlock(x.getString("text"), OcrBounds(x.getInt("left"), x.getInt("top"), x.getInt("right"), x.getInt("bottom")))
                    } } }
                    val chip = o.optJSONObject("chipRect")?.let { HostRect(it.getInt("left"), it.getInt("top"), it.getInt("right"), it.getInt("bottom")) }
                    Frame(number, i, o.getLong("tMs"), o.getBoolean("signature"), chip, blocks,
                        if (o.has("capturedAtMs")) o.getLong("capturedAtMs") else null)
                } }
            }
            val apps = g.getJSONArray("appearances").let { a -> List(a.length()) { k -> a.getJSONObject(k).let { o ->
                fun pair(key: String) = o.getJSONArray(key).let { it.getDouble(0) to it.getDouble(1) }
                val range = o.getJSONArray("frames")
                Appearance(number, k, range.getInt(0), range.getInt(1), o.getDouble("fare"), pair("pickup"), pair("trip"),
                    o.optString("card").ifEmpty { null })
            } } }
            Group(number, frames, apps)
        }
    }

    private fun text(dir: String, name: String) = requireNotNull(javaClass.getResource(dir + name)) { "missing $name" }.readText()
}
