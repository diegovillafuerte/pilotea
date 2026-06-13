package mx.kompara.data.settings

/**
 * Which net rate decides the semáforo colour (B-079). Drivers optimize ONE metric per shift —
 * pesos por kilómetro or pesos por hora — so the verdict follows the chosen one alone; the other
 * rate stays visible as secondary context. Stored by [name]; decode leniently via [fromName].
 */
enum class PreferredMetric {
    /** Ingreso por kilómetro: the net $/km floors decide the light. */
    IPK,

    /** Ingreso por hora: the net $/hr floors decide the light. */
    IPH,
    ;

    companion object {
        /** $/km is the default — the rate drivers quote and the chip has always led with. */
        val DEFAULT = IPK

        /** Decode a stored enum name; unknown or absent ⇒ [DEFAULT] (existing installs migrate silently). */
        fun fromName(name: String?): PreferredMetric {
            if (name == null) return DEFAULT
            return runCatching { valueOf(name) }.getOrNull() ?: DEFAULT
        }
    }
}
