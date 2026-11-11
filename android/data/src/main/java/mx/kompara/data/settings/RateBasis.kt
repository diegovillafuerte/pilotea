package mx.kompara.data.settings

/**
 * Which rate the semáforo judges and shows (v4 plan 4.2): NET = (fare − cost per km × km) per km or
 * per hour; GROSS = the fare per km or per hour. The driver's two limits are kept in this basis.
 * Stored by [name]; decode leniently via [fromName].
 */
enum class RateBasis {
    NET,
    GROSS,
    ;

    companion object {
        val DEFAULT = NET

        /** Unknown or absent ⇒ [DEFAULT]; offers judged before v4 were all NET. */
        fun fromName(name: String?): RateBasis = name?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEFAULT
    }
}
