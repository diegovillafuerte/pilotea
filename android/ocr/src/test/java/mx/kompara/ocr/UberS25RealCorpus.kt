package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * The sanitized S25 real-offer corpus (2026-10-30 and 2026-11-06, 13 groups, 190 frames): numeric and
 * label tokens and bounds are verbatim device OCR, every address/name is invented. Frames keep their
 * array index, tMs, block order, bounds and recorded chip rectangle. [Oracle] is the literal spec data
 * in oracle.json, never derived from the parser under test.
 */
internal object UberS25RealCorpus {
    const val DIR = "/uber/s25/2026-11-06/"

    data class Frame(val group: Int, val index: Int, val tMs: Long, val signature: Boolean, val chip: ScreenRect?,
        val blocks: List<OcrBlock>, val legacy: Legacy?) {
        val id get() = "G$group[$index]"
        /** Production masking: the pipeline drops our own chip before any parser sees the frame. */
        val masked get() = ChipMask.maskOwnChip(blocks, chip)
    }

    /** What 0.2.14 recorded for the frame (`parsed_0_2_14`). */
    data class Legacy(val fare: Double?, val pickup: List<Double?>, val trip: List<Double?>, val action: String?)

    data class Appearance(val from: Int, val to: Int, val fare: Double, val total: Pair<Int, Double>, val rate: Double,
        val pickup: Pair<Int, Double>, val trip: Pair<Int, Double>) {
        operator fun contains(index: Int) = index in from..to
    }

    data class Group(val group: Int, val file: String, val frames: List<Frame>, val appearances: List<Appearance>,
        val signatureFree: Set<Int>, val legacyFareRead: Set<Int>, val legacyTripRead: Set<Int>,
        /** Frame index -> slot code (F, R, TM, TK, PM, PK, QM, QK) -> non-READ provenance. */
        val provenance: Map<Int, Map<String, String>>) {
        fun appearance(index: Int) = appearances.singleOrNull { index in it }
    }

    val groups: List<Group> by lazy { load() }

    fun text(path: String) = requireNotNull(javaClass.getResource(DIR + path)) { "missing $path" }.readText()

    private fun load(): List<Group> {
        val oracle = JSONObject(text("oracle.json"))
        val list = oracle.getJSONArray("groups")
        return List(list.length()) { gi ->
            val g = list.getJSONObject(gi)
            val number = g.getInt("group")
            val json = JSONObject(text(g.getString("file"))).getJSONArray("frames")
            val frames = List(json.length()) { i -> frame(number, i, json.getJSONObject(i)) }
            require(frames.size == g.getInt("frames")) { "G$number frame count" }
            val apps = g.getJSONArray("appearances").let { a ->
                List(a.length()) { a.getJSONObject(it).let { o ->
                    val range = o.getJSONArray("frames")
                    Appearance(range.getInt(0), range.getInt(1), o.getDouble("fare"), o.pair("total"),
                        o.getDouble("rate"), o.pair("pickup"), o.pair("trip"))
                } }
            }
            val legacy = g.getJSONObject("legacy_0_2_14")
            val provenance = mutableMapOf<Int, MutableMap<String, String>>()
            g.getJSONArray("provenance").let { p ->
                for (k in 0 until p.length()) {
                    val entry = p.getJSONObject(k)
                    val fields = entry.getJSONObject("fields")
                    for (i in ranges(entry.getJSONArray("frames"))) for (key in fields.keys())
                        provenance.getOrPut(i) { mutableMapOf() }[key] = fields.getString(key)
                }
            }
            Group(number, g.getString("file"), frames, apps, ints(g.getJSONArray("signatureFree")),
                ranges(legacy.getJSONArray("fareRead")), ranges(legacy.getJSONArray("tripRead")), provenance)
        }
    }

    private fun frame(group: Int, index: Int, o: JSONObject): Frame {
        val blocks = o.getJSONArray("blocks").let { b ->
            List(b.length()) { b.getJSONObject(it).let { x ->
                OcrBlock(x.getString("text"), OcrBounds(x.getInt("left"), x.getInt("top"), x.getInt("right"), x.getInt("bottom")))
            } }
        }
        val chip = o.optJSONObject("chipRect")?.let { ScreenRect(it.getInt("left"), it.getInt("top"), it.getInt("right"), it.getInt("bottom")) }
        // Signature-free frames recorded no parse (all fields null).
        val legacy = o.optJSONObject("parsed_0_2_14")?.takeUnless { it.isNull("pickup") }?.let { p ->
            fun nums(key: String) = p.getJSONArray(key).let { a -> List(a.length()) { if (a.isNull(it)) null else a.getDouble(it) } }
            Legacy(if (p.isNull("fare")) null else p.getDouble("fare"), nums("pickup"), nums("trip"),
                if (p.isNull("action")) null else p.getString("action"))
        }
        return Frame(group, index, o.getLong("tMs"), o.getBoolean("signature"), chip, blocks, legacy)
    }

    private fun JSONObject.pair(key: String) = getJSONArray(key).let { it.getInt(0) to it.getDouble(1) }
    private fun ints(a: JSONArray) = (0 until a.length()).map { a.getInt(it) }.toSet()
    private fun ranges(a: JSONArray) = (0 until a.length()).flatMap { a.getJSONArray(it).let { r -> r.getInt(0)..r.getInt(1) } }.toSet()

    /** Original order, reversed, and ten seeded shuffles. */
    fun orders(blocks: List<OcrBlock>): List<Pair<String, List<OcrBlock>>> =
        listOf("original" to blocks, "reversed" to blocks.reversed()) +
            (1..10).map { seed -> "shuffle$seed" to blocks.shuffled(Random(seed)) }
}
