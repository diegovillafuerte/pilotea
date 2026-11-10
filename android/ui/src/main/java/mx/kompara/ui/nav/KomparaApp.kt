package mx.kompara.ui.nav

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import mx.kompara.ui.R
import mx.kompara.ui.components.DrawnIcon
import mx.kompara.ui.theme.KomparaType
import mx.kompara.ui.screens.HoyRecoveryScreen
import mx.kompara.ui.screens.HoyBatteryScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mx.kompara.ui.imports.importDestination
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.paywall.PaywallScreen
import mx.kompara.ui.referral.referralDestination
import mx.kompara.ui.screens.AccountScreen
import mx.kompara.ui.screens.AjustesScreen
import mx.kompara.ui.screens.CompararScreen
import mx.kompara.ui.screens.CostProfileScreen
import mx.kompara.ui.screens.DayDetailScreen
import mx.kompara.ui.screens.HelpScreen
import mx.kompara.ui.screens.ImssScreen
import mx.kompara.ui.screens.LectorScreen
import mx.kompara.ui.screens.ThresholdsScreen
import mx.kompara.ui.screens.WeekSummaryScreen
import mx.kompara.ui.share.ShareCardScreen
import mx.kompara.ui.stats.DayDetailViewModel
import mx.kompara.ui.stats.WeekSummaryViewModel
import mx.kompara.ui.theme.KomparaTheme

/**
 * The whole app: [KomparaShell] over the three tab roots and their drill-ins. Launches into
 * [KomparaDestination.START] (Ganancias). `:app`'s MainActivity just wraps this in [KomparaTheme],
 * keeping the app module thin.
 *
 * [registerExtraDestinations] lets modules outside :ui supply optional routes.
 * The legacy onboarding trial entry now opens the single limits editor and its labelled example;
 * S15 owns the short first-run demo. No separate simulator can persist a competing rule.
 */
@Composable
fun KomparaApp(
    modifier: Modifier = Modifier,
    navigateToReaderTrial: Boolean = false,
    navigateToShareCard: Boolean = false,
    navigateToImport: Boolean = false,
    notification: mx.kompara.ui.notifications.NoticeTarget? = null,
    onNotificationConsumed: () -> Unit = {},
    registerExtraDestinations: NavGraphBuilder.(NavController) -> Unit = {},
) {
    val navController = rememberNavController()

    var readerDeepLinkConsumed by rememberSaveable { mutableStateOf(false) }
    if (navigateToReaderTrial && !readerDeepLinkConsumed) {
        LaunchedEffect(Unit) { navController.navigateToReaderTrial(); readerDeepLinkConsumed = true }
    }

    // Week-close notification deep link (B-055): jump straight to the share-card preview.
    var shareDeepLinkConsumed by rememberSaveable { mutableStateOf(false) }
    if (navigateToShareCard && !shareDeepLinkConsumed) {
        LaunchedEffect(Unit) { navController.navigate(KomparaDestination.SHARE_CARD_ROUTE); shareDeepLinkConsumed = true }
    }

    // Share-target deep link (PR-D3): jump to the import flow; the shared file is already staged in
    // SharedImportBuffer, so the import screen opens on the SharedReady confirmation. One-shot: a config
    // change/recreation re-enters this composition with navigateToImport still true (the intent extra
    // persists), so a saved "consumed" flag stops it re-pushing import with an already-taken (empty)
    // buffer (codex review).
    var importDeepLinkConsumed by rememberSaveable { mutableStateOf(false) }
    if (navigateToImport && !importDeepLinkConsumed) {
        LaunchedEffect(Unit) {
            navController.navigate(KomparaDestination.IMPORT_ROUTE)
            importDeepLinkConsumed = true
        }
    }

    if (notification != null) {
        val dispatcher = hiltViewModel<mx.kompara.ui.notifications.NoticeNavigation>()
        LaunchedEffect(notification) {
            val destination = dispatcher.route(notification)
            navController.navigate(destination) {
                popUpTo(navController.graph.startDestinationId) { inclusive = destination == KomparaDestination.START.route }
                launchSingleTop = true
            }
            onNotificationConsumed()
        }
    }

    val limitsSnackbar = androidx.compose.runtime.remember { mx.kompara.ui.components.SaveSnackbarState() }
    LaunchedEffect(limitsSnackbar.sequence) {
        if (limitsSnackbar.message != null) { kotlinx.coroutines.delay(4_000); limitsSnackbar.clear() }
    }
    androidx.compose.runtime.CompositionLocalProvider(mx.kompara.ui.components.LocalLimitsSnackbar provides limitsSnackbar) {
        KomparaShell(navController, modifier) {
            tabScreens(navController)
            statsScreens(navController, limitsSnackbar)
            registerExtraDestinations(navController)
        }
    }
}

