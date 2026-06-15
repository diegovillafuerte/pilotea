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
 * **PARTIALLY CALIBRATED (B-084).** The DiDi to-pickup / offer-accepted anchors below are confirmed
 * from real on-device captures (2026-06-15); the on-trip (post-pickup) and trip-completion screens
 * are still uncaptured, so the rest stay best-guess (see techdebt TD-030). The phrases are shared
 * across Uber/DiDi/inDrive (all Spanish, MX), so a single list is applied to every OCR-owned
 * platform; split per-package here if further calibration shows the wording diverges.
 *
 * The cancel-reason dialog ("¿Por qué cancelaste el viaje?" with options like "Punto de encuentro
 * muy lejano" / "Tenía otro viaje en curso") *mentions* trip words but must NEVER open a trip — on
 * device it falsely manufactured bare zero-value trips (B-084). [excludedMarkers] vetoes any frame
 * matching one of those phrases, regardless of which trip markers it also contains.
 */
data class OcrTripMarkers(
    val tripMarkers: List<String> = DEFAULT_TRIP_MARKERS,
    val excludedMarkers: List<String> = DEFAULT_EXCLUDED_MARKERS,
) {

    /**
     * Whether [text] (the joined OCR frame) looks like a trip-in-progress screen: a trip marker
     * matches AND no [excludedMarkers] (cancel-reason dialog) does — the exclusion wins so a
     * cancellation never opens a trip (B-084).
     */
    fun isTripLike(text: String): Boolean =
        excludedMarkers.none { text.contains(it, ignoreCase = true) } &&
            tripMarkers.any { text.contains(it, ignoreCase = true) }

    companion object {
        /** Trip-screen phrases (es-MX). The first block is confirmed from real captures (B-084). */
        val DEFAULT_TRIP_MARKERS: List<String> = listOf(
            // DiDi offer-accepted / to-pickup navigation, confirmed on-device 2026-06-15 (B-084).
            "Viaje aceptado",
            "Llegué por el pasajero",
            "La grabación iniciará al empezar el viaje",
            "¡Vamos!",
            // Best-guess on-trip / completion phrases — still NEEDS on-device calibration (TD-030).
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

        /**
         * Cancel-reason dialog phrases (es-MX). A frame containing any of these is NOT a trip even
         * when it also contains trip words ("Tenía otro viaje en curso", "Punto de encuentro muy
         * lejano") — on device this dialog falsely opened bare trips (B-084). Confirmed from the
         * 2026-06-15 captures.
         */
        val DEFAULT_EXCLUDED_MARKERS: List<String> = listOf(
            "cancelaste el viaje",
            "Por favor dinos por qué",
            "Punto de encuentro muy lejano",
        )

        val DEFAULT = OcrTripMarkers()
    }
}
