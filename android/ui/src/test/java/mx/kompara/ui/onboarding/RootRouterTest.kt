package mx.kompara.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** Root routing from the persisted onboarding-completed flag (B-036). */
class RootRouterTest {

    @Test
    fun `null flag (settings loading) routes to LOADING`() {
        assertEquals(RootRoute.LOADING, RootRouter.route(null))
    }

    @Test
    fun `incomplete onboarding routes to ONBOARDING`() {
        assertEquals(RootRoute.ONBOARDING, RootRouter.route(false))
    }

    @Test
    fun `completed onboarding routes to MAIN`() {
        assertEquals(RootRoute.MAIN, RootRouter.route(true))
    }
}
