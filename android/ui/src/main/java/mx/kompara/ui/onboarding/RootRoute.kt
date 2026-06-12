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
}
