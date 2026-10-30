package mx.kompara.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mx.kompara.capture.OfferEvent
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.OfferCard
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaPosition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowViewGroup
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Clock
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2340dp-mdpi", shadows = [OverlayControllerPlacementTest.ScreenLocation::class, OverlayControllerPlacementTest.GroupScreenLocation::class])
class OverlayControllerPlacementTest {
    /** Model the WM screen transform explicitly; the JVM has no real display compositor. */
    @Implements(View::class)
    open class ScreenLocation : ShadowViewGroup() {
        @Implementation fun getLocationOnScreen(location: IntArray) {
            reads++
            location[0] = position.x; location[1] = position.y
        }
        companion object {
            var position = OverlayPosition(324, 243)
            var reads = 0
        }
    }

    @Implements(ViewGroup::class)
    class GroupScreenLocation : ScreenLocation()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val viewport = OverlayViewport(HostRect(0, 0, 1080, 2340), 3f, HostOrientation.PORTRAIT)
    private val geometry = HostGeometry("com.ubercab.driver", "fake-live-frame", System.currentTimeMillis(),
        viewport.bounds, viewport.orientation, HostRect(0, 0, 1080, 120), HostRect(48, 627, 1032, 2140),
        emptyList(), emptyList())
    private val placement = OverlayPlacement.Displayed(OverlayPosition(324, 243), ChipSize(720, 336), PlateFormat.FULL)
    private val reading = VerdictChipState.reading(PlatformThreshold.DEFAULT, PreferredMetric.IPK)
    private val rated = VerdictChipState(reading.copy(band = ReglaBand.Rated(ReglaPosition.ABOVE, BigDecimal("12"))), "15")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `visible same-slot content and geometry updates never hide or reset placement`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = controller()
        try {
            val v = method("readViewport", WindowInsets::class.java).invoke(controller, null) as OverlayViewport
            val g = geometry.copy(displayBounds = v.bounds, offerCard = HostRect(0, 600, v.bounds.right, 800),
                topChrome = HostRect(0, 0, v.bounds.right, 100))
            val shown = OverlayPlacement.Displayed(OverlayPosition(12, 300), ChipSize(240, 112), PlateFormat.FULL)
            val view = ComposeView(context).apply { alpha = 1f }
            field("composeView").set(controller, view)
            field("placement").set(controller, shown)
            field("layoutParams").set(controller, WindowManager.LayoutParams().apply { x = 12; y = 300 })
            state(controller, "geometry", g)
            state(controller, "viewport", v)
            state(controller, "chipState", reading)
            val next = OfferEvent.Parsed(g.packageName, g.capturedAtMs,
                OfferCard(platform = g.packageName, fare = 79.45, hostGeometry = g.copy(layoutId = "next-frame",
                    protectedControls = listOf(HostRect(0, 700, 100, 750))), sessionId = "same-offer"))
            render(controller, scope, OverlayVisibility.Showing(rated, "same-offer", next))
            assertEquals("A changed verdict must remain visible while its same footprint is remeasured", 1f, view.alpha)
            assertSame(shown, field("placement").get(controller))
            assertSame(view, field("composeView").get(controller))
            assertEquals(0, (field("layoutParams").get(controller) as WindowManager.LayoutParams).flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } finally {
            controller.stop(); scope.cancel(); Dispatchers.resetMain()
        }
    }

