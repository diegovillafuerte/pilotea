package mx.kompara.ui.onboarding

/**
 * Where the app root should send the user on launch. The root composable observes
 * [mx.kompara.data.settings.Settings.onboardingCompleted] plus the auth session state and renders
 * one of these.
 */
enum class RootRoute {
    /** Still deciding — settings/session haven't loaded from DataStore yet. Show nothing/splash. */
    LOADING,

    /** Onboarding not completed — show the onboarding nav graph (which includes signup). */
    ONBOARDING,

    /**
     * Onboarding completed but no account session — show the standalone signup flow. Covers
     * installs that finished onboarding before accounts became required, and logged-out sessions.
     */
    AUTH,

    /** Onboarding completed and signed in — show the main 5-tab shell. */
    MAIN,
}

/**
 * Pure routing decision so the "flags → which root" logic is unit-tested without Compose.
 *
 * @param onboardingCompleted the persisted flag, or null while settings are still loading.
 * @param authenticated whether a session token + driver profile exist, or null while loading.
 */
object RootRouter {
    fun route(onboardingCompleted: Boolean?, authenticated: Boolean?): RootRoute = when {
        onboardingCompleted == null || authenticated == null -> RootRoute.LOADING
        !onboardingCompleted -> RootRoute.ONBOARDING
        !authenticated -> RootRoute.AUTH
        else -> RootRoute.MAIN
    }

    /**
     * Holds the standalone signup gate open until the WHOLE flow finishes (B-069 item 4). The session —
     * and thus [route] — flips to [RootRoute.MAIN] the moment the OTP verifies, but the profile
     * (name/ciudad) step hasn't shown yet. While [authFlowActive] (set once we've entered AUTH this
     * session, cleared by the flow's onComplete), keep rendering AUTH instead of MAIN so the profile
     * step runs. ONBOARDING/LOADING are passed through untouched (a returning, already-authenticated
     * user — authFlowActive false — goes straight to MAIN; only an in-session signup is held).
     */
    fun effectiveRoute(route: RootRoute, authFlowActive: Boolean): RootRoute =
        if (authFlowActive && route == RootRoute.MAIN) RootRoute.AUTH else route
}
