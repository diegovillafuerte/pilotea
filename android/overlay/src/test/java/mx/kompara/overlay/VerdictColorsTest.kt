package mx.kompara.overlay

import mx.kompara.metrics.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VerdictColorsTest {

    @Test
    fun `each verdict maps to its traffic-light colour`() {
        assertEquals(VerdictColors.Good, VerdictColors.forVerdict(Verdict.GOOD))
        assertEquals(VerdictColors.Marginal, VerdictColors.forVerdict(Verdict.MARGINAL))
        assertEquals(VerdictColors.Bad, VerdictColors.forVerdict(Verdict.BAD))
    }

    @Test
    fun `colours are distinct`() {
        assertNotEquals(VerdictColors.Good, VerdictColors.Marginal)
        assertNotEquals(VerdictColors.Marginal, VerdictColors.Bad)
        assertNotEquals(VerdictColors.Good, VerdictColors.Bad)
    }
}
