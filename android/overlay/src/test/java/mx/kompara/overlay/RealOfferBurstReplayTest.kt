package mx.kompara.overlay

import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.HostWindowEvidence
import mx.kompara.capture.HostWindowOwnership
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.capture.lifecycle.RollupTrigger
import mx.kompara.capture.lifecycle.TripLifecycleTracker
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.overlay.RealGeometryFixtures.DIDI
import mx.kompara.overlay.RealGeometryFixtures.UBER
import mx.kompara.overlay.RealGeometryFixtures.b
import mx.kompara.overlay.RealGeometryFixtures.critical
import mx.kompara.overlay.RealGeometryFixtures.display
import mx.kompara.overlay.RealGeometryFixtures.statusBar
import mx.kompara.overlay.RealGeometryFixtures.systemWindows
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * The real pipeline (parser -> outlier guard -> session -> payment) feeding the overlay placement
 * contract, with the placed chip fed back as the next capture's mask and its text composited into
 * the frame. Real S25 geometry, invented addresses. Mirrors the 2026-10-30 device bursts; ported
 * from the diagnosis harness (archtrace/Replay.kt). On 1c5a7c7 the burst, DiDi and fare-unread
 * cases failed exactly as the device did (two-capture first-frame detach, NO_SAFE_SPACE at card
 * top 339, Sin lectura with the old legs, recover/display livelock, DiDi initial gate, foot slot
 * over the unread fare).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealOfferBurstReplayTest {
    private val viewport = OverlayViewport(display, 3f, HostOrientation.PORTRAIT, OverlayInsets(0, 91, 0, 144))
    private val full = ChipSize(720, 336)
    private val compact = ChipSize(720, 240)

    /**
     * [fare]/[tripKm]: the card published on the bus this frame. [shownFare]/[shownTrip]/[shownPickup]:
     * what the visible chip displays, as production computes it — the ledger row's evaluation
     * ([RowBackedVerdict], OverlayController.evaluate) of the last placed card, held frames included;
     * all null while that evaluation withholds payment (Sin lectura). [read]/[readPickup]: the fare and
     * pickup km this frame's own pixels carry. [pickup]: the published card's pickup. [rows]: the
     * ledger after both consumers wrote this frame.
     */
    data class Frame(val label: String, val offer: String, val signature: Boolean, val published: Boolean,
        val sessionId: String?, val fare: Double?, val tripKm: Double?, val state: String,
        val rect: HostRect?, val visible: Boolean, val critical: List<HostRect>,
        val shownFare: Double? = null, val shownTrip: Double? = null, val shownPickup: Double? = null,
        val read: Double? = null, val pickup: Double? = null, val readPickup: Double? = null,
        val rows: List<mx.kompara.data.db.entity.OfferEntity> = emptyList())

    private inner class Lane(val host: String, val full: ChipSize = this.full, val compact: ChipSize = this.compact) {
        val pipeline = OfferFramePipeline()
        val frames = mutableListOf<Frame>()
        val events = mutableListOf<OfferEvent>()
        val signals = mutableListOf<LifecycleSignal>()
        /** Both production ledger consumers write these rows: the overlay's evaluation and the lifecycle. */
        val offers = BoundaryOfferDao()
        private val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No settings writes")
        }
        private val recorder = OfferRecorder(offers, CostProfileRepository(BoundaryCostProfileDao()),
            SettingsRepository(store), NetProfitEngine())
        private val tracker = TripLifecycleTracker(offers, BoundaryTripDao(offers), BoundaryShiftDao(), recorder, RollupTrigger {})
        private val evidence = PlacementEvidence()
        private var shown: OverlayPlacement.Displayed? = null
        private var softHidden = false
        private var key: String? = null
        /** What the chip displays: the last placed Parsed's row-backed evaluation, kept while later frames hold. */
        private var displayed: RowBackedVerdict? = null
        private val epoch = System.currentTimeMillis()
        private var elapsed = 0L

        fun frame(label: String, offer: String, blocks: List<OcrBlock>, atMs: Long, signature: Boolean = true,
            controls: List<HostRect> = emptyList()) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(atMs - elapsed)); elapsed = atMs
            val chip = shown?.position?.bounds(shown!!.size)
            // A soft-hidden plate is transparent: no chip pixels, but its rect still masks.
            val raw = blocks + (chip?.takeIf { !softHidden }?.let(::chipText) ?: emptyList())
            ScreenReaderState.setForegroundHost(host, SystemClock.elapsedRealtime())
            val before = events.size
            val beforeSignals = signals.size
            val captured = epoch + atMs
            // Node-measured controls (e.g. Uber's home/search corner buttons, 13:12:53) when given.
            val nodes = controls.takeIf { it.isNotEmpty() }?.let {
                HostWindowEvidence(host, captured, display, 3f, statusBar, emptyList(), protectedControls = it, windowId = 1)
            }
            pipeline.process(raw, chip?.let { ScreenRect(it.left, it.top, it.right, it.bottom) }, null,
                capturedAtMs = captured, displayBounds = display, density = 3f, accessibility = nodes,
                foregroundPackage = host,
                windowOwnership = HostWindowOwnership(host, captured, display, 1, display, systemWindows))
            var published: OfferEvent.Parsed? = null
            var state = "none"
            for (event in events.drop(before)) when (event) {
                is OfferEvent.Parsed -> {
                    // OverlayController evaluates every Parsed through the ledger row, placed or not.
                    val verdict = runBlocking { RowBackedVerdict.evaluate(recorder, event, PlatformThreshold.DEFAULT) }
                    published = event; softHidden = false; state = show(event)
                    displayed = verdict.takeIf { shown != null }
                }
                is OfferEvent.LayoutUncertain -> if (state == "none") state = if (shown != null) "held" else "absent"
                is OfferEvent.NoCard -> if (event.reason == OfferEvent.Reason.SIGNATURE_ABSENT) {
                    if (key != null) { softHidden = true; state = "soft-hidden" }
                } else { shown = null; key = null; softHidden = false; displayed = null; state = "detached:${event.reason}" }
                else -> Unit
            }
            // The lifecycle consumer writes the same row from the same frames.
            runBlocking { signals.drop(beforeSignals).forEach { tracker.handle(it) } }
            if (published == null && state == "none") state = if (shown != null) "held" else "absent"
            val visible = shown != null && !softHidden
            val onScreen = displayed?.takeIf { visible && it.paymentShown }?.row
            frames += Frame(label, offer, signature, published != null, published?.card?.sessionId,
                published?.card?.fare, published?.card?.tripDistanceKm, state, shown?.position?.bounds(shown!!.size),
                visible, critical(blocks), onScreen?.fareMxn, onScreen?.tripKm, onScreen?.pickupKm, readFare(blocks),
                published?.card?.pickupDistanceKm, readPickup(blocks), offers.all())
        }

        fun forceDetach() { shown = null }

        /** OverlayController semantics: Reading swaps content in place; keep while safe, else re-place. */
        private fun show(event: OfferEvent.Parsed): String {
            var prefix = ""
            if (event.card.sessionId != key) { key = event.card.sessionId; evidence.reset(); prefix = "reading+" }
            val g0 = event.card.hostGeometry ?: return "detached:no-geometry".also { shown = null }
            val decision = evidence.observe(g0, viewport)
            val g = decision.geometry
            val current = shown
            if (current != null && !(decision.dock && !current.docked) &&
                OverlayPositioning.valid(current.position, current.size, g, viewport, current.overMarker, current.docked))
                return "${prefix}displayed:keep"
            return when (val p = OverlayPositioning.place(g, host, viewport, g.capturedAtMs, full, compact, null, decision.dock)) {
                is OverlayPlacement.Displayed -> { shown = p; "${prefix}displayed:${p.rule}:${p.format}" }
                else -> { shown = null; "${prefix}detached:$p ceiling=${decision.ceiling}" }
            }
        }
    }

    private fun chipText(r: HostRect) = listOf(b("Sin lectura", r.left + 42, r.top + 31, r.left + 347, r.top + 95),
        b("falta la tarifa", r.left + 42, r.top + 113, r.left + 297, r.top + 159),
        b("tu meta \$10.00", r.left + 42, r.top + 203, r.left + 315, r.top + 247))

    private fun replay(host: String, full: ChipSize = this.full, compact: ChipSize = this.compact,
        body: Lane.() -> Unit): Lane = runBlocking {
        val lane = Lane(host, full, compact)
        val job = launch(Dispatchers.Unconfined) { OfferEventBus.events.collect { lane.events += it } }
        val lifecycle = launch(Dispatchers.Unconfined) { OcrLifecycleBus.signals.collect { lane.signals += it } }
        yield()
        try { lane.body() } finally {
            job.cancelAndJoin()
            lifecycle.cancelAndJoin()
            ScreenReaderState.setForegroundHost(null, SystemClock.elapsedRealtime())
            ScreenReaderState.clearOverlayChipRect()
        }
        lane
    }

    /** The fare this frame's own pixels carry: the tallest "$" block (the visible fare). */
    private fun readFare(blocks: List<OcrBlock>) = blocks.filter { it.text.trimStart().startsWith("$") }
        .maxByOrNull { it.bounds.bottom - it.bounds.top }
        ?.let { Regex("""\$\s*(\d+(?:\.\d+)?)""").find(it.text)?.groupValues?.get(1)?.toDouble() }

    /** The pickup km this frame's own pixels carry: the topmost leg ("1 min (0.2 km)", "5min (862m)"). */
    private fun readPickup(blocks: List<OcrBlock>) = blocks.filter { Regex("""\d+\s*min\s*\(""").containsMatchIn(it.text) }
        .minByOrNull { it.bounds.top }?.let { Regex("""\(\s*(\d+(?:\.\d+)?)\s*(km|m)\b""").find(it.text) }
        ?.let { it.groupValues[1].toDouble() / if (it.groupValues[2] == "m") 1000.0 else 1.0 }

    private fun trace(lane: Lane) = lane.frames.joinToString("\n") {
        "${it.label}: pub=${it.published} sid=${it.sessionId?.takeLast(4)} fare=${it.fare} pickup=${it.pickup} trip=${it.tripKm} ${it.state} " +
            "rect=${it.rect} visible=${it.visible} read=${it.read}/${it.readPickup} shows=${it.shownFare}/${it.shownPickup}+${it.shownTrip}"
    } + "\n--- rows ---\n" + lane.offers.all().joinToString("\n") {
        "row ${it.id} sid=${it.sessionId?.takeLast(4)} ${it.outcome} fare=${it.fareMxn} pickup=${it.pickupKm} trip=${it.tripKm}"
    }

    /**
     * The six device-acceptance assertions, applied to every offer in a replay. (2) follows
     * production's rule (round 4): a first reveal is one frame; every revision of the fare or the
     * pickup needs two consecutive agreeing observations. A published payment-bearing card's fare is
     * one the offer's frames read; each change of it is read on that frame and the one before it
     * (fresh agreement, round 5: no historical agreement can license a single-frame reversion). The
     * pickup of every card of the offer's own session and trip leg, paid or not, is one the offer's
     * frames read up to its first admitted frame (its first published frame in its own session),
     * then changes only on fresh agreement (round 5: the exemption no longer runs to the first
     * payment reveal). Every visible
     * frame (held frames included) displays production's row-backed evaluation: nothing, or exactly
     * the economics of this offer's latest published card when that card carries payment, so the
     * chip follows each confirmation by that frame's own evaluation and never shows another offer's
     * economics. [misreads] are known OCR misreads of an offer's own trip leg: such a frame may only
     * publish and display without payment.
     */
    private fun assertContract(lane: Lane, fares: Map<String, Double>, trips: Map<String, Double>,
        misreads: Map<String, Set<Double>> = emptyMap()) {
        val failures = mutableListOf<String>()
        val byOffer = lane.frames.groupBy { it.offer }
        val seen = mutableSetOf<String>()
        for ((offer, frames) in byOffer) {
            val earlierSessions = seen.toSet()
            seen += frames.mapNotNull { it.sessionId }
            val published = frames.filter { it.published }
            val first = published.firstOrNull()
            if (first == null || !first.visible) failures += "(1) $offer first published frame not displayed: ${first?.state}"
            fun trip(fare: Double?, trip: Double?, label: String, verb: String) {
                if (trip != null && trip != trips[offer] && !(fare == null && trip in misreads[offer].orEmpty()))
                    failures += "(2) $label $verb trip $trip, not ${trips[offer]}"
            }
            var latest: Frame? = null
            var lastPaid: Frame? = null
            var lastPickup: Double? = null
            // The offer's first admitted frame is its first published frame in a session of its own
            // (earlier frames publish under the previous offer's session while they vote).
            val firstOwn = frames.indexOfFirst { it.published && it.sessionId != null && it.sessionId !in earlierSessions }
            frames.forEachIndexed { i, f ->
                // Read on this frame AND the one before it: a revision is published on the
                // stabilizer's second consecutive agreeing observation, so its agreement is fresh.
                fun fresh(value: Double?, read: (Frame) -> Double?) =
                    value != null && i >= 1 && read(f) == value && read(frames[i - 1]) == value
                if (f.published) {
                    latest = f
                    trip(f.fare, f.tripKm, f.label, "publishes")
                    if (f.fare != null) {
                        if (f.fare !in frames.take(i + 1).mapNotNull { it.read })
                            failures += "(2) ${f.label} publishes ${f.fare}, which none of $offer's frames read"
                        lastPaid?.let { prior ->
                            if (f.fare != prior.fare && !fresh(f.fare) { it.read })
                                failures += "(2) ${f.label} revises ${prior.fare} (${prior.label}) to ${f.fare} " +
                                    "without two fresh consecutive reads"
                        }
                        lastPaid = f
                    }
                    // Every card of this offer's own session and trip leg, paid or not, carries the
                    // confirmed pickup: any read up to the first admitted frame, then only revisions
                    // read on two fresh consecutive frames.
                    if (firstOwn in 0..i && f.sessionId !in earlierSessions && f.tripKm == trips[offer] &&
                        f.pickup != null) {
                        if (lastPickup == null) {
                            if (f.pickup !in frames.take(i + 1).mapNotNull { it.readPickup })
                                failures += "(2) ${f.label} reveals pickup ${f.pickup}, which none of $offer's frames read"
                        } else if (f.pickup != lastPickup && !fresh(f.pickup) { it.readPickup }) {
                            failures += "(2) ${f.label} revises pickup $lastPickup to ${f.pickup} without two fresh " +
                                "consecutive reads"
                        }
                        lastPickup = f.pickup
                    }
                }
                if (!f.visible) return@forEachIndexed
                trip(f.shownFare, f.shownTrip, f.label, "displays")
                val misread = f.tripKm in misreads[offer].orEmpty()
                if (misread && f.shownFare != null) failures += "(2) ${f.label} misread trip displays payment ${f.shownFare}"
                val paid = latest?.takeIf { it.fare != null }
                val shown = Triple(f.shownFare, f.shownPickup, f.shownTrip)
                val expected = Triple(paid?.fare, paid?.pickup, paid?.tripKm)
                if (shown != expected) failures += "(2) ${f.label} displays $shown, not $expected " +
                    "(the latest published card of $offer: ${latest?.label})"
            }
            if (published.size >= 2 && published.drop(1).none { it.fare == fares[offer] })
                failures += "(2) $offer never shows its own fare ${fares[offer]}"
            frames.zipWithNext().forEach { (a, c) ->
                if (a.visible && c.signature && !c.visible) failures += "(3) ${c.label} hidden while signature present: ${c.state}"
            }
            frames.forEach { f ->
                f.rect?.let { r -> f.critical.firstOrNull { it.overlaps(r) }?.let { failures += "(4) ${f.label} chip $r covers $it" } }
            }
            // A row is written for every session that shows this offer's own fare.
            val sessions = published.filter { it.fare == fares[offer] }.mapNotNull { it.sessionId }.distinct()
            if (sessions.size > 1) failures += "(6) $offer split into ${sessions.size} sessions"
        }
        val allSessions = byOffer.mapValues { (offer, f) -> f.filter { it.fare == fares[offer] }.mapNotNull { it.sessionId }.toSet() }
        allSessions.entries.toList().let { entries ->
            for (i in entries.indices) for (j in i + 1 until entries.size)
                if (entries[i].value.intersect(entries[j].value).isNotEmpty())
                    failures += "(6) ${entries[i].key} and ${entries[j].key} share a session"
        }
        assertTrue(failures.joinToString("\n", postfix = "\n--- trace ---\n${trace(lane)}"), failures.isEmpty())
    }

    @Test fun `zero-gap Radar burst of three distinct offers shows each own fare in a stable chip`() {
        val lane = replay(UBER) {
            var t = 0L
            repeat(4) { frame("o1 f$it", "o1", RealGeometryFixtures.radarHighDistinct(), t); t += 850 }
            repeat(5) { frame("o2 f$it", "o2", RealGeometryFixtures.radar(), t); t += 850 }
            repeat(8) { frame("o3 f$it", "o3", RealGeometryFixtures.uberx(), t); t += 850 }
        }
        assertContract(lane, mapOf("o1" to 118.20, "o2" to 129.55, "o3" to 104.41),
            mapOf("o1" to 9.6, "o2" to 12.2, "o3" to 7.3))
    }

    @Test fun `a fare-unread frame never places the chip over the fare`() {
        val unread = RealGeometryFixtures.uberx().filterNot { it.text == "\$104.41" }
        val lane = replay(UBER) {
            frame("o3 f0", "o3", RealGeometryFixtures.uberx(), 0)
            frame("o3 f1", "o3", RealGeometryFixtures.uberx(), 850)
            forceDetach() // e.g. a dismissal soft hide, then the next frame reveals afresh
            frame("o3 f2 unread", "o3", unread, 1700)
            frame("o3 f3", "o3", RealGeometryFixtures.uberx(), 2550)
        }
        val failures = lane.frames.mapNotNull { f ->
            f.rect?.let { r -> RealGeometryFixtures.critical(RealGeometryFixtures.uberx()).firstOrNull { it.overlaps(r) }
                ?.let { "${f.label} chip $r covers $it" } }
        }
        assertTrue(failures.joinToString("\n", postfix = "\n--- trace ---\n${trace(lane)}"), failures.isEmpty())
    }

    @Test fun `a replacement confirmed by a fare-unread frame paints the pending frame's own fare`() {
        // Device 12:04:25.966: o3's second frame had paymentRead=false.
        val unread = RealGeometryFixtures.uberx().filterNot { it.text == "\$104.41" }
        val lane = replay(UBER) {
            var t = 0L
            repeat(3) { frame("o2 f$it", "o2", RealGeometryFixtures.radar(), t); t += 850 }
            frame("o3 f0", "o3", RealGeometryFixtures.uberx(), t); t += 850
            frame("o3 f1 unread", "o3", unread, t); t += 850
            frame("o3 f2", "o3", RealGeometryFixtures.uberx(), t)
        }
        val second = lane.frames.single { it.label == "o3 f1 unread" }
        assertTrue("The new instance shows its own seeded fare at once\n${trace(lane)}",
            second.fare == 104.41 && second.tripKm == 7.3 && second.visible)
        // Round 4 (B4): what the chip displays (the row-backed evaluation), not only the bus.
        assertTrue("The chip displays the seeded fare at once\n${trace(lane)}",
            second.shownFare == 104.41 && second.shownTrip == 7.3)
        assertContract(lane, mapOf("o2" to 129.55, "o3" to 104.41), mapOf("o2" to 12.2, "o3" to 7.3))
    }

    @Test fun `real DiDi card is displayed on its first frame and keeps one session`() {
        val lane = replay(DIDI) {
            repeat(5) { frame("didi f$it", "didi", RealGeometryFixtures.didi(), it * 850L) }
        }
        assertContract(lane, mapOf("didi" to 132.59), mapOf("didi" to 17.3))
    }

    @Test fun `displaced same Radar card keeps one session and the chip only moves up`() {
        val lane = replay(UBER) {
            var t = 0L
            repeat(4) { frame("o2 high f$it", "o2", RealGeometryFixtures.radarHighDisplaced(), t); t += 850 }
            repeat(5) { frame("o2 rest f$it", "o2", RealGeometryFixtures.radar(), t); t += 850 }
        }
        assertContract(lane, mapOf("o2" to 129.55), mapOf("o2" to 12.2))
        val tops = lane.frames.mapNotNull { it.rect?.top }
        assertTrue("A displaced card never moves its chip down: $tops\n${trace(lane)}",
            tops.zipWithNext().all { (a, c) -> c <= a })
    }

    @Test fun `three-card Radar stack Priority offer gets a chip on every frame`() {
        val lane = replay(UBER) {
            repeat(6) { frame("p f$it", "p", RealGeometryFixtures.priorityRadarStack(), it * 850L) }
        }
        assertContract(lane, mapOf("p" to 88.30), mapOf("p" to 7.8))
    }

    @Test fun `taller Priority Radar after a shorter UberX card never shows the previous verdict`() {
        val lane = replay(UBER) {
            var t = 0L
            repeat(4) { frame("x f$it", "x", RealGeometryFixtures.uberx(), t); t += 850 }
            repeat(5) { frame("p f$it", "p", RealGeometryFixtures.priorityRadarStack(), t); t += 850 }
        }
        assertContract(lane, mapOf("x" to 104.41, "p" to 88.30), mapOf("x" to 7.3, "p" to 7.8))
        val first = lane.frames.first { it.offer == "p" }
        assertTrue("The first taller-card frame must not carry the old fare or legs\n${trace(lane)}",
            first.fare == null && first.tripKm != 7.3)
    }

    @Test fun `Priority Aceptar card with the live close glyph is displayed on its first frame`() {
        val lane = replay(UBER) {
            repeat(4) { frame("pr f$it", "pr", RealGeometryFixtures.priority(), it * 850L) }
        }
        assertContract(lane, mapOf("pr" to 79.45), mapOf("pr" to 2.4))
    }

    @Test fun `DiDi Aceptar cards dock clear of the visible fare legs and button`() {
        for ((name, blocks, fare, trip) in listOf(
            Quad("bid", RealGeometryFixtures.didiBid(), 89.12, 10.2),
            Quad("live", RealGeometryFixtures.didiLive(), 200.85, 11.3))) {
            val lane = replay(DIDI) { repeat(4) { frame("$name f$it", name, blocks, it * 850L) } }
            assertContract(lane, mapOf(name to fare), mapOf(name to trip))
            // B3: the "Aceptar $X" block is above the nav bar, so the parser really sees it; the
            // visible fare above the first leg still wins the fare rect.
            val visible = blocks.filter { it.text.startsWith("$") }.maxBy { it.bounds.bottom - it.bounds.top }.bounds
            val parsed = lane.events.filterIsInstance<OfferEvent.Parsed>()
            assertTrue("$name: parsed", parsed.isNotEmpty())
            parsed.forEach {
                val f = it.card.fareBounds
                assertTrue("$name: fareBounds $f is the visible fare $visible, never the button",
                    f != null && f.top == visible.top && f.bottom == visible.bottom && f.left == visible.left)
            }
        }
    }

    @Test fun `back-to-back DiDi offers through the trip list never lend the previous fare`() {
        val lane = replay(DIDI) {
            var t = 0L
            repeat(3) { frame("a f$it", "a", RealGeometryFixtures.didi(), t); t += 850 }
            repeat(2) { frame("list f$it", "list", RealGeometryFixtures.didiList(), t); t += 850 }
            repeat(4) { frame("b f$it", "b", RealGeometryFixtures.didiNext(), t); t += 850 }
        }
        assertContract(lane, mapOf("a" to 132.59, "list" to 74.10, "b" to 61.20),
            mapOf("a" to 17.3, "list" to 6.0, "b" to 5.1))
        assertTrue("The chip was masked on every frame after the first\n${trace(lane)}",
            lane.frames.drop(1).all { it.rect != null })
    }

    @Test fun `a misread first DiDi fare is revised by later correct frames`() {
        val misread = RealGeometryFixtures.retext(RealGeometryFixtures.didi(), "$132.59" to "$32.59")
        val lane = replay(DIDI) {
            frame("d f0", "d", misread, 0)
            repeat(4) { frame("d f${it + 1}", "d", RealGeometryFixtures.didi(), (it + 1) * 850L) }
        }
        assertTrue(trace(lane), lane.frames.last().fare == 132.59)
        assertTrue(trace(lane), lane.frames.mapNotNull { it.sessionId }.distinct().size == 1)
        assertTrue(trace(lane), lane.frames.all { it.visible })
        // Round 4 (B4): the revision reaches what the chip displays (the row), not only the bus.
        assertTrue("The second agreeing frame revises the displayed fare\n${trace(lane)}",
            lane.frames.drop(2).all { it.shownFare == 132.59 } && lane.frames[1].shownFare == 32.59)
        assertContract(lane, mapOf("d" to 132.59), mapOf("d" to 17.3))
    }

    @Test fun `TD-028 tenfold leg frame is held and a fivefold real next offer still replaces`() {
        val tenfold = RealGeometryFixtures.retext(RealGeometryFixtures.uberx(), "34 min (7.3 km)" to "34 min (73.0 km)")
        val big = RealGeometryFixtures.retext(RealGeometryFixtures.uberx(), "$104.41" to "$520.00",
            "34 min (7.3 km)" to "60 min (40.1 km)", "5 min (1.0 km)" to "9 min (5.6 km)")
        val lane = replay(UBER) {
            var t = 0L
            repeat(3) { frame("x f$it", "x", RealGeometryFixtures.uberx(), t); t += 850 }
            frame("x tenfold", "x", tenfold, t); t += 850
            repeat(2) { frame("x f${it + 3}", "x", RealGeometryFixtures.uberx(), t); t += 850 }
            repeat(4) { frame("big f$it", "big", big, t); t += 850 }
        }
        // A1(a): the tenfold frame reads a different trip leg, so it publishes WITHOUT payment
        // (one Sin lectura frame, the accepted cost) and never keeps or lends a fare.
        assertContract(lane, mapOf("x" to 104.41, "big" to 520.00), mapOf("x" to 7.3, "big" to 40.1),
            misreads = mapOf("x" to setOf(73.0)))
        val tenfoldFrame = lane.frames.single { it.label == "x tenfold" }
        assertTrue("The tenfold frame displays no fare\n${trace(lane)}",
            tenfoldFrame.published && tenfoldFrame.visible && tenfoldFrame.shownFare == null)
    }

    @Test fun `a guard-rejected fare never becomes the first reveal of the replacement it confirms`() {
        // Codex C1: B's first frame reads its own fare; its confirming frame reads the same legs
        // with a garbled fare that the outlier guard rejects against A's window.
        val garbled = RealGeometryFixtures.retext(RealGeometryFixtures.didiNext(), "$61.20" to "$7.84")
        val lane = replay(DIDI) {
            var t = 0L
            repeat(3) { frame("a f$it", "a", RealGeometryFixtures.didi(), t); t += 850 }
            frame("b f0", "b", RealGeometryFixtures.didiNext(), t); t += 850
            frame("b f1 garbled", "b", garbled, t); t += 850
            repeat(2) { frame("b f${it + 2}", "b", RealGeometryFixtures.didiNext(), t); t += 850 }
        }
        val reveal = lane.frames.single { it.label == "b f1 garbled" }
        assertTrue("The confirming frame shows the pending frame's clean fare or none\n${trace(lane)}",
            reveal.visible && reveal.shownFare in setOf(null, 61.20))
        assertTrue("The garbled fare never reaches the bus or the chip\n${trace(lane)}",
            lane.frames.none { it.fare == 7.84 || it.shownFare == 7.84 })
        assertTrue(trace(lane), lane.frames.single { it.label == "b f2" }.shownFare == 61.20)
        assertContract(lane, mapOf("a" to 132.59, "b" to 61.20), mapOf("a" to 17.3, "b" to 5.1))
    }

    @Test fun `a fivefold next offer's guard-rejected first frame never keeps the previous verdict on screen`() {
        // W1: Priority pickup 0.2 km then UberX pickup 1.0 km (5.0x) at zero gap; the guard
        // rejects both of the next card's first frames against the old window.
        val lane = replay(UBER) {
            var t = 0L
            repeat(3) { frame("x f$it", "x", RealGeometryFixtures.priority(), t); t += 850 }
            repeat(3) { frame("y f$it", "y", RealGeometryFixtures.uberx(), t); t += 850 }
        }
        val y0 = lane.frames.single { it.label == "y f0" }
        // The chip displays the row-backed evaluation: without payment it shows no economics at all,
        // so Y's own legs are asserted on the published card.
        assertTrue("Y's first frame publishes its own legs without payment, never a bare heartbeat\n${trace(lane)}",
            y0.published && y0.visible && y0.shownFare == null && y0.fare == null && y0.tripKm == 7.3)
        assertTrue("Y's confirming frame shows Y's fare\n${trace(lane)}",
            lane.frames.single { it.label == "y f1" }.shownFare == 104.41)
        assertContract(lane, mapOf("x" to 79.45, "y" to 104.41), mapOf("x" to 2.4, "y" to 7.3))
    }

    @Test fun `a dock over the Radar pill never pins an unread first fare as Sin lectura`() {
        // Codex C3 / W3: the high Radar layout docks over the pill on its first frame (fare unread);
        // our own mask then hid the pill on every later frame and kept the offer UNCERTAIN.
        for ((name, layout, fare) in listOf(Triple("displaced", RealGeometryFixtures.radarHighDisplaced(), 129.55),
            Triple("distinct", RealGeometryFixtures.radarHighDistinct(), 118.20))) {
            val amount = "$" + String.format(java.util.Locale.US, "%.2f", fare)
            val unread = layout.filterNot { it.text == amount }
            val lane = replay(UBER) {
                frame("$name f0 unread", name, unread, 0)
                repeat(4) { frame("$name f${it + 1}", name, layout, (it + 1) * 850L) }
            }
            assertTrue("$name: the first chip docks over the pill\n${trace(lane)}",
                lane.frames.first().rect?.top == 139)
            assertTrue("$name: frame 2 confirms the fare\n${trace(lane)}",
                lane.frames.single { it.label == "$name f1" }.shownFare == fare)
            assertContract(lane, mapOf(name to fare), mapOf(name to if (name == "displaced") 12.2 else 9.6))
        }
    }

    @Test fun `a dock pushed below node corner buttons onto the card top still revises a misread first fare`() {
        // W3: stale home/search corner nodes (13:12:53) push the dock to y=283 from the first frame;
        // its mask reaches y=627, 22 px into the Priority card's derived top (605), on every frame,
        // which kept every later frame UNCERTAIN: a misread first fare could never be revised.
        val corners = listOf(HostRect(48, 91, 192, 235), HostRect(888, 91, 1032, 235))
        val misread = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "$79.45" to "$19.45")
        val lane = replay(UBER) {
            frame("pr f0 misread", "pr", misread, 0, controls = corners)
            repeat(4) { frame("pr f${it + 1}", "pr", RealGeometryFixtures.priority(), (it + 1) * 850L, controls = corners) }
        }
        assertTrue("The chip docks under the corner buttons\n${trace(lane)}", lane.frames.all { it.rect?.top == 283 })
        assertTrue("Two agreeing later frames revise the misread fare\n${trace(lane)}",
            lane.frames.single { it.label == "pr f2" }.shownFare == 79.45 && lane.frames.last().shownFare == 79.45)
        assertContract(lane, mapOf("pr" to 79.45), mapOf("pr" to 2.4))
    }

    @Test fun `a guard-rejected confirming frame never lends its pickup to the accepted seed fare`() {
        // Codex R2 #1: B's confirming frame loses the pickup's "0." (decimal loss: about once per 13
        // offers in the June device frames); the guard rejects it against A's window. Its 6.0 km
        // pickup must never travel with the accepted $61.20 seed, onto the card or into the row.
        val lost = RealGeometryFixtures.retext(RealGeometryFixtures.didiNext(), "3min (0.6km)" to "3min (6km)")
        val lane = replay(DIDI) {
            var t = 0L
            repeat(3) { frame("a f$it", "a", RealGeometryFixtures.didi(), t); t += 850 }
            frame("b f0", "b", RealGeometryFixtures.didiNext(), t); t += 850
            frame("b f1 lost pickup", "b", lost, t); t += 850
            repeat(3) { frame("b f${it + 2}", "b", RealGeometryFixtures.didiNext(), t); t += 850 }
        }
        val paid = lane.events.filterIsInstance<OfferEvent.Parsed>().filter { it.card.fare != null }
        assertTrue("No published payment carries the rejected pickup\n${trace(lane)}",
            paid.none { it.card.pickupDistanceKm == 6.0 })
        assertTrue("The chip never displays the rejected pickup\n${trace(lane)}", lane.frames.none { it.shownPickup == 6.0 })
        val confirming = lane.frames.single { it.label == "b f1 lost pickup" }
        assertTrue("The confirming frame shows the seed's economics or none\n${trace(lane)}",
            confirming.visible && (confirming.shownFare == null || confirming.shownFare == 61.20 && confirming.shownPickup == 0.6))
        lane.frames.filter { it.offer == "b" }.drop(2).forEach {
            // Round 4 (B3): PUBLISHED as well, so a hold on the minted row cannot pass for enrichment.
            assertTrue("${it.label}: later correct frames publish and display B's own economics\n${trace(lane)}",
                it.visible && it.shownFare == 61.20 && it.shownPickup == 0.6 && it.shownTrip == 5.1 &&
                    it.published && it.fare == 61.20 && it.tripKm == 5.1)
        }
        val row = lane.offers.all().single { it.outcome == "PENDING" }
        assertTrue("B's row carries the accepted pickup\n${trace(lane)}",
            row.fareMxn == 61.20 && row.pickupKm == 0.6 && row.tripKm == 5.1)
        assertContract(lane, mapOf("a" to 132.59, "b" to 61.20), mapOf("a" to 17.3, "b" to 5.1))
    }

    @Test fun `a same-trip fare revision reaches the displayed row whether or not the pickup moved`() {
        // Codex R2 #2: the first reveal is wrong in about one offer in six (June frames); the
        // stabilizer's revision must reach the row the chip displays, also when the pickup leg moved
        // live between the frames (0.2 -> 0.3 km). The trip leg is the identity key.
        for (pickup in listOf("1 min (0.2 km)", "1 min (0.3 km)")) {
            val misread = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "$79.45" to "$19.45")
            val later = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "1 min (0.2 km)" to pickup)
            val km = if (pickup.contains("0.3")) 0.3 else 0.2
            val lane = replay(UBER) {
                frame("pr f0 misread", "pr", misread, 0)
                repeat(4) { frame("pr f${it + 1}", "pr", later, (it + 1) * 850L) }
            }
            lane.frames.drop(2).forEach {
                assertTrue("$pickup ${it.label}: the second agreeing frame revises the displayed fare\n${trace(lane)}",
                    it.visible && it.shownFare == 79.45 && it.shownPickup == km)
            }
            val row = lane.offers.all().single()
            assertTrue("$pickup: the row ends with the revised fare and the latest pickup\n${trace(lane)}",
                row.fareMxn == 79.45 && row.pickupKm == km && row.tripKm == 2.4)
            assertContract(lane, mapOf("pr" to 79.45), mapOf("pr" to 2.4))
        }
    }

    @Test fun `an identical-leg Exclusivo to Radar card of the same product shows the Radar fare`() {
        // B2: only the action changes (Aceptar -> Me interesa) with the exclusive label; the pill is
        // not identity. A sustained card-class action change is the second key: replace, never pin.
        val radar = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "Exclusivo" to "Radar de solicitud",
            "Aceptar" to "Me interesa", "$79.45" to "$88.30")
        val lane = replay(UBER) {
            var t = 0L
            repeat(3) { frame("ex f$it", "ex", RealGeometryFixtures.priority(), t); t += 850 }
            repeat(5) { frame("radar f$it", "radar", radar, t); t += 850 }
        }
        val radarFrames = lane.frames.filter { it.offer == "radar" }
        assertTrue("Never the Exclusivo price on the Radar card\n${trace(lane)}",
            radarFrames.none { it.fare == 79.45 || it.shownFare == 79.45 })
        radarFrames.drop(1).forEach {
            assertTrue("${it.label}: within two frames the chip shows the Radar fare\n${trace(lane)}",
                it.visible && it.shownFare == 88.30)
        }
        val rows = lane.offers.all()
        assertTrue("Exclusivo retired with its own fare, Radar pending with its own\n${trace(lane)}",
            rows.size == 2 && rows[0].fareMxn == 79.45 && rows[0].outcome == "UNKNOWN" &&
                rows[1].fareMxn == 88.30 && rows[1].outcome == "PENDING")
        assertContract(lane, mapOf("ex" to 79.45, "radar" to 88.30), mapOf("ex" to 2.4, "radar" to 2.4))
    }

    @Test fun `an identical-leg replacement paints its accepted pending vote's economics on the confirming frame`() {
        // B6 round 4: like a trip-leg replacement, the identical-leg (action-class) path seeds the
        // first reveal from the accepted voting frame and the guard window with admitted numbers.
        val radar = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "Exclusivo" to "Radar de solicitud",
            "Aceptar" to "Me interesa", "$79.45" to "$88.30")
        for ((name, confirming) in listOf("agree" to radar, "unread" to radar.filterNot { it.text == "$88.30" },
            "rejected" to RealGeometryFixtures.retext(radar, "1 min (0.2 km)" to "1 min (2 km)"))) {
            val lane = replay(UBER) {
                var t = 0L
                repeat(3) { frame("ex f$it", "ex", RealGeometryFixtures.priority(), t); t += 850 }
                frame("radar f0", "radar", radar, t); t += 850
                frame("radar f1 $name", "radar", confirming, t); t += 850
                repeat(3) { frame("radar f${it + 2}", "radar", radar, t); t += 850 }
            }
            val reveal = lane.frames.single { it.label == "radar f1 $name" }
            assertTrue("$name: the confirming frame shows the Radar fare with the voting frame's legs\n${trace(lane)}",
                reveal.published && reveal.visible && reveal.shownFare == 88.30 && reveal.shownPickup == 0.2)
            lane.frames.filter { it.offer == "radar" }.drop(2).forEach {
                assertTrue("$name ${it.label}: later correct frames publish, never held as outliers\n${trace(lane)}",
                    it.published && it.shownFare == 88.30 && it.shownPickup == 0.2)
            }
            assertContract(lane, mapOf("ex" to 79.45, "radar" to 88.30), mapOf("ex" to 2.4, "radar" to 2.4))
        }
    }

    @Test fun `a dock touching the previous card never voids the next card's visible votes`() {
        // Codex R3 #1 (A1 round 4): corner nodes dock the chip at (36,283)-(756,619); its mask reaches
        // y627, into Priority A's padded card boundary. The measured normal Radar layout B (pill
        // y614-667, fare y916-1038, 42 px close, "Me interesa" y1948-2001) then reads $88.30 with A's
        // exact legs. B's action, product and class votes rest on visible blocks: B replaces by its
        // second frame and never shows A's $79.45.
        val corners = listOf(HostRect(48, 91, 192, 235), HostRect(888, 91, 1032, 235))
        val radarB = RealGeometryFixtures.retext(RealGeometryFixtures.radar(), "$129.55" to "$88.30",
            "4 min (0.9 km)" to "1 min (0.2 km)", "34 min (12.2 km)" to "24 min (2.4 km)",
            "38 min • 13.1 km • $9.89/km est." to "25 min • 2.6 km • $33.96/km est.")
        val lane = replay(UBER) {
            var t = 0L
            repeat(3) { frame("a f$it", "a", RealGeometryFixtures.priority(), t, controls = corners); t += 850 }
            repeat(5) { frame("b f$it", "b", radarB, t, controls = corners); t += 850 }
        }
        assertTrue("A's chip docks under the corner buttons; its mask reaches A's card top\n${trace(lane)}",
            lane.frames.filter { it.offer == "a" }.all { it.rect?.top == 283 && it.rect?.bottom == 619 })
        val a = lane.frames.first().sessionId
        val b = lane.frames.filter { it.offer == "b" }
        assertTrue("No B frame publishes or displays A's fare\n${trace(lane)}",
            b.none { it.fare == 79.45 || it.shownFare == 79.45 })
        b.drop(1).forEach {
            assertTrue("${it.label}: B is its own session from its second frame and displays its own economics\n${trace(lane)}",
                it.published && it.sessionId != a && it.visible && it.shownFare == 88.30 && it.shownPickup == 0.2 &&
                    it.shownTrip == 2.4)
        }
        val rows = lane.offers.all()
        assertTrue("A retired with its own fare, B pending with its own\n${trace(lane)}",
            rows.size == 2 && rows[0].fareMxn == 79.45 && rows[0].outcome == "UNKNOWN" &&
                rows[1].fareMxn == 88.30 && rows[1].outcome == "PENDING")
        assertContract(lane, mapOf("a" to 79.45, "b" to 88.30), mapOf("a" to 2.4, "b" to 2.4))
    }

    @Test fun `a guard-blind pickup misread on an offer's second frame never revises the card or the row`() {
        // A2 round 4 (wf3 PROBE1): the offer's second frame is judged against ONE prior, so the
        // outlier guard is blind; its decimal-lost pickup (6.0 km for 0.6) is a single observation.
        // Revisions need two consecutive agreeing observations: card, row and chip keep 0.6.
        val lost = RealGeometryFixtures.retext(RealGeometryFixtures.didiNext(), "3min (0.6km)" to "3min (6km)")
        val lane = replay(DIDI) {
            var t = 0L
            frame("b f0", "b", RealGeometryFixtures.didiNext(), t); t += 850
            frame("b f1 lost", "b", lost, t); t += 850
            repeat(3) { frame("b f${it + 2}", "b", RealGeometryFixtures.didiNext(), t); t += 850 }
        }
        // The generic contract alone (B2) must catch a wrong $/km rate.
        assertContract(lane, mapOf("b" to 61.20), mapOf("b" to 5.1))
        assertBlindPickupNeverLands(lane, 6.0, 0.6)
    }

    @Test fun `a guard-blind pickup misread after a replacement never revises the card or the row`() {
        // A2 round 4 (wf3 PROBE3): the replacement's guard window is re-seeded with ONE sample, so
        // the first post-replacement frame is equally blind.
        val lost = RealGeometryFixtures.retext(RealGeometryFixtures.didiNext(), "3min (0.6km)" to "3min (6km)")
        val lane = replay(DIDI) {
            var t = 0L
            repeat(3) { frame("a f$it", "a", RealGeometryFixtures.didi(), t); t += 850 }
            frame("b f0", "b", RealGeometryFixtures.didiNext(), t); t += 850
            frame("b f1", "b", RealGeometryFixtures.didiNext(), t); t += 850
            frame("b f2 lost", "b", lost, t); t += 850
            repeat(3) { frame("b f${it + 3}", "b", RealGeometryFixtures.didiNext(), t); t += 850 }
        }
        assertBlindPickupNeverLands(lane, 6.0, 0.6)
        assertContract(lane, mapOf("a" to 132.59, "b" to 61.20), mapOf("a" to 17.3, "b" to 5.1))
    }

    private fun assertBlindPickupNeverLands(lane: Lane, wrong: Double, right: Double) {
        val paid = lane.events.filterIsInstance<OfferEvent.Parsed>().filter { it.card.fare != null }
        assertTrue("No published payment carries the single-frame pickup\n${trace(lane)}",
            paid.none { it.card.pickupDistanceKm == wrong })
        assertTrue("The chip never displays the single-frame pickup\n${trace(lane)}",
            lane.frames.none { it.shownPickup == wrong })
        assertTrue("No ledger row ever takes the single-frame pickup\n${trace(lane)}",
            lane.frames.flatMap { it.rows }.none { it.pickupKm == wrong })
        assertTrue("The offer ends on its own pickup\n${trace(lane)}", lane.frames.last().shownPickup == right)
    }

    @Test fun `a decimal-lost pending pickup is an accepted first reveal revised only by agreeing observations`() {
        // A2 round 4, wf3 disputed 0 (the Decision.admitted mirror): B's PENDING frame loses the
        // pickup's decimal and is guard-accepted against A's 3.2 km window while B's correct
        // confirming frame is rejected, so the replacement's first reveal carries 6.0: the accepted
        // first-reveal class (TD-059). Like any wrong first reveal it is revised only by agreeing
        // observations, never by a single frame; the guard's two-prior median makes that a few frames.
        val aFar = RealGeometryFixtures.retext(RealGeometryFixtures.didi(), "6min (1.2km)" to "6min (3.2km)")
        val lost = RealGeometryFixtures.retext(RealGeometryFixtures.didiNext(), "3min (0.6km)" to "3min (6km)")
        val lane = replay(DIDI) {
            var t = 0L
            repeat(3) { frame("a f$it", "a", aFar, t); t += 850 }
            frame("b f0 lost", "b", lost, t); t += 850
            repeat(6) { frame("b f${it + 1}", "b", RealGeometryFixtures.didiNext(), t); t += 850 }
        }
        assertTrue("Only the first reveal or B's own agreed pickup is ever displayed\n${trace(lane)}",
            lane.frames.filter { it.offer == "b" }.all { it.shownPickup in setOf(null, 6.0, 0.6) })
        assertTrue("B ends on its own economics\n${trace(lane)}",
            lane.frames.takeLast(2).all { it.shownFare == 61.20 && it.shownPickup == 0.6 && it.shownTrip == 5.1 })
        assertContract(lane, mapOf("a" to 132.59, "b" to 61.20), mapOf("a" to 17.3, "b" to 5.1))
    }

    @Test fun `a pickup read on two consecutive frames revises the row at the second`() {
        // A2 round 4: a revision is two consecutive agreeing observations, nothing less and nothing more.
        val lost = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "1 min (0.2 km)" to "1 min (2 km)")
        val lane = replay(UBER) {
            frame("pr f0", "pr", RealGeometryFixtures.priority(), 0)
            frame("pr f1 lost", "pr", lost, 850)
            frame("pr f2 lost", "pr", lost, 1700)
        }
        val (f0, f1, f2) = lane.frames
        assertTrue("Row and chip keep 0.2 through the first reading\n${trace(lane)}",
            f0.shownPickup == 0.2 && f1.shownPickup == 0.2 && f1.rows.single().pickupKm == 0.2 && f1.pickup == 0.2)
        assertTrue("The second agreeing reading revises card, row and chip\n${trace(lane)}",
            f2.pickup == 2.0 && f2.shownPickup == 2.0 && f2.rows.single().pickupKm == 2.0 && f2.shownFare == 79.45)
    }

    @Test fun `a designed hold on the second agreeing read never fails the contract`() {
        // B1 round 4: the contract's confirmation is the stabilizer's second consecutive agreeing
        // OBSERVATION, not two raw pixel reads. Two legitimate holds after a misread first fare: f2's
        // Aceptar label is split by the countdown fill (an uncertain frame keeps the confirmed fare
        // and breaks agreement), or f1's pickup loses its decimal so the guard rejects the correct f2.
        val misread = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "$79.45" to "$19.45")
        for ((name, f1, f2) in listOf(
            Triple("split", RealGeometryFixtures.priority(), RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "Aceptar" to "Acep")),
            Triple("pickup", RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "1 min (0.2 km)" to "1 min (2 km)"),
                RealGeometryFixtures.priority()))) {
            val lane = replay(UBER) {
                frame("$name f0 misread", name, misread, 0)
                frame("$name f1", name, f1, 850)
                frame("$name f2", name, f2, 1700)
                frame("$name f3", name, RealGeometryFixtures.priority(), 2550)
                frame("$name f4", name, RealGeometryFixtures.priority(), 3400)
            }
            assertTrue("$name: f2 holds the confirmed first reveal\n${trace(lane)}",
                lane.frames[2].visible && lane.frames[2].shownFare == 19.45)
            assertTrue("$name: two agreeing observations after the hold revise it\n${trace(lane)}",
                lane.frames[4].shownFare == 79.45 && lane.frames[4].shownPickup == 0.2)
            assertContract(lane, mapOf(name to 79.45), mapOf(name to 2.4))
        }
    }

    @Test fun `a fare-less admitted replacement frame confirms the pickup before the fare reveals`() {
        // Codex R4 #1 (A1 round 5): measured Radar geometry, round 4's corner-docked chip, 850 ms.
        // B f0 reads no fare (pickup 0.2, guard-accepted); B f1 reads $8.30 (guard-rejected) and
        // confirms the replacement with f0 admitted; B f2 reads $88.30 with a decimal-lost pickup
        // 2.0 on a guard-blind frame. The admitted fare-less frame is the pickup's first reveal:
        // $88.30 reveals at once, the pickup stays 0.2 until two consecutive frames read 2.0.
        val corners = listOf(HostRect(48, 91, 192, 235), HostRect(888, 91, 1032, 235))
        val radarB = RealGeometryFixtures.retext(RealGeometryFixtures.radar(), "$129.55" to "$88.30",
            "4 min (0.9 km)" to "1 min (0.2 km)", "34 min (12.2 km)" to "24 min (2.4 km)",
            "38 min • 13.1 km • $9.89/km est." to "25 min • 2.6 km • $33.96/km est.")
        assertFareLessAdmittedFramePinsPickup("identical legs", RealGeometryFixtures.priority(), radarB, 2.4, corners)
    }

    @Test fun `a fare-less admitted frame of a trip-leg replacement confirms the pickup before the fare reveals`() {
        // A1 round 5 mirror: the same sequence on a replacement that reads another trip leg.
        val other = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "$79.45" to "$88.30",
            "24 min (2.4 km)" to "30 min (3.1 km)", "25 min • 2.6 km • $30.56/km est." to "31 min • 3.3 km • $26.76/km est.")
        assertFareLessAdmittedFramePinsPickup("trip leg", RealGeometryFixtures.priority(), other, 3.1)
    }

    private fun assertFareLessAdmittedFramePinsPickup(name: String, a: List<OcrBlock>, b: List<OcrBlock>, bTrip: Double,
        controls: List<HostRect> = emptyList()) {
        val unread = b.filterNot { it.text == "$88.30" }
        val garbled = RealGeometryFixtures.retext(b, "$88.30" to "$8.30")
        val lost = RealGeometryFixtures.retext(b, "1 min (0.2 km)" to "1 min (2 km)")
        for (agreeing in listOf(false, true)) {
            val lane = replay(UBER) {
                var t = 0L
                repeat(3) { frame("a f$it", "a", a, t, controls = controls); t += 850 }
                frame("b f0 unread", "b", unread, t, controls = controls); t += 850
                frame("b f1 garbled", "b", garbled, t, controls = controls); t += 850
                frame("b f2 lost", "b", lost, t, controls = controls); t += 850
                frame(if (agreeing) "b f3 lost" else "b f3", "b", if (agreeing) lost else b, t, controls = controls); t += 850
                frame("b f4", "b", b, t, controls = controls)
            }
            val case = "$name/${if (agreeing) "two readings" else "one reading"}"
            // The generic contract alone must catch a pickup revised by one frame (B3 round 5).
            assertContract(lane, mapOf("a" to 79.45, "b" to 88.30), mapOf("a" to 2.4, "b" to bTrip))
            val aSession = lane.frames.first().sessionId
            val f1 = lane.frames.single { it.label == "b f1 garbled" }
            val bSession = f1.sessionId
            fun row(f: Frame) = f.rows.single { it.sessionId == bSession }
            assertTrue("$case: f1 confirms B fare-less, its row minted with the admitted pickup\n${trace(lane)}",
                f1.published && bSession != null && bSession != aSession && f1.fare == null && f1.pickup == 0.2 &&
                    row(f1).fareMxn == null && row(f1).pickupKm == 0.2)
            val f2 = lane.frames.single { it.label == "b f2 lost" }
            assertTrue("$case: \$88.30 reveals at f2 with the confirmed pickup on card, row and chip\n${trace(lane)}",
                f2.published && f2.sessionId == bSession && f2.fare == 88.30 && f2.pickup == 0.2 && f2.visible &&
                    f2.shownFare == 88.30 && f2.shownPickup == 0.2 && f2.shownTrip == bTrip &&
                    row(f2).fareMxn == 88.30 && row(f2).pickupKm == 0.2)
            val f3 = lane.frames[lane.frames.indexOf(f2) + 1]
            if (agreeing) {
                assertTrue("$case: the second agreeing reading revises card, row and chip at f3\n${trace(lane)}",
                    f3.published && f3.pickup == 2.0 && f3.shownPickup == 2.0 && row(f3).pickupKm == 2.0 &&
                        f3.shownFare == 88.30)
            } else {
                val paid = lane.events.filterIsInstance<OfferEvent.Parsed>().filter { it.card.sessionId == bSession }
                assertTrue("$case: no B card, display or row ever carries the single-frame pickup\n${trace(lane)}",
                    paid.none { it.card.pickupDistanceKm == 2.0 } && lane.frames.none { it.shownPickup == 2.0 } &&
                        lane.frames.flatMap { it.rows }.none { it.pickupKm == 2.0 })
                assertTrue("$case: B ends on its own economics\n${trace(lane)}", lane.frames.last().let {
                    it.visible && it.shownFare == 88.30 && it.shownPickup == 0.2 && it.shownTrip == bTrip })
            }
        }
    }

    @Test fun `a payment-less card carries the confirmed pickup, never a single-frame reading`() {
        // B1 round 5 (Codex R4 low): fare unread on f0 (pickup 0.2) and on f1 (decimal-lost 2.0).
        // The payment-less card on the bus carries the confirmed legs, so it differs from the row
        // only by the missing fare; two consecutive readings revise it like any revision.
        val unread = RealGeometryFixtures.priority().filterNot { it.text == "$79.45" }
        val lostUnread = RealGeometryFixtures.retext(unread, "1 min (0.2 km)" to "1 min (2 km)")
        val lane = replay(UBER) {
            frame("pr f0 unread", "pr", unread, 0)
            frame("pr f1 unread lost", "pr", lostUnread, 850)
            frame("pr f2 unread lost", "pr", lostUnread, 1700)
        }
        val (f0, f1, f2) = lane.frames
        assertTrue("f0 reveals the pickup without payment\n${trace(lane)}",
            f0.published && f0.fare == null && f0.pickup == 0.2 && f0.rows.single().pickupKm == 0.2)
        assertTrue("f1's single reading reaches neither the card nor the row\n${trace(lane)}",
            f1.published && f1.fare == null && f1.pickup == 0.2 && f1.rows.single().pickupKm == 0.2 && f1.shownFare == null)
        assertTrue("Two consecutive readings revise the payment-less card\n${trace(lane)}",
            f2.published && f2.fare == null && f2.pickup == 2.0)
    }

    @Test fun `a single frame never reverts a two-frame revision`() {
        // B2 round 5: each revision needs FRESH agreement — a single frame reading the first-reveal
        // fare again after a confirmed revision is a new candidate, never a reversion.
        val misread = RealGeometryFixtures.retext(RealGeometryFixtures.priority(), "$79.45" to "$19.45")
        val lane = replay(UBER) {
            frame("pr f0 misread", "pr", misread, 0)
            frame("pr f1", "pr", RealGeometryFixtures.priority(), 850)
            frame("pr f2", "pr", RealGeometryFixtures.priority(), 1700)
            frame("pr f3 misread", "pr", misread, 2550)
            frame("pr f4", "pr", RealGeometryFixtures.priority(), 3400)
        }
        assertTrue("f2 revises to the agreed fare\n${trace(lane)}", lane.frames[2].shownFare == 79.45)
        assertTrue("A single misread frame never reverts it\n${trace(lane)}",
            lane.frames.drop(2).all { it.shownFare == 79.45 && (!it.published || it.fare == 79.45) })
        assertContract(lane, mapOf("pr" to 79.45), mapOf("pr" to 2.4))
    }

    @Test fun `a route-incomplete frame withholds payment only when its fare contradicts the confirmed one`() {
        // B6: no readable trip leg; a fare other than the confirmed one withholds payment for that
        // frame (A1 (a)); the confirmed fare itself keeps the held verdict.
        val noTrip = RealGeometryFixtures.retext(RealGeometryFixtures.uberx(), "34 min (7.3 km)" to "34 mn (7.3 km")
        val lane = replay(UBER) {
            var t = 0L
            repeat(3) { frame("x f$it", "x", RealGeometryFixtures.uberx(), t); t += 850 }
            frame("x other fare", "x", RealGeometryFixtures.retext(noTrip, "$104.41" to "$7.84"), t); t += 850
            frame("x f3", "x", RealGeometryFixtures.uberx(), t); t += 850
            frame("x same fare", "x", noTrip, t)
        }
        val sid = lane.frames.first().sessionId
        val other = lane.frames.single { it.label == "x other fare" }
        assertTrue("A contradicting fare publishes without payment in the same session\n${trace(lane)}",
            other.published && other.visible && other.fare == null && other.shownFare == null && other.sessionId == sid)
        // Round 1: a route-incomplete frame republishes the held verdict (old legs, confirmed fare).
        val same = lane.frames.single { it.label == "x same fare" }
        assertTrue("The confirmed fare keeps the held verdict\n${trace(lane)}",
            same.visible && same.shownFare == 104.41 && same.sessionId == sid && same.fare == 104.41 && same.tripKm == 7.3)
        assertTrue(trace(lane), lane.frames.single { it.label == "x f3" }.shownFare == 104.41)
    }

    @Test fun `garbled and signature-free frames hold then soft-hide then retire one row`() {
        val garbled = RealGeometryFixtures.uberx().map {
            if (it.text == "5 min (1.0 km)") it.copy(text = "5 mn 1.0 km") else it }
        val lane = replay(UBER) {
            frame("x f0", "x", RealGeometryFixtures.uberx(), 0)
            frame("x f1", "x", RealGeometryFixtures.uberx(), 850)
            frame("x garbled", "x", garbled, 1700)
            frame("x f3", "x", RealGeometryFixtures.uberx(), 2550)
            frame("gone f0", "gone", emptyList(), 3400, signature = false)
            frame("x back", "x", RealGeometryFixtures.uberx(), 4250)
            frame("gone f1", "gone", emptyList(), 5100, signature = false)
            frame("gone f2", "gone", emptyList(), 5950, signature = false)
            frame("gone f3", "gone", emptyList(), 6800, signature = false)
            frame("gone f4", "gone", emptyList(), 7650, signature = false)
        }
        val byLabel = lane.frames.associateBy { it.label }
        assertTrue("A garbled leg keeps the chip and the confirmed verdict\n${trace(lane)}",
            byLabel.getValue("x garbled").visible && byLabel.getValue("x garbled").fare in setOf(null, 104.41) &&
                byLabel.getValue("x garbled").tripKm in setOf(null, 7.3))
        assertTrue(trace(lane), byLabel.getValue("gone f0").state == "soft-hidden" && byLabel.getValue("gone f0").rect != null)
        assertTrue(trace(lane), byLabel.getValue("x back").visible)
        assertTrue(trace(lane), lane.frames.filter { it.offer == "x" }.mapNotNull { it.sessionId }.distinct().size == 1)
        assertTrue("Two signature-free frames spanning 850 ms only soft-hide\n${trace(lane)}",
            byLabel.getValue("gone f3").state == "soft-hidden" && byLabel.getValue("gone f3").rect != null)
        assertTrue("Three signature-free frames spanning 1.8 s retire the offer\n${trace(lane)}",
            byLabel.getValue("gone f4").state.startsWith("detached") && byLabel.getValue("gone f4").rect == null)
        assertTrue(trace(lane), lane.frames.filter { it.offer == "x" && it.published }.all { it.fare == 104.41 })
    }

    @Test fun `route-incomplete frames cannot hold a verdict past the 15 s parse ceiling`() {
        // C6 (S4): a held route-incomplete frame is not a successful parse; main's ceiling bounds it.
        val garbled = RealGeometryFixtures.uberx().map {
            if (it.text == "5 min (1.0 km)") it.copy(text = "5 mn 1.0 km") else it }
        val lane = replay(UBER) {
            frame("x f0", "x", RealGeometryFixtures.uberx(), 0)
            for (t in 850L..16_150L step 850) frame("x garbled $t", "x", garbled, t)
        }
        val retired = lane.frames.indexOfFirst { it.state == "detached:CONFIRMED_ABSENCE" }
        assertTrue("Retired at the first frame past 15 s\n${trace(lane)}",
            retired >= 0 && lane.frames[retired].label == "x garbled 15300")
        assertTrue("Held and visible until then\n${trace(lane)}", lane.frames.take(retired).all { it.visible })
    }

    @Test fun `a contradictory garbled frame never shows the previous offer's fare`() {
        val nextGarbled = RealGeometryFixtures.retext(RealGeometryFixtures.uberx(), "$104.41" to "$88.30",
            "5 min (1.0 km)" to "5 mi (1.0 km", "34 min (7.3 km)" to "25 min (7.8 km)")
        val lane = replay(UBER) {
            var t = 0L
            repeat(3) { frame("x f$it", "x", RealGeometryFixtures.uberx(), t); t += 850 }
            frame("garbled next", "garbled", nextGarbled, t)
        }
        val garbled = lane.frames.last()
        assertTrue("Unreadable legs plus another fare withhold payment\n${trace(lane)}",
            garbled.visible && garbled.fare == null)
        assertTrue(trace(lane), lane.frames.filter { it.offer == "x" }.all { it.fare == 104.41 })
    }

    @Test fun `every real layout slid from card top 300 to 1500 is displayed clear of fare legs close and accept`() {
        val layouts = mapOf(UBER to listOf("radar" to RealGeometryFixtures.radar(), "uberx" to RealGeometryFixtures.uberx(),
            "priority" to RealGeometryFixtures.priority(), "stack" to RealGeometryFixtures.priorityRadarStack()),
            DIDI to listOf("didi" to RealGeometryFixtures.didi(), "bid" to RealGeometryFixtures.didiBid(),
                "live" to RealGeometryFixtures.didiLive()))
        val failures = mutableListOf<String>()
        var exercised = 0
        var offScreen = 0
        for (scale in listOf(1f, 1.3f)) {
            val f = ChipSize((720 * scale).toInt(), (336 * scale).toInt())
            val c = ChipSize((720 * scale).toInt(), (240 * scale).toInt())
            // The two dock columns (right, left) and what may block each: the right one also has
            // the One UI edge handle, a protected system window.
            val columns = listOf(1044 - c.width to listOf(RealGeometryFixtures.edgeHandle.expanded(48)), 36 to emptyList())
            for ((host, list) in layouts) for ((name, blocks) in list) {
                val cardTop = blocks.filter { it.bounds.top >= 600 || host == DIDI }.minOf { it.bounds.top }
                for (top in 300..1500 step 100) {
                    val moved = RealGeometryFixtures.shift(blocks, top - cardTop)
                        .filter { it.bounds.top >= 91 && it.bounds.bottom <= 2196 }
                    val lane = replay(host, f, c) { frame("$name@$top f0", name, moved, 0); frame("$name@$top f1", name, moved, 850) }
                    val crit = critical(moved)
                    val complete = RealGeometryFixtures.legsOf(moved).size >= 2 && RealGeometryFixtures.fareOf(moved) != null
                    if (lane.frames.none { it.published }) {
                        // Only a card slid partly under the nav bar may publish nothing.
                        if (complete) failures += "$name@$top x$scale: a complete card published nothing"
                        else offScreen++
                        continue
                    }
                    exercised++
                    for (fr in lane.frames.filter { it.published }) {
                        fr.rect?.let { r -> crit.firstOrNull { it.overlaps(r) }?.let { failures += "${fr.label} x$scale covers $it with $r" } }
                        if (fr.rect == null) {
                            // C11 is legitimate only when the docked compact plate fits in NEITHER dock
                            // column: each column's band runs from the top chrome to its first blocker.
                            val fits = columns.any { (x, own) ->
                                val bottom = (crit + own).filter { it.left < x + c.width && it.right > x && it.bottom > 139 }
                                    .minOfOrNull { it.top } ?: 2196 - 48
                                bottom - 139 >= c.height
                            }
                            if (fits) failures += "${fr.label} x$scale hidden although a dock column fits: ${fr.state}"
                        }
                    }
                }
            }
        }
        assertTrue("Positions exercised: $exercised (off-screen: $offScreen) of 182", exercised + offScreen == 182 && exercised >= 130)
        assertTrue(failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    private data class Quad(val name: String, val blocks: List<OcrBlock>, val fare: Double, val trip: Double)
}
