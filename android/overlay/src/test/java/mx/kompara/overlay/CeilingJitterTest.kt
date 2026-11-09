package mx.kompara.overlay

import mx.kompara.overlay.RealGeometryFixtures.DIDI
import mx.kompara.overlay.RealGeometryFixtures.UBER
import mx.kompara.overlay.RealGeometryFixtures.display
import mx.kompara.overlay.RealGeometryFixtures.systemWindows
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostOcrGeometryEvidence
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PlacementEvidence.Decision.ceiling] on the TEMP DIAG show lines of the 0.2.19 live run of
 * 2026-11-09 (sids 5722 and 8ceb), trimmed to what placement reads: one noisy OCR frame must not
 * move the held ceiling, and a real rise must latch on the frame it happens.
 */
class CeilingJitterTest {
    private val v = OverlayViewport(display, 3f, HostOrientation.PORTRAIT, OverlayInsets(0, 91, 0, 144))
    private fun r(l: Int, t: Int, right: Int, b: Int) = HostRect(l, t, right, b)
    private fun frame(host: String, top: Int, fare: HostRect?, upper: List<HostRect>, routes: List<HostRect>, mask: HostRect? = null) =
        HostGeometry(host, "derived-ocr_text_bounds", 1_000, display, HostOrientation.PORTRAIT, r(0, 0, 1080, 1), r(0, top, 1080, 2340),
            systemWindows, emptyList(), source = HostGeometrySource.OCR_TEXT_BOUNDS,
            ocrEvidence = HostOcrGeometryEvidence(mask, upper, routes, false), fareBounds = fare)

    /** Sid 5722's first frame, moved [up] px: the fare binds its ceiling (902 - 176 = 726). */
    private fun uber(up: Int = 0, fareTop: Int = 902, fareBottom: Int = 1012) = frame(UBER, 749 - up,
        r(102, fareTop - up, 489, fareBottom - up), listOf(r(124, 797 - up, 327, 844 - up), r(891, 824 - up, 933, 866 - up)),
        listOf(r(193, 1425 - up, 509, 1469 - up), r(194, 1565 - up, 533, 1616 - up)))
    private val mask5722 = r(28, 334, 764, 686)
    private val uberNoisy = frame(UBER, 749, r(101, 897, 488, 1011), listOf(r(126, 797, 327, 844), r(890, 825, 934, 867)),
        listOf(r(193, 1425, 509, 1469), r(116, 1559, 533, 1616)), mask5722)

    /** Sid 8ceb: the header binds its ceiling (637 - 48 = 589). */
    private val mask8ceb = r(28, 293, 764, 549)
    private val didiRoutes = listOf(r(152, 1339, 459, 1391), r(152, 1550, 562, 1602))
    private fun didi(top: Int, fare: HostRect, upper: HostRect = r(105, 637, 425, 689), mask: HostRect? = mask8ceb) =
        frame(DIDI, top, fare, listOf(upper), didiRoutes, mask)
    private val didi0 = didi(589, r(83, 776, 729, 883), mask = null)

    /** The normal Pon Tu Precio card of DeclineControlPlacementTest: fare limit 595 against a ceiling of 593. */
    private fun ptp(fare: HostRect) = frame(DIDI, 593, fare, listOf(r(105, 641, 425, 682)),
        listOf(r(118, 1339, 504, 1394), r(151, 1550, 524, 1602)))

    @Test fun `5722 one noisy fare box keeps the held ceiling`() {
        val e = PlacementEvidence()
        assertEquals(726, e.observe(uber(), v).ceiling)
        assertEquals("Fare 902..1012 then 897..1011", 726, e.observe(uberNoisy, v).ceiling)
    }

    @Test fun `8ceb a 1 px anchor jitter keeps the held ceiling`() {
        val e = PlacementEvidence()
        assertEquals(589, e.observe(didi0, v).ceiling)
        assertEquals(589, e.observe(didi(588, r(83, 776, 729, 883), r(105, 636, 425, 689)), v).ceiling)
    }

    @Test fun `8ceb the countdown wipe across the fare keeps the held ceiling`() {
        val e = PlacementEvidence()
        assertEquals(589, e.observe(didi0, v).ceiling)
        assertEquals("Fare 776..883 then 766..895", 589, e.observe(didi(589, r(82, 766, 731, 895)), v).ceiling)
        assertEquals("And the frames after it", 589, e.observe(didi(589, r(83, 779, 731, 884)), v).ceiling)
    }

