package mx.kompara.ui.contract.v4

/** WP-E, Comparar and IMSS (plan §4.6, §4.7). Only WP-E edits this file. K3 and K4 are not built (V4Contract.notBuilt). */
internal object WpE : V4Package {
    override val sections = mapOf(
        "K" to Section(pending = setOf("K1", "K2")),
        "M" to Section(pending = setOf("M1", "M2", "M3", "M4")),
    )
    override val v3Retired = setOf<String>()
    override val wordingAllowlist = setOf(
        "ui/src/main/java/mx/kompara/ui/screens/CompararScreen.kt",
        "ui/src/main/java/mx/kompara/ui/screens/FiscalScreen.kt",
        "ui/src/main/java/mx/kompara/ui/stats/ComparisonBuilder.kt",
    )
}
