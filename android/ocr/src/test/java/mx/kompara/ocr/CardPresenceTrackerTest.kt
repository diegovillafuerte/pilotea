package mx.kompara.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPresenceTrackerTest {

    private val tracker = CardPresenceTracker(goneFrames = 3, absenceSpanMs = 600L)

    @Test
    fun `garbled card-like frames never hide the chip`() {
        tracker.onParsed(nowMs = 0L)
        // A long run of OCR garble while the card is still up (map animating underneath).
        repeat(20) { i ->
            assertFalse(tracker.onMiss(cardLike = true, nowMs = 300L * (i + 1)))
        }
    }

    @Test
    fun `dismissal hides after goneFrames signature-free frames`() {
        tracker.onParsed(nowMs = 0L)
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 300L))
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 600L))
        assertTrue(tracker.onMiss(cardLike = false, nowMs = 900L))
        // Once gone, further misses are no-ops until the next parse.
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 1200L))
    }

    @Test
    fun `card-like frames reset the miss streak`() {
        tracker.onParsed(nowMs = 0L)
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 300L))
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 600L))
        // A garbled-but-card-like frame interrupts the dismissal count...
        assertFalse(tracker.onMiss(cardLike = true, nowMs = 900L))
        // ...so the streak starts over.
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 1200L))
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 1500L))
        assertTrue(tracker.onMiss(cardLike = false, nowMs = 1800L))
    }

    @Test
    fun `a fresh parse resets the streak and re-arms tracking`() {
        tracker.onParsed(nowMs = 0L)
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 300L))
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 600L))
        tracker.onParsed(nowMs = 900L)
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 1200L))
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 1500L))
        assertTrue(tracker.onMiss(cardLike = false, nowMs = 1800L))
    }

    @Test
    fun `elapsed time never proves absence through card-like frames`() {
        tracker.onParsed(nowMs = 0L)
        assertFalse(tracker.onMiss(cardLike = true, nowMs = 9_999L))
        // A timeout alone is not evidence of disappearance.
        assertFalse(tracker.onMiss(cardLike = true, nowMs = 10_000L))
    }

    @Test fun `production policy requires both three misses and eighteen hundred milliseconds`() {
        val production = CardPresenceTracker()
        production.onParsed(0)
        assertFalse(production.onMiss(false, 900))
        assertFalse(production.onMiss(false, 1800))
        assertTrue(production.onMiss(false, 2700))
        production.onParsed(3000)
        repeat(10) { assertFalse(production.onMiss(false, 3100L + it * 100)) }
        assertTrue(production.onMiss(false, 4900))
    }

    @Test
    fun `misses before any parse are ignored`() {
        assertFalse(tracker.onMiss(cardLike = false, nowMs = 300L))
        assertFalse(tracker.onMiss(cardLike = true, nowMs = 600L))
    }
}
