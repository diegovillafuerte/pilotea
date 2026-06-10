package mx.kompara.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** Completeness-hint logic for [CompletenessHints] (B-040). */
class CompletenessHintsTest {

    @Test
    fun `no trips means no hint`() {
        assertEquals(
            CompletenessHint.NONE,
            CompletenessHints.hintFor(hasTrips = false, hoursOnline = 0.0, tripsEstimated = true),
        )
    }

    @Test
    fun `realized (non-estimated) trips are not flagged as inferred`() {
        assertEquals(
            CompletenessHint.NONE,
            CompletenessHints.hintFor(hasTrips = true, hoursOnline = 5.0, tripsEstimated = false),
        )
    }

    @Test
    fun `estimated trips with hours flag the inferred-hours hint`() {
        assertEquals(
            CompletenessHint.HOURS_INFERRED,
            CompletenessHints.hintFor(hasTrips = true, hoursOnline = 5.0, tripsEstimated = true),
        )
    }

    @Test
    fun `estimated trips with no shift flag the missing-hours hint`() {
        assertEquals(
            CompletenessHint.HOURS_MISSING,
            CompletenessHints.hintFor(hasTrips = true, hoursOnline = 0.0, tripsEstimated = true),
        )
    }
}
