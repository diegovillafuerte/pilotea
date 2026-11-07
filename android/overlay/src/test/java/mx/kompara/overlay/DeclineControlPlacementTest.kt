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
 * soft, mask) plus the decline rects OCR read on those frames. FULL 720x336 and COMPACT 720x240 are
 * the plates the device measured.
 */
class DeclineControlPlacementTest {
    private val viewport = OverlayViewport(display, 3f, HostOrientation.PORTRAIT, OverlayInsets(0, 91, 0, 144))
    private val full = ChipSize(720, 336)
    private val compact = ChipSize(720, 240)

    private val pill = HostRect(480, 163, 993, 213)          // 1794009689245, first frame
    private val tallPill = HostRect(479, 156, 993, 212)      // 1794010032773
    private val tall2Pill = HostRect(480, 163, 994, 213)     // 1794010056028
    private val permitido = HostRect(769, 158, 989, 208)     // masked continuation: only "permitido"
    private val roundX = HostRect(957, 163, 997, 200)        // 1794009751842

    private fun geometry(host: String = DIDI, top: Int, fare: HostRect?, upper: List<HostRect>, routes: List<HostRect>,
        controls: List<HostRect>, decline: List<HostRect> = emptyList(), soft: List<HostRect> = emptyList(),
        mask: HostRect? = null, at: Long = 1_000) =
        HostGeometry(host, "derived-ocr_text_bounds", at, display, HostOrientation.PORTRAIT,
            HostRect(0, 0, 1080, 1), HostRect(0, top, 1080, 2340), (controls + decline + systemWindows).distinct(),
            emptyList(), source = HostGeometrySource.OCR_TEXT_BOUNDS,
            ocrEvidence = HostOcrGeometryEvidence(mask, upper, routes, false), fareBounds = fare,
            softObstacles = soft, declineControls = decline)

    private fun place(g: HostGeometry, decision: PlacementEvidence.Decision? = null, saved: OverlayPosition? = null,
        savedDocked: Boolean = false) = OverlayPositioning.place(decision?.geometry ?: g, g.packageName, viewport,
        g.capturedAtMs, full, compact, saved, decision?.dock ?: false, savedDocked)

    private fun shown(g: HostGeometry, decision: PlacementEvidence.Decision? = null) =
        place(g, decision) as OverlayPlacement.Displayed

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