    @Test fun `the same wipe on a fare-bound ceiling keeps it`() {
        val e = PlacementEvidence()
        assertEquals(593, e.observe(ptp(r(83, 774, 511, 886)), v).ceiling)
        assertEquals("Fare top -10, bottom +12", 593, e.observe(ptp(r(83, 764, 511, 898)), v).ceiling)
        val fareOnly = PlacementEvidence()
        assertEquals(726, fareOnly.observe(uber(), v).ceiling)
        assertEquals("Fare top -10, bottom +12", 726, fareOnly.observe(uber(fareTop = 892, fareBottom = 1024), v).ceiling)
    }

    @Test fun `an unread fare rebuilt from an inflated frame still uses the smallest height`() {
        val e = PlacementEvidence()
        e.observe(uber(), v)
        val wiped = uber(fareTop = 892, fareBottom = 1024)
        assertEquals(726, e.observe(wiped, v).ceiling)
        assertEquals("Rebuilt at 892..1024, still 176 px of clearance", 726, e.observe(wiped.copy(fareBounds = null), v).ceiling)
    }

    @Test fun `an unresolved frame's smaller fare clears by its own height and leaves the smallest alone`() {
        val e = PlacementEvidence()
        assertEquals(726, e.observe(uber(), v).ceiling)
        assertEquals("Height 100: 802 - 160", 642, e.observe(uber(100, fareBottom = 1002), v, identityUnresolved = true).ceiling)
        assertEquals("Height 110 again: 752 - 176, not 752 - 160", 576, e.observe(uber(150), v).ceiling)
    }

    @Test fun `an unresolved frame's taller fare clears by its own height`() {
        val e = PlacementEvidence()
        assertEquals(726, e.observe(uber(), v).ceiling)
        assertEquals("Height 130: 802 - 208, not 802 - 176", 594, e.observe(uber(100, fareBottom = 1032), v, identityUnresolved = true).ceiling)
    }

    @Test fun `a resolved frame's smaller fare still folds into the smallest height`() {
        val e = PlacementEvidence()
        assertEquals(726, e.observe(uber(), v).ceiling)
        assertEquals("Height 100: 802 - 160", 642, e.observe(uber(100, fareBottom = 1002), v).ceiling)
        assertEquals("Height 110 now clears by 160: 752 - 160", 592, e.observe(uber(150), v).ceiling)
    }

    @Test fun `a real rise latches on that frame`() {
        val e = PlacementEvidence()
        e.observe(uber(), v)
        assertEquals("All anchors up 41 px", 685, e.observe(uber(41), v).ceiling)
    }

    @Test fun `a rise of exactly 4 dp keeps the ceiling and one more pixel latches`() {
        val inside = PlacementEvidence()
        inside.observe(uber(), v)
        assertEquals(726, inside.observe(uber(12), v).ceiling)
        val past = PlacementEvidence()
        past.observe(uber(), v)
        assertEquals(713, past.observe(uber(13), v).ceiling)
    }

    @Test fun `repeated rises of 3 px cannot creep past the band`() {
        val e = PlacementEvidence()
        e.observe(uber(), v)
        for (up in listOf(3, 6, 9, 12)) assertEquals("Up $up", 726, e.observe(uber(up), v).ceiling)
        assertEquals("15 px past the held value is a real rise", 711, e.observe(uber(15), v).ceiling)
    }

    @Test fun `reset clears the held ceiling`() {
        val e = PlacementEvidence()
        assertEquals(526, e.observe(uber(200), v).ceiling)
        e.reset()
        assertEquals(726, e.observe(uber(), v).ceiling)
    }

    @Test fun `reset clears the smallest fare height`() {
        val e = PlacementEvidence()
        assertEquals("A 60 px fare: the header binds", 749, e.observe(uber(fareBottom = 962), v).ceiling)
        e.reset()
        assertEquals("Height 110 again: 902 - 176", 726, e.observe(uber(), v).ceiling)
    }

    @Test fun `a new offer's ceiling is unaffected by the band or the last offer's fare`() {
        val e = PlacementEvidence()
        assertEquals(726, e.observe(uber(), v).ceiling)
        assertEquals("Within one offer it never moves down", 726, e.observe(uber(-50), v).ceiling)
        e.reset()
        assertEquals("Within the old band, but a new offer", 721, e.observe(uber(5), v).ceiling)
        e.reset()
        assertEquals("Below the old ceiling", 776, e.observe(uber(-50), v).ceiling)
        e.reset()
        assertEquals("A taller fare is this offer's own", 715, e.observe(uberNoisy, v).ceiling)
    }

    @Test fun `another host's frame starts its own ceiling and fare height`() {
        val e = PlacementEvidence()
        assertEquals(726, e.observe(uber(), v).ceiling)
        assertEquals(715, e.observe(uberNoisy.copy(packageName = DIDI), v).ceiling)
    }
}
