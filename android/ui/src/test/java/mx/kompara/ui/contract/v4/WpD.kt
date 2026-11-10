package mx.kompara.ui.contract.v4

/** WP-D, Ganancias (plan §4.5). Only WP-D edits this file. */
internal object WpD : V4Package {
    // L1–L8: ui/src/test/java/mx/kompara/ui/screens/GananciasScreenTest.kt records ganancias_L*_{day,night}_*.png.
    override val sections = mapOf(
        "L" to Section(),
    )
    override val v3Retired = setOf(
        // Tu día, replaced by the day layout (Hoy L1, a past day L6), its offer sheet (L7) and Cómo se calculó (L8).
        "D1", "D1a", "D1b", "D2", "D2b", "D2c", "D2d", "D2e", "D3", "D3a", "N3",
        // Ganancias semana, replaced by Semana (L4, L5).
        "E1", "E1b", "E1c", "E1d", "E1e", "E1f", "E2", "E6", "E7", "E7b", "E8", "N4",
    )
    override val wordingAllowlist = setOf(
        // The v3 period ledger the dormant IMSS PDF's month still prints (MonthPresentation, after launch).
        "ui/src/main/java/mx/kompara/ui/stats/MonthLedger.kt",
    )
}