/**
 * The frame around every screen: the header (root title and gear on a tab root, back arrow on the
 * drill-ins it titles), the bottom bar on the roots and a past day, and the [NavHost] over [graph].
 */
// currentBackStack is @RestrictTo(LIBRARY_GROUP) but is the only way to read the FULL back stack as
// a state (needed for the B-074 tab-highlight + re-tap-to-pop behavior); the public
// currentBackStackEntryAsState exposes only the top entry. It is de-facto stable and widely used;
// a deliberate, reviewed use rather than a public-API alternative that would change tab behavior.
@SuppressLint("RestrictedApi")
@Composable
internal fun KomparaShell(navController: NavHostController, modifier: Modifier = Modifier, graph: NavGraphBuilder.() -> Unit) {
    val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
    // The highlighted tab is the topmost tab in the back stack: on a detail screen (Ayuda, a past
    // day…) this lights the tab it was opened from, so a re-tap can pop that detail stack back to
    // the tab root (B-074 F5).
    val current = KomparaDestination.activeTab(backStack.map { it.destination.route })
    val currentRoute = backStack.lastOrNull()?.destination?.route
    val isTabRoot = KomparaDestination.barItems.any { it.route == currentRoute }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            KomparaDestination.headerTitle(currentRoute)?.let { title ->
                NavigationHeader(title,
                    onBack = if (isTabRoot) null else ({ navController.popBackStack(); Unit }),
                    onSettings = if (isTabRoot) ({ navController.navigate(KomparaDestination.AJUSTES.route) }) else null)
            }
        },
        bottomBar = {
            if (KomparaDestination.showsBottomBar(currentRoute)) KomparaBottomBar(
                current = current,
                onSelect = { navController.selectRoot(it, current) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KomparaDestination.START.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            builder = graph,
        )
    }
}

