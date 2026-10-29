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
    @Test fun `production stays gated without certified layout even for complete observations`() {
        assertNull(HostGeometryAdapter().adapt(host,1000,observed))
        assertNull(adapter.adapt(host,1000,null))
    }
    @Test fun `validated complete bounds pass through unchanged without scaling or guessing`() {
        assertEquals(g,adapter.adapt(host,1000,observed))
    }
    @Test fun `layout frame host and complete inventories must all agree`() {
        assertNull(adapter.adapt("com.didiglobal.driver",1000,observed))
        assertNull(adapter.adapt(host,1001,observed))
        assertNull(adapter.adapt(host,1000,observed.copy(controlsComplete=false)))
        assertNull(adapter.adapt(host,1000,observed.copy(markersComplete=false)))
        assertNull(adapter.adapt(host,1000,observed.copy(geometry=g.copy(layoutId="new-version"))))
    }
    @Test fun `navigation requires next manoeuvre outer bounds and full card must fit display`() {
        assertNull(adapter.adapt(host,1000,observed.copy(geometry=g.copy(navigation=true))))
        assertNull(adapter.adapt(host,1000,observed.copy(geometry=g.copy(offerCard=HostRect(0,900,1080,2400)))))
        val nav=g.copy(navigation=true,markers=listOf(MapMarker(MapMarkerKind.NEXT_MANOEUVRE,HostRect(400,500,440,550))))
        assertEquals(nav,adapter.adapt(host,1000,observed.copy(geometry=nav)))
    }
    @Test fun `DiDi adapter has independent certification and never borrows Uber bounds`() {
        val didi=g.copy(packageName="com.didiglobal.driver")
        assertNull(adapter.adapt(didi.packageName,1000,observed.copy(geometry=didi)))
        assertEquals(didi,HostGeometryAdapter(setOf(didi.packageName to didi.layoutId))
            .adapt(didi.packageName,1000,observed.copy(geometry=didi)))
    }
}