    @Test fun `normal first attach reveals at first completed layout pre-draw`() {
        val controller = prepared()
        try {
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            val params = field("layoutParams").get(controller) as WindowManager.LayoutParams
            // The real 1080x2340 geometry is anchored before the size is known to WindowManager.
            assertEquals(Gravity.BOTTOM or Gravity.RIGHT, params.gravity)
            assertEquals(36, params.x)
            assertEquals(1761, params.y)
            assertEquals(0f, view.alpha)
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, placement)
            assertEquals("Measurement must not itself reveal or read a provisional screen origin", 0, ScreenLocation.reads)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("Screen verification must happen on first pre-draw", 1, ScreenLocation.reads)
            assertEquals(1f, view.alpha)
            assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } finally { controller.stop() }
    }

    @Test fun `a safe saved position also reveals on its first completed layout`() {
        val controller = prepared()
        val saved = placement.copy(position = OverlayPosition(36, 243))
        try {
            state(controller, "savedPosition", saved.position)
            ScreenLocation.position = saved.position
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            val params = field("layoutParams").get(controller) as WindowManager.LayoutParams
            assertEquals(Gravity.TOP or Gravity.LEFT, params.gravity)
            assertEquals(36, params.x); assertEquals(243, params.y)
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, saved)
            assertEquals(0, ScreenLocation.reads)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1, ScreenLocation.reads)
            assertEquals(1f, view.alpha)
        } finally { controller.stop() }
    }

    @Test fun `a pending layout never reads the provisional screen position`() {
        val controller = prepared()
        try {
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            view.layout(0, 0, 720, 336)
            field("placement").set(controller, placement)
            view.requestLayout()
            ScreenLocation.position = OverlayPosition(0, 0)
            method("settleWindow").invoke(controller)
            assertEquals(0, ScreenLocation.reads)
            assertEquals(0f, view.alpha)
        } finally { controller.stop() }
    }

    @Test fun `real movement hides until the new position has completed layout`() {
        val controller = prepared()
        try {
            method("attach").invoke(controller)
            val view = field("composeView").get(controller) as ComposeView
            view.layout(0, 0, 720, 336)
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, placement)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1f, view.alpha)
            val move = placement.copy(position = OverlayPosition(36, 243))
            method("applyPlacement", OverlayPlacement::class.java).invoke(controller, move)
            assertEquals(0f, view.alpha)
            assertTrue("WindowManager moves must request a fresh layout", view.isLayoutRequested)
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("Never calibrate against the previous frame's coordinates", 1, ScreenLocation.reads)
            assertEquals(0f, view.alpha)
            // Simulate the next completed WM traversal, then its pre-draw screen transform.
            view.layout(0, 0, 720, 336)
            ScreenLocation.position = move.position
            view.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1f, view.alpha)
            assertEquals(2, ScreenLocation.reads)
        } finally { controller.stop() }
    }

    private fun prepared() = controller().also {
        ScreenLocation.reads = 0; ScreenLocation.position = placement.position
        state(it, "geometry", geometry); state(it, "viewport", viewport); state(it, "chipState", reading)
        field("event").set(it, OfferEvent.Parsed(geometry.packageName, geometry.capturedAtMs,
            OfferCard(platform = geometry.packageName, hostGeometry = geometry)))
    }

    private fun controller(): OverlayController {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No writes")
        }
        val settings = SettingsRepository(store)
        val readiness = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) = Unit
        }, Clock.systemUTC()).apply {
            supportedApps(setOf(geometry.packageName)); bound(); captureRunning(true)
        }
        return OverlayController(OfferRecorder(dao<OfferDao>(), CostProfileRepository(dao<CostProfileDao>()), settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(dao<OfferPresentationDao>()), readiness).also {
            field("overlayContext").set(it, context)
        }
    }

    private fun field(name: String) = OverlayController::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun method(name: String, vararg args: Class<*>) = OverlayController::class.java.getDeclaredMethod(name, *args).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    private fun state(controller: OverlayController, name: String, value: Any?) {
        (field("$name\$delegate").get(controller) as MutableState<Any?>).value = value
    }
    private suspend fun render(controller: OverlayController, scope: CoroutineScope, visibility: OverlayVisibility): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            method("render", CoroutineScope::class.java, OverlayVisibility::class.java, Continuation::class.java)
                .invoke(controller, scope, visibility, continuation)
        }
    private inline fun <reified T> dao(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "observe" -> flowOf(null)
            "observeVersions" -> flowOf(emptyList<mx.kompara.data.db.entity.CostVersionEntity>())
            else -> error("Unexpected DAO call: ${method.name}")
        }
    } as T
}
