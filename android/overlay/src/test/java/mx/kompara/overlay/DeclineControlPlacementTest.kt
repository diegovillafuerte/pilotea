package mx.kompara.overlay

import mx.kompara.overlay.RealGeometryFixtures.DIDI
import mx.kompara.overlay.RealGeometryFixtures.UBER
import mx.kompara.overlay.RealGeometryFixtures.closeBody
import mx.kompara.overlay.RealGeometryFixtures.display
import mx.kompara.overlay.RealGeometryFixtures.pillBody
import mx.kompara.overlay.RealGeometryFixtures.systemWindows
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostOcrGeometryEvidence
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.MapMarker
import mx.kompara.parsers.model.MapMarkerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 0.2.17: the chip never hides or blocks the host's decline control. Literal S25 inputs from the
 * 0.2.15 device log of 2026-11-06 (TEMP DIAG show lines: rawCardTop, fare, upper, routes, controls,
 * soft, mask) plus the decline rects OCR read on those frames. v4 (WP-B): the pill as the overlay
 * measures it at density 3 — 205 × 56 dp plain ([pill]); on a Pon Tu Precio card the larger size is
 * the chip with its two-amount tray, 207 × 90 dp ([full]), and the smaller the plain pill ([compact]).
 * [V3_FULL]/[V3_COMPACT] are 0.2.15's plates, kept only where an assertion pins what they did.
 */
class DeclineControlPlacementTest {
    private val viewport = OverlayViewport(display, 3f, HostOrientation.PORTRAIT, OverlayInsets(0, 91, 0, 144))
    private val full = ChipSize(621, 270)
    private val compact = ChipSize(615, 168)
    private val plain = ChipSize(615, 168)
    private val V3_FULL = ChipSize(720, 336)
    private val V3_COMPACT = ChipSize(720, 240)
    private fun v3(g: HostGeometry) = OverlayPositioning.place(g, g.packageName, viewport, g.capturedAtMs, V3_FULL, V3_COMPACT) as OverlayPlacement.Displayed

    private val pill = HostRect(480, 163, 993, 213)          // 1794009689245, first frame
    private val tallPill = HostRect(479, 156, 993, 212)      // 1794010032773
    private val tall2Pill = HostRect(480, 163, 994, 213)     // 1794010056028
    private val permitido = HostRect(769, 158, 989, 208)     // masked continuation: only "permitido"
    private val roundX = HostRect(957, 163, 997, 200)        // 1794009751842
    /** 0.2.18's calibrated decline area on the S25: 1080 - 201/28 dp, safe top 91 + 21/41 dp. */
    private val reservation = HostRect(477, 154, 996, 214)

    private fun geometry(host: String = DIDI, top: Int, fare: HostRect?, upper: List<HostRect>, routes: List<HostRect>,
        controls: List<HostRect>, decline: List<HostRect> = emptyList(), soft: List<HostRect> = emptyList(),
        mask: HostRect? = null, at: Long = 1_000) =
        HostGeometry(host, "derived-ocr_text_bounds", at, display, HostOrientation.PORTRAIT,
            HostRect(0, 0, 1080, 1), HostRect(0, top, 1080, 2340), (controls + decline + systemWindows).distinct(),
            emptyList(), source = HostGeometrySource.OCR_TEXT_BOUNDS,
            ocrEvidence = HostOcrGeometryEvidence(mask, upper, routes, false), fareBounds = fare,
            softObstacles = soft, declineControls = decline)

    /** [bid]: a Pon Tu Precio card (its chip may carry the tray); any other offer measures the plain pill twice. */
    private fun place(g: HostGeometry, decision: PlacementEvidence.Decision? = null, saved: OverlayPosition? = null,
        savedDocked: Boolean = false, bid: Boolean = true) = OverlayPositioning.place(decision?.geometry ?: g, g.packageName, viewport,
        g.capturedAtMs, if (bid) full else plain, if (bid) compact else plain, saved, decision?.dock ?: false, savedDocked)

    private fun shown(g: HostGeometry, decision: PlacementEvidence.Decision? = null, bid: Boolean = true) =
        place(g, decision, bid = bid) as OverlayPlacement.Displayed

    /** S07 f0: the normal Pon Tu Precio card (ceiling 593). */
    private fun ptp(decline: List<HostRect> = listOf(pill), mask: HostRect? = null, upper: List<HostRect> = listOf(HostRect(105, 641, 425, 682))) =
        geometry(top = 593, fare = HostRect(83, 774, 511, 886), upper = upper,
            routes = listOf(HostRect(118, 1339, 504, 1394), HostRect(151, 1550, 524, 1602)),
            controls = listOf(HostRect(348, 1822, 722, 1876), HostRect(99, 1996, 796, 2045)), decline = decline, mask = mask)

    /** S13 f0: the tall Pon Tu Precio card (card top 423, fare top 618). */
    private fun tall(decline: List<HostRect> = listOf(tallPill), fare: HostRect = HostRect(12, 618, 472, 730),
        top: Int = 423, upper: List<HostRect> = listOf(HostRect(104, 471, 420, 529)), mask: HostRect? = null,
        routes: List<HostRect> = listOf(HostRect(176, 1337, 483, 1394), HostRect(150, 1547, 502, 1601))) =
        geometry(top = top, fare = fare, upper = upper, routes = routes,
            controls = listOf(HostRect(357, 1823, 717, 1882), HostRect(98, 1996, 795, 2045)), decline = decline, mask = mask)

    /** S14 f0: the second tall card (card top 437). */
    private fun tall2(decline: List<HostRect> = listOf(tall2Pill)) =
        geometry(top = 437, fare = HostRect(84, 618, 493, 730), upper = listOf(HostRect(105, 485, 425, 526)),
            routes = listOf(HostRect(151, 1337, 469, 1394), HostRect(150, 1547, 509, 1604)),
            controls = listOf(HostRect(353, 1821, 717, 1879), HostRect(0, 1991, 792, 2051)), decline = decline)

