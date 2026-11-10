package mx.kompara.overlay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import mx.kompara.parsers.model.*
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w560dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MeasuredVerdictChipTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val model=ReglaModel(ReglaBand.Rated(ReglaPosition.ABOVE,BigDecimal("9.4916666667")),ReglaUnit.KM)
    private fun geometry(top: Int=545, chrome: Int=128, markers: List<MapMarker> = emptyList(), width: Int=393) =
        HostGeometry("com.ubercab.driver","test-only",1000,HostRect(0,0,width,852),HostOrientation.PORTRAIT,
            HostRect(0,80,width,chrome),HostRect(0,top,width,838),listOf(HostRect(20,750,width-20,810)),markers)
    private fun marker(kind: MapMarkerKind,l:Int,t:Int,r:Int,b:Int)=MapMarker(kind,HostRect(l,t,r,b))

    @Test fun `actual full compact and long-label measurements drive suppression and recovery`() {
        var scale by mutableFloatStateOf(1f)
        var g by mutableStateOf(geometry())
        var value by mutableStateOf(model)
        var decision: OverlayPlacement? = null
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) { KomparaTheme {
            MeasuredVerdictChip(value,g,OverlayViewport(g.displayBounds,1f,g.orientation),1000,null,true,true,false,
                VerdictChipCallbacks(),Modifier.testTag("plate")) { decision=it }
        } } }
        compose.waitForIdle()
        assertEquals(PlateFormat.FULL,(decision as OverlayPlacement.Displayed).format)
        compose.runOnIdle { g=geometry(250) } //122px band: only compact
        compose.waitForIdle()
        assertEquals(PlateFormat.COMPACT,(decision as OverlayPlacement.Displayed).format)
        compose.onNodeWithTag("plate").assertHeightIsEqualTo(80.dp)
        compose.runOnIdle { scale=1.3f }
        compose.waitForIdle()
        assertTrue(decision is OverlayPlacement.Suppressed)
        compose.onNodeWithText("tu meta $8").assertDoesNotExist()
        compose.runOnIdle { scale=1f; g=geometry(); value=model.copy(band=ReglaBand.Rated(ReglaPosition.ABOVE,BigDecimal("99999999999999999999999999999999999999.99"))) }
        compose.waitForIdle()
        assertTrue("long labels must be measured rather than clipped to 240",decision is OverlayPlacement.Suppressed)
        compose.runOnIdle { value=model }
        compose.waitForIdle()
        assertEquals(PlateFormat.FULL,(decision as OverlayPlacement.Displayed).format)
    }

    @Test fun `placement frame screenshots include narrow large-font and night hosts`() {
        data class Case(val id:String,val g:HostGeometry=geometry(),val scale:Float=1f,val dark:Boolean=false,
            val value:ReglaModel=model)
        val cases=listOf(
            Case("C8",geometry(316,markers=listOf(marker(MapMarkerKind.PICKUP,210,276,227,293),marker(MapMarkerKind.DESTINATION,321,147,339,165)))),
            Case("C9",geometry(chrome=180,markers=listOf(marker(MapMarkerKind.DRIVER,77,489,115,527),
                marker(MapMarkerKind.NEXT_MANOEUVRE,197,482,225,512),marker(MapMarkerKind.DESTINATION,321,371,339,389))),
                value=model.copy(band=ReglaBand.Rated(ReglaPosition.MEETS,BigDecimal("7.158333333")))),
            Case("C10",geometry(469),1.3f), Case("C11",geometry(260,180)),
            Case("C12",geometry(342,markers=listOf(marker(MapMarkerKind.DRIVER,159,153,233,227)))),
            Case("C12_1.3",geometry(342,markers=listOf(marker(MapMarkerKind.DRIVER,159,153,233,227))),1.3f),
            Case("narrow_1.3",geometry(width=320),1.3f), Case("large_2.0",geometry(),2f),
            Case("N8",dark=true), Case("N9",dark=true,value=model.copy(band=ReglaBand.Rated(ReglaPosition.MEETS,BigDecimal("7.158333333")))),
            Case("N10",dark=true,value=model.copy(band=ReglaBand.Unavailable(ReglaUnavailable.FARE))))
        var case by mutableStateOf(cases.first())
        var placed by mutableStateOf<OverlayPlacement?>(null)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,case.scale)) { KomparaTheme(darkTheme=case.dark) {
            val g=case.g
            Box(Modifier.width(g.displayBounds.width.dp).height(852.dp).background(if(case.dark) Color(0xFF262C33) else Color(0xFFEBEEF4))) {
                Canvas(Modifier.fillMaxSize()) {
                    fun box(rect: HostRect,color:Color)=drawRect(color,Offset(rect.left.toFloat(),rect.top.toFloat()),Size(rect.width.toFloat(),rect.height.toFloat()))
                    box(g.topChrome,if(case.dark) Color(0xFF111110) else Color(0xFFCDD0D5))
                    box(g.offerCard,if(case.dark) Color(0xFF121211) else Color.White)
                    g.protectedControls.forEach { box(it,Color(0xFF111110)) }
                    g.markers.forEach { box(it.outerBounds,Color(0xFF6B86A2)) }
                }
                val p=(placed as? OverlayPlacement.Displayed)?.position ?: OverlayPosition(0,0)
                MeasuredVerdictChip(case.value,g,OverlayViewport(g.displayBounds,1f,g.orientation),1000,null,true,true,case.dark,
                    VerdictChipCallbacks(),Modifier.offset { IntOffset(p.x,p.y) }) { if(placed!=it) placed=it }
            }
        } } }
        for(item in cases) {
            compose.runOnIdle { case=item }
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            val shown=placed as? OverlayPlacement.Displayed
            if(shown!=null) assertTrue(OverlayPositioning.valid(shown.position,shown.size,item.g,
                OverlayViewport(item.g.displayBounds,1f,item.g.orientation),shown.overMarker))
            val bitmap=compose.runOnIdle {
                val view=compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(item.g.displayBounds.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val out=File("build/outputs/v3-screenshots/placement_${item.id}.png").apply { parentFile!!.mkdirs() }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            val baseline=File("src/test/snapshots/v3/${out.name}")
            if(System.getProperty("v3.recordScreenshots")=="true") out.copyTo(baseline,overwrite=true)
            else { assertTrue("Missing baseline $baseline",baseline.exists()); assertTrue("Changed $out",BitmapFactory.decodeFile(baseline.path).sameAs(bitmap)) }
        }
    }
}
