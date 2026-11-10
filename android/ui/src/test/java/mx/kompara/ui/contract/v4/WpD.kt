package mx.kompara.ui.contract.v4

/** WP-D, Ganancias (plan §4.5). Only WP-D edits this file. */
internal object WpD : V4Package {
    override val sections = mapOf(
        "L" to Section(pending = setOf("L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8")),
    )
    override val v3Retired = setOf<String>()
    override val wordingAllowlist = setOf(
        "ui/src/main/java/mx/kompara/ui/screens/OfferDetailSheet.kt",
        "ui/src/main/java/mx/kompara/ui/screens/WeekSheets.kt",
        "ui/src/main/java/mx/kompara/ui/screens/WeekSummaryScreen.kt",
        "ui/src/main/java/mx/kompara/ui/stats/DayDetail.kt",
        "ui/src/main/java/mx/kompara/ui/stats/DayPresentation.kt",
        "ui/src/main/java/mx/kompara/ui/stats/WeekPresentation.kt",
    )
}
