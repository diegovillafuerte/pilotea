package mx.kompara.ui.onboarding

/**
 * Where the app root should send the user on launch. The root composable observes
 * [mx.kompara.data.settings.Settings.onboardingCompleted] and renders one of these.
 */
enum class RootRoute {
    /** Still deciding — settings haven't loaded from DataStore yet. Show nothing/splash. */
    LOADING,

    /** Onboarding not completed — show the onboarding nav graph. */
    ONBOARDING,

    /** Onboarding completed — show the main 5-tab shell. */
    MAIN,
}

/**
 * Pure routing decision so the "completed flag → which root" logic is unit-tested without Compose.
 *
 * @param onboardingCompleted the persisted flag, or null while settings are still loading.
 */
object RootRouter {
    fun route(onboardingCompleted: Boolean?): RootRoute = when (onboardingCompleted) {
        null -> RootRoute.LOADING
        true -> RootRoute.MAIN
        false -> RootRoute.ONBOARDING
    }
}
