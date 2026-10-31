package mx.kompara.parsers

import mx.kompara.parsers.model.*
import org.junit.Assert.*
import org.junit.Test

class HostGeometryDeriverTest {
    private val host = "com.ubercab.driver"
    private val display = HostRect(0, 0, 393, 852)
    private val chrome = HostRect(0, 0, 393, 128)
    private fun box(text: String, top: Int, bottom: Int = top + 24) =
        HostTextBounds(text, HostRect(24, top, 369, bottom))
    private val boxes = listOf(box("MXN148.00", 561, 605), box("5 min (2.0 km)", 620),
        box("Viaje: 19 min (10.0 km)", 660), box("Aceptar", 760, 808))

    private fun derive(text: List<HostTextBounds> = boxes, content: OfferContentBounds? = null) =
        HostGeometryDeriver.offer(host, 1000, display, 1f, text, HostGeometrySource.OCR_TEXT_BOUNDS,
            contentBounds = content, topChrome = chrome)

    @Test fun `fare-only envelopes carry no upper-boundary proof even from accessibility text`() {
        for (source in listOf(HostGeometrySource.OCR_TEXT_BOUNDS, HostGeometrySource.ACCESSIBILITY_BOUNDS)) {
            val partial = requireNotNull(HostGeometryDeriver.offer(host, 1000, display, 1f,
                boxes, source, topChrome = chrome, recognizedOffer = true))
            assertTrue(partial.ocrEvidence!!.upperAnchors.isEmpty())
            assertFalse(partial.ocrEvidence!!.independentlyMeasuredCard)
            val upper = box("Uber Priority", 500)
            val complete = requireNotNull(HostGeometryDeriver.offer(host, 1000, display, 1f,
                listOf(upper) + boxes, source, topChrome = chrome, recognizedOffer = true))
            assertEquals(listOf(upper.bounds), complete.ocrEvidence!!.upperAnchors)
            val measured = requireNotNull(HostGeometryDeriver.offer(host, 1000, display, 1f,
                boxes, source, topChrome = chrome, observedCardBounds = HostRect(0, 500, 393, 852)))
            assertTrue(measured.ocrEvidence!!.independentlyMeasuredCard)
        }
    }

    @Test fun `OCR fare legs and Aceptar derive a card envelope and protect the button`() {
        val g = requireNotNull(derive())
        assertEquals(HostRect(0,545,393,852),g.offerCard)
        assertEquals(chrome,g.topChrome)
        assertEquals(listOf(boxes.last().bounds),g.protectedControls)
        assertEquals(HostGeometrySource.OCR_TEXT_BOUNDS,g.source)
        assertFalse(g.isIdleHost)
        assertNotNull(HostGeometryAdapter().adapt(host,1000,HostLayoutObservation(g,false,false)))
    }

    @Test fun `explicit close glyph remains a protected control independently of the card heading`() {
        val close = HostTextBounds("X", HostRect(330, 180, 370, 220))
        val g = requireNotNull(derive(boxes + close))
        assertTrue(g.protectedControls.contains(close.bounds))
        assertTrue(g.protectedControls.contains(boxes.last().bounds))
    }

    // 0.2.14: the floating Trip Radar pill is host chrome, not the card (it lowered the safe band
    // to nothing at 12:04:15 and 13:12:30). It stays an upper anchor and becomes a soft obstacle.
    @Test fun `Me interesa is protected and the Radar pill is a soft obstacle outside the card`() {
        val texts = listOf(box("Radar de solicitud", 200), box("5 min (2.0 km)", 620),
            box("19 min (10.0 km)", 660), box("Me interesa", 760, 808))
        val g = requireNotNull(HostGeometryDeriver.offer(host, 1000, display, 1f, texts,
            HostGeometrySource.OCR_TEXT_BOUNDS, topChrome = chrome, recognizedOffer = true))
        assertEquals("The pill never becomes the card top", 604, g.offerCard.top)
        assertEquals(listOf(texts.last().bounds), g.protectedControls)
        assertEquals(listOf(texts.first().bounds), g.softObstacles)
        assertEquals(listOf(texts.first().bounds), g.ocrEvidence!!.upperAnchors)
    }

