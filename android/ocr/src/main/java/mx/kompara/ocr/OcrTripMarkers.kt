package mx.kompara.ocr

/**
 * OCR-text markers that identify a **trip-in-progress** screen (navigation / active ride) for the
 * B-039 OCR ledger path. The OCR analogue of `:capture`'s node-side `TripStateMarkers`, but matched
 * against the user-visible Spanish text OCR reads (the node side matches resource-id fragments,
 * which OCR never sees).
 *
 * A frame that is not an offer card and does not match any marker is treated as **idle** (home /
 * waiting-for-request) — the same default-to-idle policy the node mapper uses.
 *
 * **NEEDS ON-DEVICE CALIBRATION.** These defaults are reasoned from the MX driver UIs (and partly
 * corroborated: the Uber offer spec's `cardDetector.noneOf` already lists "Viaje en curso" /
 * "Iniciar viaje" / "Finalizar viaje" as *non-offer* trip screens). They have NOT been validated
 * against captured trip-screen frames — see techdebt. The phrases are shared across Uber/DiDi/inDrive
 * (all Spanish, MX), so a single list is applied to every OCR-owned platform; split per-package here
 * if calibration shows the wording diverges.
 */
data class OcrTripMarkers(val tripMarkers: List<String> = DEFAULT_TRIP_MARKERS) {

    /** Whether [text] (the joined OCR frame) looks like a trip-in-progress screen. */
    fun isTripLike(text: String): Boolean =
        tripMarkers.any { text.contains(it, ignoreCase = true) }

    companion object {
        /** Best-guess trip-screen phrases (es-MX). NEEDS CALIBRATION against real captures. */
        val DEFAULT_TRIP_MARKERS: List<String> = listOf(
            "Viaje en curso",
            "Iniciar viaje",
            "Finalizar viaje",
            "Finalizando viaje",
            "Terminar viaje",
            "Cancelar viaje",
            "Llegar a",
            "En camino",
            "Recoger a",
            "Recoger al",
            "Desliza para",
            "Punto de encuentro",
            "Navegar",
        )

        val DEFAULT = OcrTripMarkers()
    }
}
