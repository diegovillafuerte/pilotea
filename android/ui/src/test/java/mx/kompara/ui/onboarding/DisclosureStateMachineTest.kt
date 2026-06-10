package mx.kompara.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accept/decline state machine for the prominent disclosure gate (B-036). */
class DisclosureStateMachineTest {

    @Test
    fun `starts pending and blocks the accessibility grant`() {
        val sm = DisclosureStateMachine()
        assertEquals(DisclosureDecision.PENDING, sm.decision)
        assertFalse(sm.mayProceedToAccessibility)
        assertFalse(sm.shouldShowLimitedInfo)
    }

    @Test
    fun `accept unlocks the accessibility grant`() {
        val sm = DisclosureStateMachine()
        assertTrue(sm.accept())
        assertEquals(DisclosureDecision.ACCEPTED, sm.decision)
        assertTrue(sm.mayProceedToAccessibility)
        assertFalse(sm.shouldShowLimitedInfo)
    }

    @Test
    fun `repeated accept is a no-op transition`() {
        val sm = DisclosureStateMachine()
        assertTrue(sm.accept())
        assertFalse("second accept should not re-fire", sm.accept())
        assertTrue(sm.mayProceedToAccessibility)
    }

    @Test
    fun `decline routes to limited info and never unlocks the grant`() {
        val sm = DisclosureStateMachine()
        assertTrue(sm.decline())
        assertEquals(DisclosureDecision.DECLINED, sm.decision)
        assertFalse(sm.mayProceedToAccessibility)
        assertTrue(sm.shouldShowLimitedInfo)
    }

    @Test
    fun `repeated decline is a no-op transition`() {
        val sm = DisclosureStateMachine()
        assertTrue(sm.decline())
        assertFalse(sm.decline())
    }

    @Test
    fun `driver can change their mind from decline to accept`() {
        val sm = DisclosureStateMachine()
        sm.decline()
        assertTrue(sm.accept())
        assertTrue(sm.mayProceedToAccessibility)
        assertFalse(sm.shouldShowLimitedInfo)
    }
}
