package mx.kompara.data.model

/**
 * The cities Kompara publishes population benchmarks for (B-043).
 *
 * This is the 10-city launch set the backend seeds `population_stats` for
 * (`cdmx`, `monterrey`, …, `cancun`) — the subset of the web app's city list
 * (`src/lib/constants.ts`) that actually has benchmark data. A driver's city
 * selects which percentile breakpoints the app downloads via
 * `GET /v1/benchmarks?city=…`; unseeded cities fall back to `national` server-side.
 *
 * The [key] is the lower-case slug sent on the wire and used as the
 * `population_stats.city` value — it MUST stay in lockstep with the backend's
 * `CITIES` seed list and the web `constants.ts` keys. Stored as [name] in
 * DataStore so adding a city never renumbers existing persisted values; the
 * default is [CDMX] (the largest market).
 */
enum class City(val key: String, val displayName: String) {
    CDMX("cdmx", "Valle de Mexico (CDMX + Edomex)"),
    MONTERREY("monterrey", "Monterrey"),
    GUADALAJARA("guadalajara", "Guadalajara"),
    PUEBLA("puebla", "Puebla-Tlaxcala"),
    TOLUCA("toluca", "Toluca"),
    TIJUANA("tijuana", "Tijuana"),
    LEON("leon", "Leon"),
    QUERETARO("queretaro", "Queretaro"),
    MERIDA("merida", "Merida"),
    CANCUN("cancun", "Cancun");

    companion object {
        /** Launch default — the largest market. */
        val DEFAULT = CDMX

        /** Resolve a wire/slug [key] back to a [City], or null when unknown. */
        fun fromKey(key: String?): City? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}
