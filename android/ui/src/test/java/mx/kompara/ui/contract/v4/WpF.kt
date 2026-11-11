package mx.kompara.ui.contract.v4

/** WP-F, Ajustes and first run (plan §4.8, §4.9). J5's switch that is not built is in [V4Contract.notBuiltStrings]. */
internal object WpF : V4Package {
    override val sections = mapOf(
        "P" to Section(),
        "J" to Section(),
    )
    override val v3Retired = setOf(
        // Ajustes, redrawn as J1 (six rows; Lo del coche, Avisos, Invita y gana and Tema gone).
        "G1", "G1b", "G1e", "G1n",
        // Tu meta y tu mínimo, redrawn as J2/J7.
        "G2", "G2b", "G2c", "G2d", "G2e",
        // Tus costos, redrawn as J3; the car sheet now opens from it (G5 drew it over Ajustes).
        "G3", "G5",
        // Semáforo, redrawn as J4: no pause, no suppressed-offer list.
        "G4", "G4b", "G4c", "G4d", "G4e", "G4f", "G4g", "G4h", "G4i",
        // Tus datos and Ayuda, redrawn as J5 and J6.
        "G6", "G6b", "G8",
        // The first run, redrawn as P1 and restyled as P2–P4.
        "A1", "A2", "A3", "A3n", "A4", "A4b", "A4r",
    )
    override val wordingAllowlist = setOf<String>()
}
