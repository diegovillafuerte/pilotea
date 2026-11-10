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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import mx.kompara.data.settings.RateBasis
import mx.kompara.metrics.CounterOffers
import mx.kompara.parsers.model.*
import mx.kompara.testing.BaselineComparison
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
    /** The v4 kit's S1 pill, and S5's bid below the mínimo with both counter-offers (plan 4.4). */
    private val model=ReglaModel(ReglaBand.Rated(ReglaPosition.ABOVE,BigDecimal("9.4916666667")),ReglaUnit.KM)
    private val bid=ReglaModel(ReglaBand.Rated(ReglaPosition.BELOW,BigDecimal("1.0906")),ReglaUnit.KM,counterOffers=CounterOffers(203,249))
    private fun geometry(top: Int=545, chrome: Int=128, markers: List<MapMarker> = emptyList(), width: Int=393,
        host: String="com.ubercab.driver", controls: List<HostRect> = emptyList()) =
        HostGeometry(host,"test-only",1000,HostRect(0,0,width,852),HostOrientation.PORTRAIT,
            HostRect(0,80,width,chrome),HostRect(0,top,width,838),listOf(HostRect(20,750,width-20,810))+controls,markers,
            declineControls=controls)
    private fun marker(kind: MapMarkerKind,l:Int,t:Int,r:Int,b:Int)=MapMarker(kind,HostRect(l,t,r,b))

    @Test fun `the tray and the plain pill are the placer's two sizes and drive compact, suppression and recovery`() {
        var scale by mutableFloatStateOf(1f)
        var g by mutableStateOf(geometry())
        var value by mutableStateOf(bid)
        var decision: OverlayPlacement? = null
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) { KomparaTheme {
            MeasuredVerdictChip(value,g,OverlayViewport(g.displayBounds,1f,g.orientation),1000,null,true,true,false,
                VerdictChipCallbacks(),Modifier.testTag("plate")) { decision=it }
        } } }
        compose.waitForIdle()
        fun shown() = decision as OverlayPlacement.Displayed
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(174,439),ChipSize(207,90),PlateFormat.FULL),shown())
        compose.onNodeWithContentDescription("Desde 203 pesos",substring=true).assertExists()
        compose.runOnIdle { g=geometry(230) } // a 70 px band: only the plain pill
        compose.waitForIdle()
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(176,158),ChipSize(205,56),PlateFormat.COMPACT),shown())
        compose.onNodeWithTag("plate").assertHeightIsEqualTo(56.dp)
        compose.onNodeWithContentDescription("Abajo de tu mínimo: 1.09 pesos por kilómetro, neto").assertExists()
        compose.runOnIdle { scale=1.3f } // 72.8 dp no longer fits the band, and no dock clears the card
        compose.waitForIdle()
        assertTrue(decision is OverlayPlacement.Suppressed)
        compose.onAllNodes(hasContentDescription("pesos",substring=true)).assertCountEquals(0)
        compose.runOnIdle { scale=1f; g=geometry(); value=bid.copy(band=ReglaBand.Rated(ReglaPosition.BELOW,BigDecimal("-99999999999999999999999999999999999999.99"))) }
        compose.waitForIdle()
        assertTrue("long rates must be measured rather than clipped",decision is OverlayPlacement.Suppressed)
        compose.runOnIdle { value=bid }
        compose.waitForIdle()
        assertEquals(PlateFormat.FULL,shown().format)
    }

    @Test fun `an offer without counter-offers measures the plain pill twice`() {
        var g by mutableStateOf(geometry())
        var decision: OverlayPlacement? = null
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,1f)) { KomparaTheme {
            MeasuredVerdictChip(model,g,OverlayViewport(g.displayBounds,1f,g.orientation),1000,null,true,true,false,
                VerdictChipCallbacks(),Modifier.testTag("plate")) { decision=it }
        } } }
        compose.waitForIdle()
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(176,473),ChipSize(205,56),PlateFormat.FULL),decision)
        // The band that only admitted the bid's plain pill admits this whole chip.
        compose.runOnIdle { g=geometry(230) }
        compose.waitForIdle()
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(176,158),ChipSize(205,56),PlateFormat.FULL),decision)
    }

    @Test fun `the drawn chip is exactly the placed size in every format, so the window never hides for a size change`() {
        data class Case(val value: ReglaModel, val g: HostGeometry, val size: ChipSize, val format: PlateFormat)
        val cases=listOf(Case(bid,geometry(),ChipSize(207,90),PlateFormat.FULL), Case(bid,geometry(230),ChipSize(205,56),PlateFormat.COMPACT),
            Case(model,geometry(),ChipSize(205,56),PlateFormat.FULL),
            // The marker tier always reports COMPACT: a chip without a tray draws the same plain pill.
            Case(model,geometry(342,markers=listOf(marker(MapMarkerKind.DRIVER,0,150,393,300))),ChipSize(205,56),PlateFormat.COMPACT))
        var case by mutableStateOf(cases.first())
        var decision: OverlayPlacement? = null
        compose.setContent { KomparaTheme {
            MeasuredVerdictChip(case.value,case.g,OverlayViewport(case.g.displayBounds,1f,case.g.orientation),1000,null,true,true,false,
                VerdictChipCallbacks()) { decision=it }
        } }
        for (item in cases) {
            compose.runOnIdle { case=item }
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
            val shown=decision as OverlayPlacement.Displayed
            assertEquals(item.size,shown.size); assertEquals(item.format,shown.format)
            val drawn=compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Image))
                .fetchSemanticsNode().size
            assertEquals("${item.format} draws its placed size",ChipSize(shown.size.width,shown.size.height),ChipSize(drawn.width,drawn.height))
        }
    }

    /** v4 kit S6 (native owner of the frame): a tall offer under navigation leaves about 100 dp between the banner and the card. */
    @Test fun `S6 the pill fits a tall offer under navigation where 0_2_20's plate was hidden`() {
        val g=geometry(top=280,chrome=180).copy(navigation=true)
        var decision: OverlayPlacement? = null
        compose.setContent { KomparaTheme {
            MeasuredVerdictChip(model.copy(band=ReglaBand.Rated(ReglaPosition.MEETS,BigDecimal("7.158333333"))),g,
                OverlayViewport(g.displayBounds,1f,g.orientation),1000,null,true,true,false,VerdictChipCallbacks()) { decision=it }
        } }
        compose.waitForIdle()
        val shown=decision as OverlayPlacement.Displayed
        assertEquals(OverlayPlacement.Displayed(OverlayPosition(176,208),ChipSize(205,56),PlateFormat.FULL,rule=PlacementRule.FOOT),shown)
        val rect=shown.position.bounds(shown.size)
        assertTrue("Clear of the banner and the card by 16 dp",rect.top>=g.topChrome.bottom+16 && rect.bottom<=g.offerCard.top-16)
        assertEquals("0.2.20's 240 x 112 / 240 x 80 plate had no slot here",OverlayPlacement.Suppressed("NAVIGATION"),
            OverlayPositioning.place(g,g.packageName,OverlayViewport(g.displayBounds,1f,g.orientation),1000,ChipSize(240,112),ChipSize(240,80)))
    }

    @Test fun `placement frame screenshots of the v4 chip on day and night hosts, large fonts, markers and a DiDi bid`() {
        val didi=RealGeometryFixtures.DIDI
        val decline=listOf(HostRect(170,140,381,184))
        fun rated(position: ReglaPosition, rate: String, basis: RateBasis=RateBasis.NET) =
            ReglaModel(ReglaBand.Rated(position,BigDecimal(rate)),ReglaUnit.KM,basis)
        data class Case(val screen:String,val frame:String,val g:HostGeometry=geometry(),val scale:Float=1f,val dark:Boolean=false,
            val value:ReglaModel=model) { val name get()="${screen}_${frame}_${if(dark) "night" else "day"}_${g.displayBounds.width}_$scale" }
        val cases=listOf(
            Case("placement","S1"), Case("placement","S1",dark=true),
            Case("placement","S2",value=rated(ReglaPosition.MEETS,"7.158333333")),
            Case("placement","S2",dark=true,value=rated(ReglaPosition.MEETS,"7.158333333")),
            Case("placement","S3",value=rated(ReglaPosition.BELOW,"2.199")),
            Case("placement","S4",value=model.copy(band=ReglaBand.Unavailable(ReglaUnavailable.FARE))),
            Case("placement","S4",dark=true,value=model.copy(band=ReglaBand.Unavailable(ReglaUnavailable.FARE))),
            Case("placement","S5",geometry(host=didi,controls=decline),value=bid),
            Case("placement","S9",geometry(host=didi,controls=decline),
                value=rated(ReglaPosition.MEETS,"6.389").copy(counterOffers=CounterOffers(92,113))),
            Case("placement","S10",value=rated(ReglaPosition.ABOVE,"12.3333",RateBasis.GROSS)),
            Case("placement","S7",geometry(469),1.3f),
            Case("placement","S1",geometry(width=320),1.3f), Case("placement","S1",geometry(),2f),
            Case("placement-markers","S1",geometry(316,markers=listOf(marker(MapMarkerKind.PICKUP,210,276,227,293),marker(MapMarkerKind.DESTINATION,321,147,339,165)))),
            Case("placement-ring","S1",geometry(342,markers=listOf(marker(MapMarkerKind.DRIVER,159,153,233,227)))),
            Case("placement-ring","S1",geometry(342,markers=listOf(marker(MapMarkerKind.DRIVER,159,153,233,227))),1.3f))
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
            assertNotNull("${item.name}: the pill is shown",shown)
            assertTrue(OverlayPositioning.valid(shown!!.position,shown.size,item.g,
                OverlayViewport(item.g.displayBounds,1f,item.g.orientation),shown.overMarker))
            val bitmap=compose.runOnIdle {
                val view=compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(item.g.displayBounds.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val out=File("build/outputs/v4-screenshots/${item.name}.png").apply { parentFile!!.mkdirs() }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            val baseline=File("src/test/snapshots/v4/${out.name}")
            if(System.getProperty("v4.recordScreenshots")=="true") { baseline.parentFile!!.mkdirs(); out.copyTo(baseline,overwrite=true) }
            else { assertTrue("Missing v4 baseline $baseline",baseline.exists()); BaselineComparison.assertMatches(BitmapFactory.decodeFile(baseline.path),bitmap,out) }
        }
    }
}
