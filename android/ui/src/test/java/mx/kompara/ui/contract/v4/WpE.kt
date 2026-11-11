package mx.kompara.ui.contract.v4

/** WP-E, Comparar and IMSS (plan §4.6, §4.7). K3 and K4 are not built (V4Contract.notBuilt). */
internal object WpE : V4Package {
    // K1, K2 and M1–M4 have day and night baselines (CompararScreenTest, ImssScreenTest); so does Tu ciudad's
    // "todavía no abre" state, which has no kit frame (comparar_ciudad_*).
    override val sections = mapOf(
        "K" to Section(),
        "M" to Section(),
    )
    override val v3Retired = setOf(
        // Comparar (Tus apps, Tu ciudad by cohort and its night twins), replaced by K1/K2 and Tu ciudad's one state.
        "F1", "F2", "F3", "F4", "F5", "N6", "N7",
        // The factual IMSS screen and its pending rule example, replaced by M1–M4.
        "E4", "E5",
    )
    override val wordingAllowlist = setOf<String>()
}
