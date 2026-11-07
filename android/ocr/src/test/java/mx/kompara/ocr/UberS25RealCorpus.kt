package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * The sanitized S25 real-offer corpora: numeric and label tokens and bounds are verbatim device OCR,
 * every address/name is invented. Frames keep their array index, tMs, block order, bounds and recorded
 * chip rectangle. Each corpus's [Group] data is the literal spec data in its oracle.json, never derived
 * from the parser under test.
 * - [groups]: 2026-10-30 and 2026-11-06 morning (0.2.14 recorder), 13 groups, 190 frames.
 * - [pmGroups]: 2026-11-06 17:42–17:46 (0.2.15 recorder), 10 groups, 126 frames, the hour cards.
 * - [lateGroups]: 2026-11-06 18:09–18:12 (0.2.15 recorder), one group per card appearance, 60 frames: the
 *   hour-only `1 h` TOTAL (H0), three more hour cards and the $78.08 Priority with a capital `H ` icon.
 */
internal object UberS25RealCorpus {
    const val DIR = "/uber/s25/2026-11-06/"
    const val PM_DIR = "/uber/s25/2026-11-06-pm/"
    const val LATE_DIR = "/uber/s25/2026-11-06-late/"

    data class Frame(val group: Int, val index: Int, val tMs: Long, val signature: Boolean, val chip: ScreenRect?,
        val blocks: List<OcrBlock>, val legacy: Legacy?, val capturedAtMs: Long? = null) {
        val id get() = "G$group[$index]"
        /** Production masking: the pipeline drops our own chip before any parser sees the frame. */
        val masked get() = ChipMask.maskOwnChip(blocks, chip)
    }

    /** What 0.2.14 recorded for the frame (`parsed_0_2_14`). */
    data class Legacy(val fare: Double?, val pickup: List<Double?>, val trip: List<Double?>, val action: String?)

    data class Appearance(val from: Int, val to: Int, val fare: Double, val total: Pair<Int, Double>, val rate: Double,
        val pickup: Pair<Int, Double>, val trip: Pair<Int, Double>, val card: String? = null) {
        operator fun contains(index: Int) = index in from..to
    }

    /**
     * A signature frame of an appearance whose rows are obscured: the literal values it publishes (null
     * when unread), its grade — null on the legacy path (no TOTAL row, no validation) — its payment basis,
     * and the validation slots (code -> units / non-READ provenance) it checks.
     */
    data class Incomplete(val fare: Double?, val pickup: List<Double?>, val trip: List<Double?>, val grade: String?,
        val units: Map<String, Long>, val provenance: Map<String, String>, val paymentBasis: String = "DRIVER_PAYMENT")

    data class Group(val group: Int, val file: String, val frames: List<Frame>, val appearances: List<Appearance>,
        val signatureFree: Set<Int>, val legacyFareRead: Set<Int>, val legacyTripRead: Set<Int>,
        /** Frame index -> slot code (F, R, TM, TK, PM, PK, QM, QK) -> non-READ provenance. */
        val provenance: Map<Int, Map<String, String>>, val incomplete: Map<Int, Incomplete> = emptyMap()) {
        fun appearance(index: Int) = appearances.singleOrNull { index in it }
    }

    val groups: List<Group> by lazy { load(DIR) }
    val pmGroups: List<Group> by lazy { load(PM_DIR) }
    val lateGroups: List<Group> by lazy { load(LATE_DIR) }

    fun text(path: String, dir: String = DIR) = requireNotNull(javaClass.getResource(dir + path)) { "missing $path" }.readText()

    private fun load(dir: String): List<Group> {
        val oracle = JSONObject(text("oracle.json", dir))
        val list = oracle.getJSONArray("groups")
        return List(list.length()) { gi ->
            val g = list.getJSONObject(gi)
            val number = g.getInt("group")
            val json = JSONObject(text(g.getString("file"), dir)).getJSONArray("frames")
            val frames = List(json.length()) { i -> frame(number, i, json.getJSONObject(i)) }
            require(frames.size == g.getInt("frames")) { "G$number frame count" }
            val apps = g.getJSONArray("appearances").let { a ->
                List(a.length()) { a.getJSONObject(it).let { o ->
                    val range = o.getJSONArray("frames")
                    Appearance(range.getInt(0), range.getInt(1), o.getDouble("fare"), o.pair("total"),
                        o.getDouble("rate"), o.pair("pickup"), o.pair("trip"), o.optString("card").ifEmpty { null })
                } }
            }
            val legacy = g.optJSONObject("legacy_0_2_14")
            val provenance = mutableMapOf<Int, MutableMap<String, String>>()
            g.getJSONArray("provenance").let { p ->
                for (k in 0 until p.length()) {
                    val entry = p.getJSONObject(k)
                    val fields = entry.getJSONObject("fields")
                    for (i in ranges(entry.getJSONArray("frames"))) for (key in fields.keys())
                        provenance.getOrPut(i) { mutableMapOf() }[key] = fields.getString(key)
                }
            }
            val incomplete = g.optJSONArray("incomplete")?.let { a ->
                (0 until a.length()).associate { k -> a.getJSONObject(k).let { o ->
                    o.getInt("frame") to Incomplete(o.optDouble("fare").takeUnless { o.isNull("fare") }, o.nullableNums("pickup"),
                        o.nullableNums("trip"), if (o.isNull("grade")) null else o.getString("grade"),
                        o.getJSONObject("units").let { u -> u.keys().asSequence().associateWith { u.getLong(it) } },
                        o.getJSONObject("provenance").let { p -> p.keys().asSequence().associateWith { p.getString(it) } },
                        o.optString("paymentBasis", "DRIVER_PAYMENT"))
                } }
            }.orEmpty()
            Group(number, g.getString("file"), frames, apps, ints(g.getJSONArray("signatureFree")),
                legacy?.let { ranges(it.getJSONArray("fareRead")) }.orEmpty(),
                legacy?.let { ranges(it.getJSONArray("tripRead")) }.orEmpty(), provenance, incomplete)
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
            Legacy(if (p.isNull("fare")) null else p.getDouble("fare"), p.nullableNums("pickup"), p.nullableNums("trip"),
                if (p.isNull("action")) null else p.getString("action"))
        }
        return Frame(group, index, o.getLong("tMs"), o.getBoolean("signature"), chip, blocks, legacy,
            if (o.has("capturedAtMs")) o.getLong("capturedAtMs") else null)
    }

    private fun JSONObject.pair(key: String) = getJSONArray(key).let { it.getInt(0) to it.getDouble(1) }
    private fun JSONObject.nullableNums(key: String) = getJSONArray(key).let { a -> List(a.length()) { if (a.isNull(it)) null else a.getDouble(it) } }
    private fun ints(a: JSONArray) = (0 until a.length()).map { a.getInt(it) }.toSet()
    private fun ranges(a: JSONArray) = (0 until a.length()).flatMap { a.getJSONArray(it).let { r -> r.getInt(0)..r.getInt(1) } }.toSet()

    /** Original order, reversed, and ten seeded shuffles. */
    fun orders(blocks: List<OcrBlock>): List<Pair<String, List<OcrBlock>>> =
        listOf("original" to blocks, "reversed" to blocks.reversed()) +
            (1..10).map { seed -> "shuffle$seed" to blocks.shuffled(Random(seed)) }
}
