package mx.kompara.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** Root routing from the persisted onboarding-completed flag + auth session (B-036). */
class RootRouterTest {

    @Test
    fun `null onboarding flag (settings loading) routes to LOADING`() {
        assertEquals(RootRoute.LOADING, RootRouter.route(null, authenticated = true))
    }

    @Test
    fun `null session (auth loading) routes to LOADING`() {
        assertEquals(RootRoute.LOADING, RootRouter.route(onboardingCompleted = true, authenticated = null))
    }

    @Test
    fun `incomplete onboarding routes to ONBOARDING regardless of session`() {
        assertEquals(RootRoute.ONBOARDING, RootRouter.route(false, authenticated = false))
        // Mid-onboarding signup already verified, app restarted: still finish the funnel.
        assertEquals(RootRoute.ONBOARDING, RootRouter.route(false, authenticated = true))
    }

    @Test
    fun `completed onboarding without a session routes to AUTH`() {
        // Pre-account installs and logged-out drivers must sign up before the shell.
        assertEquals(RootRoute.AUTH, RootRouter.route(true, authenticated = false))
    }

    @Test
    fun `completed onboarding with a session routes to MAIN`() {
        assertEquals(RootRoute.MAIN, RootRouter.route(true, authenticated = true))
    }

    @Test
    fun `effectiveRoute holds AUTH while the signup flow is active even though the session flipped to MAIN`() {
        // B-069 item 4: OTP verified mid-flow → route is MAIN, but the profile step hasn't shown.
        assertEquals(RootRoute.AUTH, RootRouter.effectiveRoute(RootRoute.MAIN, authFlowActive = true))
    }

    @Test
    fun `effectiveRoute releases to MAIN once the flow finishes`() {
        assertEquals(RootRoute.MAIN, RootRouter.effectiveRoute(RootRoute.MAIN, authFlowActive = false))
    }

    @Test
    fun `effectiveRoute never overrides a non-MAIN route`() {
        // A returning, already-authenticated user (latch off) and the genuine AUTH/ONBOARDING/LOADING
        // states all pass through untouched.
        assertEquals(RootRoute.AUTH, RootRouter.effectiveRoute(RootRoute.AUTH, authFlowActive = true))
        assertEquals(RootRoute.ONBOARDING, RootRouter.effectiveRoute(RootRoute.ONBOARDING, authFlowActive = true))
        assertEquals(RootRoute.LOADING, RootRouter.effectiveRoute(RootRoute.LOADING, authFlowActive = true))
    }
}
