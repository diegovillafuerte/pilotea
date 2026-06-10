package mx.kompara.data.rollup

import org.junit.Assert.assertEquals
import org.junit.Test

/** Consecutive-weeks streak math for [StreakCalculator] (B-039). Pure JVM. */
class StreakCalculatorTest {

    private val calc = StreakCalculator()

    @Test
    fun `empty input is a zero streak`() {
        assertEquals(0, calc.streak(emptyList()))
    }

    @Test
    fun `three consecutive weeks count as a streak of three`() {
        val weeks = listOf("2026-05-25", "2026-06-01", "2026-06-08")
        assertEquals(3, calc.streak(weeks))
    }

    @Test
    fun `a gap breaks the streak and only the run ending at the latest week counts`() {
        // 2026-05-04, then a gap (missing 05-11), then 05-18, 05-25, 06-01.
        val weeks = listOf("2026-05-04", "2026-05-18", "2026-05-25", "2026-06-01")
        assertEquals(3, calc.streak(weeks)) // ends at 06-01: 06-01, 05-25, 05-18, breaks at 05-11
    }

    @Test
    fun `duplicate week starts from multiple platforms collapse to one`() {
        val weeks = listOf("2026-06-01", "2026-06-01", "2026-06-08", "2026-06-08")
        assertEquals(2, calc.streak(weeks))
    }

    @Test
    fun `a single week is a streak of one`() {
        assertEquals(1, calc.streak(listOf("2026-06-08")))
    }

    @Test
    fun `unparseable entries are ignored`() {
        val weeks = listOf("garbage", "2026-06-08", "2026-06-01")
        assertEquals(2, calc.streak(weeks))
    }

    @Test
    fun `unordered input still anchors on the latest week`() {
        val weeks = listOf("2026-06-08", "2026-05-25", "2026-06-01")
        assertEquals(3, calc.streak(weeks))
    }
}
