package mx.kompara.overlay

import mx.kompara.parsers.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

/** Coordinates measured from the reference renders are TEST inputs only. */
class OverlayPositioningTest {
    private val full = ChipSize(480, 224)
    private val compact = ChipSize(480, 160)
    private val viewport = OverlayViewport(HostRect(0, 0, 786, 1704), 2f, HostOrientation.PORTRAIT)
    private fun rect(l: Double, t: Double, r: Double, b: Double) = HostRect((l*2).toInt(), (t*2).toInt(), (r*2).toInt(), (b*2).toInt())
    private fun marker(kind: MapMarkerKind, l: Double, t: Double, r: Double, b: Double) = MapMarker(kind, rect(l,t,r,b))
    private fun geometry(top: Int = 545, chrome: Int = 128, markers: List<MapMarker> = emptyList(), nav: Boolean = false) =
        HostGeometry("uber", "test-only", 1000, viewport.bounds, viewport.orientation,
            HostRect(0, 80*2, 786, chrome*2), HostRect(0, top*2, 786, 1676),
            listOf(HostRect(40, 1500, 746, 1620)), markers, nav)
    private fun place(g: HostGeometry, saved: OverlayPosition? = null) =
        OverlayPositioning.place(g, "uber", viewport, 1000, full, compact, saved)
    private fun shown(g: HostGeometry) = place(g) as OverlayPlacement.Displayed

    // 0.2.14: the masked-anchor recovery and initial-boundary gates (0.2.8-0.2.11) detached the
    // chip; they are converted into a monotone card ceiling plus the top dock. Nothing detaches.
    private fun ocrGeometry(upper: List<HostRect>, routes: List<HostRect>, mask: HostRect? = null) =
        geometry().copy(source = HostGeometrySource.OCR_TEXT_BOUNDS,
            ocrEvidence = HostOcrGeometryEvidence(mask, upper, routes, false))

    @Test fun `masked header with a rigid route shift raises the ceiling to its displaced position`() {
        val tracker = PlacementEvidence()
        val initial = ocrGeometry(listOf(HostRect(40, 650, 600, 730)),
            listOf(HostRect(40, 900, 600, 940), HostRect(40, 1000, 600, 1040)))
        val first = tracker.observe(initial, viewport)
        assertFalse(first.dock)
        assertEquals("The header bounds the card", 650 - viewport.dp(16), first.geometry.offerCard.top)
        val masked = ocrGeometry(emptyList(), listOf(HostRect(40, 650, 600, 690), HostRect(40, 750, 600, 790)),
            mask = HostRect(20, 400, 700, 640))
        val next = tracker.observe(masked, viewport)
        assertTrue("The header moved 250 px up beneath our mask", next.geometry.offerCard.top <= 400 - viewport.dp(16))
        assertTrue("Only legs are visible: the dock tier", next.dock)
        // The unread fare lies between the hidden header and the first leg: even the dock keeps off it.
        val shown = OverlayPositioning.place(next.geometry, "uber", viewport, 1000, full, compact, dock = next.dock)
        assertTrue(shown is OverlayPlacement.Suppressed || shown is OverlayPlacement.Displayed &&
            !shown.position.bounds(shown.size).overlaps(HostRect(0, 400, 786, 650)))
    }

    @Test fun `masked header with unknown route correspondence docks instead of detaching`() {
        for (routes in listOf(emptyList(), listOf(HostRect(40, 750, 600, 790)),
            listOf(HostRect(40, 650, 600, 690), HostRect(40, 820, 600, 860)))) {
            val tracker = PlacementEvidence()
            tracker.observe(ocrGeometry(listOf(HostRect(40, 650, 600, 730)),
                listOf(HostRect(40, 900, 600, 940), HostRect(40, 1000, 600, 1040))), viewport)
            val next = tracker.observe(ocrGeometry(emptyList(), routes, HostRect(20, 400, 700, 690)), viewport)
            assertTrue("An unknown displacement docks: $routes", next.dock)
            val shown = OverlayPositioning.place(next.geometry, "uber", viewport, 1000, full, compact, dock = true)
            assertTrue("Never hidden for lack of proof: $routes", shown is OverlayPlacement.Displayed)
        }
    }

