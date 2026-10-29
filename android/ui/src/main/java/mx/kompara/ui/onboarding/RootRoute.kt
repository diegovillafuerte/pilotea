package mx.kompara.ui.onboarding

/**
 * Where the app root should send the user on launch. The root composable observes
 * [mx.kompara.data.settings.Settings.onboardingCompleted] and renders
 * one of these.
 */
enum class RootRoute {
    /** Still deciding — settings haven't loaded from DataStore yet. Show nothing/splash. */
    LOADING,

    /** Onboarding not completed — show the onboarding nav graph (with optional contextual signup). */
    ONBOARDING,

    /** Standalone signup entered explicitly for an account feature; local reading needs no login. */
    AUTH,

    /** Onboarding completed — show the main three-tab shell, including anonymous sessions. */
    MAIN,
}

/**
 * Pure routing decision so the "flags → which root" logic is unit-tested without Compose.
 *
 * @param onboardingCompleted the persisted flag, or null while settings are still loading.
 * @param authenticated retained for callers; identity never gates local use.
 */
object RootRouter {
    fun route(onboardingCompleted: Boolean?, authenticated: Boolean?): RootRoute = when {
        onboardingCompleted == null -> RootRoute.LOADING
        !onboardingCompleted -> RootRoute.ONBOARDING
        else -> RootRoute.MAIN
    }

    /**
     * Keeps an explicitly entered standalone signup open until its caller receives the OTP result.
     * ONBOARDING/LOADING pass through untouched; ordinary anonymous sessions still use MAIN.
     */
    fun effectiveRoute(route: RootRoute, authFlowActive: Boolean): RootRoute =
        if (authFlowActive && route == RootRoute.MAIN) RootRoute.AUTH else route
}
