package mx.kompara.data.settings

/** Stored by enum name; absent/unknown preferences follow Android, including future migrations. */
enum class ThemePreference {
    AUTOMATIC, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        AUTOMATIC -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromName(value: String?): ThemePreference = entries.firstOrNull { it.name == value } ?: AUTOMATIC
    }
}
