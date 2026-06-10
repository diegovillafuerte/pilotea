package mx.kompara.overlay

import mx.kompara.metrics.VerdictLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VerdictColorsTest {

    @Test
    fun `each verdict level maps to its traffic-light colour`() {
        assertEquals(VerdictColors.Good, VerdictColors.forVerdict(VerdictLevel.GREEN))
        assertEquals(VerdictColors.Marginal, VerdictColors.forVerdict(VerdictLevel.YELLOW))
        assertEquals(VerdictColors.Bad, VerdictColors.forVerdict(VerdictLevel.RED))
    }

    @Test
    fun `colours are distinct`() {
        assertNotEquals(VerdictColors.Good, VerdictColors.Marginal)
        assertNotEquals(VerdictColors.Marginal, VerdictColors.Bad)
        assertNotEquals(VerdictColors.Good, VerdictColors.Bad)
    }
}
