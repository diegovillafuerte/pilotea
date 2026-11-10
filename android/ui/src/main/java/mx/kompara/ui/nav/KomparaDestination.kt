package mx.kompara.ui.nav

/** Three roots and the stable drill-in route keys used by existing links. */
enum class KomparaDestination(val route: String) {
    GANANCIAS("ganancias"),
    COMPARAR("comparar"),
    IMSS("imss"),
    SEMAFORO("semaforo"),
    AJUSTES("ajustes");

    companion object {
        /** The bar shows on the three roots and on a past day (docs/v4-simple-launch-plan.md §3). */
        fun showsBottomBar(route: String?): Boolean = barItems.any { it.route == route } ||
            route == DAY_DETAIL_ROUTE || route?.startsWith("$DAY_DETAIL_ROUTE/") == true

        fun dayRoute(day: String, offerId: Long? = null, ledger: Boolean = false): String = "$DAY_DETAIL_ROUTE/$day" +
            (offerId?.let { "?offerId=$it" + if (ledger) "&ledger=true" else "" } ?: if (ledger) "?ledger=true" else "")

        const val RECOVERY_ROUTE = "reader-recovery"
        const val BATTERY_ROUTE = "reader-battery"

        /** The destination the app launches into. */
        val START: KomparaDestination = GANANCIAS

        /** Bar items in display order (same as declaration order). */
        val barItems: List<KomparaDestination> = listOf(GANANCIAS, COMPARAR, IMSS)

        /**
         * The tab to highlight for a given nav back stack, by route. Detail screens (Ayuda,
         * Tu meta y tu mínimo…) are pushed on top of the tab they were opened from but are not
         * tabs themselves, so we pick the topmost route in [backStackRoutes] that *is* a tab and fall
         * back to [START]. This keeps the parent tab lit on detail screens and lets a re-tap pop back
         * to its root (B-074 F5). [backStackRoutes] runs root → top (oldest entry first).
         */
        fun activeTab(backStackRoutes: List<String?>): KomparaDestination =
            backStackRoutes.asReversed().firstNotNullOfOrNull { route -> barItems.firstOrNull { it.route == route } } ?: START

        /** The shell's header title for [route]; null where the screen draws its own header. */
        fun headerTitle(route: String?): String? = when {
            route == GANANCIAS.route || route?.startsWith("$WEEK_SUMMARY_ROUTE/") == true -> "Ganancias"
            route == COMPARAR.route -> "Comparar"
            route == IMSS.route -> "IMSS"
            route == AJUSTES.route -> "Ajustes"
            else -> null
        }

        /** Cost-profile editor (B-040 req 4), reachable from Ajustes. */
        const val COST_PROFILE_ROUTE: String = "cost-profile"

        /** Threshold editor (B-070): the semáforo floors (neto $/km y $/h), reachable from Ajustes. One semáforo para todas las plataformas (B-076). */
        const val THRESHOLDS_ROUTE: String = "thresholds"

        /** In-app help center (B-071), reachable from Ajustes. */
        const val HELP_ROUTE: String = "help"

        /** Account management ("Tu cuenta", B-069): profile edit, logout, delete; reachable from Ajustes. */
        const val ACCOUNT_ROUTE: String = "account"
        const val DATA_ROUTE = "your-data"

        /**
         * Import flow (B-045), reachable from the History screen's "Importar semana" CTA. The screen +
         * ViewModel live in `:sync` (which owns the upload + local-backfill repository); `:app`
         * registers the composable into the shared graph via `registerExtraDestinations`, the same
         * dependency inversion other modules use. The route key lives here
         * so `:ui` (which owns the History CTA) can navigate to it without depending on `:sync`.
         */
        const val IMPORT_ROUTE: String = "import"

        /**
         * Referral / "Invita y gana" flow (B-056), reachable from Ajustes. After launch, per
         * docs/v4-simple-launch-plan.md: the manifest no longer routes `kompara://referral` here.
         */
        const val REFERRAL_ROUTE: String = "referral"

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
        const val PLAN_ROUTE: String = "tu-plan"
        const val PAYWALL_ROUTE: String = "paywall"

        /** Nav-arg key carrying the originating gate surface name on [PAYWALL_ROUTE]. */
        const val ARG_PAYWALL_SURFACE: String = "surface"

        /** Build the navigable paywall destination for an originating surface name. */
        fun paywallRoute(surface: String): String = "$PAYWALL_ROUTE/$surface"

        /**
         * Shareable earnings-card preview (B-055). After launch, per docs/v4-simple-launch-plan.md:
         * the shell header no longer opens it.
         */
        const val SHARE_CARD_ROUTE: String = "share-card"
    }
}
