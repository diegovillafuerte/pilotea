package mx.kompara.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * WP-AD (founder decision 2026-11-10): a card on screen that no frame can publish, with no session, gets the grey
 * "Sin lectura" notice once three unread frames have spanned 1,800 ms. A notice, never an offer: no session, ledger
 * row or lifecycle signal; its card carries placement only. The sanitized S25 `$109` frame with the TOTAL row
 * `$9.99/km` is contradictory on every frame (UberWholePesoFareTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferFramePipelineUnreadNoticeTest {
    @Before fun foreground() {
        ScreenReaderState.setForegroundHost(UberOcrParser.UBER_PACKAGE, android.os.SystemClock.elapsedRealtime())
    }

    private val uber = UberOcrParser.UBER_PACKAGE
    private val readable get() = UberWholePesoFrame.blocks
    private val contradictory get() = readable.retext(UberWholePesoFrame.TOTAL, "51 min 8.6 km \$9.99/km est.")
    /** The trip row unread on every frame (the fare still validates): no complete route, so no session. */
    private val routeUnread get() = readable.retext("47 min (7.8 km)", "4? min (?.8 km)")
    private val blank = emptyList<OcrBlock>()

    /** [staleToken]: captured before the reader's capture token changed (round 4); [before] runs just before the frame. */
    private class Frame(val blocks: List<OcrBlock>, val host: String = UberOcrParser.UBER_PACKAGE, val chip: ScreenRect? = null,
        val end: Boolean = false, val display: HostRect? = HostRect(0, 0, 1080, 2340), val staleToken: Boolean = false,
        val before: () -> Unit = {}, val afterMs: Long? = null)

    private class Run(val events: List<List<OfferEvent>>, val signals: List<LifecycleSignal>, val logs: List<String>) {
        fun notices(frame: Int) = events[frame].filterIsInstance<OfferEvent.Unreadable>()
        fun parsed(frame: Int) = events[frame].filterIsInstance<OfferEvent.Parsed>()
        fun diag(frame: Int) = logs.filter { it.startsWith("TEMP DIAG frame") }[frame]
        fun allNotices() = events.flatten().filterIsInstance<OfferEvent.Unreadable>()
    }

    private fun run(frames: List<Frame>, stepMs: Long = 900, readiness: ReaderReadinessRepository? = null): Run = runBlocking {
        ShadowLog.clear()
        val events = mutableListOf<OfferEvent>()
        val signals = mutableListOf<LifecycleSignal>()
        val collector = launch(Dispatchers.Unconfined) { OfferEventBus.events.collect { events += it } }
        val lifecycle = launch(Dispatchers.Unconfined) { OcrLifecycleBus.signals.collect { signals += it } }
        val perFrame = mutableListOf<List<OfferEvent>>()
        try {
            val pipeline = OfferFramePipeline(readiness)
            for (frame in frames) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(frame.afterMs ?: stepMs))
                frame.before()
                val before = events.size
                val token = (readiness?.captureToken() ?: 0L) - if (frame.staleToken) 1 else 0
                if (frame.end) pipeline.onCaptureEnd(System.currentTimeMillis())
                else pipeline.processOwnedFrame(frame.blocks, frame.chip, null, displayBounds = frame.display,
                    density = 3f, token = token, foregroundPackage = frame.host)
                yield()
                perFrame += events.drop(before)
            }
        } finally { collector.cancelAndJoin(); lifecycle.cancelAndJoin() }
        Run(perFrame, signals, ShadowLog.getLogsForTag("KomparaOCR").map { it.msg })
    }

    private fun frames(blocks: List<OcrBlock>, count: Int) = List(count) { Frame(blocks) }

    private fun assertPlacementOnly(event: OfferEvent.Unreadable) {
        val card = event.card
        assertEquals(uber, event.packageName)
        assertEquals(uber, card.platform)
        assertNotNull("Placement needs the host geometry", card.hostGeometry)
        assertEquals("The headline's pixels stay protected", UberWholePesoFrame.fareBounds, card.fareBounds)
        assertNotNull(card.contentBounds)
        assertNotNull(card.instanceEvidence)
        assertTrue("No payment, legs or identity: $card", card.fare == null && card.pickupEtaMin == null &&
            card.pickupDistanceKm == null && card.tripDurationMin == null && card.tripDistanceKm == null &&
            card.sessionId == null && card.sessionStartedAt == null && card.validation == null && card.raw.isEmpty() &&
            card.variant == null && card.paymentBasis == "UNCONFIRMED" && !card.surge && card.paymentType == null)
    }

    @Test fun `a contradictory card with no session gets the notice after three frames spanning 1,800 ms`() {
        val result = run(frames(contradictory, 6))
        for (i in 0..1) {
            assertEquals("f$i: nothing yet", emptyList<OfferEvent>(), result.events[i])
            assertTrue(result.diag(i), "rejection=PARSED_CONTRADICTORY" in result.diag(i) && "notice=1" in result.diag(i))
        }
        for (i in 2..5) {
            val notice = result.notices(i).single()
            assertEquals("f$i emits only the notice", listOf<OfferEvent>(notice), result.events[i])
            assertPlacementOnly(notice)
            assertTrue(result.diag(i), "notice=2" in result.diag(i))
        }
        assertEquals("No offer reaches the lifecycle", emptyList<LifecycleSignal>(), result.signals)
        assertEquals(1, result.logs.count { it.startsWith("offer visibility: UNREAD_NOTICE_SHOWN") })
        assertTrue(result.logs.toString(), result.logs.any { it == "offer visibility: UNREAD_NOTICE_SHOWN; frames=3 spanMs=1800" })
        for (line in result.logs) assertFalse("an amount or text logged: $line",
            "109" in line || "9.99" in line || "Colonia" in line || "Calle" in line)
    }

    @Test fun `at the S25's 850 ms cadence the fourth frame shows it`() {
        val result = run(frames(contradictory, 4), stepMs = 850)
        assertTrue((0..2).all { result.events[it].isEmpty() })
        assertEquals(1, result.notices(3).size)
    }

    @Test fun `when the card turns readable a normal Parsed with a session takes over and no notice follows`() {
        val shown = run(frames(contradictory, 3))
        val card = shown.notices(2).single().card.hostGeometry!!.offerCard
        // Our notice's chip sits above the card (the default slot is 16 dp above it, 48 px at density 3).
        val chip = ScreenRect(325, card.top - 48 - 330, 1040, card.top - 48)
        val result = run(frames(contradictory, 3) + List(4) { Frame(readable, chip = chip) })
        val first = result.parsed(3).single()
        assertEquals("The publication alone, no hide in between", listOf<OfferEvent>(first), result.events[3])
        assertTrue("${first.card}", first.card.sessionId != null && first.card.fare == 109.0)
        for (i in 4..6) assertEquals(first.card.sessionId, result.parsed(i).single().card.sessionId)
        assertTrue("No notice after the publication", (3..6).all { result.notices(it).isEmpty() })
        assertTrue(result.diag(3), "notice=0" in result.diag(3))
        assertEquals("The ledger sees the offer only from its publication", 1,
            result.signals.count { it is LifecycleSignal.OfferSeen })
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=PUBLISHED") })
    }

    @Test fun `a card whose legs are unread on every frame gets the notice too`() {
        val result = run(frames(routeUnread, 4))
        // This frame reads its fare, its pickup leg and the card's arithmetic; the notice carries none of them.
        assertTrue(result.diag(0), "rejection=PARSED_ROUTE_INCOMPLETE" in result.diag(0) && "paymentRead=true" in result.diag(0) &&
            "legs=4.0/0.8+null/null" in result.diag(0) && "grade=-1" !in result.diag(0))
        assertTrue((0..1).all { result.events[it].isEmpty() })
        for (i in 2..3) {
            val notice = result.notices(i).single()
            assertEquals(listOf<OfferEvent>(notice), result.events[i])
            assertPlacementOnly(notice)
        }
        assertEquals(emptyList<LifecycleSignal>(), result.signals)
    }

    @Test fun `with a session present it never shows`() {
        val result = run(listOf(Frame(readable)) + frames(contradictory, 8))
        val sid = result.parsed(0).single().card.sessionId
        assertNotNull(sid)
        assertEquals(emptyList<OfferEvent.Unreadable>(), result.allNotices())
        for (i in 1..8) {
            val held = result.parsed(i).single().card
            assertTrue("f$i: the session's own unread pill: $held", held.sessionId == sid && held.fare == null)
        }
    }

    @Test fun `with the reader not allowed to read the host it never shows`() {
        val readiness = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) = Unit
        }, java.time.Clock.systemUTC()).apply {
            bound()
            supportedApps(setOf(DidiOcrParser.DIDI_PACKAGE))
        }
        assertTrue(readiness.canRead())
        val result = run(frames(contradictory, 8), readiness = readiness)
        assertEquals(emptyList<OfferEvent>(), result.events.flatten())
    }

    @Test fun `a signature-free frame soft-hides it, an unread frame shows it again, corroborated absence hides it`() {
        val result = run(frames(contradictory, 3) + listOf(Frame(blank), Frame(contradictory)) +
            List(3) { Frame(blank) } + frames(contradictory, 3), stepMs = 900)
        assertEquals(1, result.notices(2).size)
        assertEquals(listOf<OfferEvent>(OfferEvent.NoCard(uber, (result.events[3].single() as OfferEvent.NoCard).timestampMs,
            OfferEvent.Reason.SIGNATURE_ABSENT)), result.events[3])
        assertEquals("Back at once", 1, result.notices(4).size)
        assertEquals(OfferEvent.Reason.SIGNATURE_ABSENT, (result.events[5].single() as OfferEvent.NoCard).reason)
        assertEquals(OfferEvent.Reason.SIGNATURE_ABSENT, (result.events[6].single() as OfferEvent.NoCard).reason)
        assertEquals("Three signature-free frames over 1,800 ms", OfferEvent.Reason.CONFIRMED_ABSENCE,
            (result.events[7].single() as OfferEvent.NoCard).reason)
        assertTrue(result.diag(7), "notice=0" in result.diag(7))
        assertTrue("The run starts over", result.events[8].isEmpty() && result.events[9].isEmpty())
        assertEquals(1, result.notices(10).size)
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=ABSENCE") })
    }

    @Test fun `a card frame that is not unread is the overlay's heartbeat while it shows`() {
        // Without display bounds the frame's card has no geometry: a signature frame, but not an unread one.
        val noGeometry = Frame(contradictory, display = null)
        val probe = run(listOf(noGeometry))
        assertTrue(probe.diag(0), "signature=true" in probe.diag(0) && "parsed=true" in probe.diag(0))
        assertTrue("Neither counts nor breaks the run", probe.events.flatten().isEmpty())
        val counted = run(listOf(Frame(contradictory), noGeometry, Frame(contradictory), Frame(contradictory)))
        assertTrue((0..2).all { counted.events[it].isEmpty() })
        assertEquals("Three unread frames over 2.7 s", 1, counted.notices(3).size)
        val result = run(frames(contradictory, 3) + listOf(noGeometry) + frames(contradictory, 1))
        assertEquals(OfferEvent.LayoutUncertain(uber, (result.events[3].single() as OfferEvent.LayoutUncertain).timestampMs,
            processedFrame = true), result.events[3].single())
        assertEquals(1, result.notices(4).size)
    }

    @Test fun `it is retired 15 s after it first showed and stays off while the card is there`() {
        val result = run(frames(contradictory, 25))
        // Shown at f2, 1.8 s after f0; the first frame 15 s after that is f2 + 17 (15.3 s at 900 ms per frame).
        val shownAt = 2
        val retiredAt = (shownAt until 25).first { result.notices(it).isEmpty() }
        assertEquals("15 s after the first show: f2 + ceil(15000 / 900) frames", shownAt + 17, retiredAt)
        assertEquals(listOf<OfferEvent>(OfferEvent.NoCard(uber, (result.events[retiredAt].single() as OfferEvent.NoCard).timestampMs,
            OfferEvent.Reason.CONFIRMED_ABSENCE)), result.events[retiredAt])
        assertTrue("Nothing after the retirement", (retiredAt + 1 until 25).all { result.events[it].isEmpty() })
        assertTrue(result.diag(24), "notice=3" in result.diag(24))
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=CAP") })
        // Corroborated absence lets the next card earn it again.
        val again = run(frames(contradictory, 20) + List(3) { Frame(blank) } + frames(contradictory, 3))
        assertEquals(1, again.notices(25).size)
    }

    // Round 2 (Codex): the card expires as the cap falls; the absence between it and the next card was complete.
    @Test fun `ordinary absence straddling the cap lets the next unreadable card earn its notice`() {
        // f0..f16 one unreadable card (shown at f2, 2.7 s); f17..f19 the bare map, f19 at 18 s past the 17.7 s cap;
        // f20.. another unreadable card.
        val result = run(frames(contradictory, 17) + List(3) { Frame(blank) } + frames(contradictory, 4))
        assertEquals(1, result.notices(2).size)
        assertEquals(1, result.notices(16).size)
        assertEquals(OfferEvent.Reason.SIGNATURE_ABSENT, (result.events[17].single() as OfferEvent.NoCard).reason)
        assertEquals(OfferEvent.Reason.SIGNATURE_ABSENT, (result.events[18].single() as OfferEvent.NoCard).reason)
        assertEquals(OfferEvent.Reason.CONFIRMED_ABSENCE, (result.events[19].single() as OfferEvent.NoCard).reason)
        assertTrue(result.diag(19), "notice=0" in result.diag(19))
        assertTrue("The next card's own run: two frames of nothing", result.events[20].isEmpty() && result.events[21].isEmpty())
        assertEquals("then its notice, 1.8 s after its first frame", 1, result.notices(22).size)
        assertEquals(1, result.notices(23).size)
    }

    /** Round 3b: a chip rect inside the card with no text beneath it, so the masked frame still reads completely. */
    private val chipOnCard = ScreenRect(460, 1040, 840, 1100)

    @Test fun `a frame rejected as masked with no session ends the notice at once and the next frame starts the session`() {
        val result = run(frames(contradictory, 3) + listOf(Frame(readable, chip = chipOnCard), Frame(readable)))
        assertEquals(1, result.notices(2).size)
        assertTrue(result.diag(3), "rejection=PARSED_CARD_MASKED" in result.diag(3) && "notice=0" in result.diag(3))
        assertEquals("The hide and nothing else", listOf<OfferEvent>(OfferEvent.NoCard(uber,
            (result.events[3].single() as OfferEvent.NoCard).timestampMs, OfferEvent.Reason.CONFIRMED_ABSENCE)), result.events[3])
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=MASKED") })
        val first = result.parsed(4).single()
        assertTrue(result.diag(4), first.card.sessionId != null && first.card.fare == 109.0 && "rejection=null" in result.diag(4))
        assertTrue(result.notices(4).isEmpty())
    }

    @Test fun `a masked frame changes nothing the tracker decides or a session does`() {
        // With a session present the same chip rect is our own chip's, and the session goes on exactly as before.
        val result = run(listOf(Frame(readable)) + List(3) { Frame(readable, chip = chipOnCard) })
        val sid = result.parsed(0).single().card.sessionId
        for (i in 1..3) {
            assertTrue(result.diag(i), "rejection=PARSED_CARD_MASKED" !in result.diag(i))
            assertEquals(sid, result.parsed(i).single().card.sessionId)
        }
        assertTrue(result.events.flatten().none { it is OfferEvent.NoCard || it is OfferEvent.Unreadable })
    }

    @Test fun `a card read without host geometry is never rejected as masked, so it starts its session and ends the notice`() {
        // The tracker's mask lives in the card's host geometry: without it no frame can be rejected as masked, so this
        // frame publishes (and the overlay, unable to place it, takes the old window down).
        val result = run(frames(contradictory, 3) + listOf(Frame(readable, chip = chipOnCard, display = null)))
        assertTrue(result.diag(3), "rejection=PARSED_CARD_MASKED" !in result.diag(3))
        val parsed = result.parsed(3).single()
        assertTrue("${parsed.card}", parsed.card.sessionId != null && parsed.card.hostGeometry == null)
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=PUBLISHED") })
    }

    // Round 4 (Codex): every end of a notice that was on screen leaves the overlay an event that removes it.
    private fun reader() = ReaderReadinessRepository(object : ReaderMemoryStore {
        override fun load() = ReaderMemory()
        override fun save(memory: ReaderMemory) = Unit
    }, java.time.Clock.systemUTC()).apply { bound(); supportedApps(setOf(uber, DidiOcrParser.DIDI_PACKAGE)) }

    private fun hidden(events: List<OfferEvent>) = listOf<OfferEvent>(OfferEvent.NoCard(uber,
        (events.single() as OfferEvent.NoCard).timestampMs, OfferEvent.Reason.CONFIRMED_ABSENCE))

    @Test fun `a stale capture token ends a shown notice with an event that removes it`() {
        // A delayed "Borrar datos del teléfono" invalidates capture tokens only: the overlay's own gate does not change.
        val readiness = reader()
        val result = run(frames(contradictory, 3) + listOf(Frame(contradictory, staleToken = true), Frame(readable)), readiness = readiness)
        assertEquals(1, result.notices(2).size)
        assertEquals("The hide, for the notice's host", hidden(result.events[3]), result.events[3])
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=READINESS") })
        val first = result.parsed(4).single()
        assertTrue("Captured without a chip rect, the readable card starts its session: ${first.card}",
            first.card.sessionId != null && first.card.fare == 109.0)
    }

    @Test fun `a host the reader may no longer read ends a shown notice with an event that removes it`() {
        val readiness = reader()
        val result = run(frames(contradictory, 3) + listOf(
            Frame(contradictory, before = { readiness.supportedApps(setOf(DidiOcrParser.DIDI_PACKAGE)) }),
            Frame(readable, before = { readiness.supportedApps(setOf(uber, DidiOcrParser.DIDI_PACKAGE)) })), readiness = readiness)
        assertEquals(1, result.notices(2).size)
        assertTrue(result.diag(3), "parsed=true" in result.diag(3) && "sid=null" in result.diag(3))
        assertEquals("The hide, for the notice's host", hidden(result.events[3]), result.events[3])
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=READINESS") })
        assertTrue(result.parsed(4).single().card.sessionId != null)
    }

    @Test fun `a readiness reset with no notice on screen emits nothing`() {
        val readiness = reader()
        val result = run(listOf(Frame(contradictory), Frame(contradictory, staleToken = true),
            Frame(contradictory, before = { readiness.supportedApps(setOf(DidiOcrParser.DIDI_PACKAGE)) })), readiness = readiness)
        assertEquals(emptyList<OfferEvent>(), result.events.flatten())
        assertFalse(result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED") })
    }

    // Round 5 (Codex): a session that reaches its parse ceiling while the notice is on screen ends the notice with an event.
    private val didi = DidiOcrParser.DIDI_PACKAGE
    /** Main's real DiDi S25 layout (2026-06-11), addresses invented; [fare] and the trip row as given. */
    private fun didiCard(fare: String, trip: String) = listOf(
        OcrBlock(fare, OcrBounds(83, 973, 543, 1085)), OcrBlock("1 punto(s)", OcrBounds(96, 1134, 306, 1194)),
        OcrBlock("4.84 293 viajes", OcrBounds(168, 1446, 527, 1499)), OcrBlock("Tarjeta bancaria verificada", OcrBounds(138, 1534, 756, 1580)),
        OcrBlock("6min (1.2km)", OcrBounds(139, 1692, 441, 1745)), OcrBlock("Calle Ficticia 10, Colonia Ejemplo,", OcrBounds(138, 1761, 934, 1811)),
        OcrBlock(trip, OcrBounds(156, 1902, 514, 1960)), OcrBlock("Privada Inventada 26, Colonia Prueba,", OcrBounds(139, 1968, 832, 2025)))
    /** The trip row without its minutes: no complete route, no session. */
    private val didiUnread get() = didiCard("\$40.00", "?min (17.3km)")
    /** Complete, but 7.5 times the fare the guard has seen on this card: a session starts and its publication is held. */
    private val didiOutlier get() = didiCard("\$300.00", "39min (17.3km)")

    @Test fun `a session that reaches its parse ceiling while the notice is on screen ends it with an event that removes it`() {
        val result = run(List(3) { Frame(didiUnread, host = didi) } + listOf(Frame(didiOutlier, host = didi)) +
            // Three 5 s OCR timeouts: no frame for 15 s; then a partial frame expires the unpublished session.
            listOf(Frame(didiUnread, host = didi, afterMs = 15_300)) + List(3) { Frame(didiUnread, host = didi) } +
            List(3) { Frame(blank, host = didi) } + List(3) { Frame(didiUnread, host = didi) })
        assertEquals("The notice shows on the third unread frame", 1, result.notices(2).size)
        // The outlier frame starts a session whose publication the guard holds: the notice only beats.
        assertTrue(result.diag(3), "sid=null" !in result.diag(3) && "shownPayment=false" in result.diag(3))
        assertEquals(listOf<OfferEvent>(OfferEvent.LayoutUncertain(didi, (result.events[3].single() as OfferEvent.LayoutUncertain).timestampMs,
            processedFrame = true)), result.events[3])
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: PARSED_OCR_OUTLIER") })
        // The ceiling: the session ends with the card still there; the notice is retired, and the overlay told so.
        assertEquals("The removal on the frame that expires the session", listOf<OfferEvent>(OfferEvent.NoCard(didi,
            (result.events[4].single() as OfferEvent.NoCard).timestampMs, OfferEvent.Reason.CONFIRMED_ABSENCE)), result.events[4])
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=SESSION_CEILING") })
        assertTrue(result.diag(4), "notice=3" in result.diag(4))
        assertTrue("Retired while the card stays", (5..7).all { result.events[it].isEmpty() })
        // Absence corroborated over three signature-free frames clears the retirement; the next card earns the notice.
        assertTrue(result.diag(10), "notice=0" in result.diag(10))
        assertTrue((11..12).all { result.events[it].isEmpty() })
        assertEquals(1, result.notices(13).size)
    }

    @Test fun `a session held by the guard that publishes later takes the notice over silently`() {
        val result = run(List(3) { Frame(didiUnread, host = didi) } + List(3) { Frame(didiOutlier, host = didi) })
        assertEquals(1, result.notices(2).size)
        assertTrue("Held frames only beat", (3..4).all { i -> result.events[i].all { it is OfferEvent.LayoutUncertain } })
        val published = (3..5).firstNotNullOfOrNull { result.parsed(it).singleOrNull() }
        assertNotNull("Once the guard's window agrees, the session publishes", published)
        assertTrue(published!!.card.sessionId != null && published.card.fare == 300.0)
        assertTrue("No notice event after the publication", result.events.drop(3).flatten().none { it is OfferEvent.Unreadable || it is OfferEvent.NoCard })
        assertTrue(result.logs.toString(), result.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=PUBLISHED") })
    }

    @Test fun `the end of capture and a host change reset the run`() {
        val ended = run(frames(contradictory, 3) + listOf(Frame(blank, end = true)) + frames(contradictory, 3))
        assertEquals(OfferEvent.NoCard(uber, (ended.events[3].single() as OfferEvent.NoCard).timestampMs, OfferEvent.Reason.NOT_VISIBLE),
            ended.events[3].single())
        assertTrue("The run starts over", ended.events[4].isEmpty() && ended.events[5].isEmpty())
        assertEquals(1, ended.notices(6).size)
        assertTrue(ended.logs.toString(), ended.logs.any { it.startsWith("offer visibility: UNREAD_NOTICE_ENDED; cause=CAPTURE_END") })
        val switched = run(frames(contradictory, 3) + listOf(Frame(blank, host = DidiOcrParser.DIDI_PACKAGE)) + frames(contradictory, 3))
        assertEquals(OfferEvent.Reason.NOT_VISIBLE, (switched.events[3].first() as OfferEvent.NoCard).reason)
        assertEquals(uber, switched.events[3].first().packageName)
        assertTrue("Nothing for the other host", switched.events[3].none { it is OfferEvent.Unreadable })
        assertTrue("The run starts over", switched.events[4].filterIsInstance<OfferEvent.Unreadable>().isEmpty() &&
            switched.events[5].filterIsInstance<OfferEvent.Unreadable>().isEmpty())
        assertEquals(1, switched.notices(6).size)
    }

    @Test fun `a session that reached its parse ceiling with the card still there leaves no notice behind`() {
        // A readable frame starts a session; route-unread frames hold it until main's 15 s ceiling retires it.
        val result = run(listOf(Frame(readable)) + frames(routeUnread, 24))
        val ceiling = result.events.indexOfFirst { e -> e.any { it is OfferEvent.NoCard && it.reason == OfferEvent.Reason.CONFIRMED_ABSENCE } }
        assertTrue("The session reached its ceiling: $ceiling", ceiling > 0)
        assertEquals("No grey chip pinned after it", emptyList<OfferEvent.Unreadable>(), result.allNotices())
        assertTrue(result.diag(24), "notice=3" in result.diag(24))
    }

    private fun List<OcrBlock>.retext(from: String, to: String) = also { require(any { it.text == from }) { from } }
        .map { if (it.text == from) it.copy(text = to) else it }
}
