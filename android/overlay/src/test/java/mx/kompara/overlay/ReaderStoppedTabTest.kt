package mx.kompara.overlay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class ReaderStoppedTabGateTest {
    @Test fun `five seconds once per host foreground without renewal from repeated failures`() {
        val gate = ReaderStoppedTabGate()
        assertTrue(gate.visible("uber", true, 100))
        assertTrue(gate.visible("uber", true, 5099))
        assertEquals(1L, gate.remainingMs(5099))
        assertFalse(gate.visible("uber", true, 5100))
        assertFalse(gate.visible("uber", true, 8000))
        assertFalse(gate.visible(null, true, 9000))
        assertTrue(gate.visible("uber", true, 10000))
    }
    @Test fun `switching host creates one visit and loses previous tab`() {
        val gate = ReaderStoppedTabGate()
        assertTrue(gate.visible("uber", true, 0))
        assertTrue(gate.visible("didi", true, 4000))
        assertFalse(gate.visible("didi", true, 9000))
    }
    @Test fun `permission loss pause recovery and missing host immediately hide without rearming visit`() {
        val gate = ReaderStoppedTabGate()
        assertFalse(gate.visible("uber", false, 0))
        assertTrue(gate.visible("uber", true, 100))
        assertFalse(gate.visible("uber", false, 200))
        assertFalse(gate.visible("uber", true, 300))
        assertFalse(gate.visible(null, false, 400))
        assertTrue(gate.visible("uber", true, 500))
    }
}

class StoppedTabWindowPlacementTest {
    private val viewport = OverlayViewport(HostRect(0, 0, 393, 852), 1f, HostOrientation.PORTRAIT)
    private val geometry = HostGeometry("uber", "test-only", 1_000, viewport.bounds, viewport.orientation,
        HostRect(0, 80, 393, 128), HostRect(0, 545, 393, 838),
        listOf(HostRect(20, 750, 373, 810)), emptyList())
    private val size = ChipSize(240, 72)
    private val foot = OverlayPosition(141, 457)

    @Test fun `opaque idle nodes keep the last OCR card exclusion for only the admitted visit`() {
        val idle = geometry.copy(offerCard = HostRect(0, 851, 393, 852), protectedControls = emptyList(), isIdleHost = true)
        val admitted = stoppedTabGeometry(idle, geometry, null, "uber", viewport, 1_500)!!
        assertEquals(geometry.offerCard, admitted.offerCard)
        assertEquals(StoppedTabWindowDecision.Show, stoppedTabWindowDecision(admitted, "uber", viewport,
            size, foot, foot, layoutPending = false))
        val later = idle.copy(capturedAtMs = 4_000)
        assertEquals(geometry.offerCard, stoppedTabGeometry(later, geometry, admitted, "uber", viewport, 4_000)!!.offerCard)
        assertTrue(stoppedTabGeometry(later, geometry, null, "uber", viewport, 4_000)!!.isIdleHost)
        assertNull(stoppedTabGeometry(null, geometry, admitted, "uber", viewport, 4_000))
        assertNull(stoppedTabGeometry(later, geometry, admitted, "didi", viewport, 4_000))
    }

    @Test fun `C6 stays hidden until actual location matches and corrects a nonzero window origin`() {
        val initial = stoppedTabWindowDecision(geometry, "uber", viewport, size,
            actualPosition = OverlayPosition(0, 24), requestedPosition = OverlayPosition(0, 0), layoutPending = false)
        val move = StoppedTabWindowDecision.Move(OverlayPosition(141, 433))
        assertEquals(move, initial)
        assertEquals(StoppedTabWindowDecision.WaitForLayout, stoppedTabWindowDecision(geometry, "uber", viewport,
            size, OverlayPosition(0, 24), move.windowPosition, layoutPending = true))
        // WindowManager can lag the params mutation before isLayoutRequested becomes true.
        assertEquals(StoppedTabWindowDecision.WaitForLayout, stoppedTabWindowDecision(geometry, "uber", viewport,
            size, OverlayPosition(0, 24), move.windowPosition, layoutPending = false, lastMoveFrom = OverlayPosition(0, 24)))
        assertEquals(StoppedTabWindowDecision.Show, stoppedTabWindowDecision(geometry, "uber", viewport,
            size, foot, move.windowPosition, layoutPending = false))
    }

