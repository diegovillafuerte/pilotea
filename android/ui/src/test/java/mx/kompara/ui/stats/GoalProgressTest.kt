package mx.kompara.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goal-progress and streak-display logic (B-040 / B-039 goals & streaks). */
class GoalProgressTest {

    @Test
    fun `no goal set has no bar`() {
        val g = GoalProgress.of(goalMxn = null, netMxn = 1234.0)
        assertFalse(g.hasGoal)
        assertEquals(0f, g.fraction, 0f)
    }

    @Test
    fun `partial progress reports fraction and remaining`() {
        val g = GoalProgress.of(goalMxn = 5000.0, netMxn = 3450.0)
        assertTrue(g.hasGoal)
        assertEquals(0.69f, g.fraction, 0.01f)
        assertFalse(g.reached)
        assertEquals(1550.0, g.remainingMxn, 0.001)
    }

    @Test
    fun `meeting or beating the goal clamps fraction to 1 and zeroes remaining`() {
        val g = GoalProgress.of(goalMxn = 5000.0, netMxn = 6000.0)
        assertEquals(1f, g.fraction, 0f)
        assertTrue(g.reached)
        assertEquals(0.0, g.remainingMxn, 0.0)
    }

    @Test
    fun `negative net floors fraction at zero`() {
        val g = GoalProgress.of(goalMxn = 5000.0, netMxn = -200.0)
        assertEquals(0f, g.fraction, 0f)
        assertFalse(g.reached)
    }

    @Test
    fun `streak hidden at zero, singular at one, plural beyond`() {
        assertFalse(StreakDisplay(0).visible)
        assertTrue(StreakDisplay(1).visible)
        assertTrue(StreakDisplay(1).singular)
        assertTrue(StreakDisplay(4).visible)
        assertFalse(StreakDisplay(4).singular)
    }
}