    @Test fun `the first Pon Tu Precio frame rejects the 0_2_15 FULL foot over the pill and admits the compact at once`() {
        val evidence = PlacementEvidence()
        val decision = evidence.observe(ptp(), viewport)
        assertFalse(decision.dock)
        assertEquals(593, decision.ceiling)
        assertFalse("0.2.15's FULL foot sits on the pill's keep-out",
            OverlayPositioning.valid(OverlayPosition(36, 209), full, decision.geometry, viewport))
        val p = shown(ptp(), decision)
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 305), compact, PlateFormat.COMPACT, rule = PlacementRule.FOOT), p)
        assertFalse(rect(p).overlaps(pillBody))
        // Without the decline control (0.2.15's controls) the FULL foot covered the pill.
        val old = shown(ptp(decline = emptyList()))
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 209), full, PlateFormat.FULL, rule = PlacementRule.FOOT), old)
        assertTrue(rect(old).overlaps(pillBody))
    }

    @Test fun `the tall Pon Tu Precio card docks a compact plate directly below the pill instead of covering it`() {
        val p = shown(tall(), PlacementEvidence().observe(tall(), viewport))
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 260), compact, PlateFormat.COMPACT, rule = PlacementRule.DOCK), p)
        assertEquals(HostRect(36, 260, 756, 500), rect(p))
        assertFalse(rect(p).overlaps(pillBody))
        assertTrue("Only the product label is covered: ends above the fare's 16 dp line", rect(p).bottom <= 618 - 48)
        assertEquals("0.2.15 docked FULL over the pill", HostRect(36, 139, 756, 475),
            rect(shown(tall(decline = emptyList()))))
        val p2 = shown(tall2(), PlacementEvidence().observe(tall2(), viewport))
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 261), compact, PlateFormat.COMPACT, rule = PlacementRule.DOCK), p2)
        assertEquals("0.2.15's compact foot sat on the pill", HostRect(36, 149, 756, 389),
            rect(shown(tall2(decline = emptyList()))))
    }

    @Test fun `C11 only when the compact plate directly below the pill would reach the fare clearance`() {
        assertEquals(HostRect(36, 260, 756, 500), rect(shown(tall(fare = HostRect(84, 548, 473, 660)))))
        assertEquals(OverlayPlacement.Suppressed("NO_SAFE_SPACE"), place(tall(fare = HostRect(84, 547, 473, 659))))
    }

    @Test fun `the round X card keeps the 0_2_15 top-left dock and never touches the X`() {
        val decision = PlacementEvidence().observe(masRapido(), viewport)
        assertEquals(115, decision.ceiling)
        val p = shown(masRapido(), decision)
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 139), full, PlateFormat.FULL, rule = PlacementRule.DOCK), p)
        assertFalse(rect(p).overlaps(closeBody))
        assertFalse(OverlayPositioning.touchable(p, decision.geometry))
    }

    @Test fun `at font 1_3 the round X card docks below the whole X button instead of C11`() {
        val g = masRapido()
        val decision = PlacementEvidence().observe(g, viewport)
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
            protectedControls = decision.geometry.protectedControls - roundXTarget)
        assertEquals(OverlayPlacement.Suppressed("NO_SAFE_SPACE"), OverlayPositioning.place(glyphOnly, DIDI, viewport,
            g.capturedAtMs, ChipSize(936, 437), ChipSize(936, 312), dock = decision.dock))
    }

    @Test fun `a masked continuation keeps the latched pill and the chip without a detach or a re-place`() {
        val evidence = PlacementEvidence()
        val first = evidence.observe(tall(), viewport)
        var current = shown(tall(), first)
        assertEquals(HostRect(36, 260, 756, 500), rect(current))
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
        assertEquals(listOf(HostRect(479, 156, 993, 212)), d.geometry.declineControls)
        val again = evidence.observe(ptp(decline = emptyList(), mask = HostRect(28, 297, 764, 553)), viewport)
        assertEquals(listOf(HostRect(479, 156, 993, 212)), again.geometry.declineControls)
        evidence.reset()
        val fresh = evidence.observe(ptp(decline = emptyList()), viewport)
        assertTrue(fresh.geometry.declineControls.isEmpty())
        assertFalse(HostRect(479, 156, 993, 212) in fresh.geometry.protectedControls)
        assertEquals(0, fresh.latched)
    }

    @Test fun `the latch never lowers the ceiling or docks, and a C11 frame does not clear it`() {
        val evidence = PlacementEvidence()
        val d = evidence.observe(ptp(), viewport)
        assertEquals(593, d.ceiling)
        assertFalse(d.dock)
        // A frame where even the compact cannot fit below the latched pill: C11 (fare at 547).
        val tight = evidence.observe(tall(decline = emptyList(), fare = HostRect(84, 547, 473, 659)), viewport)
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
        val p = shown(g, PlacementEvidence().observe(g, viewport))
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
        val p0 = shown(radarF0(), d0)
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 140), compact, PlateFormat.COMPACT, rule = PlacementRule.HIGHER), p0)
        assertTrue("A clear strict slot is draggable", OverlayPositioning.touchable(p0, d0.geometry))
        val d1 = evidence.observe(radarF1(), viewport)
        val p1 = place(radarF1(), d1) as OverlayPlacement.Displayed
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 139), full, PlateFormat.FULL, rule = PlacementRule.DOCK), p1)
        assertTrue(rect(p1).overlaps(HostRect(202, 423, 873, 495)))
        assertFalse("Taps on the covered Radar pill reach Uber", OverlayPositioning.touchable(p1, d1.geometry))
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
        val docked = OverlayPlacement.Displayed(OverlayPosition(36, 139), full, PlateFormat.FULL, rule = PlacementRule.DOCK)
        assertTrue(OverlayPositioning.valid(docked.position, full, hidden.geometry, viewport, docked = true))
        assertFalse(OverlayPositioning.touchable(docked, hidden.geometry))
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
    @Test fun `a Radar offer keeps its compact plate beside the pill and never flips to FULL over it`() {
        val evidence = PlacementEvidence()
        val d0 = evidence.observe(radarF0(), viewport)
        var current = shown(radarF0(), d0)
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(36, 140), compact, PlateFormat.COMPACT, rule = PlacementRule.HIGHER), current)
        val compactMask = rect(current).expanded(8)
        val frames = listOf(uberRadar(listOf(HostRect(205, 428, 861, 480)), compactMask,
            listOf(HostRect(205, 428, 861, 480), HostRect(195, 634, 479, 684), uberClose))) +
            List(12) { radarF2(mask = compactMask) }
        for ((i, raw) in frames.withIndex()) {
            val d = evidence.observe(raw, viewport)
            assertTrue("frame $i: the pill stays a soft obstacle", d.geometry.softObstacles.any { it.contains(HostRect(205, 428, 861, 480)) })
            val safe = OverlayPositioning.valid(current.position, current.size, d.geometry, viewport, current.overMarker, current.docked)
            assertTrue("frame $i: the compact plate stays valid", safe)
            val next = place(raw, d, current.position, savedDocked = current.docked) as OverlayPlacement.Displayed
            assertEquals("frame $i: same plate, same slot", PlateFormat.COMPACT, next.format)
            assertEquals(current.position, next.position)
            assertFalse("frame $i: never over the pill", rect(next).overlaps(HostRect(202, 423, 873, 495)))
            assertTrue("frame $i: a clear strict slot stays draggable", OverlayPositioning.touchable(next, d.geometry))
            current = next
        }
        assertFalse("FULL at the slot would cover the pill's keep-out", OverlayPositioning.valid(current.position, full,
            evidence.observe(radarF2(mask = compactMask), viewport).geometry, viewport))
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
        val p = place(g, d) as OverlayPlacement.Displayed
        assertEquals(HostRect(36, 139, 756, 475), rect(p))
        assertTrue(p.docked)
        assertFalse(OverlayPositioning.touchable(p, d.geometry))
        assertFalse(rect(p).overlaps(close.expanded(48)))
        // 1794008746496 replaces it in place: fare (100,902)-(551,1012), TOTAL (97,1053)-(870,1103).
        for (content in listOf(HostRect(100, 902, 551, 1012), HostRect(97, 1053, 870, 1103)))
            assertFalse(rect(p).expanded(8).overlaps(content))
    }

    @Test fun `touch follows the tier and the overlap - draggable only in a clear strict slot`() {
        val g = ptp()
        val foot = OverlayPlacement.Displayed(OverlayPosition(36, 305), compact, PlateFormat.COMPACT, rule = PlacementRule.FOOT)
        assertTrue(OverlayPositioning.touchable(foot, g))
        assertTrue(OverlayPositioning.touchable(foot.copy(rule = PlacementRule.HIGHER), g))
        val marker = foot.copy(rule = PlacementRule.MARKER, overMarker = true)
        assertTrue("A map marker is not a host control",
            OverlayPositioning.touchable(marker, g.copy(markers = listOf(MapMarker(MapMarkerKind.PICKUP, HostRect(300, 400, 340, 440))))))
        assertFalse("Docked: always pass-through", OverlayPositioning.touchable(foot.copy(rule = PlacementRule.DOCK), g))
        assertFalse("Over a decline control", OverlayPositioning.touchable(foot.copy(position = OverlayPosition(36, 150)), g))
        assertFalse("Over soft host chrome", OverlayPositioning.touchable(foot, g.copy(softObstacles = listOf(HostRect(500, 500, 600, 520)))))
        assertFalse("Over any protected control", OverlayPositioning.touchable(foot,
            g.copy(protectedControls = g.protectedControls + HostRect(700, 530, 760, 560))))
    }

    @Test fun `a drag can never end over host chrome or start from a docked chip`() {
        val g = PlacementEvidence().observe(ptp(), viewport).geometry
        val foot = shown(ptp(), PlacementEvidence().observe(ptp(), viewport))
        assertEquals(OverlayPosition(36, 305), foot.position)
        assertEquals("Onto the pill's keep-out: rejected", foot.position, OverlayPositioning.drag(foot, OverlayPosition(36, 209), g, viewport))
        assertEquals("A clear strict slot is allowed", OverlayPosition(36, 300), OverlayPositioning.drag(foot, OverlayPosition(36, 300), g, viewport))
        val soft = g.copy(softObstacles = listOf(HostRect(36, 100, 756, 230)))
        assertEquals(foot.position, OverlayPositioning.drag(foot, OverlayPosition(36, 240), soft, viewport))
        val tallGeometry = PlacementEvidence().observe(tall(), viewport).geometry
        val docked = OverlayPlacement.Displayed(OverlayPosition(36, 260), compact, PlateFormat.COMPACT, rule = PlacementRule.DOCK)
        assertEquals("A docked chip is pass-through: no drag", docked.position,
            OverlayPositioning.drag(docked, OverlayPosition(36, 262), tallGeometry, viewport))
        assertEquals(docked.position, OverlayPositioning.snapToEdge(docked, tallGeometry, viewport))
        assertFalse(OverlayPositioning.clearSlot(docked.position, compact, tallGeometry, viewport))
        assertTrue(OverlayPositioning.clearSlot(foot.position, compact, g, viewport))
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
