package mx.kompara.ui.contract.v4

/** WP-F, Ajustes and first run (plan §4.8, §4.9). Only WP-F edits this file. */
internal object WpF : V4Package {
    override val sections = mapOf(
        "P" to Section(pending = setOf("P1", "P2", "P3", "P4")),
        "J" to Section(pending = setOf("J1", "J2", "J3", "J4", "J5", "J6", "J7")),
    )
    override val v3Retired = setOf<String>()
    override val wordingAllowlist = setOf<String>()
}
