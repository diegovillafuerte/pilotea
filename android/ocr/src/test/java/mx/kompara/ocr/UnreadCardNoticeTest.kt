package mx.kompara.ocr

import mx.kompara.ocr.UnreadCardNotice.Action.HEARTBEAT
import mx.kompara.ocr.UnreadCardNotice.Action.HIDE
import mx.kompara.ocr.UnreadCardNotice.Action.NONE
import mx.kompara.ocr.UnreadCardNotice.Action.SHOW
import mx.kompara.ocr.UnreadCardNotice.Action.SOFT_HIDE
import mx.kompara.ocr.UnreadCardNotice.Frame.ABSENT
import mx.kompara.ocr.UnreadCardNotice.Frame.ABSENT_CLEAR
import mx.kompara.ocr.UnreadCardNotice.Frame.ABSENT_UNDER_CHIP
import mx.kompara.ocr.UnreadCardNotice.Frame.MASKED
import mx.kompara.ocr.UnreadCardNotice.Frame.SIGNATURE
import mx.kompara.ocr.UnreadCardNotice.Frame.UNREAD
import mx.kompara.ocr.UnreadCardNotice.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-AD: when the grey unread notice shows for a card no frame can publish. Frame kinds and monotonic time
 * in, what the overlay should do out; the numbers are [CardPresenceTracker]'s (3 frames over 1,800 ms, 15 s).
 */
class UnreadCardNoticeTest {
    private val notice = UnreadCardNotice()

    /** Feeds [frames] at [atMs] and returns the actions. */
    private fun feed(vararg frames: Pair<UnreadCardNotice.Frame, Long>) = frames.map { (frame, at) -> notice.onFrame(frame, at) }

    /** Shows the notice with three unread frames at the phone's 900 ms cadence, ending at [endMs]. */
    private fun shown(endMs: Long = 1_800L) {
        assertEquals(listOf(NONE, NONE, SHOW), feed(UNREAD to endMs - 1_800, UNREAD to endMs - 900, UNREAD to endMs))
        assertEquals(State.SHOWN, notice.state)
    }

    @Test fun `the rule uses the presence tracker's numbers`() {
        assertEquals(3, CardPresenceTracker.DEFAULT_GONE_FRAMES)
        assertEquals(1_800L, CardPresenceTracker.DEFAULT_ABSENCE_SPAN_MS)
        assertEquals(15_000L, CardPresenceTracker.DEFAULT_PARSE_CEILING_MS)
    }

    @Test fun `three unread frames spanning 1,800 ms show it, two frames do not`() {
        assertEquals(listOf(NONE, NONE), feed(UNREAD to 0, UNREAD to 900))
        assertEquals(State.PENDING, notice.state)
        assertEquals(SHOW, notice.onFrame(UNREAD, 1_800))
        assertEquals(3, notice.shownAfterFrames)
        assertEquals(1_800L, notice.shownAfterMs)
    }

    @Test fun `three frames in 700 ms show nothing until the run spans 1,800 ms`() {
        assertEquals(listOf(NONE, NONE, NONE), feed(UNREAD to 0, UNREAD to 350, UNREAD to 700))
        assertEquals(NONE, notice.onFrame(UNREAD, 1_799))
        assertEquals(SHOW, notice.onFrame(UNREAD, 1_800))
        assertEquals(5, notice.shownAfterFrames)
    }

    @Test fun `at the S25's 850 ms cadence it shows on the fourth unread frame`() {
        assertEquals(listOf(NONE, NONE, NONE, SHOW), feed(UNREAD to 0, UNREAD to 850, UNREAD to 1_700, UNREAD to 2_550))
    }

    @Test fun `a signature frame that is not unread neither counts nor breaks the run`() {
        assertEquals(listOf(NONE, NONE, NONE), feed(UNREAD to 0, SIGNATURE to 900, UNREAD to 1_800))
        assertEquals("Two unread frames so far", State.PENDING, notice.state)
        assertEquals(SHOW, notice.onFrame(UNREAD, 2_700))
        assertEquals(3, notice.shownAfterFrames)
        assertEquals(2_700L, notice.shownAfterMs)
    }

