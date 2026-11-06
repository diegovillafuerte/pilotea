package mx.kompara.overlay

import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.parsers.model.HostRect
import org.json.JSONObject

/**
 * The sanitized S25 real-offer corpus and its literal oracle, read from the single repository copy
 * in :ocr's test resources (the overlay test source set includes that directory). Every address and
 * name in it is invented; numeric/label tokens and bounds are verbatim device OCR.
 */
internal object RealS25Corpus {
    private const val DIR = "/uber/s25/2026-11-06/"

    data class Frame(val group: Int, val index: Int, val tMs: Long, val signature: Boolean, val chip: HostRect?,
        val blocks: List<OcrBlock>) {
        val id get() = "G$group[$index]"
    }

    /** One card appearance: fare, pickup and trip (minutes, km) for its inclusive frame range. */
    data class Appearance(val group: Int, val ordinal: Int, val from: Int, val to: Int, val fare: Double,
        val pickup: Pair<Double, Double>, val trip: Pair<Double, Double>) {
        val id get() = "G${group}a$ordinal"
        operator fun contains(index: Int) = index in from..to
    }

    data class Group(val group: Int, val frames: List<Frame>, val appearances: List<Appearance>) {
        fun appearance(index: Int) = appearances.singleOrNull { index in it }
    }

    val groups: List<Group> by lazy {
        val oracle = JSONObject(text("oracle.json")).getJSONArray("groups")
        List(oracle.length()) { gi ->
            val g = oracle.getJSONObject(gi)
            val number = g.getInt("group")
            val frames = JSONObject(text(g.getString("file"))).getJSONArray("frames").let { a ->
                List(a.length()) { i -> a.getJSONObject(i).let { o ->
                    val blocks = o.getJSONArray("blocks").let { b -> List(b.length()) { b.getJSONObject(it).let { x ->
                        OcrBlock(x.getString("text"), OcrBounds(x.getInt("left"), x.getInt("top"), x.getInt("right"), x.getInt("bottom")))
                    } } }
                    val chip = o.optJSONObject("chipRect")?.let { HostRect(it.getInt("left"), it.getInt("top"), it.getInt("right"), it.getInt("bottom")) }
                    Frame(number, i, o.getLong("tMs"), o.getBoolean("signature"), chip, blocks)
                } }
            }
            val apps = g.getJSONArray("appearances").let { a -> List(a.length()) { k -> a.getJSONObject(k).let { o ->
                fun pair(key: String) = o.getJSONArray(key).let { it.getDouble(0) to it.getDouble(1) }
                val range = o.getJSONArray("frames")
                Appearance(number, k, range.getInt(0), range.getInt(1), o.getDouble("fare"), pair("pickup"), pair("trip"))
            } } }
            Group(number, frames, apps)
        }
    }

    private fun text(name: String) = requireNotNull(javaClass.getResource(DIR + name)) { "missing $name" }.readText()
}
