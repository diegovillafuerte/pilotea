package mx.kompara.parsers.scrub

import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot

/**
 * Masks personally-identifiable information out of a [ParserSnapshot] before it is persisted as a
 * fixture or shipped off-device (android-technical-design.md privacy posture; task requirement 5).
 *
 * What it scrubs, in es-MX context:
 *  - **Phone numbers** — 10-digit MX mobiles, with optional +52 / 044 / 045 prefixes and common
 *    separators.
 *  - **License plates** — MX private-car plate shapes (e.g. `ABC-12-34`, `ABC-1234`, `123-ABC`).
 *  - **Exact street addresses** — street + number ("Av. Reforma 222", "Calle 5 #14"), while
 *    deliberately KEEPING colonia / zona / municipio tokens so an offer's pickup zone survives.
 *  - **Name-like tokens** — runs of Capitalized Words after a name label ("Pasajero: Juan Pérez")
 *    or standalone two-to-three Capitalized-word runs that look like a person's name.
 *
 * Conservative by design: it errs toward over-masking PII rather than leaking it. Bounds/viewIds/
 * classNames are structural and never contain PII, so they pass through untouched, preserving the
 * geometry the engine needs.
 */
class SnapshotScrubber {

    fun scrub(snapshot: ParserSnapshot): ParserSnapshot =
        snapshot.copy(nodes = snapshot.nodes.map(::scrubNode))

    fun scrubNode(node: ParserNode): ParserNode =
        node.text?.let { node.copy(text = scrubText(it)) } ?: node

    /** Mask PII in a single string. Order matters: addresses before names, names before plates. */
    fun scrubText(text: String): String {
        var out = text
        out = PHONE.replace(out) { PHONE_MASK }
        out = STREET_ADDRESS.replace(out) { m ->
            // Keep the street-type word + a masked number, drop the rest of the exact address.
            "${m.groupValues[1]} $ADDR_MASK"
        }
        out = LABELED_NAME.replace(out) { m -> "${m.groupValues[1]}$NAME_MASK" }
        out = PLATE.replace(out) { PLATE_MASK }
        out = STANDALONE_NAME.replace(out) { NAME_MASK }
        return out
    }

    companion object {
        const val PHONE_MASK = "«phone»"
        const val PLATE_MASK = "«plate»"
        const val NAME_MASK = "«name»"
        const val ADDR_MASK = "«addr»"

        // +52 1 55 1234 5678 / 55-1234-5678 / 5512345678 / 044 55 1234 5678
        private val PHONE = Regex(
            """(?:\+?52\s?1?\s?|0?4[45]\s?)?(?:\d[\s\-.]?){10}""",
        )

        // MX private plates: 3 letters + 4 digits with optional dashes, or older 3-digit-led forms.
        private val PLATE = Regex(
            """\b(?:[A-Z]{3}[\-\s]?\d{2}[\-\s]?\d{2}|[A-Z]{3}[\-\s]?\d{4}|\d{3}[\-\s]?[A-Z]{3})\b""",
        )

        // "Av. Reforma 222", "Calle 5 #14", "C. Morelos 100-B". Capture group 1 = street-type word.
        private val STREET_ADDRESS = Regex(
            """\b(Calle|Av\.?|Avenida|Blvd\.?|Boulevard|C\.|Calz\.?|Calzada|Priv\.?|Privada|Carr\.?|Carretera|Cda\.?|Cerrada)\s+[\p{L}0-9.\s]{1,40}?\s*#?\s*\d+[A-Za-z\-]*""",
            RegexOption.IGNORE_CASE,
        )

        // "Pasajero: Juan Pérez", "Conductor - María López". Capture group 1 = label + separator.
        private val LABELED_NAME = Regex(
            """(\b(?:Pasajero|Cliente|Conductor|Chofer|Nombre|Para|Recoger a)\b\s*[:\-]?\s*)""" +
                """\p{Lu}[\p{L}']+(?:\s+\p{Lu}[\p{L}']+){0,3}""",
        )

        // Bare two-to-three Capitalized-word run that reads like a person's name. Kept narrow to
        // avoid eating place names; labeled names above catch the common case.
        private val STANDALONE_NAME = Regex(
            """\b\p{Lu}[\p{Ll}']+\s+\p{Lu}[\p{Ll}']+(?:\s+\p{Lu}[\p{Ll}']+)?\b""",
        )
    }
}
