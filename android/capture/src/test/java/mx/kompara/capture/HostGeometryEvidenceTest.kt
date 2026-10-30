package mx.kompara.capture

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.MapMarkerKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class HostGeometryEvidenceTest {
    private val host = "com.ubercab.driver"
    private val display = HostDisplayFrame(HostRect(0, 0, 393, 852), 1f, HostRect(0, 0, 393, 24))
    private fun node(text: String?, bounds: Rect, id: String? = null, className: String = "android.view.View") =
        SnapshotNode(text, id, className, bounds, 1, 0)
    private fun snapshot(nodes: List<SnapshotNode>) = ScreenSnapshot(host, 100L, nodes)

    @After fun clear() {
        HostGeometryEvidence.publish(null)
        HostWindowOwnershipSource.detach(this)
    }

    @Test fun `node card bounds and known marker outer bounds reach offer geometry`() {
        val card = HostRect(0, 545, 393, 852)
        val evidence = snapshot(listOf(
            node(null, Rect(0, 545, 393, 852), "$host:id/offer_card"),
            node("MXN148.00", Rect(24, 570, 250, 615)),
            node("Viaje: 20 min (10 km)", Rect(24, 650, 350, 674)),
            node("Aceptar", Rect(24, 750, 369, 800), className = "android.widget.Button"),
            node("Tu ubicación", Rect(100, 300, 130, 330), "$host:id/driver_marker"),
        )).hostEvidence(display)!!
        assertEquals(card, evidence.observedCardBounds)
        assertEquals(MapMarkerKind.DRIVER, evidence.markers.single().kind)
        assertEquals(HostRect(100, 300, 130, 330), evidence.markers.single().outerBounds)
        val geometry = evidence.geometryForOffer()!!
        assertEquals(card, geometry.offerCard)
        assertEquals(HostGeometrySource.ACCESSIBILITY_BOUNDS, geometry.source)
        assertTrue(geometry.protectedControls.contains(HostRect(24, 750, 369, 800)))
    }

    @Test fun `idle host supplies C6 without offer text or an old offer frame`() {
        val evidence = snapshot(listOf(node("En línea", Rect(100, 80, 250, 128))))
            .hostEvidence(display)!!
        assertNull(evidence.geometryForOffer())
        val geometry = evidence.idleGeometry()!!
        assertTrue(geometry.isIdleHost)
        assertEquals(HostGeometrySource.IDLE_HOST, geometry.source)
        assertEquals(100L, geometry.capturedAtMs)
        assertTrue(geometry.protectedControls.contains(HostRect(100, 80, 250, 128)))
        assertEquals(128, geometry.topChrome.bottom)
    }

    @Test fun `separated navigation banner and toolbar extend chrome but bottom navigation does not`() {
        val evidence = snapshot(listOf(
            node("Menú", Rect(12, 80, 60, 128)),
            node(null, Rect(0, 140, 393, 188), "$host:id/toolbar"),
            node("En 200 m", Rect(0, 200, 393, 280), "$host:id/navigation_banner"),
            node("En línea", Rect(0, 780, 393, 852), "$host:id/bottom_navigation"),
            node("Menú", Rect(10, 790, 60, 838)),
        )).hostEvidence(display)!!
        assertEquals(280, evidence.topChrome.bottom)
        assertTrue(evidence.navigation)
        assertEquals(280, evidence.idleGeometry()!!.topChrome.bottom)
        assertTrue(evidence.protectedControls.contains(HostRect(0, 780, 393, 852)))
    }

    @Test fun `unlabelled fullscreen compose root is never treated as an offer card`() {
        val evidence = snapshot(listOf(node(null, Rect(0, 0, 393, 852), "$host:id/offer_card")))
            .hostEvidence(display)!!
        assertNull(evidence.observedCardBounds)
        assertNull(evidence.geometryForOffer())
    }

    @Test fun `OCR can use only fresh matching host and display evidence`() {
        val evidence = snapshot(emptyList()).hostEvidence(display)!!
        HostGeometryEvidence.publish(evidence)
        assertSame(evidence, HostGeometryEvidence.current(host, 1_100, display.bounds))
        assertNull(HostGeometryEvidence.current(host, 1_101, display.bounds))
        assertNull(HostGeometryEvidence.current(host, 99, display.bounds))
        assertNull(HostGeometryEvidence.current("com.didiglobal.driver", 100))
        assertNull(HostGeometryEvidence.current(host, 100, HostRect(0, 0, 852, 393)))
        HostGeometryEvidence.publish(null)
        assertNull(HostGeometryEvidence.current(host, 100))
    }

    @Test fun `partially clipped markers and controls preserve their visible exclusions`() {
        val evidence = snapshot(listOf(
            node("Tu ubicación", Rect(380, 300, 420, 340)),
            node("Menú", Rect(-10, 80, 40, 128), className = "android.widget.Button"),
        )).hostEvidence(display)!!
        assertEquals(HostRect(380, 300, 393, 340), evidence.markers.single().outerBounds)
        assertTrue(evidence.protectedControls.contains(HostRect(0, 80, 40, 128)))
        assertEquals(128, evidence.topChrome.bottom)
        assertTrue(evidence.textBounds.isEmpty())
    }

    @Test fun `fully offscreen node and unsupported app are discarded`() {
        val snapshot = snapshot(listOf(node("Tu ubicación", Rect(-40, 0, -10, 40))))
        assertTrue(snapshot.hostEvidence(display)!!.textBounds.isEmpty())
        assertTrue(snapshot.hostEvidence(display)!!.markers.isEmpty())
        assertNull(snapshot.copy(packageName = "com.example.other").hostEvidence(display))
    }

    @Test fun `window source never merges another host into geometry and clears on detach`() {
        HostWindowOwnershipSource.attach(this) { pkg, at -> HostWindowOwnership(pkg, at, display.bounds, 7, display.bounds) }
        val source = WindowSnapshotSource(NodeFlattener())
        source.attach(WindowSnapshotSource.RootProvider {
            listOf(root(host, "Uber", 7), root("com.didiglobal.driver", "DiDi", 8))
        }) { display }
        val captured = source.read(host, 100)!!
        assertEquals(host, captured.packageName)
        assertEquals(listOf("Uber"), captured.nodes.map { it.text })
        assertEquals(7, captured.hostWindow!!.windowId)
        assertSame(captured.hostWindow, HostGeometryEvidence.current(host, 100))
        source.detach()
        assertNull(HostGeometryEvidence.state.value)
    }

    @Test fun `same package lower window idle nodes cannot enter selected window geometry`() {
        HostWindowOwnershipSource.attach(this) { pkg, at -> HostWindowOwnership(pkg, at, display.bounds, 7, display.bounds) }
        val source = WindowSnapshotSource(NodeFlattener())
        source.attach(WindowSnapshotSource.RootProvider {
            listOf(root(host, "Buscando viajes", 6), root(host, "Uber Priority", 7))
        }) { display }
        val captured = source.read(host, 100)!!
        assertEquals(listOf("Buscando viajes", "Uber Priority"), captured.nodes.map { it.text })
        assertEquals(listOf("Uber Priority"), captured.hostWindow!!.textBounds.map { it.text })
        assertEquals(7, captured.hostWindow!!.windowId)
        assertSame(captured.hostWindow, HostGeometryEvidence.current(host, 100))
        source.detach()
    }

    @Test fun `node geometry is withheld when selected window changes while nodes are read`() {
        var selectedId = 7
        HostWindowOwnershipSource.attach(this) { pkg, at -> HostWindowOwnership(pkg, at, display.bounds, selectedId, display.bounds) }
        val source = WindowSnapshotSource(NodeFlattener())
        source.attach(WindowSnapshotSource.RootProvider {
            selectedId = 8
            listOf(root(host, "Buscando viajes", 7))
        }) { display }
        assertNull(source.read(host, 100)!!.hostWindow)
        assertNull(HostGeometryEvidence.state.value)
        source.detach()
    }

    private fun root(packageName: String, label: String, id: Int) =
        android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            this.packageName = packageName
            text = label
            setBoundsInScreen(Rect(0, 0, 393, 852))
            val window = android.view.accessibility.AccessibilityWindowInfo.obtain()
            shadowOf(window).setId(id)
            shadowOf(this).setAccessibilityWindowInfo(window)
        }
}
