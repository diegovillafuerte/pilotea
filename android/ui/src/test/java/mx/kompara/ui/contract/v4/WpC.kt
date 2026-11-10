package mx.kompara.ui.contract.v4

/** WP-C, the app shell. Only WP-C (and, at the end, the integration sweep) edits this file. */
internal object WpC : V4Package {
    override val sections = mapOf(
        "V" to Section(native = mapOf("V1" to "ui/src/test/java/mx/kompara/ui/notifications/LaunchNoticesTest.kt")),
    )

    override val v3Retired = setOf(
        // Inicio, replaced by Ganancias Hoy.
        "B1", "B2", "B2r", "B3", "B3n", "B4", "B5", "B6", "B7", "B8", "B9", "N1", "N2",
        // Ganancias mes, replaced by the IMSS tab.
        "E3", "E3b", "E3c", "E3d", "D3b", "N5",
        // Avisos.
        "G7",
    )

    override val wordingAllowlist = setOf(
        // After-launch surfaces, dormant with their v3 baselines (plan §3); reworded when they return.
        "ui/src/main/java/mx/kompara/ui/fiscal/FiscalReportComposer.kt",
        "ui/src/main/java/mx/kompara/ui/imports/ImportReviewSource.kt",
        "ui/src/main/java/mx/kompara/ui/imports/ImportScreen.kt",
        "ui/src/main/java/mx/kompara/ui/imports/ImportUiState.kt",
        "ui/src/main/java/mx/kompara/ui/share/ShareCardComposer.kt",
        // The month the IMSS PDF still prints.
        "ui/src/main/java/mx/kompara/ui/stats/MonthPresentation.kt",
        // v3_rate_basis, read only by the debug component gallery (V3ComponentHost D1); the integration sweep drops it.
        "ui/src/main/res/values/strings.xml",
    )
}