    @Test fun `a signature-free frame breaks the run`() {
        for (gap in listOf(ABSENT, ABSENT_UNDER_CHIP, ABSENT_CLEAR)) {
            val notice = UnreadCardNotice()
            val actions = listOf(UNREAD to 0L, UNREAD to 900L, gap to 1_800L, UNREAD to 2_700L, UNREAD to 3_600L)
                .map { (frame, at) -> notice.onFrame(frame, at) }
            assertEquals("$gap", List(5) { NONE }, actions)
            assertEquals("$gap: the run restarted at 2.7 s", SHOW, notice.onFrame(UNREAD, 4_500))
        }
    }

    @Test fun `while shown every unread frame shows it, a card frame beats and a signature-free frame soft-hides`() {
        shown()
        assertEquals(listOf(SHOW, HEARTBEAT, SHOW), feed(UNREAD to 2_700, SIGNATURE to 3_600, UNREAD to 4_500))
        assertEquals(SOFT_HIDE, notice.onFrame(ABSENT, 5_400))
        assertEquals("Still shown (soft-hidden), not ended", State.SHOWN, notice.state)
        assertEquals("An unread frame after a soft hide shows it again at once", SHOW, notice.onFrame(UNREAD, 6_300))
    }

    @Test fun `absence corroborated like a session's hides it for good and resets the run`() {
        shown()
        assertEquals(listOf(SOFT_HIDE, SOFT_HIDE, SOFT_HIDE, HIDE),
            feed(ABSENT to 2_700, ABSENT to 3_300, ABSENT to 3_900, ABSENT to 4_500))
        assertEquals(State.ABSENT, notice.state)
        assertEquals("The run starts over", listOf(NONE, NONE, SHOW), feed(UNREAD to 5_400, UNREAD to 6_300, UNREAD to 7_200))
    }

    @Test fun `a card frame or one under our own chip breaks absence agreement`() {
        shown()
        assertEquals(listOf(SOFT_HIDE, SOFT_HIDE, HEARTBEAT, SOFT_HIDE, SOFT_HIDE),
            feed(ABSENT to 2_700, ABSENT to 3_600, SIGNATURE to 4_500, ABSENT to 5_400, ABSENT to 6_300))
        assertEquals("Under our chip the missing signature may be ours: it never counts as absence",
            listOf(SOFT_HIDE, SOFT_HIDE), feed(ABSENT_UNDER_CHIP to 7_200, ABSENT to 8_100))
        assertEquals(State.SHOWN, notice.state)
        assertEquals(listOf(SOFT_HIDE, HIDE), feed(ABSENT to 9_000, ABSENT to 9_900))
    }

    @Test fun `the frame's own clear absence hides it at once`() {
        shown()
        assertEquals(HIDE, notice.onFrame(ABSENT_CLEAR, 2_700))
        assertEquals(State.ABSENT, notice.state)
        assertEquals("Nothing to hide twice", NONE, notice.onFrame(ABSENT_CLEAR, 3_600))
    }

