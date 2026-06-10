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

        /** Cost-profile editor (B-040 req 4), reachable from Ajustes and the Inicio first-run nudge. */
        const val COST_PROFILE_ROUTE: String = "cost-profile"

        /** History weeks list (B-040 req 3), reachable from Inicio. */
        const val HISTORY_ROUTE: String = "history"

        /**
         * Import flow (B-045), reachable from the History screen's "Importar semana" CTA. The screen +
         * ViewModel live in `:sync` (which owns the upload + local-backfill repository); `:app`
         * registers the composable into the shared graph via `registerExtraDestinations`, the same
         * dependency inversion the offer simulator uses ([SIMULATOR_ROUTE]). The route key lives here
         * so `:ui` (which owns the History CTA) can navigate to it without depending on `:sync`.
         */
        const val IMPORT_ROUTE: String = "import"

        /**
         * Day-detail route (B-040 req 2). The day is an optional ISO arg ([mx.kompara.ui.stats.
         * DayDetailViewModel.ARG_DAY]); omitting it defaults to today.
         */
        const val DAY_DETAIL_ROUTE: String = "day"

        /**
         * Week-summary route (B-040 req 3). Carries the ISO Monday in
         * [mx.kompara.ui.stats.WeekSummaryViewModel.ARG_WEEK_START].
         */
        const val WEEK_SUMMARY_ROUTE: String = "week"

        /**
         * Paywall route (B-050). NOT a bottom-bar tab — a detail screen reached from a
         * [mx.kompara.ui.paywall.PaywallGate] CTA. Carries the originating
         * [mx.kompara.ui.paywall.GateSurface] name as a path arg (purely to bucket conversion
         * analytics). Use [paywallRoute] to build a navigable destination string.
         */
        const val PAYWALL_ROUTE: String = "paywall"

        /** Nav-arg key carrying the originating gate surface name on [PAYWALL_ROUTE]. */
        const val ARG_PAYWALL_SURFACE: String = "surface"

        /** Build the navigable paywall destination for an originating surface name. */
        fun paywallRoute(surface: String): String = "$PAYWALL_ROUTE/$surface"
    }
}
