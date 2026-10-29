package mx.kompara.parsers

import mx.kompara.parsers.model.*
import org.junit.Assert.*
import org.junit.Test

class HostGeometryAdapterTest {
    private val host="com.ubercab.driver"
    private val g=HostGeometry(host,"replay-test",1000,HostRect(0,0,1080,2340),HostOrientation.PORTRAIT,
        HostRect(0,80,1080,300),HostRect(0,900,1080,2300),listOf(HostRect(40,2100,1040,2240)),emptyList())
    private val observed=HostLayoutObservation(g,true,true)
    private val adapter=HostGeometryAdapter(setOf(host to g.layoutId))
    @Test fun `production accepts real bounds without layout certification but not absent geometry`() {
        assertEquals(g,HostGeometryAdapter().adapt(host,1000,observed))
        assertNull(adapter.adapt(host,1000,null))
    }
    @Test fun `validated complete bounds pass through unchanged without scaling or guessing`() {
        assertEquals(g,adapter.adapt(host,1000,observed))
    }
    @Test fun `frame and host must agree but unknown layout and incomplete inventories do not gate`() {
        assertNull(adapter.adapt("com.didiglobal.driver",1000,observed))
        assertNull(adapter.adapt(host,1001,observed))
        assertEquals(g,adapter.adapt(host,1000,observed.copy(controlsComplete=false)))
        assertEquals(g,adapter.adapt(host,1000,observed.copy(markersComplete=false)))
        val changed = g.copy(layoutId="new-version")
        assertEquals(changed,adapter.adapt(host,1000,observed.copy(geometry=changed)))
    }
    @Test fun `unknown manoeuvre does not gate navigation but all supplied bounds must fit display`() {
        val noMarkers = g.copy(navigation=true, protectedControls=emptyList())
        assertEquals(noMarkers,adapter.adapt(host,1000,observed.copy(geometry=noMarkers)))
        assertNull(adapter.adapt(host,1000,observed.copy(geometry=g.copy(offerCard=HostRect(0,900,1080,2400)))))
        val nav=g.copy(navigation=true,markers=listOf(MapMarker(MapMarkerKind.NEXT_MANOEUVRE,HostRect(400,500,440,550))))
        assertEquals(nav,adapter.adapt(host,1000,observed.copy(geometry=nav)))
    }
    @Test fun `DiDi needs matching geometry but not independent certification`() {
        val didi=g.copy(packageName="com.didiglobal.driver")
        assertNull(adapter.adapt(didi.packageName,1000,observed))
        assertEquals(didi,adapter.adapt(didi.packageName,1000,observed.copy(geometry=didi)))
        assertEquals(didi,HostGeometryAdapter(setOf(didi.packageName to didi.layoutId))
            .adapt(didi.packageName,1000,observed.copy(geometry=didi)))
    }
    @Test fun `overlapping card and chrome is measured no space rather than missing geometry`() {
        val blocked=g.copy(topChrome=HostRect(0,80,1080,1000))
        assertEquals(blocked,adapter.adapt(host,1000,observed.copy(geometry=blocked)))
    }
    @Test fun `malformed card chrome controls and markers remain rejected`() {
        listOf(g.copy(offerCard=HostRect(0,900,0,2300)),
            g.copy(topChrome=HostRect(-1,80,1080,300)),
            g.copy(protectedControls=listOf(HostRect(0,0,1081,400))),
            g.copy(markers=listOf(MapMarker(MapMarkerKind.DRIVER,HostRect(0,-1,40,30)))))
            .forEach { assertNull(adapter.adapt(host,1000,observed.copy(geometry=it))) }
    }
}