    @Test fun `it is retired 15 s after it first showed and does not return until the signature was absent`() {
        shown(endMs = 1_800)
        var at = 1_800L
        // Soft hides and returns do not restart the cap.
        assertEquals(SOFT_HIDE, notice.onFrame(ABSENT, 2_700))
        while (at + 900 < 1_800 + 15_000) { at += 900; assertEquals("at $at", SHOW, notice.onFrame(UNREAD, at)) }
        assertEquals(HIDE, notice.onFrame(UNREAD, 16_800))
        assertEquals(State.RETIRED, notice.state)
        // A card that is still there, read or not, never brings it back.
        assertEquals(List(40) { NONE }, (1..40).map { notice.onFrame(if (it % 3 == 0) SIGNATURE else UNREAD, 16_800L + it * 900) })
        // One signature-free frame, or two, is not absence.
        assertEquals(listOf(NONE, NONE, NONE, NONE, NONE, NONE),
            feed(ABSENT to 60_000, UNREAD to 60_900, ABSENT to 61_800, ABSENT to 62_700, UNREAD to 63_600, UNREAD to 64_500))
        assertEquals(State.RETIRED, notice.state)
        // Corroborated absence clears it: the next card earns the notice again.
        assertEquals(listOf(NONE, NONE, NONE), feed(ABSENT to 70_000, ABSENT to 70_900, ABSENT to 71_800))
        assertEquals(State.ABSENT, notice.state)
        assertEquals(listOf(NONE, NONE, SHOW), feed(UNREAD to 72_700, UNREAD to 73_600, UNREAD to 74_500))
    }

    @Test fun `the cap also retires a notice whose frames turned to card frames or absence`() {
        for (last in listOf(SIGNATURE, ABSENT, ABSENT_UNDER_CHIP)) {
            val notice = UnreadCardNotice()
            listOf(0L, 900L, 1_800L).forEach { notice.onFrame(UNREAD, it) }
            assertEquals("$last", HIDE, notice.onFrame(last, 16_800))
            assertEquals("$last", State.RETIRED, notice.state)
        }
        val clear = UnreadCardNotice()
        listOf(0L, 900L, 1_800L).forEach { clear.onFrame(UNREAD, it) }
        assertEquals(HIDE, clear.onFrame(ABSENT_CLEAR, 16_800))
        assertEquals("Clear absence at the cap: nothing to wait for", State.ABSENT, clear.state)
    }

    // Round 2 (Codex): retiring at the cap must keep the absence evidence already gathered.
    @Test fun `ordinary absence straddling the cap still counts, so the next card earns its notice`() {
        shown(endMs = 1_800)
        var at = 1_800L
        while (at + 900 <= 14_400) { at += 900; assertEquals(SHOW, notice.onFrame(UNREAD, at)) }
        // The card expires: three signature-free frames spanning 1.8 s, the third past the 16.8 s cap.
        assertEquals(listOf(SOFT_HIDE, SOFT_HIDE, HIDE), feed(ABSENT to 15_300, ABSENT to 16_200, ABSENT to 17_100))
        assertEquals("The absence was complete: nothing is retired", State.ABSENT, notice.state)
        assertEquals("The next unreadable card gets its notice after its own 3 frames and 1,800 ms",
            listOf(NONE, NONE, SHOW), feed(UNREAD to 18_000, UNREAD to 18_900, UNREAD to 19_800))
    }

    @Test fun `absence still needs its span when the cap falls inside it`() {
        shown(endMs = 1_800)
        assertEquals(listOf(SOFT_HIDE, SOFT_HIDE), feed(ABSENT to 15_900, ABSENT to 16_500))
        assertEquals("The cap, with two misses over 0.9 s", HIDE, notice.onFrame(ABSENT, 16_800))
        assertEquals(State.RETIRED, notice.state)
        assertEquals("The third miss 1.8 s after the first clears it", NONE, notice.onFrame(ABSENT, 17_700))
        assertEquals(State.ABSENT, notice.state)
    }

    // Round 3b: a frame rejected as PARSED_CARD_MASKED with no session: the notice's own mask is on the card.
    @Test fun `a masked frame ends a shown notice at once and the run starts over`() {
        shown()
        assertEquals(HIDE, notice.onFrame(MASKED, 2_700))
        assertEquals("Not retired: the next unmasked frame decides", State.ABSENT, notice.state)
        assertEquals(listOf(NONE, NONE, SHOW), feed(UNREAD to 3_600, UNREAD to 4_500, UNREAD to 5_400))
    }

    @Test fun `a masked frame ends a soft-hidden notice too`() {
        shown()
        assertEquals(SOFT_HIDE, notice.onFrame(ABSENT, 2_700))
        assertEquals(HIDE, notice.onFrame(MASKED, 3_600))
        assertEquals(State.ABSENT, notice.state)
    }

