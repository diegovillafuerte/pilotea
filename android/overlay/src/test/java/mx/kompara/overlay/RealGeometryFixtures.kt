package mx.kompara.overlay

import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import java.io.File

/**
 * Real S25 geometry (1080x2340, density 3, font 1.0), read from device screenshots and the
 * 0.2.13 device log of 2026-10-30. Block segmentation follows ML Kit's observed output (a 42 px
 * close glyph, the pill count as its own block). EVERY address/name string here is invented.
 */
internal object RealGeometryFixtures {
    const val UBER = "com.ubercab.driver"
    const val DIDI = "com.didiglobal.driver"
    val display = HostRect(0, 0, 1080, 2340)
    val statusBar = HostRect(0, 0, 1080, 91)
    val navigationBar = HostRect(0, 2196, 1080, 2340)
    /** One UI edge-panel handle: a real system window above the host. */
    val edgeHandle = HostRect(1038, 418, 1080, 760)
    val systemWindows = listOf(statusBar, edgeHandle, navigationBar)

    fun b(text: String, l: Int, t: Int, r: Int, bt: Int) = OcrBlock(text, OcrBounds(l, t, r, bt))

    fun json(name: String): List<OcrBlock> {
        val json = JSONArray(File("../ocr/src/test/resources/$name").readText())
        return List(json.length()) { i ->
            val o = json.getJSONObject(i)
            OcrBlock(o.getString("text"), OcrBounds(o.getInt("left"), o.getInt("top"), o.getInt("right"), o.getInt("bottom")))
        }
    }

    /** $129.55 Trip Radar "Me interesa" card at rest (shot-120417). */
    fun radar() = json("radar_me_interesa_s25_2026_10_30.json")
    /** $104.41 UberX "Me interesa" card, no pill (shots 120424/120430). */
    fun uberx() = json("uberx_me_interesa_s25_2026_10_30.json")
    /** $79.45 Priority "Aceptar" card with the transcribed 146x143 close box replaced by the live glyph. */
    fun priority() = json("uber_priority_s25_2026_10_29.json").filterNot { it.text in OLD_CHIP_TEXT }.map {
        if (it.text == "X") it.copy(bounds = OcrBounds(889, 700, 931, 742)) else it
    }

    private val OLD_CHIP_TEXT = setOf("Sin lectura", "falta la tarifa", "tu meta $10.00", "tu mínimo $7.50")

    fun shift(blocks: List<OcrBlock>, dy: Int) = blocks.map {
        it.copy(bounds = OcrBounds(it.bounds.left, it.bounds.top + dy, it.bounds.right, it.bounds.bottom + dy))
    }

    fun retext(blocks: List<OcrBlock>, vararg pairs: Pair<String, String>) = blocks.map { block ->
        pairs.firstOrNull { it.first == block.text }?.let { block.copy(text = it.second) } ?: block
    }

    /**
     * 0.2.15: a misread frame whose TOTAL row ("N min • X km • $R/km est.") OCR also lost takes the
     * legacy path, so a synthetic single-field misread still reaches the downstream policy under test.
     * With its TOTAL row the card's own arithmetic proves such a frame contradictory.
     */
    fun withoutTotal(blocks: List<OcrBlock>) = blocks.filterNot { it.text.contains("/km est") }

    /** 0.2.15: a genuine other card or live change keeps its TOTAL row consistent with fare and legs. */
    fun withTotal(blocks: List<OcrBlock>, total: String) =
        blocks.map { if (it.text.contains("/km est")) it.copy(text = total) else it }

    /**
     * o1, distinct reading: an earlier Radar card 227 px higher (logged card.top=339) with its own legs.
     * 0.2.15: its TOTAL row now matches its own fare and legs (3+27 min, 0.7+9.6 km, $118.20/10.3 km);
     * the transcription had kept o2's "38 min • 13.1 km • $9.89/km", which every identity contradicts.
     */
    fun radarHighDistinct() = retext(shift(radar(), -227), "$129.55" to "$118.20",
        "4 min (0.9 km)" to "3 min (0.7 km)", "34 min (12.2 km)" to "27 min (9.6 km)",
        "38 min • 13.1 km • $9.89/km est." to "30 min • 10.3 km • $11.48/km est.")
    /** o1, displaced reading: the same $129.55 card while the Radar sheet sits 227 px higher. */
    fun radarHighDisplaced() = shift(radar(), -227)