    /** The tall card's tightest fare the plain pill still clears below the pill (bottom 428 + 48), and one pixel less. */
    private val tightFare = HostRect(84, 476, 473, 588)
    private val c11Fare = HostRect(84, 475, 473, 587)

    /** The round X's 48 dp touch target as the deriver protects it (the circle is ≈ (905,110)-(1050,255)). */
    private val roundXTarget = HostRect(905, 109, 1049, 253)

    /** S09 f0: Más Rápido with the round X above the card and a metres pickup. */
    private fun masRapido() = geometry(top = 925, fare = HostRect(84, 973, 541, 1085), upper = listOf(roundX),
        routes = listOf(HostRect(152, 1694, 495, 1746), HostRect(165, 1902, 536, 1956)),
        controls = listOf(roundX), decline = listOf(roundXTarget))

    /** Uber H1 (17:43:28): the floating Trip Radar pill and the in-card close X. */
    private fun uberRadar(soft: List<HostRect>, mask: HostRect?, upper: List<HostRect>) = geometry(host = UBER, top = 586,
        fare = HostRect(102, 737, 548, 847), upper = upper,
        routes = listOf(HostRect(193, 1384, 506, 1428), HostRect(194, 1588, 543, 1633)),
        controls = listOf(HostRect(891, 659, 933, 701), HostRect(395, 1948, 683, 1995)),
        decline = listOf(HostRect(891, 659, 933, 701)), soft = soft, mask = mask)

    private val uberClose = HostRect(891, 659, 933, 701)
    private fun radarF0() = uberRadar(listOf(HostRect(205, 428, 862, 480)), null,
        listOf(HostRect(205, 428, 862, 480), HostRect(192, 634, 474, 680), uberClose))
    private fun radarF1() = uberRadar(listOf(HostRect(205, 428, 861, 480), HostRect(202, 423, 873, 495)),
        HostRect(28, 132, 764, 388), listOf(HostRect(205, 428, 861, 480), HostRect(195, 634, 479, 684), uberClose,
            HostRect(202, 423, 795, 495)))
    private fun radarF2(mask: HostRect? = HostRect(28, 131, 764, 483)) = uberRadar(emptyList(), mask,
        listOf(HostRect(195, 633, 472, 680), uberClose))

    private fun rect(p: OverlayPlacement.Displayed) = p.position.bounds(p.size)
    private fun observed(g: HostGeometry) = g.declineControls - reservation

