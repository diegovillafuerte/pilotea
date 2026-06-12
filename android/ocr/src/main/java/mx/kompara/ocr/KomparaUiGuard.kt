package mx.kompara.ocr

/**
 * Detects frames showing Kompara's OWN UI so the OCR pipeline never parses itself (found live,
 * 2026-06-12: the Inicio tab's "$0.14" stat parsed as a DiDi fare, and the simulator renders mock
 * DiDi cards that would trigger real verdicts — a feedback loop).
 *
 * Signature: the bottom-nav labels. "Comparar" + "Ajustes" appear together on every Kompara shell
 * screen and on no DiDi/inDrive offer surface.
 */
object KomparaUiGuard {

    fun isOwnUi(text: String): Boolean =
        text.contains("Comparar") && text.contains("Ajustes")
}
