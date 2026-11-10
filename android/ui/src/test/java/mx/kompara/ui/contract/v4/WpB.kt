package mx.kompara.ui.contract.v4

/**
 * WP-B, the chip (plan §4.3, §4.4). S1–S5, S7, S9 and S10 have their
 * baselines in `snapshots/v4` (ui `regla_*` from ReglaTest, overlay `placement*_*` from MeasuredVerdictChipTest).
 */
internal object WpB : V4Package {
    override val sections = mapOf(
        "S" to Section(native = mapOf(
            // Placement: the pill fits a tall offer under navigation where 0.2.20's plate was hidden.
            "S6" to "overlay/src/test/java/mx/kompara/overlay/MeasuredVerdictChipTest.kt",
            // ReaderStoppedTab, unchanged from v3 (C6): its day, night and large-font renders stay.
            "S8" to "overlay/src/test/java/mx/kompara/overlay/ReaderStoppedTabTest.kt",
        )),
    )
    override val v3Retired = setOf(
        // The v3 plate's frames and their host scenes: the v4 pill replaces them (S1–S7, S9, S10).
        // C6 (the stopped tab, v4's S8) and C13 (no chip at all) are not chip drawings and stay.
        "C1", "C2", "C3", "C4", "C5", "C7", "C8", "C9", "C10", "C11", "C12", "N8", "N9", "N10",
    )
    override val wordingAllowlist = setOf<String>()
}