    @Test fun `with no notice on screen a masked frame neither counts toward a run nor breaks one`() {
        assertEquals(NONE, notice.onFrame(MASKED, 0))
        assertEquals("Absent stays absent", State.ABSENT, notice.state)
        assertEquals(listOf(NONE, NONE, NONE), feed(UNREAD to 900, MASKED to 1_350, UNREAD to 1_800))
        assertEquals("Two unread frames, the masked one not counted", State.PENDING, notice.state)
        assertEquals(SHOW, notice.onFrame(UNREAD, 2_700))
        assertEquals(3, notice.shownAfterFrames)
    }

    @Test fun `a retired notice stays retired through a masked frame, which breaks absence like a card frame`() {
        shown(endMs = 1_800)
        assertEquals(HIDE, notice.onFrame(UNREAD, 16_800))
        assertEquals(State.RETIRED, notice.state)
        assertEquals(listOf(NONE, NONE, NONE), feed(ABSENT to 17_700, ABSENT to 18_600, MASKED to 19_000))
        assertEquals(State.RETIRED, notice.state)
        assertEquals(listOf(NONE, NONE), feed(ABSENT to 19_500, ABSENT to 20_400))
        assertEquals("Absence counted afresh after the masked frame", State.RETIRED, notice.state)
        assertEquals(NONE, notice.onFrame(ABSENT, 21_300))
        assertEquals(State.ABSENT, notice.state)
    }

    @Test fun `a masked frame at the cap retires the notice like any other frame there`() {
        shown(endMs = 1_800)
        assertEquals(HIDE, notice.onFrame(MASKED, 16_800))
        assertEquals(State.RETIRED, notice.state)
    }

    @Test fun `reset at a boundary clears the run and says whether a notice was on screen`() {
        assertFalse(notice.reset())
        feed(UNREAD to 0, UNREAD to 900)
        assertFalse("Pending is not on screen", notice.reset())
        assertEquals(State.ABSENT, notice.state)
        assertEquals("The run starts over", listOf(NONE, NONE, SHOW), feed(UNREAD to 1_800, UNREAD to 2_700, UNREAD to 3_600))
        assertEquals(SOFT_HIDE, notice.onFrame(ABSENT, 4_500))
        assertTrue("Soft-hidden is still on screen for the overlay", notice.reset())
        assertEquals(State.ABSENT, notice.state)
        assertEquals(NONE, notice.onFrame(UNREAD, 5_400))
        assertEquals(State.PENDING, notice.state)
    }

    @Test fun `reset also ends a retirement`() {
        shown()
        notice.onFrame(UNREAD, 16_800)
        assertEquals(State.RETIRED, notice.state)
        assertFalse("A retired notice is off screen", notice.reset())
        assertEquals(listOf(NONE, NONE, SHOW), feed(UNREAD to 20_000, UNREAD to 20_900, UNREAD to 21_800))
    }

    @Test fun `retire holds a pending run back until the signature was absent`() {
        feed(UNREAD to 0, UNREAD to 900)
        notice.retire()
        assertEquals(State.RETIRED, notice.state)
        assertEquals(List(5) { NONE }, (1..5).map { notice.onFrame(UNREAD, 900L + it * 900) })
        assertEquals(listOf(NONE, NONE, NONE), feed(ABSENT to 10_000, ABSENT to 10_900, ABSENT to 11_800))
        assertEquals(listOf(NONE, NONE, SHOW), feed(UNREAD to 12_700, UNREAD to 13_600, UNREAD to 14_500))
    }

    @Test fun `diagnostic codes are numbers`() {
        assertEquals(listOf(0, 1, 2, 3), listOf(State.ABSENT, State.PENDING, State.SHOWN, State.RETIRED).map { it.code })
        shown(endMs = 1_800)
        assertEquals(900L, notice.shownForMs(2_700))
    }
}