    @Test fun `the first Pon Tu Precio frame rejects the 0_2_15 FULL foot over the pill and admits the chip with its tray at once`() {
        val evidence = PlacementEvidence()
        val decision = evidence.observe(ptp(), viewport)
        assertFalse(decision.dock)
        assertEquals(593, decision.ceiling)
        assertFalse("0.2.15's FULL foot sits on the pill's keep-out",
            OverlayPositioning.valid(OverlayPosition(36, 209), V3_FULL, decision.geometry, viewport))
        val p = shown(ptp(), decision)
        // v4: the chip with its tray fits the foot (0.2.20's 720x240 compact sat at (36,305)).
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 275), full, PlateFormat.FULL, rule = PlacementRule.FOOT), p)
        assertFalse(rect(p).overlaps(pillBody))
        // Without the decline control (0.2.15's controls) its FULL foot covered the pill.
        val old = v3(ptp(decline = emptyList()))
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 209), V3_FULL, PlateFormat.FULL, rule = PlacementRule.FOOT), old)
        assertTrue(rect(old).overlaps(pillBody))
    }

    @Test fun `the tall Pon Tu Precio card docks the chip directly below the pill instead of covering it`() {
        val p = shown(tall(), PlacementEvidence().observe(tall(), viewport))
        // v4: the chip with its tray docks there (0.2.20 docked its 720x240 compact at the same slot).
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 262), full, PlateFormat.FULL, rule = PlacementRule.DOCK), p)
        assertEquals(HostRect(36, 262, 657, 532), rect(p))
        assertFalse(rect(p).overlaps(pillBody))
        assertTrue("Only the product label is covered: ends above the fare's 16 dp line", rect(p).bottom <= 618 - 48)
        assertEquals("0.2.15 docked FULL over the pill", HostRect(36, 139, 756, 475),
            rect(v3(tall(decline = emptyList()))))
        val p2 = shown(tall2(), PlacementEvidence().observe(tall2(), viewport))
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 262), full, PlateFormat.FULL, rule = PlacementRule.DOCK), p2)
        assertEquals("0.2.15's compact foot sat on the pill", HostRect(36, 149, 756, 389),
            rect(v3(tall2(decline = emptyList()))))
    }

    @Test fun `C11 only when the compact plate directly below the pill would reach the fare clearance`() {
        // v4: the plain pill (168 px) fits 72 px tighter than 0.2.20's 240 px compact (C11 below fare top 548).
        assertEquals(HostRect(36, 260, 651, 428), rect(shown(tall(fare = tightFare))))
        assertEquals(OverlayPlacement.Suppressed("NO_SAFE_SPACE"), place(tall(fare = c11Fare)))
    }

    @Test fun `the round X card docks below the reservation and never touches the X`() {
        val decision = PlacementEvidence().observe(masRapido(), viewport)
        assertEquals(115, decision.ceiling)
        val p = shown(masRapido(), decision, bid = false)
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 262), plain, PlateFormat.FULL, rule = PlacementRule.DOCK), p)
        assertEquals(HostRect(36, 262, 651, 430), rect(p))
        assertFalse(rect(p).overlaps(closeBody))
        assertFalse(OverlayPositioning.touchable(p, decision.geometry, seenClear = true))
    }

    @Test fun `at font 1_3 the round X card docks below the whole X button instead of C11`() {
        val g = masRapido()
        val decision = PlacementEvidence().observe(g, viewport)
        // v4: the pill at 1.3x is 243 x 72.8 dp (729 x 218 px), clear of the X altogether, docked below the reservation.
        val large = ChipSize(729, 218)
        val pill = OverlayPositioning.place(decision.geometry, DIDI, viewport, g.capturedAtMs, large, large,
            dock = decision.dock) as OverlayPlacement.Displayed
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 262), large, PlateFormat.FULL, rule = PlacementRule.DOCK), pill)
        assertTrue(rect(pill).right < roundXTarget.left && !rect(pill).overlaps(closeBody))
        // 0.2.17's fix, kept for any plate this wide (0.2.20's at 1.3x): below the whole button, not just its glyph.
        val p = OverlayPositioning.place(decision.geometry, DIDI, viewport, g.capturedAtMs, ChipSize(936, 437),
            ChipSize(936, 312), dock = decision.dock) as OverlayPlacement.Displayed
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 301), ChipSize(936, 437), PlateFormat.FULL,
            rule = PlacementRule.DOCK), p)
        assertFalse("The wide plate clears the circle, not only its glyph", rect(p).overlaps(closeBody))
        // Below the glyph alone (bottom 200 + 16 dp) the 972 px-wide plate would graze the circle.
        assertTrue(HostRect(36, 248, 972, 685).overlaps(closeBody))
        // 0.2.15's geometry (the glyph only, no decline control): the wide plate touched the glyph's
        // margin and nothing pushed it below, so there was no slot at all.
        val glyphOnly = decision.geometry.copy(declineControls = emptyList(),
            protectedControls = decision.geometry.protectedControls - roundXTarget - reservation)
        assertEquals(OverlayPlacement.Suppressed("NO_SAFE_SPACE"), OverlayPositioning.place(glyphOnly, DIDI, viewport,
            g.capturedAtMs, ChipSize(936, 437), ChipSize(936, 312), dock = decision.dock))
    }

    @Test fun `a masked continuation keeps the latched pill and the chip without a detach or a re-place`() {
        val evidence = PlacementEvidence()
        val first = evidence.observe(tall(), viewport)
        var current = shown(tall(), first)
        assertEquals(HostRect(36, 262, 657, 532), rect(current))
        val mask = rect(current).expanded(8)
        // 1794010033647: the heading is under our mask, only "permitido" (or nothing) is read.
        for (decline in listOf(listOf(permitido), emptyList(), listOf(permitido), emptyList())) {
            val raw = tall(decline = decline, top = 570, upper = emptyList(), mask = mask, fare = HostRect(84, 618, 473, 730),
                routes = listOf(HostRect(182, 1339, 483, 1394), HostRect(150, 1547, 502, 1601)))
            val d = evidence.observe(raw, viewport)
            assertTrue("The pill stays protected: ${d.geometry.protectedControls}", tallPill in d.geometry.protectedControls)
            assertTrue(tallPill in d.geometry.declineControls)
            assertEquals(1, d.latched)
            assertEquals(423, d.ceiling)
            assertFalse(d.dock)
            val safe = OverlayPositioning.valid(current.position, current.size, d.geometry, viewport, current.overMarker, current.docked)
            assertTrue("The placed chip stays valid: no hide, no move", safe)
            val next = place(raw, d, current.position, savedDocked = true) as OverlayPlacement.Displayed
            assertEquals(current, next)
            current = next
        }
    }

    @Test fun `partial and full pill merge into one latched rect, released only by reset`() {
        val evidence = PlacementEvidence()
        evidence.observe(ptp(decline = listOf(permitido)), viewport)
        val d = evidence.observe(ptp(decline = listOf(tallPill)), viewport)
        assertEquals(listOf(HostRect(479, 156, 993, 212)), observed(d.geometry))
        val again = evidence.observe(ptp(decline = emptyList(), mask = HostRect(28, 297, 764, 553)), viewport)
        assertEquals(listOf(HostRect(479, 156, 993, 212)), observed(again.geometry))
        evidence.reset()
        val fresh = evidence.observe(ptp(decline = emptyList()), viewport)
        assertTrue(observed(fresh.geometry).isEmpty())
        assertFalse(HostRect(479, 156, 993, 212) in fresh.geometry.protectedControls)
        assertEquals(0, fresh.latched)
    }

    @Test fun `the latch never lowers the ceiling or docks, and a C11 frame does not clear it`() {
        val evidence = PlacementEvidence()
        val d = evidence.observe(ptp(), viewport)
        assertEquals(593, d.ceiling)
        assertFalse(d.dock)
        // A frame where even the plain pill cannot fit below the latched pill: C11.
        val tight = evidence.observe(tall(decline = emptyList(), fare = c11Fare), viewport)
        assertTrue(place(tall(), tight) is OverlayPlacement.Suppressed)
        val after = evidence.observe(ptp(decline = emptyList()), viewport)
        assertTrue("A C11 detach does not clear the latch", pill in after.geometry.protectedControls)
    }

    @Test fun `in-card decline controls are never latched`() {
        val evidence = PlacementEvidence()
        val first = evidence.observe(radarF0(), viewport)
        assertTrue(uberClose in first.geometry.declineControls)
        assertEquals(0, first.latched)
        val next = evidence.observe(radarF2(mask = null).copy(protectedControls = systemWindows, declineControls = emptyList()), viewport)
        assertFalse(uberClose in next.geometry.protectedControls)
        assertTrue(next.geometry.declineControls.isEmpty())
    }

    @Test fun `metres pickup rows are route anchors the dock keeps clear`() {
        val g = masRapido()
        val p = shown(g, PlacementEvidence().observe(g, viewport), bid = false)
        assertFalse(rect(p).overlaps(HostRect(152, 1694, 495, 1746).expanded(48)))
    }

    @Test fun `the counter-offer price row is protected even from a slot that could reach it`() {
        val row = HostRect(98, 1996, 795, 2045)
        val g = tall()
        assertTrue(row in g.protectedControls)
        for (y in listOf(1700, 1760, 1800)) assertFalse(OverlayPositioning.valid(OverlayPosition(36, y), compact, g, viewport, docked = true))
    }

    @Test fun `the Radar pill is covered only by a docked chip, and a docked chip is pass-through`() {
        val evidence = PlacementEvidence()
        val d0 = evidence.observe(radarF0(), viewport)
        val p0 = shown(radarF0(), d0, bid = false)
        // v4: the pill takes the right HIGHER slot above the Radar pill (0.2.20's compact sat at (36,140)).
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(429, 202), plain, PlateFormat.FULL, rule = PlacementRule.HIGHER), p0)
        assertFalse("Uber: never draggable, even in a clear strict slot", OverlayPositioning.touchable(p0, d0.geometry, seenClear = true))
        val d1 = evidence.observe(radarF1(), viewport)
        val p1 = place(radarF1(), d1, bid = false) as OverlayPlacement.Displayed
        // v4: the pill keeps its strict slot beside the merged pill (0.2.20's FULL had to dock over it).
        assertEquals(p0, p1)
        assertFalse(rect(p1).overlaps(HostRect(202, 423, 873, 495)))
        assertFalse("Uber: never touchable", OverlayPositioning.touchable(p1, d1.geometry, seenClear = true))
    }

    // Round 2: the soft latch is persistent for the offer like the decline latch (Codex's floating-control
    // latch): fresh observations of the same pill merge into it, a missing label is not a reset.
    @Test fun `a Radar pill seen once stays one merged soft obstacle for the whole offer, released only by reset`() {
        val evidence = PlacementEvidence()
        evidence.observe(radarF0(), viewport)
        val merged = HostRect(202, 423, 873, 495) // OCR (205,428)-(862,480) + node (202,423)-(873,495)
        assertEquals(listOf(merged), evidence.observe(radarF1(), viewport).geometry.softObstacles)
        val hidden = evidence.observe(radarF2(), viewport)
        assertEquals(listOf(merged), hidden.geometry.softObstacles)
        val docked = OverlayPlacement.Displayed(OverlayPosition(36, 139), plain, PlateFormat.FULL, rule = PlacementRule.DOCK)
        assertTrue(OverlayPositioning.valid(docked.position, plain, hidden.geometry, viewport, docked = true))
        assertFalse(OverlayPositioning.touchable(docked, hidden.geometry, seenClear = true))
        // Not read and not under our mask (an OCR dropout, a smaller plate): still the same pill.
        for (mask in listOf(null, HostRect(28, 132, 764, 388)))
            assertEquals(listOf(merged), evidence.observe(radarF2(mask = mask), viewport).geometry.softObstacles)
        evidence.reset()
        assertTrue(evidence.observe(radarF2(), viewport).geometry.softObstacles.isEmpty())
        assertTrue(evidence.observe(radarF2(mask = null), viewport).geometry.softObstacles.isEmpty())
    }

    // Round 2, raw replay 1794008610488..1794008621331 (H1): the compact plate's mask (28,132)-(764,388)
    // does not reach the pill, so the 0.2.17 round-1 latch dropped it and FULL (36,140)-(756,476) grew
    // over the pill, touchable; its mask re-added the pill and the next frame shrank it: a flip per frame.
    // v4: an Uber offer has one size, the plain pill, so it keeps that plate and that slot on every frame.
    @Test fun `a Radar offer keeps its plate beside the pill and never grows over it`() {
        val evidence = PlacementEvidence()
        val d0 = evidence.observe(radarF0(), viewport)
        var current = shown(radarF0(), d0, bid = false)
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(429, 202), plain, PlateFormat.FULL, rule = PlacementRule.HIGHER), current)
        val compactMask = rect(current).expanded(8)
        val frames = listOf(uberRadar(listOf(HostRect(205, 428, 861, 480)), compactMask,
            listOf(HostRect(205, 428, 861, 480), HostRect(195, 634, 479, 684), uberClose))) +
            List(12) { radarF2(mask = compactMask) }
        for ((i, raw) in frames.withIndex()) {
            val d = evidence.observe(raw, viewport)
            assertTrue("frame $i: the pill stays a soft obstacle", d.geometry.softObstacles.any { it.contains(HostRect(205, 428, 861, 480)) })
            val safe = OverlayPositioning.valid(current.position, current.size, d.geometry, viewport, current.overMarker, current.docked)
            assertTrue("frame $i: the compact plate stays valid", safe)
            val next = place(raw, d, current.position, savedDocked = current.docked, bid = false) as OverlayPlacement.Displayed
            assertEquals("frame $i: same plate, same slot", plain, next.size)
            assertEquals(current.position, next.position)
            assertFalse("frame $i: never over the pill", rect(next).overlaps(HostRect(202, 423, 873, 495)))
            assertFalse("frame $i: Uber's chip is never draggable", OverlayPositioning.touchable(next, d.geometry, seenClear = true))
            current = next
        }
    }

    @Test fun `a host or display change releases both latches`() {
        val evidence = PlacementEvidence()
        evidence.observe(ptp(), viewport)
        assertEquals(1, evidence.observe(ptp(decline = emptyList()), viewport).latched)
        val uber = evidence.observe(radarF2(mask = null).copy(declineControls = emptyList(), protectedControls = systemWindows), viewport)
        assertEquals("Another host: DiDi's pill is not Uber's", 0, uber.latched)
        assertFalse(pill in uber.geometry.protectedControls)
        evidence.observe(radarF0(), viewport)
        assertTrue(evidence.observe(radarF2(mask = null), viewport).geometry.softObstacles.isNotEmpty())
        val rotated = HostRect(0, 0, 2340, 1080)
        val landscape = evidence.observe(radarF2(mask = null).copy(displayBounds = rotated, orientation = HostOrientation.LANDSCAPE,
            offerCard = HostRect(0, 586, 2340, 1080)), viewport)
        assertTrue("A display change: the portrait pill is gone", landscape.geometry.softObstacles.isEmpty())
        // Back on the same host and display, a pill-less frame keeps whatever this offer latched since.
        evidence.observe(ptp(), viewport)
        assertEquals(1, evidence.observe(ptp(decline = emptyList()), viewport).latched)
    }

    @Test fun `the high Radar card keeps its dock and the next card's fare and TOTAL stay clear`() {
        // 1794008743732: Radar pill (204,281)-(878,333) leaves no strict slot; the dock covers it, pass-through.
        val close = HostRect(891, 512, 933, 554)
        val g = geometry(host = UBER, top = 438, fare = HostRect(100, 589, 541, 699),
            upper = listOf(HostRect(204, 281, 878, 333), HostRect(164, 486, 479, 542), close),
            routes = listOf(HostRect(192, 1231, 533, 1279), HostRect(116, 1433, 606, 1486)),
            controls = listOf(close, HostRect(396, 1952, 686, 1994)), decline = listOf(close), soft = listOf(HostRect(204, 281, 878, 333)))
        val d = PlacementEvidence().observe(g, viewport)
        val p = place(g, d, bid = false) as OverlayPlacement.Displayed
        // v4: the narrower pill takes the right dock slot that 0.2.20's plate lost to the close X's margin.
        assertEquals(HostRect(429, 139, 1044, 307), rect(p))
        assertTrue(p.docked)
        assertTrue("Only a docked chip covers the Radar pill", rect(p).overlaps(HostRect(204, 281, 878, 333)))
        assertFalse(OverlayPositioning.touchable(p, d.geometry, seenClear = true))
        assertFalse(rect(p).overlaps(close.expanded(48)))
        // 1794008746496 replaces it in place: fare (100,902)-(551,1012), TOTAL (97,1053)-(870,1103).
        for (content in listOf(HostRect(100, 902, 551, 1012), HostRect(97, 1053, 870, 1103)))
            assertFalse(rect(p).expanded(8).overlaps(content))
    }

    @Test fun `touch follows the tier and the overlap - draggable only in a clear strict slot`() {
        val g = ptp()
        val foot = OverlayPlacement.Displayed(OverlayPosition(36, 377), compact, PlateFormat.COMPACT, rule = PlacementRule.FOOT)
        fun touchable(p: OverlayPlacement.Displayed, geometry: HostGeometry = g) = OverlayPositioning.touchable(p, geometry, seenClear = true)
        assertTrue(touchable(foot))
        assertTrue(touchable(foot.copy(rule = PlacementRule.HIGHER)))
        val marker = foot.copy(rule = PlacementRule.MARKER, overMarker = true)
        assertTrue("A map marker is not a host control",
            touchable(marker, g.copy(markers = listOf(MapMarker(MapMarkerKind.PICKUP, HostRect(300, 400, 340, 440))))))
        assertFalse("Docked: always pass-through", touchable(foot.copy(rule = PlacementRule.DOCK)))
        assertFalse("Over a decline control", touchable(foot.copy(position = OverlayPosition(36, 150))))
        assertFalse("Over soft host chrome", touchable(foot, g.copy(softObstacles = listOf(HostRect(500, 500, 600, 520)))))
        assertFalse("Over any protected control", touchable(foot,
            g.copy(protectedControls = g.protectedControls + HostRect(600, 530, 660, 560))))
    }

    @Test fun `a drag can never end over host chrome or start from a docked chip`() {
        val g = PlacementEvidence().observe(ptp(), viewport).geometry
        val foot = shown(ptp(), PlacementEvidence().observe(ptp(), viewport))
        assertEquals(OverlayPosition(36, 275), foot.position)
        assertEquals("Onto the pill's keep-out: rejected", foot.position, OverlayPositioning.drag(foot, OverlayPosition(36, 209), g, viewport))
        assertEquals("A clear strict slot is allowed", OverlayPosition(36, 270), OverlayPositioning.drag(foot, OverlayPosition(36, 270), g, viewport))
        val soft = g.copy(softObstacles = listOf(HostRect(36, 100, 756, 230)))
        assertEquals(foot.position, OverlayPositioning.drag(foot, OverlayPosition(36, 240), soft, viewport))
        val tallGeometry = PlacementEvidence().observe(tall(), viewport).geometry
        val docked = OverlayPlacement.Displayed(OverlayPosition(36, 262), compact, PlateFormat.COMPACT, rule = PlacementRule.DOCK)
        assertEquals("A docked chip is pass-through: no drag", docked.position,
            OverlayPositioning.drag(docked, OverlayPosition(36, 264), tallGeometry, viewport))
        assertEquals(docked.position, OverlayPositioning.snapToEdge(docked, tallGeometry, viewport))
        assertFalse(OverlayPositioning.clearSlot(docked.position, compact, tallGeometry, viewport))
        assertTrue(OverlayPositioning.clearSlot(foot.position, foot.size, g, viewport))
    }

    // 0.2.18, Codex's sequence: the normal Pon Tu Precio card with only the pill's OCR missing on the
    // first capture. 0.2.17 gave a touchable FOOT/FULL (36,209)-(756,545) over the decline body.
    private val tap = HostRect(600, 230, 601, 231)

    @Test fun `a first frame with the pill OCR missing never covers the decline body or takes its taps`() {
        val raw = ptp(decline = emptyList())
        val p = shown(raw, PlacementEvidence().observe(raw, viewport))
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 275), full, PlateFormat.FULL, rule = PlacementRule.FOOT), p)
        assertFalse("No FOOT/FULL over the body", rect(p).overlaps(pillBody))
        assertFalse("A tap at (600,230) reaches DiDi", rect(p).overlaps(tap))
        val without = v3(raw)
        assertEquals("The geometry without the reservation was 0.2.17's bug", HostRect(36, 209, 756, 545), rect(without))
        assertTrue(rect(without).overlaps(pillBody) && rect(without).overlaps(tap))
    }

    @Test fun `every form of DiDi card places below the reservation with no OCR of its decline control`() {
        val tallP = shown(tall(decline = emptyList()), PlacementEvidence().observe(tall(decline = emptyList()), viewport))
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 262), full, PlateFormat.FULL, rule = PlacementRule.DOCK), tallP)
        val g = masRapido().copy(protectedControls = systemWindows, declineControls = emptyList(),
            ocrEvidence = masRapido().ocrEvidence!!.copy(upperAnchors = emptyList()))
        val round = shown(g, PlacementEvidence().observe(g, viewport), bid = false)
        assertEquals(HostRect(36, 262, 651, 430), rect(round))
        assertTrue(round.docked)
        for (body in listOf(pillBody, closeBody)) assertFalse(rect(round).overlaps(body))
    }

    @Test fun `the reservation is calibrated from the viewport, in dp from the right edge and the safe top`() {
        val d = PlacementEvidence().observe(ptp(decline = emptyList()), viewport)
        assertEquals(listOf(reservation), d.geometry.declineControls)
        assertTrue(reservation in d.geometry.protectedControls)
        val big = HostRect(0, 0, 1440, 3120)
        val density4 = OverlayViewport(big, 4f, HostOrientation.PORTRAIT, OverlayInsets(0, 130, 0, 144))
        val g = ptp(decline = emptyList()).copy(displayBounds = big, offerCard = HostRect(0, 800, 1440, 3120), protectedControls = emptyList())
        assertEquals("Right 1440 - 28 dp, left 1440 - 201 dp, top 130 + 21 dp, bottom 130 + 41 dp",
            listOf(HostRect(636, 214, 1328, 294)), PlacementEvidence().observe(g, density4).geometry.declineControls)
        val narrow = OverlayViewport(HostRect(0, 0, 720, 1600), 2f, HostOrientation.PORTRAIT, OverlayInsets(0, 60, 0, 96))
        val n = ptp(decline = emptyList()).copy(displayBounds = narrow.bounds, offerCard = HostRect(0, 500, 720, 1600), protectedControls = emptyList())
        assertEquals(listOf(HostRect(318, 102, 664, 142)), PlacementEvidence().observe(n, narrow).geometry.declineControls)
    }

    @Test fun `the reservation is never an upper anchor, a ceiling input or a dock request`() {
        val raw = ptp(decline = emptyList())
        val d = PlacementEvidence().observe(raw, viewport)
        assertEquals(593, d.ceiling)
        assertEquals(593, d.geometry.offerCard.top)
        assertFalse(d.dock)
        assertEquals(raw.ocrEvidence, d.geometry.ocrEvidence)
        assertEquals("It counts as no latched observation", 0, d.latched)
        assertEquals("A tall card's ceiling stays its heading's", 423, PlacementEvidence().observe(tall(decline = emptyList()), viewport).ceiling)
        assertTrue("No upper anchor stays unobserved: the reservation cannot vouch for one",
            PlacementEvidence().observe(ptp(decline = emptyList(), upper = emptyList()), viewport).dock)
        val uberLike = PlacementEvidence().observe(raw.copy(packageName = UBER), viewport)
        assertEquals("The ceiling is the same without the reservation", uberLike.ceiling, d.ceiling)
        assertEquals(uberLike.dock, d.dock)
    }

    @Test fun `observed decline rects still enlarge the reservation, and it adds to an observed pill`() {
        val low = HostRect(480, 170, 993, 230)
        val d = PlacementEvidence().observe(tall(decline = listOf(low)), viewport)
        assertTrue(reservation in d.geometry.declineControls && low in d.geometry.declineControls)
        assertEquals(HostRect(36, 278, 657, 548), rect(shown(tall(decline = listOf(low)), d)))
        val high = HostRect(480, 120, 993, 170)
        val above = PlacementEvidence().observe(tall(decline = listOf(high)), viewport)
        assertEquals("Still below the reservation", HostRect(36, 262, 657, 532), rect(shown(tall(decline = listOf(high)), above)))
    }

    @Test fun `off-calibration displays and Uber have no reservation`() {
        val rotated = HostRect(0, 0, 2340, 1080)
        val landscape = OverlayViewport(rotated, 3f, HostOrientation.LANDSCAPE, OverlayInsets(0, 91, 0, 0))
        val g = ptp(decline = emptyList()).copy(displayBounds = rotated, orientation = HostOrientation.LANDSCAPE,
            offerCard = HostRect(0, 593, 2340, 1080), protectedControls = emptyList())
        assertTrue(PlacementEvidence().observe(g, landscape).geometry.declineControls.isEmpty())
        val observedOnly = g.copy(protectedControls = listOf(pill), declineControls = listOf(pill))
        assertEquals("Landscape relies on OCR", listOf(pill), PlacementEvidence().observe(observedOnly, landscape).geometry.declineControls)
        assertFalse(reservation in PlacementEvidence().observe(radarF0(), viewport).geometry.protectedControls)
    }

    // "It must not make a chip fail": no card where only the reservation leaves no slot loses its chip.
    // v4: the pill docked below the reservation ends at 430, so the fare top is 470 (0.2.20's 336 px plate: 540).
    private val waiverFare = HostRect(84, 470, 541, 582)
    private fun waiverCard(decline: List<HostRect>, mask: HostRect? = null) = geometry(top = 422, fare = waiverFare, upper = listOf(roundX),
        routes = listOf(HostRect(152, 1694, 495, 1746), HostRect(165, 1902, 536, 1956)),
        controls = listOf(roundX), decline = decline, mask = mask)

    @Test fun `a C11 only the reservation causes waives it for the offer and the chip stays`() {
        fun raw(mask: HostRect? = null) = waiverCard(listOf(roundXTarget), mask)
        val evidence = PlacementEvidence()
        val d = evidence.observe(raw(), viewport)
        assertEquals(OverlayPlacement.Suppressed("NO_SAFE_SPACE"), place(raw(), d, bid = false))
        val relaxed = requireNotNull(evidence.withoutReservation())
        assertFalse(reservation in relaxed.protectedControls || reservation in relaxed.declineControls)
        assertTrue("Only the synthetic rect goes", roundXTarget in relaxed.declineControls && roundXTarget in relaxed.protectedControls)
        val p = OverlayPositioning.place(relaxed, DIDI, viewport, relaxed.capturedAtMs, plain, plain, null, d.dock) as OverlayPlacement.Displayed
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 139), plain, PlateFormat.FULL, rule = PlacementRule.DOCK), p)
        assertFalse("Not waived until that placement is applied", evidence.waived)
        evidence.commitWaiver()
        assertTrue(evidence.waived)
        val next = evidence.observe(raw(mask = rect(p).expanded(8)), viewport)
        assertFalse("Waived until the offer ends: the chip is not moved back", reservation in next.geometry.protectedControls)
        assertTrue(OverlayPositioning.valid(p.position, p.size, next.geometry, viewport, p.overMarker, p.docked, kept = true))
        assertTrue("Nothing left to waive", evidence.withoutReservation() == null)
        evidence.reset()
        assertFalse(evidence.waived)
        assertTrue("A new offer has its reservation again", reservation in evidence.observe(raw(), viewport).geometry.protectedControls)
    }

    // Codex: an observed decline rect exactly equal to the reservation, fare top 540.
    @Test fun `the waiver never drops an observed or latched rect that happens to equal the reservation`() {
        val observed = waiverCard(listOf(reservation))
        val evidence = PlacementEvidence()
        val d = evidence.observe(observed, viewport)
        assertEquals(OverlayPlacement.Suppressed("NO_SAFE_SPACE"), place(observed, d, bid = false))
        val relaxed = requireNotNull(evidence.withoutReservation())
        assertTrue("Observed: it stays", reservation in relaxed.protectedControls && reservation in relaxed.declineControls)
        assertEquals("So no DOCK/FULL (36,139) over it", OverlayPlacement.Suppressed("NO_SAFE_SPACE"),
            OverlayPositioning.place(relaxed, DIDI, viewport, relaxed.capturedAtMs, plain, plain, null, d.dock))
        val latchedOnly = PlacementEvidence()
        latchedOnly.observe(observed, viewport)
        latchedOnly.observe(waiverCard(emptyList()), viewport)
        val held = requireNotNull(latchedOnly.withoutReservation())
        assertTrue("Latched on an earlier frame: it stays", reservation in held.protectedControls && reservation in held.declineControls)
    }

    // Codex: the tall card with fare top 618, then the fare unread and the routes 100 px higher: observe() rebuilds top 518.
    @Test fun `a fare observe reconstructed never waives the reservation`() {
        val routes = listOf(HostRect(176, 1337, 483, 1394), HostRect(150, 1547, 502, 1601))
        val evidence = PlacementEvidence()
        evidence.observe(tall(decline = emptyList(), routes = routes), viewport)
        val unread = tall(decline = emptyList(), routes = routes.map { it.copy(top = it.top - 100, bottom = it.bottom - 100) }).copy(fareBounds = null)
        val d = evidence.observe(unread, viewport)
        assertEquals("observe moved the last fare with the routes", 518, d.geometry.fareBounds?.top)
        assertEquals("Not read on this frame", null, evidence.withoutReservation())
    }

    @Test fun `C11 keeps its contract for an observed pill, and a trial that does not place keeps the reservation`() {
        val tight = tall(fare = c11Fare)
        val evidence = PlacementEvidence()
        val d = evidence.observe(tight, viewport)
        val relaxed = requireNotNull(evidence.withoutReservation())
        assertEquals("Compact directly below the OCR pill still reaches the fare's clearance",
            OverlayPlacement.Suppressed("NO_SAFE_SPACE"), OverlayPositioning.place(relaxed, DIDI, viewport, relaxed.capturedAtMs, full, compact, null, d.dock))
        assertFalse(evidence.waived)
        assertTrue("The reservation is kept", reservation in evidence.observe(tight, viewport).geometry.protectedControls)
        assertFalse(evidence.waived)
        val unread = tall(decline = emptyList()).copy(fareBounds = null)
        val fresh = PlacementEvidence()
        fresh.observe(unread, viewport)
        assertEquals("An unread fare is transient: no waiver", null, fresh.withoutReservation())
    }

    // USER DECISION 2026-11-08: Uber's Radar pill can appear under the normal slot between captures.
    @Test fun `the chip is never touchable on Uber, in any tier or format, and cannot be dragged there`() {
        val g = geometry(host = UBER, top = 593, fare = HostRect(83, 774, 511, 886), upper = listOf(HostRect(105, 641, 425, 682)),
            routes = listOf(HostRect(118, 1339, 504, 1394), HostRect(151, 1550, 524, 1602)), controls = emptyList())
        for (size in listOf(full to PlateFormat.FULL, compact to PlateFormat.COMPACT)) for (rule in PlacementRule.entries) {
            val p = OverlayPlacement.Displayed(OverlayPosition(36, 200), size.first, size.second, rule = rule)
            assertFalse("$rule ${size.second}", OverlayPositioning.touchable(p, g, seenClear = true))
        }
        val saved = OverlayPosition(36, 200)
        val p = OverlayPositioning.place(g, UBER, viewport, g.capturedAtMs, plain, plain, saved) as OverlayPlacement.Displayed
        assertEquals("A saved slot still places", saved, p.position)
        assertFalse("...but passes taps through", OverlayPositioning.touchable(p, g, seenClear = true))
        assertEquals(p.position, OverlayPositioning.drag(p, OverlayPosition(36, 180), g, viewport))
        assertEquals(OverlayPosition(60, 200), OverlayPositioning.snapToEdge(p.copy(position = OverlayPosition(60, 200)), g, viewport))
        assertFalse("No Uber slot is a drag target", OverlayPositioning.clearSlot(p.position, p.size, g, viewport))
        val didi = g.copy(packageName = DIDI)
        assertEquals("The same drag on DiDi is allowed", OverlayPosition(36, 180), OverlayPositioning.drag(p, OverlayPosition(36, 180), didi, viewport))
    }

    @Test fun `a DiDi chip is touchable only after its footprint was seen without our own plate`() {
        val footprint = HostRect(36, 305, 756, 545)
        val foot = OverlayPlacement.Displayed(OverlayPosition(36, 305), compact, PlateFormat.COMPACT, rule = PlacementRule.FOOT)
        val evidence = PlacementEvidence()
        assertFalse("Nothing observed yet", evidence.seenClear(footprint))
        val under = evidence.observe(ptp(mask = footprint.expanded(8)), viewport)
        assertFalse("Captured under the old plate: a host control there was never seen", evidence.seenClear(footprint))
        assertFalse(OverlayPositioning.touchable(foot, under.geometry, evidence.seenClear(footprint)))
        val clear = evidence.observe(ptp(mask = null), viewport)
        assertTrue("A frame with no plate on screen", evidence.seenClear(footprint))
        assertTrue(OverlayPositioning.touchable(foot, clear.geometry, evidence.seenClear(footprint)))
        evidence.reset()
        assertFalse("Reset with the session", evidence.seenClear(footprint))
        evidence.observe(ptp(mask = HostRect(28, 600, 764, 900)), viewport)
        assertTrue("Our plate was elsewhere", evidence.seenClear(footprint))
        assertFalse("That frame saw nothing under the plate itself", evidence.seenClear(HostRect(36, 700, 756, 940)))
        evidence.observe(ptp(mask = footprint.expanded(8)), viewport)
        assertTrue("Seen clear stays seen for the session", evidence.seenClear(footprint))
    }

    @Test fun `an identity-unresolved frame is not seen clear`() {
        val footprint = HostRect(36, 305, 756, 545)
        val evidence = PlacementEvidence()
        evidence.observe(ptp(mask = null), viewport, identityUnresolved = true)
        assertFalse("No plate on it, but it may be another offer", evidence.seenClear(footprint))
        evidence.observe(ptp(mask = HostRect(28, 600, 764, 900)), viewport, identityUnresolved = true)
        assertFalse(evidence.seenClear(footprint))
        evidence.observe(ptp(mask = HostRect(28, 600, 764, 900)), viewport)
        assertTrue(evidence.seenClear(footprint))
    }

    // ML Kit boxes jitter 1-3 px and the latch only grows; S25 13 (1794010032773...): pill bottom 212 then 213.
    @Test fun `OCR jitter on the pill keeps a placed dock slot, real growth moves it, new placements keep the full gap`() {
        val evidence = PlacementEvidence()
        val first = tall(decline = listOf(HostRect(479, 156, 993, 212)))
        val current = shown(first, evidence.observe(first, viewport))
        assertEquals(HostRect(36, 262, 657, 532), rect(current))
        val mask = rect(current).expanded(8)
        fun frame(bottom: Int) = tall(decline = listOf(HostRect(479, 156, 993, bottom)), mask = mask)
        val jitter = evidence.observe(frame(215), viewport)
        assertTrue("212 -> 215 keeps the slot", OverlayPositioning.valid(current.position, current.size, jitter.geometry, viewport,
            current.overMarker, current.docked, kept = true))
        assertEquals("No re-place, same plate", current, place(frame(215), jitter, current.position, savedDocked = true))
        assertFalse("A new placement keeps the full 16 dp", OverlayPositioning.valid(current.position, current.size, jitter.geometry, viewport,
            docked = true))
        val edge = PlacementEvidence().also { it.observe(first, viewport) }.observe(frame(226), viewport)
        assertTrue("4 dp over the calibrated bottom still keeps it", OverlayPositioning.valid(current.position, current.size, edge.geometry, viewport,
            docked = true, kept = true))
        val past = PlacementEvidence().also { it.observe(first, viewport) }.observe(frame(227), viewport)
        assertFalse("One pixel past 4 dp does not: the keep is bounded", OverlayPositioning.valid(current.position, current.size, past.geometry, viewport,
            docked = true, kept = true))
        val grown = evidence.observe(frame(251), viewport)
        assertFalse("A 13 dp growth moves it", OverlayPositioning.valid(current.position, current.size, grown.geometry, viewport,
            current.overMarker, current.docked, kept = true))
        assertEquals("Below the grown pill", HostRect(36, 299, 657, 569), rect(place(frame(251), grown) as OverlayPlacement.Displayed))
    }

    @Test fun `the jitter allowance is only for a docked chip's decline rects`() {
        val g = tall(decline = listOf(HostRect(479, 156, 993, 215)))
        val strict = OverlayPosition(36, 262)
        assertFalse("A strict slot keeps the full gap", OverlayPositioning.valid(strict, compact, g.copy(offerCard = g.offerCard.copy(top = 600)), viewport, kept = true))
        val control = g.copy(protectedControls = g.protectedControls + HostRect(100, 200, 300, 225), declineControls = g.declineControls)
        assertFalse("Another control keeps the full gap", OverlayPositioning.valid(OverlayPosition(36, 262), compact, control, viewport, docked = true, kept = true))
    }

    /**
     * The decline words are read from OCR blocks BEFORE ChipMask (under our own chip), so nothing an
     * overlay window can draw over a host may contain them: the chip (la regla) and the stopped tab,
     * i.e. :overlay and :ui's shared components, formats, theme and resources. In-app screens
     * (onboarding's pictured Android dialog) and the accessibility-settings description are never
     * drawn over Uber or DiDi.
     */
    @Test fun `no Kompara overlay UI string contains the decline words read under our own chip`() {
        val words = Regex("""rechaz|permitido""", RegexOption.IGNORE_CASE)
        val literal = Regex(""""(?:[^"\\\n]|\\.)*"""")
        val roots = listOf("../overlay/src/main", "../ui/src/main/java/mx/kompara/ui/components",
            "../ui/src/main/java/mx/kompara/ui/format", "../ui/src/main/java/mx/kompara/ui/theme", "../ui/src/main/res")
        val files = roots.flatMap { File(it).walkTopDown().filter { f -> f.isFile && (f.extension == "kt" || f.extension == "xml") }.toList() }
        assertTrue("UI sources found: ${files.size}", files.count { it.extension == "kt" } > 20 &&
            files.any { it.name == "Regla.kt" } && files.any { it.name == "VerdictChipUi.kt" } && files.any { it.name == "strings.xml" })
        val hits = files.flatMap { f ->
            // Comments (KDoc quoting the host's pill) are not UI; string literals and <string> resources are.
            val text = f.readText().replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)^\s*//.*$"""), "")
            val strings = if (f.extension == "xml") Regex("""<string[^>]*>([^<]*)</string>""").findAll(text).map { it.groupValues[1] }
                else literal.findAll(text).map { it.value }
            strings.filter { words.containsMatchIn(it) }.map { "${f.path}: $it" }.toList()
        }
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }
}