    @Test fun `a pending resize cannot reveal the old location or infer a stale window origin`() {
        assertEquals(StoppedTabWindowDecision.WaitForLayout, stoppedTabWindowDecision(geometry, "uber", viewport,
            ChipSize(312, 94), foot, foot, layoutPending = true))
        assertEquals(StoppedTabWindowDecision.Move(OverlayPosition(69, 435)), stoppedTabWindowDecision(geometry,
            "uber", viewport, ChipSize(312, 94), foot, foot, layoutPending = false))
        assertEquals(StoppedTabWindowDecision.Hide, stoppedTabWindowDecision(geometry, "uber", viewport,
            ChipSize(480, 144), foot, foot, layoutPending = false))
    }

    @Test fun `new gesture insets force relocation before showing the tab`() {
        val changed = viewport.copy(backInsets = OverlayInsets(right = 40))
        assertEquals(StoppedTabWindowDecision.Move(OverlayPosition(113, 457)), stoppedTabWindowDecision(geometry,
            "uber", changed, size, foot, foot, layoutPending = false))
        assertEquals(StoppedTabWindowDecision.Show, stoppedTabWindowDecision(geometry, "uber", changed,
            size, OverlayPosition(113, 457), OverlayPosition(113, 457), layoutPending = false))
    }

    @Test fun `different host or rotated geometry hides even if old coordinates still agree`() {
        for ((host, current) in listOf(
            "didi" to viewport,
            "uber" to viewport.copy(bounds = HostRect(0, 0, 852, 393), orientation = HostOrientation.LANDSCAPE),
        )) assertEquals(StoppedTabWindowDecision.Hide, stoppedTabWindowDecision(geometry, host, current,
            size, foot, foot, layoutPending = false))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w560dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderStoppedTabTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun `C6 passive tab retains words geometry and no action at day night and large fonts`() {
        data class Case(val name: String, val scale: Float = 1f, val width: Int = 393, val dark: Boolean = false)
        val cases = listOf(Case("C6_day"), Case("C6_night", dark = true),
            Case("C6_1.3", scale = 1.3f), Case("C6_narrow_1.3", scale = 1.3f, width = 320),
            Case("C6_large_2.0", scale = 2f, width = 320))
        var case by mutableStateOf(cases.first())
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, case.scale)) {
            KomparaTheme(darkTheme = case.dark) {
                Box(Modifier.width(case.width.dp).height(852.dp).background(if (case.dark) Color(0xFF262C33) else Color(0xFFEBEEF4))) {
                    Box(Modifier.padding(top = 200.dp, start = 12.dp, end = 12.dp)) { ReaderStoppedTab(Modifier.testTag("tab")) }
                }
            }
        } }
        for (item in cases) {
            compose.runOnIdle { case = item }; compose.waitForIdle()
            compose.onNodeWithText("Semáforo apagado", useUnmergedTree = true).assertIsDisplayed().assertHasNoClickAction()
            compose.onNodeWithText("Dejamos de leer tus ofertas", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Abre Kompara al estacionarte", useUnmergedTree = true).assertIsDisplayed()
            // Padding belongs to the fixture placement; the plate itself is 240 × 72 at 1×.
            if (item.scale == 1f) compose.onNodeWithTag("tab").assertHeightIsEqualTo(72.dp).assertWidthIsEqualTo(240.dp)
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(item.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val out = File("build/outputs/v3-screenshots/readiness_${item.name}.png").apply { parentFile!!.mkdirs() }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/${out.name}")
            if (System.getProperty("v3.recordScreenshots") == "true") out.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing $baseline", baseline.exists()); assertTrue("Changed $out", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap)) }
        }
    }
}
