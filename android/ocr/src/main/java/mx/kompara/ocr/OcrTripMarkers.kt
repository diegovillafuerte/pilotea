package mx.kompara.ocr

/** Classified evidence, so a scoped decision never reclassifies unrelated window text. */
enum class OcrLifecycleTransition { UNKNOWN, ACCEPTED, UNDERWAY, IDLE, CANCELLED }

/**
 * OCR-text markers that identify a **trip-in-progress** screen (navigation / active ride) for the
 * B-039 OCR ledger path. The OCR analogue of `:capture`'s node-side `TripStateMarkers`, but matched
 * against the user-visible Spanish text OCR reads (the node side matches resource-id fragments,
 * which OCR never sees).
 *
 * A frame without a positive lifecycle marker remains unknown. Background idle markers need
 * separate disappearance evidence in [OfferFramePipeline] while an offer session is present.
 *
 * **PARTIALLY CALIBRATED (B-084).** The DiDi (2026-06-15) and Uber (2026-06-18) to-pickup /
 * offer-accepted anchors below are confirmed from real on-device captures; the on-trip (post-pickup)
 * and trip-completion screens are still uncaptured, so the rest stay best-guess (TD-030 / TD-058).
 * The phrases are shared across Uber/DiDi/inDrive (all Spanish, MX), so a single list is applied to
 * every OCR-owned platform; split per-package here if further calibration shows the wording diverges.
 *
 * The cancel dialogs *mention* trip words but must NEVER open a trip — on device they falsely
 * manufactured bare zero-value trips (B-084): DiDi's "¿Por qué cancelaste el viaje?" (options like
 * "Punto de encuentro muy lejano") and Uber's "¿Algo salió mal? … CANCELAR VIAJE" (which hits the
 * "Iniciar viaje"/"Cancelar viaje" markers). [excludedMarkers] vetoes any frame matching one of those
 * phrases, regardless of which trip markers it also contains.
 */
data class OcrTripMarkers(
    val tripMarkers: List<String> = DEFAULT_TRIP_MARKERS,
    val excludedMarkers: List<String> = DEFAULT_EXCLUDED_MARKERS,
) {

    fun transition(text: String): OcrLifecycleTransition = when {
        isCancelled(text) -> OcrLifecycleTransition.CANCELLED
        isUnderWay(text) -> OcrLifecycleTransition.UNDERWAY
        isTripLike(text) -> OcrLifecycleTransition.ACCEPTED
        isIdle(text) -> OcrLifecycleTransition.IDLE
        else -> OcrLifecycleTransition.UNKNOWN
    }

    /**
     * Only B-084 transitions can act outside the former card. Best guesses remain confined to
     * that card region; exclusions still veto acceptance/idle across the entire owned window.
     * The caller must never use transition evidence on a surviving offer-signature frame.
     */
    fun scopedTransition(windowText: String, cardRegionText: String,
        allowWindowIdle: Boolean = false): OcrLifecycleTransition {
        if (windowText.contains("cancelaste el viaje", ignoreCase = true)) return OcrLifecycleTransition.CANCELLED
        val local = transition(cardRegionText)
        if (local == OcrLifecycleTransition.CANCELLED) return local
        val excluded = excludedMarkers.any { windowText.contains(it, ignoreCase = true) }
        if (excluded) return OcrLifecycleTransition.UNKNOWN
        if (local == OcrLifecycleTransition.UNDERWAY) return local
        if (CONFIRMED_TRIP_MARKERS.any { windowText.contains(it, ignoreCase = true) }) {
            return OcrLifecycleTransition.ACCEPTED
        }
        if (local != OcrLifecycleTransition.UNKNOWN) return local
        // S25-confirmed idle chrome can start an idle phase, or corroborate independently observed
        // semantic-card disappearance. It cannot retire a semanticless card from the background.
        return if (allowWindowIdle && windowText.contains("Buscando viajes", ignoreCase = true))
            OcrLifecycleTransition.IDLE else OcrLifecycleTransition.UNKNOWN
    }

    /**
     * Whether [text] (the joined OCR frame) looks like a trip-in-progress screen: a trip marker
     * matches AND no [excludedMarkers] (cancel-reason dialog) does — the exclusion wins so a
     * cancellation never opens a trip (B-084).
     */
    fun isTripLike(text: String): Boolean =
        excludedMarkers.none { text.contains(it, ignoreCase = true) } &&
            tripMarkers.any { text.contains(it, ignoreCase = true) }

    fun isUnderWay(text: String): Boolean = isTripLike(text) &&
        listOf("Viaje en curso", "Finalizar viaje", "Terminar viaje").any { text.contains(it, ignoreCase = true) }

    fun isCancelled(text: String): Boolean = listOf("Viaje cancelado", "cancelaste el viaje").any {
        text.contains(it, ignoreCase = true)
    }

    fun isIdle(text: String): Boolean = excludedMarkers.none { text.contains(it, true) } &&
        listOf("Buscando viajes", "Buscando solicitud de viaje", "Buscando solicitudes", "Esperando solicitudes", "Estás desconectado", "Viaje finalizado").any {
            text.contains(it, ignoreCase = true)
        }

    companion object {
        /** Trip-screen phrases (es-MX). The first block is confirmed from real captures (B-084). */
        val CONFIRMED_TRIP_MARKERS: List<String> = listOf(
            // DiDi offer-accepted / to-pickup navigation, confirmed on-device 2026-06-15 (B-084).
            "Viaje aceptado",
            "Llegué por el pasajero",
            "La grabación iniciará al empezar el viaje",
            "¡Vamos!",
            // Uber offer-accepted / to-pickup screen, confirmed on-device 2026-06-18 (B-084). The
            // itinerary header "Agenda de viajes" shows only on the accepted/to-pickup screen — never
            // on the idle/searching home or an offer card — so it cleanly marks an accepted Uber ride.
            // (Uber's accepted screen is Uber-specific text the DiDi anchors above never matched, so
            // accepted Uber rides used to open an orphan bare trip instead of linking the offer.)
            "Agenda de viajes",
        )

        val DEFAULT_TRIP_MARKERS: List<String> = CONFIRMED_TRIP_MARKERS + listOf(
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
            // DiDi cancel-reason dialog, confirmed on-device 2026-06-15.
            "cancelaste el viaje",
            "Por favor dinos por qué",
            "Punto de encuentro muy lejano",
            // Uber cancel dialog, confirmed on-device 2026-06-18 (B-084). Its "¿Algo salió mal? … No
            // vale la pena iniciar el viaje … CANCELAR VIAJE" frames hit the "Iniciar viaje" /
            // "Cancelar viaje" trip markers and manufactured a bare zero-value trip on cancellation.
            // These two phrases appear only in that cancel flow, never on an active-trip screen.
            "Acepté el viaje por error",
            "Quieres cancelar este",
        )

        val DEFAULT = OcrTripMarkers()
    }
}
