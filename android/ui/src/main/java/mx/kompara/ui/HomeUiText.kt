package mx.kompara.ui

import mx.kompara.data.settings.Settings

/**
 * Pure presentation helpers for the home screen, kept Android-free so they are unit-testable on
 * the JVM (Composable/ViewModel behaviour needs instrumentation, which is out of scope here).
 */
object HomeUiText {
    /** es-MX label listing the active platforms, or a fallback when none are enabled. */
    fun activePlatformsLabel(settings: Settings): String {
        if (settings.enabledPlatforms.isEmpty()) return "Sin plataformas activas"
        val names = settings.enabledPlatforms
            .sortedBy { it.name }
            .joinToString(", ") { it.name }
        return "Plataformas activas: $names"
    }
}