    @Test fun `unread S25 Radar fare keeps the card top at the UberX header not the pill or pickup`() {
        val s25 = HostRect(0, 0, 1080, 2340)
        fun b(text: String, l: Int, t: Int, r: Int, bt: Int) = HostTextBounds(text, HostRect(l, t, r, bt))
        val radar = listOf(b("Radar de solicitud de viaje", 202, 614, 793, 667), b("2", 834, 624, 866, 661),
            b("UberX", 193, 817, 333, 866), b("X", 891, 842, 933, 884),
            b("4 min (0.9 km)", 193, 1442, 515, 1488), b("34 min (12.2 km)", 193, 1585, 562, 1636),
            b("Me interesa", 394, 1948, 687, 2001))
        val g = requireNotNull(HostGeometryDeriver.offer(host, 1000, s25, 3f, radar,
            HostGeometrySource.OCR_TEXT_BOUNDS, OfferContentBounds(193, 1442, 562, 1636), recognizedOffer = true))
        assertEquals("UberX header minus 16 dp", 817 - 48, g.offerCard.top)
        assertTrue(g.offerCard.top <= 815)
        assertEquals(listOf(HostRect(202, 614, 866, 667)), g.softObstacles)
        assertTrue(g.protectedControls.contains(HostRect(394, 1948, 687, 2001)))
    }

    @Test fun `content anchors prevent an unrelated earnings pill from lifting the card`() {
        val g = requireNotNull(derive(listOf(box("MXN0.00",170)) + boxes,
            OfferContentBounds(24,561,369,684)))
        assertEquals(545,g.offerCard.top)
    }

    @Test fun `nearby variant heading is included before the card padding`() {
        val g = requireNotNull(derive(listOf(box("Exclusivo",525,545)) + boxes))
        assertEquals(509,g.offerCard.top)
    }

    @Test fun `incomplete offers still have geometry without fare or minute extraction`() {
        val missingFare = requireNotNull(derive(boxes.drop(1)))
        assertEquals(604,missingFare.offerCard.top)
        val missingTime = requireNotNull(derive(listOf(boxes.first(),box("2.0 km",620),
            box("Viaje: 10.0 km",660),boxes.last())))
        assertEquals(545,missingTime.offerCard.top)
    }

    @Test fun `DiDi button fare and both legs produce independent host geometry`() {
        val g = requireNotNull(HostGeometryDeriver.offer("com.didiglobal.driver",1000,display,1f,
            listOf(box("6min (1.2km)",600),box("22min (8.0km)",650),box("Aceptar $148.00",760)),
            HostGeometrySource.OCR_TEXT_BOUNDS,topChrome=chrome))
        assertEquals(584,g.offerCard.top)
        assertEquals("com.didiglobal.driver",g.packageName)
    }

    @Test fun `DiDi button anchored content includes matching large fare above the pickup`() {
        val g = requireNotNull(HostGeometryDeriver.offer("com.didiglobal.driver",1000,display,1f,
            listOf(box("$0.00",170),box("$148.00",500,545),box("6min (1.2km)",650),
                box("22min (8.0km)",700),box("Aceptar $148.00",780)),
            HostGeometrySource.OCR_TEXT_BOUNDS,OfferContentBounds(24,650,369,804),topChrome=chrome))
        assertEquals(484,g.offerCard.top)
    }

