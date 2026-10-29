package mx.kompara.ui.share

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ShareCardLayoutTest {
    @Test fun `single artefact is four by five with footer inset and separated facts`() {
        assertEquals(4f / 5f, ShareCardLayout.WIDTH.toFloat() / ShareCardLayout.HEIGHT, 0f)
        assertEquals(441.25f, ShareCardLayout.height(353f), 0f)
        assertEquals(361.25f, ShareCardLayout.factTop(441.25f, 2), 0f)
    }
    @Test fun `historical week month leap and year boundary resolve without current-month substitution`() {
        val today = LocalDate.parse("2026-10-29")
        assertEquals(LocalDate.parse("2026-10-05"), SharePeriod.resolve("2026-10-05", null, today).start)
        assertEquals(LocalDate.parse("2024-02-29"), SharePeriod.resolve(null, "2024-02", today).end)
        assertEquals(LocalDate.parse("2026-01-04"), SharePeriod.resolve("2025-12-29", null, today).end)
        assertEquals(LocalDate.parse("2026-10-19"), SharePeriod.resolve(null, null, today).start)
    }
    @Test fun `bad ambiguous future or non-Monday periods fail instead of sharing another period`() {
        val today = LocalDate.parse("2026-10-29")
        listOf("bad" to null, "2026-10-06" to null, "2026-10-05" to "2026-09", null to "2026-11", "2026-11-02" to null).forEach { (week, month) ->
            assertTrue(runCatching { SharePeriod.resolve(week, month, today) }.isFailure)
        }
    }
}
