package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chip visibility and selection resolution for [PlatformSelection] (B-040). */
class PlatformSelectionTest {

    private fun row(platform: Platform) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = "2026-06-08",
        source = AggregateSource.CAPTURED.name,
        netEarningsMxn = 100.0,
        grossEarningsMxn = 120.0,
        totalTrips = 1,
        totalKm = 10.0,
        hoursOnline = 1.0,
        earningsPerTrip = 100.0,
        earningsPerKm = 10.0,
        earningsPerHour = 100.0,
        tripsPerHour = 1.0,
        acceptanceRate = null,
        computedAt = 0L,
    )

    @Test
    fun `no chips when zero or one platform has data`() {
        assertTrue(PlatformSelection.chips(emptyList()).isEmpty())
        assertTrue(PlatformSelection.chips(listOf(row(Platform.UBER))).isEmpty())
    }

    @Test
    fun `chips lead with Todas then platforms in declaration order`() {
        val chips = PlatformSelection.chips(listOf(row(Platform.DIDI), row(Platform.UBER)))
        assertEquals(listOf(null, Platform.UBER, Platform.DIDI), chips)
    }

    @Test
    fun `single platform resolves to that platform regardless of selection`() {
        val rows = listOf(row(Platform.UBER))
        assertEquals(Platform.UBER, PlatformSelection.resolve(rows, selected = null))
        assertEquals(Platform.UBER, PlatformSelection.resolve(rows, selected = Platform.DIDI))
    }

    @Test
    fun `multi-platform keeps a valid selection but falls back to Todas otherwise`() {
        val rows = listOf(row(Platform.UBER), row(Platform.DIDI))
        assertEquals(Platform.DIDI, PlatformSelection.resolve(rows, selected = Platform.DIDI))
        // Selected a platform with no data → fall back to "Todas" (null).
        assertNull(PlatformSelection.resolve(rows, selected = Platform.INDRIVE))
        assertNull(PlatformSelection.resolve(rows, selected = null))
    }

    @Test
    fun `no data resolves to null`() {
        assertNull(PlatformSelection.resolve(emptyList(), selected = Platform.UBER))
    }
}