    @Test fun `DiDi top fare remains protected when OCR disagrees with the accept amount`() {
        val g = requireNotNull(HostGeometryDeriver.offer("com.didiglobal.driver",1000,display,1f,
            listOf(box("$900.00",170),box("$132.59",500,545),box("$12.33/km",570),
                box("+$20.00",600),box("$0.00",620),box("6min (1.2km)",650),
                box("22min (8.0km)",700),box("Aceptar $132.50",780)),
            HostGeometrySource.OCR_TEXT_BOUNDS,OfferContentBounds(24,650,369,804),topChrome=chrome))
        assertEquals(484,g.offerCard.top)
        assertTrue(g.offerCard.contains(HostRect(24,500,369,545)))
    }

    @Test fun `recognized unknown variant can derive geometry from heading and accept alone`() {
        val g = requireNotNull(HostGeometryDeriver.offer(host,1000,display,1f,
            listOf(box("Uber nuevo",550),box("Aceptar",760)),HostGeometrySource.OCR_TEXT_BOUNDS,
            topChrome=chrome,recognizedOffer=true))
        assertEquals(534,g.offerCard.top)
    }

    @Test fun `missing S25 fare cannot move the card edge below its distant current heading`() {
        val screen = HostRect(0, 0, 1080, 2340)
        val heading = HostTextBounds("Uber Priority", HostRect(96, 653, 502, 746))
        val fare = HostTextBounds("$147.46", HostRect(96, 770, 550, 897))
        val route = listOf(
            HostTextBounds("4 min (0.7 km)", HostRect(190, 1420, 510, 1477)),
            HostTextBounds("49 min (6.3 km)", HostRect(190, 1625, 541, 1682)),
            HostTextBounds("Aceptar", HostRect(442, 1989, 640, 2046)),
        )
        val read = requireNotNull(HostGeometryDeriver.offer(host, 1000, screen, 3f,
            listOf(heading, fare) + route, HostGeometrySource.OCR_TEXT_BOUNDS,
            OfferContentBounds(96, 770, 550, 1682), recognizedOffer = true))
        val unread = requireNotNull(HostGeometryDeriver.offer(host, 1300, screen, 3f,
            listOf(heading) + route, HostGeometrySource.OCR_TEXT_BOUNDS,
            OfferContentBounds(190, 1420, 541, 1682), recognizedOffer = true))
        assertEquals(605, read.offerCard.top)
        assertEquals(read.offerCard, unread.offerCard)
        assertTrue(unread.offerCard.contains(fare.bounds))
        assertEquals(1300L, unread.capturedAtMs)
    }

    @Test fun `accessibility card container takes precedence over text padding`() {
        val measured = HostRect(0,500,393,830)
        val g = requireNotNull(HostGeometryDeriver.offer(host,1000,display,1f,boxes,
            HostGeometrySource.ACCESSIBILITY_BOUNDS,topChrome=chrome,observedCardBounds=measured))
        assertEquals(measured,g.offerCard)
        assertEquals(HostGeometrySource.ACCESSIBILITY_BOUNDS,g.source)
    }

    @Test fun `older shorter node card cannot hide the current OCR fare and heading above it`() {
        val g = requireNotNull(HostGeometryDeriver.offer(host,1000,display,1f,
            listOf(box("UberX",316),box("MXN148.00",360),box("4 min (2 km)",450),
                box("Viaje: 20 min (10 km)",490),box("Aceptar",760)),
            HostGeometrySource.OCR_AND_ACCESSIBILITY,OfferContentBounds(24,360,369,514),
            topChrome=chrome,observedCardBounds=HostRect(0,545,393,852)))
        assertEquals(300,g.offerCard.top)
    }

    @Test fun `measured card must also contain the current heading and action before exact precedence`() {
        val g = requireNotNull(HostGeometryDeriver.offer(host,1000,display,1f,
            listOf(box("UberX",525))+boxes,HostGeometrySource.OCR_AND_ACCESSIBILITY,
            OfferContentBounds(24,561,369,684),topChrome=chrome,
            observedCardBounds=HostRect(0,550,393,740)))
        assertEquals(509,g.offerCard.top)
        assertTrue(g.offerCard.contains(boxes.last().bounds))
    }

