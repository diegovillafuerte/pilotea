package mx.kompara.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import mx.kompara.ui.R

/**
 * The five top-level destinations of the app shell, in bar order. [LECTOR] is the prominent centre
 * action (raised circle) — it's the product's core gesture, mirroring the web MVP's "Subir".
 *
 * @param route stable navigation route key.
 * @param labelRes Spanish tab label.
 * @param contentDescriptionRes accessibility description for the tab icon.
 * @param icon the bar icon (also reused as the placeholder screen's empty-state icon).
 * @param isCenter whether this is the raised centre action.
 */
enum class KomparaDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val contentDescriptionRes: Int,
    val icon: ImageVector,
    val isCenter: Boolean = false,
) {
    INICIO(
        route = "inicio",
        labelRes = R.string.nav_inicio,
        contentDescriptionRes = R.string.nav_inicio_desc,
        icon = Icons.Filled.Home,
    ),
    COMPARAR(
        route = "comparar",
        labelRes = R.string.nav_comparar,
        contentDescriptionRes = R.string.nav_comparar_desc,
        icon = Icons.AutoMirrored.Filled.List,
    ),
    LECTOR(
        route = "lector",
        labelRes = R.string.nav_lector,
        contentDescriptionRes = R.string.nav_lector_desc,
        icon = Icons.Filled.PlayArrow,
        isCenter = true,
    ),
    FISCAL(
        route = "fiscal",
        labelRes = R.string.nav_fiscal,
        contentDescriptionRes = R.string.nav_fiscal_desc,
        icon = Icons.Filled.DateRange,
    ),
    AJUSTES(
        route = "ajustes",
        labelRes = R.string.nav_ajustes,
        contentDescriptionRes = R.string.nav_ajustes_desc,
        icon = Icons.Filled.Settings,
    ),
    ;

    companion object {
        /** The destination the app launches into. */
        val START: KomparaDestination = INICIO

        /** Bar items in display order (same as declaration order). */
        val barItems: List<KomparaDestination> = entries

        /**
         * Route for the offer simulator (B-037). It is NOT a bottom-bar tab — it's a detail screen
         * reachable from Ajustes (and the onboarding done-screen). The route key lives here so `:ui`
         * (which owns Ajustes) can navigate to it, while `:overlay` registers the actual composable
         * (the simulator embeds the verdict chip, which lives in `:overlay`).
         */
        const val SIMULATOR_ROUTE: String = "simulator"
    }
}