    @Test fun `a disappeared close glyph under the mask raises the ceiling even when routes are still`() {
        val close = HostRect(600, 650, 740, 790)
        val tracker = PlacementEvidence()
        val routes = listOf(HostRect(40, 900, 600, 940), HostRect(40, 1000, 600, 1040))
        tracker.observe(ocrGeometry(listOf(close, HostRect(40, 700, 500, 780)), routes), viewport)
        val next = tracker.observe(ocrGeometry(listOf(HostRect(40, 700, 500, 780)), routes,
            HostRect(20, 400, 750, 680)), viewport)
        assertTrue(next.geometry.offerCard.top <= close.top - viewport.dp(16))
        assertFalse(next.dock)
    }

    @Test fun `a partial fare envelope cannot lower the retained upper-card boundary`() {
        val tracker = PlacementEvidence()
        val initial = ocrGeometry(listOf(HostRect(40, 1100, 600, 1140)),
            listOf(HostRect(40, 1300, 600, 1340), HostRect(40, 1400, 600, 1440)))
        val ceiling = tracker.observe(initial, viewport).geometry.offerCard.top
        val partial = initial.copy(offerCard = initial.offerCard.copy(top = 1200),
            ocrEvidence = initial.ocrEvidence!!.copy(upperAnchors = emptyList()))
        repeat(4) {
            val decision = tracker.observe(partial, viewport)
            assertEquals("Within one offer the ceiling only rises", ceiling, decision.geometry.offerCard.top)
        }
        tracker.reset()
        assertEquals("A new session starts its own ceiling", 1200, tracker.observe(partial, viewport).geometry.offerCard.top)
    }

    @Test fun `measured controls override mask recovery and always reject occupied saved positions`() {
        val tracker = PlacementEvidence()
        val g = geometry().copy(protectedControls = listOf(HostRect(300, 830, 760, 1080)),
            source = HostGeometrySource.OCR_AND_ACCESSIBILITY,
            ocrEvidence = HostOcrGeometryEvidence(HostRect(280, 800, 770, 1080),
                emptyList(), emptyList(), true))
        val decision = tracker.observe(g, viewport)
        assertFalse("An independently measured card is an observed upper boundary", decision.dock)
        val shown = place(decision.geometry, OverlayPosition(282, 834)) as OverlayPlacement.Displayed
        assertTrue(g.protectedControls.none { shown.position.bounds(shown.size).overlaps(it) })
    }

    @Test fun `a card without any upper label docks on every frame without alternating`() {
        val tracker = PlacementEvidence()
        val initial = ocrGeometry(emptyList(), listOf(HostRect(40, 1100, 600, 1140), HostRect(40, 1200, 600, 1240)))
        val first = tracker.observe(initial, viewport)
        assertTrue("No upper anchor observed yet: main's top dock, not the foot slot", first.dock)
        val slot = OverlayPositioning.place(first.geometry, "uber", viewport, 1000, full, compact, dock = true)
            as OverlayPlacement.Displayed
        assertTrue(slot.docked)
        val masked = initial.copy(ocrEvidence = initial.ocrEvidence!!.copy(overlayMask = slot.position.bounds(slot.size)))
        repeat(5) {
            val next = tracker.observe(masked, viewport)
            assertTrue(next.dock)
            assertEquals(slot, OverlayPositioning.place(next.geometry, "uber", viewport, 1000, full, compact,
                slot.position, dock = true, savedDocked = true))
        }
    }