/** A root retap drops its drill-in; switching roots restores that root's saved stack. */
internal fun NavController.selectRoot(destination: KomparaDestination, current: KomparaDestination) {
    if (destination == current && popBackStack(destination.route, inclusive = false)) return
    navigate(destination.route) {
        popUpTo(KomparaDestination.START.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** A root carries its title and the gear (58 dp, as the v4 kit's `.bar`); a drill-in its back arrow. */
@Composable
internal fun NavigationHeader(title: String, onBack: (() -> Unit)?, onSettings: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .heightIn(min = if (onBack == null) 58.dp else 64.dp)
        // The gear's 48 dp target overhangs the 20 dp gutter so the glyph itself sits on it.
        .padding(start = 20.dp, end = if (onSettings != null) 8.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onBack, Modifier.size(48.dp)) { DrawnIcon(R.drawable.v3_back, description = "Volver") }
        Text(title, Modifier.weight(1f), style = if (onBack == null) KomparaType.rootTitle else KomparaType.title)
        if (onSettings != null) IconButton(onSettings, Modifier.size(48.dp)) {
            DrawnIcon(R.drawable.v4_gear, description = "Ajustes", tint = KomparaTheme.colors.ink)
        }
    }
}

/** Legacy onboarding entry, kept until S15 migrates its demo. */
private fun NavHostController.navigateToReaderTrial() {
    val target = KomparaDestination.THRESHOLDS_ROUTE
    navigate(target) {
        popUpTo(KomparaDestination.START.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Navigate to the paywall screen, carrying the originating [GateSurface] for analytics bucketing. */
private fun NavController.navigateToPaywall(surface: GateSurface) {
    navigate(KomparaDestination.paywallRoute(surface.name))
}

/** Registers the screen for every top-level destination. WP-D, WP-E and WP-F replace these screens. */
private fun NavGraphBuilder.tabScreens(navController: NavController) {
    composable(KomparaDestination.GANANCIAS.route) {
        WeekSummaryScreen(
            onImportReview = { navController.navigate("import-review/$it") },
            onDay = { navController.navigate(KomparaDestination.dayRoute(it.toString())) },
            onMonth = { navController.selectRoot(KomparaDestination.IMSS, KomparaDestination.GANANCIAS) },
            onUpgrade = { navController.navigateToPaywall(GateSurface.HISTORY) },
            onImport = { navController.navigate(KomparaDestination.IMPORT_ROUTE) },
        )
    }
    composable(KomparaDestination.COMPARAR.route) { CompararScreen() }
    composable(KomparaDestination.IMSS.route) { ImssScreen() }
    composable(KomparaDestination.SEMAFORO.route) {
        LectorScreen(
            onBack = { navController.popBackStack() },
            onHelp = { navController.navigate("help?answer=semaforo") },
            onPermission = { navController.navigate(KomparaDestination.RECOVERY_ROUTE) },
            onBattery = { navController.navigate(KomparaDestination.BATTERY_ROUTE) },
            onOffer = { day, id -> navController.navigate(KomparaDestination.dayRoute(day, id)) },
            onOpenThresholds = { navController.navigate(KomparaDestination.THRESHOLDS_ROUTE) },
        )
    }
    composable(KomparaDestination.AJUSTES.route) {
        AjustesScreen(
            onOpenReader = { navController.navigate(KomparaDestination.SEMAFORO.route) },
            onOpenCostProfile = { navController.navigate(KomparaDestination.COST_PROFILE_ROUTE) },
            onOpenPermission = { navController.navigate(KomparaDestination.RECOVERY_ROUTE) },
            onSignIn = { navController.navigate("settings-signin") },
            onPlan = { navController.navigate(KomparaDestination.PLAN_ROUTE) },
            onOpenReferral = { navController.navigate(KomparaDestination.REFERRAL_ROUTE) },
            onOpenThresholds = { navController.navigate(KomparaDestination.THRESHOLDS_ROUTE) },
            onOpenHelp = { navController.navigate(KomparaDestination.HELP_ROUTE) },
            onOpenAccount = { navController.navigate(KomparaDestination.ACCOUNT_ROUTE) },
            onOpenData = { navController.navigate(KomparaDestination.DATA_ROUTE) },
            // Avisos is gone in the launch set (plan §3); WP-F removes its row.
            onOpenNotices = {},
        )
    }
}

/**
 * The B-040 stats detail screens (not bottom-bar tabs): cost-profile editor, history weeks list,
 * day detail (today by default, or a specific ISO day), and week summary.
 */
private fun NavGraphBuilder.statsScreens(navController: NavController, snackbar: mx.kompara.ui.components.SaveSnackbarState) {
    composable(KomparaDestination.RECOVERY_ROUTE) { HoyRecoveryScreen { navController.popBackStack() } }
    composable(KomparaDestination.BATTERY_ROUTE) { HoyBatteryScreen { navController.popBackStack() } }
    composable(KomparaDestination.COST_PROFILE_ROUTE) {
        CostProfileScreen(onBack = { navController.popBackStack() })
    }
    composable("cost-efficiency") { CostProfileScreen(onBack = { navController.popBackStack() }, efficiency = true) }
    composable("settings-signin") { mx.kompara.ui.auth.SignupFlowScreen(onComplete = { navController.popBackStack() }, onCancel = { navController.popBackStack() }) }
    // B-070 threshold editor and B-071 help center, both reached from Ajustes.
    composable(KomparaDestination.THRESHOLDS_ROUTE) {
        ThresholdsScreen(onBack = { navController.popBackStack() }, onSaved = { message ->
            navController.popBackStack()
            snackbar.saved(message)
        })
    }
    composable("${KomparaDestination.HELP_ROUTE}?answer={answer}", arguments = listOf(navArgument("answer") { type = NavType.StringType; defaultValue = "" })) { entry ->
        HelpScreen(initialAnswer = entry.arguments?.getString("answer"), onBack = { navController.popBackStack() })
    }
    // B-069 account management ("Tu cuenta"), reached from Ajustes.
    composable(KomparaDestination.ACCOUNT_ROUTE) {
        AccountScreen(onBack = { navController.popBackStack() }, onSignIn = { navController.navigate("settings-signin") },
            onHelp = { navController.navigate(KomparaDestination.HELP_ROUTE) })
    }
    composable(KomparaDestination.DATA_ROUTE) {
        mx.kompara.ui.screens.TusDatosScreen(onBack = { navController.popBackStack() }, onSignIn = { navController.navigate("settings-signin") })
    }
    // B-050 paywall screen, reached from any PaywallGate CTA. The surface path arg buckets analytics.
    composable(
        route = "${KomparaDestination.PAYWALL_ROUTE}/{${KomparaDestination.ARG_PAYWALL_SURFACE}}",
        arguments = listOf(
            navArgument(KomparaDestination.ARG_PAYWALL_SURFACE) { type = NavType.StringType },
        ),
    ) { entry ->
        val surfaceName = entry.arguments?.getString(KomparaDestination.ARG_PAYWALL_SURFACE)
        val surface = runCatching { GateSurface.valueOf(surfaceName ?: "") }
            .getOrDefault(GateSurface.GENERIC)
        PaywallScreen(surface = surface, onClose = { navController.popBackStack() }, onPlan = {
            navController.navigate(KomparaDestination.PLAN_ROUTE) { popUpTo(entry.destination.id) { inclusive = true }; launchSingleTop = true }
        })
    }
    composable(KomparaDestination.PLAN_ROUTE) {
        mx.kompara.ui.paywall.TuPlanScreen(onBack = { navController.popBackStack() }, onSubscribe = { navController.navigate("plan-subscribe") })
    }
    composable("plan-subscribe") {
        PaywallScreen(onClose = { navController.popBackStack() }, purchaseExplanation = true,
            onPlan = { navController.popBackStack(KomparaDestination.PLAN_ROUTE, false) })
    }
    importDestination(navController)
    // B-056 referral / "Invita y gana" flow.
    referralDestination(navController)
    // Day detail with no arg → today.
    composable(KomparaDestination.DAY_DETAIL_ROUTE) { DayDetailScreen(onBack = { navController.popBackStack() }, onLimits = { navController.navigate("thresholds") }) }
    composable(
        route = "${KomparaDestination.DAY_DETAIL_ROUTE}/{${DayDetailViewModel.ARG_DAY}}?offerId={offerId}&ledger={ledger}",
        arguments = listOf(navArgument(DayDetailViewModel.ARG_DAY) { type = NavType.StringType },
            navArgument("offerId") { type = NavType.LongType; defaultValue = -1L },
            navArgument("ledger") { type = NavType.BoolType; defaultValue = false }),
    ) { DayDetailScreen(onBack = { navController.popBackStack() }, onLimits = { navController.navigate("thresholds") }) }
    composable(
        route = "${KomparaDestination.WEEK_SUMMARY_ROUTE}/{${WeekSummaryViewModel.ARG_WEEK_START}}",
        arguments = listOf(
            navArgument(WeekSummaryViewModel.ARG_WEEK_START) { type = NavType.StringType },
        ),
    ) {
        WeekSummaryScreen(
            onImportReview = { navController.navigate("import-review/$it") },
            onDay = { navController.navigate(KomparaDestination.dayRoute(it.toString())) },
            onMonth = { navController.selectRoot(KomparaDestination.IMSS, KomparaDestination.GANANCIAS) },
            onUpgrade = { navController.navigateToPaywall(GateSurface.HISTORY) },
            // PR-E: a premium-but-unverified driver's benchmark gate here routes to import-to-verify.
            onImport = { navController.navigate(KomparaDestination.IMPORT_ROUTE) },
        )
    }
    // One share task retains the opener's period and comparison context. S19 migrates notification payloads.
    composable("${KomparaDestination.SHARE_CARD_ROUTE}?weekStart={weekStart}&month={month}&app={app}&city={city}&metric={metric}",
        arguments = listOf(navArgument("weekStart") { type = NavType.StringType; defaultValue = "" },
            navArgument("month") { type = NavType.StringType; defaultValue = "" },
            navArgument("app") { type = NavType.StringType; defaultValue = "UBER" },
            navArgument("city") { type = NavType.StringType; defaultValue = "" },
            navArgument("metric") { type = NavType.StringType; defaultValue = "PER_TRIP" })) { ShareCardScreen(onClose = { navController.popBackStack() }) }
}

@Preview(showBackground = true, name = "KomparaApp shell")
@Composable
private fun KomparaAppPreview() {
    KomparaTheme { KomparaApp() }
}