    /** 13:12:30 Priority Radar on a 3-card stack: pill "...viaje 3" (logged card.top 399), X near y=678. */
    fun priorityRadarStack() = listOf(
        b("Radar de solicitud de viaje", 202, 447, 793, 500), b("3", 834, 457, 866, 494),
        b("Uber Priority", 193, 650, 560, 699), b("X", 891, 657, 933, 699),
        b("$88.30", 96, 731, 520, 853), b("31 min • 9.4 km • $9.39/km est.", 96, 881, 848, 945),
        b("Identidad verificada", 193, 982, 646, 1032), b("★ 4.93 (412)", 123, 1100, 421, 1152),
        b("6 min (1.6 km)", 193, 1257, 515, 1303), b("Calle Imaginaria 8 - Colonia Ejemplo Dos", 193, 1328, 830, 1377),
        b("25 min (7.8 km)", 193, 1400, 540, 1451), b("Avenida Supuesta 300, Colonia Prueba", 193, 1473, 900, 1521),
        b("Aceptar", 442, 1763, 640, 1820))

    /** Main's REAL DiDi S25 capture (2026-06-11), addresses replaced. No heading, no close glyph. */
    fun didi() = listOf(
        b("$132.59", 83, 973, 543, 1085), b("1 punto(s)", 96, 1134, 306, 1194),
        b("9$13.87 de tarifa base dinámica", 77, 1286, 882, 1336), b("4.84 293 viajes", 168, 1446, 527, 1499),
        b("Tarjeta bancaria verificada", 138, 1534, 756, 1580), b("6min (1.2km)", 139, 1692, 441, 1745),
        b("Calle Ficticia 10, Colonia Ejemplo,", 138, 1761, 934, 1811), b("39min (17.3km)", 156, 1902, 514, 1960),
        b("Privada Inventada 26, Colonia Prueba,", 139, 1968, 832, 2025))

    /**
     * Main's REAL "Pon Tu Precio" bid card: payment from the Aceptar button, visible fare above pickup.
     * The button sits fully above the y2196 nav bar so window ownership keeps it for the parser.
     */
    fun didiBid() = listOf(
        b("Pon Tu Precio", 100, 700, 400, 760), b("$89.12 1.3x", 80, 900, 600, 1020),
        b("4.92 639 viajes", 168, 1446, 527, 1499), b("Tarjeta bancaria verificada", 138, 1534, 756, 1580),
        b("5min (862m)", 139, 1692, 441, 1745), b("21min (10.2km)", 156, 1902, 514, 1960),
        b("Aceptar $89.12", 200, 2040, 880, 2130))

    /** Main's live $200.85 frame: fare 973-1085, legs 1692-1960, "Aceptar $X" button (2150-2240 on
     *  device; here 2040-2130, above the nav bar, so ownership keeps it for the parser). */
    fun didiLive() = listOf(
        b("$200.85", 83, 973, 543, 1085), b("5min (862m)", 139, 1692, 441, 1745),
        b("59min (11.3km)", 156, 1902, 514, 1960), b("Aceptar $200.85", 200, 2040, 880, 2130))

    /** A second, different DiDi offer on the same layout. */
    fun didiNext() = retext(didi(), "$132.59" to "$61.20", "6min (1.2km)" to "3min (0.6km)",
        "39min (17.3km)" to "14min (5.1km)")

    /** "Centro de viajes" list between two offers: smaller fares and legs for two queued rows. */
    fun didiList() = listOf(
        b("Centro de viajes", 96, 300, 700, 380),
        b("$74.10", 96, 520, 330, 590), b("8min (2.2km)", 96, 620, 400, 670), b("18min (6.0km)", 96, 690, 420, 740),
        b("$58.00", 96, 900, 330, 970), b("2min (0.4km)", 96, 1000, 400, 1050), b("12min (4.4km)", 96, 1070, 420, 1120))

    fun fareOf(blocks: List<OcrBlock>) = blocks.filter { it.text.trimStart().startsWith("$") }
        .maxByOrNull { it.bounds.bottom - it.bounds.top }?.rect()
    fun legsOf(blocks: List<OcrBlock>) = blocks.filter { Regex("""\d+\s*min\s*\(""").containsMatchIn(it.text) }.map { it.rect() }
    fun closeOf(blocks: List<OcrBlock>) = blocks.filter { it.text.trim() == "X" }.map { it.rect() }
    fun acceptOf(blocks: List<OcrBlock>) = blocks.filter {
        it.text.startsWith("Aceptar") || it.text == "Me interesa" }.map { it.rect() }
    /** Everything the chip may never cover, expanded by the 16 dp external margin. */
    fun critical(blocks: List<OcrBlock>) = (listOfNotNull(fareOf(blocks)) + legsOf(blocks) + closeOf(blocks) +
        acceptOf(blocks)).map { it.expanded(48) }

    fun OcrBlock.rect() = HostRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
}