    @Test fun `C1 and N8 N9 N10 share the measured right foot independent of colour`() {
        assertEquals(OverlayPosition(282, 834), shown(geometry()).position)
        assertEquals(PlateFormat.FULL, shown(geometry()).format)
    }
    @Test fun `C8 clears the pin outer stroke and destination by moving higher left`() {
        val g = geometry(top = 316, markers = listOf(
            marker(MapMarkerKind.PICKUP, 188.5,275.5,205.5,292.5),
            marker(MapMarkerKind.DESTINATION,321.0,147.0,339.0,165.0)))
        assertEquals(OverlayPosition(24, 295), shown(g).position)
        assertEquals(PlateFormat.FULL, shown(g).format)
    }
    @Test fun `C9 next manoeuvre forces higher right past the destination`() {
        val g = geometry(chrome = 180, nav = true, markers = listOf(
            marker(MapMarkerKind.DRIVER,77.0,489.0,115.0,527.0),
            marker(MapMarkerKind.NEXT_MANOEUVRE,196.5,482.0,224.5,511.5),
            marker(MapMarkerKind.DESTINATION,321.0,371.0,339.0,389.0)))
        assertEquals(OverlayPosition(282, 486), shown(g).position)
    }
    @Test fun `C10 font growth keeps external margins fixed and rechecks horizontal fit`() {
        val v = OverlayViewport(HostRect(0,0,1965,4260), 5f, HostOrientation.PORTRAIT)
        val g = HostGeometry("uber", "test", 1000, v.bounds,v.orientation,
            HostRect(0,400,1965,640), HostRect(0,2345,1965,4190),
            listOf(HostRect(100,3700,1865,4080)), emptyList())
        val p = OverlayPositioning.place(g,"uber",v,1000,ChipSize(1560,728),ChipSize(1560,520)) as OverlayPlacement.Displayed
        assertEquals(OverlayPosition(345,1537),p.position) // x69, y307.4; bottom453
        assertEquals(PlateFormat.FULL,p.format)
        val huge = OverlayPositioning.place(g,"uber",v,1000,ChipSize(2400,1120),ChipSize(2400,800))
        assertTrue(huge is OverlayPlacement.Suppressed)
    }
    @Test fun `C11 measured 80dp band suppresses rather than clamping onto a control`() {
        assertEquals(OverlayPlacement.Suppressed("NAVIGATION"), place(geometry(260,180,nav=true)))
    }
    @Test fun `C12 full cannot clear puck ring but compact foot can`() {
        val g = geometry(342, markers=listOf(marker(MapMarkerKind.DRIVER,159.0,153.0,233.0,227.0)))
        val p = shown(g)
        assertEquals(PlateFormat.COMPACT,p.format)
        assertEquals(OverlayPosition(282,492),p.position)
        assertFalse(p.overMarker)
        val grown = OverlayPositioning.place(g,"uber",viewport,1000,ChipSize(624,291),ChipSize(624,208)) as OverlayPlacement.Displayed
        assertEquals(PlateFormat.COMPACT,grown.format)
        assertTrue(grown.overMarker)
        assertEquals(OverlayPosition(138,444),grown.position)
    }
    @Test fun `144 and 112dp boundaries are inclusive and one pixel below switches or suppresses`() {
        for (band in listOf(288,287,224,223)) {
            val g=geometry().copy(offerCard=HostRect(0,256+band,786,1676))
            val p=place(g)
            when(band) {
                288 -> assertEquals(PlateFormat.FULL,(p as OverlayPlacement.Displayed).format)
                287,224 -> assertEquals(PlateFormat.COMPACT,(p as OverlayPlacement.Displayed).format)
                223 -> assertTrue(p is OverlayPlacement.Suppressed)
            }
        }
    }
    @Test fun `marker alone never suppresses while protected controls always win`() {
        val g=geometry(markers=listOf(marker(MapMarkerKind.DRIVER,0.0,128.0,393.0,545.0)))
        assertTrue(shown(g).overMarker)
        assertEquals(PlateFormat.COMPACT,shown(g).format)
        assertTrue(place(g.copy(protectedControls=g.protectedControls + rect(0.0,128.0,393.0,545.0))) is OverlayPlacement.Suppressed)
    }
    @Test fun `right foot then left foot before higher slots`() {
        val g=geometry(markers=listOf(marker(MapMarkerKind.DESTINATION,340.0,480.0,360.0,500.0)))
        assertEquals(OverlayPosition(24,834),shown(g).position)
    }
    @Test fun `saved drag is restored only if valid for this frame and plate size`() {
        val g=geometry()
        val saved=OverlayPosition(24,320)
        assertEquals(saved,(place(g,saved) as OverlayPlacement.Displayed).position)
        assertEquals(shown(g).position,(place(g,OverlayPosition(900,1600)) as OverlayPlacement.Displayed).position)
        val tall=geometry(260,180,nav=true)
        assertTrue(place(tall,saved) is OverlayPlacement.Suppressed)
    }
    @Test fun `drag rejects card chrome protected controls markers and offscreen proposals`() {
        val g=geometry(markers=listOf(marker(MapMarkerKind.DRIVER,180.0,200.0,220.0,240.0)))
        val current=shown(g)
        for(p in listOf(OverlayPosition(-1,300),OverlayPosition(24,0),OverlayPosition(24,1300),OverlayPosition(24,400)))
            assertEquals(current.position,OverlayPositioning.drag(current,p,g,viewport))
        assertEquals(OverlayPosition(24,650),OverlayPositioning.drag(current,OverlayPosition(24,650),g,viewport))
    }
    @Test fun `snap uses nearer edge only when that edge remains safe`() {
        val g=geometry(markers=listOf(marker(MapMarkerKind.DESTINATION,380.0,480.0,390.0,500.0)))
        val p=OverlayPlacement.Displayed(OverlayPosition(240,834),full,PlateFormat.FULL)
        assertTrue(OverlayPositioning.valid(p.position,full,g,viewport))
        assertEquals(p.position,OverlayPositioning.snapToEdge(p,g,viewport))
        assertEquals(OverlayPosition(24,834),OverlayPositioning.snapToEdge(p.copy(position=OverlayPosition(80,834)),g,viewport))
    }
    @Test fun `unknown host stale future rotated or resized bounds are unsupported not no room`() {
        assertEquals("UNVALIDATED_GEOMETRY",(OverlayPositioning.place(null,"uber",viewport,1000,full,compact) as OverlayPlacement.Unsupported).cause)
        for (g in listOf(geometry().copy(packageName="didi"),geometry().copy(capturedAtMs=1000-OverlayPositioning.MAX_GEOMETRY_AGE_MS-1),geometry().copy(capturedAtMs=1001),
            geometry().copy(orientation=HostOrientation.LANDSCAPE),geometry().copy(displayBounds=HostRect(0,0,1000,786))))
            assertTrue(place(g) is OverlayPlacement.Unsupported)
    }
    @Test fun `screenshot rate limit retry retains bounds until bounded capture expiry`() {
        val g = geometry()
        assertTrue(OverlayPositioning.place(g,"uber",viewport,3_000,full,compact) is OverlayPlacement.Displayed)
        assertTrue(OverlayPositioning.place(g,"uber",viewport,3_501,full,compact) is OverlayPlacement.Unsupported)
    }
    @Test fun `capture timestamp and source refresh do not reset a settled window but changed bounds do`() {
        val g = geometry()
        assertTrue(OverlayPositioning.sameLayout(g, g.copy(capturedAtMs = 2_000, layoutId = "derived-ocr",
            source = HostGeometrySource.OCR_TEXT_BOUNDS)))
        assertFalse(OverlayPositioning.sameLayout(g, g.copy(offerCard = g.offerCard.copy(top = 800))))
        assertFalse(OverlayPositioning.sameLayout(g, g.copy(markers = listOf(
            MapMarker(MapMarkerKind.PICKUP, HostRect(200, 300, 240, 340))))))
        assertFalse(OverlayPositioning.sameLayout(null, g))
    }
    @Test fun `display origin density cutout and Back strips are respected in landscape`() {
        val v=OverlayViewport(HostRect(50,20,1754,806),2f,HostOrientation.LANDSCAPE,
            OverlayInsets(left=70,top=25,right=20),OverlayInsets(left=90,right=60))
        val g=HostGeometry("uber","test",1000,v.bounds,v.orientation,
            HostRect(100,50,1600,100),HostRect(50,650,1754,806),listOf(HostRect(100,700,1650,780)),emptyList())
        val p=OverlayPositioning.place(g,"uber",v,1000,full,compact) as OverlayPlacement.Displayed
        assertEquals(1694,p.position.x+full.width)
        assertEquals(OverlayPosition(p.position.x-50,p.position.y-45),p.position.inWindow(OverlayPosition(50,45)))
    }
    @Test fun `random measured layouts never intersect controls or card and marker fallback remains compact`() {
        val random=Random(5)
        repeat(1500) {
            val cardTop=random.nextInt(150,750)*2
            val chrome=random.nextInt(40,cardTop/2)*2
            val g=geometry().copy(topChrome=HostRect(0,0,786,chrome),offerCard=HostRect(0,cardTop,786,1704),
                protectedControls=listOf(HostRect(20,cardTop,766,1704)),
                markers=listOf(MapMarker(MapMarkerKind.PICKUP,HostRect(200,chrome,400,minOf(chrome+80,1704)))))
            val scale=random.nextDouble(.8,2.0)
            val f=ChipSize((480*scale).roundToInt(),(224*scale).roundToInt())
            val c=ChipSize((480*scale).roundToInt(),(160*scale).roundToInt())
            val p=OverlayPositioning.place(g,"uber",viewport,1000,f,c)
            if(p is OverlayPlacement.Displayed) {
                assertTrue(OverlayPositioning.valid(p.position,p.size,g,viewport,p.overMarker))
                assertFalse(p.position.bounds(p.size).overlaps(g.offerCard))
                if(p.overMarker) assertEquals(PlateFormat.COMPACT,p.format)
            }
        }
    }
}
