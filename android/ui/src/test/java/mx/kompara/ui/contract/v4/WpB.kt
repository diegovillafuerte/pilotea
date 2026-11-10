package mx.kompara.ui.contract.v4

/** WP-B, the chip (plan §4.3, §4.4). Only WP-B edits this file. */
internal object WpB : V4Package {
    override val sections = mapOf(
        "S" to Section(pending = setOf("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10")),
    )
    override val v3Retired = setOf(
        // A missing time with its limit lines: the v4 pill reads "Sin lectura" (plan §4.3), drawn as S4.
        "C5",
    )
    override val wordingAllowlist = setOf<String>()
}