    @Test fun `observed host chrome and map exclusions are retained without complete inventories`() {
        val marker = MapMarker(MapMarkerKind.NEXT_MANOEUVRE,HostRect(196,482,224,511))
        val control = HostRect(10,200,60,248)
        val g = requireNotNull(HostGeometryDeriver.offer(host,1000,display,1f,
            listOf(box("Gira a la izquierda",84,180))+boxes,HostGeometrySource.OCR_AND_ACCESSIBILITY,
            topChrome=chrome,protectedControls=listOf(control),markers=listOf(marker),navigation=true))
        assertEquals(180,g.topChrome.bottom)
        assertEquals(listOf(marker),g.markers)
        assertTrue(control in g.protectedControls)
        assertTrue(g.navigation)
    }

    @Test fun `partly visible controls and markers keep their visible exclusion on both live and idle hosts`() {
        val controls = listOf(HostRect(-10,300,40,340),HostRect(-20,300,-5,340))
        val markers = listOf(MapMarker(MapMarkerKind.DRIVER,HostRect(380,350,420,390)),
            MapMarker(MapMarkerKind.DESTINATION,HostRect(400,400,420,420)))
        val live = requireNotNull(HostGeometryDeriver.offer(host,1000,display,1f,boxes,
            HostGeometrySource.OCR_AND_ACCESSIBILITY,topChrome=chrome,
            protectedControls=controls,markers=markers))
        val idle = requireNotNull(HostGeometryDeriver.idleHost(host,1000,display,chrome,controls,markers))
        for (g in listOf(live,idle)) {
            assertTrue(HostRect(0,300,40,340) in g.protectedControls)
            assertEquals(listOf(MapMarker(MapMarkerKind.DRIVER,HostRect(380,350,393,390))),g.markers)
            assertTrue(g.protectedControls.all(display::contains))
        }
    }

    @Test fun `padding uses display density and all output stays in physical pixels`() {
        val g = requireNotNull(HostGeometryDeriver.offer(host,1000,HostRect(0,0,786,1704),2f,
            boxes.map { it.copy(bounds=it.bounds.let { b -> HostRect(b.left*2,b.top*2,b.right*2,b.bottom*2) }) },
            HostGeometrySource.OCR_TEXT_BOUNDS,topChrome=HostRect(0,0,786,256)))
        assertEquals(1090,g.offerCard.top)
        assertEquals(HostOrientation.PORTRAIT,g.orientation)
    }

    @Test fun `missing geometry and nonhost windows are not invented`() {
        assertNull(derive(emptyList()))
        assertNull(derive(listOf(box("Buscando viajes",100),box("+MXN10",150))))
        assertNull(derive(listOf(box("Aceptar",760))))
        assertNull(HostGeometryDeriver.offer("mx.kompara",1000,display,1f,boxes,HostGeometrySource.OCR_TEXT_BOUNDS))
        assertNull(HostGeometryDeriver.offer(host,1000,display,Float.NaN,boxes,HostGeometrySource.OCR_TEXT_BOUNDS))
    }

    @Test fun `absent top chrome uses display origin and never a screen fraction`() {
        val g = requireNotNull(HostGeometryDeriver.offer(host,1000,display,1f,boxes,HostGeometrySource.OCR_TEXT_BOUNDS))
        assertEquals(HostRect(0,0,393,1),g.topChrome)
    }

    @Test fun `idle host has explicit source and display foot rather than stale offer bounds`() {
        val control = HostRect(0,760,393,820)
        val g = requireNotNull(HostGeometryDeriver.idleHost(host,2000,display,chrome,listOf(control)))
        assertTrue(g.isIdleHost)
        assertEquals(HostGeometrySource.IDLE_HOST,g.source)
        assertEquals(851,g.offerCard.top)
        assertEquals(2000L,g.capturedAtMs)
        assertEquals(listOf(control),g.protectedControls)
        assertNotNull(HostGeometryAdapter().adapt(host,2000,HostLayoutObservation(g,false,false)))
    }
}
