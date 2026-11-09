package mx.kompara.overlay

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.capture.HostGeometryEvidence
import mx.kompara.capture.HostWindowEvidence
import mx.kompara.overlay.RealGeometryFixtures.UBER
import mx.kompara.overlay.RealGeometryFixtures.display
import mx.kompara.overlay.RealGeometryFixtures.navigationBar
import mx.kompara.overlay.RealGeometryFixtures.statusBar
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

private const val S25 = "w360dp-h780dp-xxhdpi"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayControllerBannerTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `collector failure stops overlay without failing the service scope or logging content`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IllegalStateException("private offer content") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No writes")
        }
        val settings = SettingsRepository(store)
        val controller = OverlayController(OfferRecorder(dao<OfferDao>(), CostProfileRepository(dao<CostProfileDao>()), settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(dao<OfferPresentationDao>()), ReaderReadinessRepository(context))
        val serviceScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        fun field(name: String) = OverlayController::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            controller.start(serviceScope, emptyFlow(), context, flowOf(null))
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull("Failed component must release its context and jobs", field("serviceScope").get(controller))
            assertNull(field("overlayContext").get(controller))
            assertTrue(serviceScope.isActive)
            var siblingRan = false
            serviceScope.launch { siblingRan = true }
            runCurrent()
            assertTrue(siblingRan)
            val messages = ShadowLog.getLogsForTag("OverlayController").map { it.msg }
            assertTrue(messages.any { it == "overlay stopped after IllegalStateException" })
            assertFalse(messages.any { "private offer content" in it })
        } finally {
            controller.stop()
            serviceScope.cancel()
        }
    }

    @Test fun `stopped tab attaches from idle host evidence with no previous offer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No writes")
        }
        val settings = SettingsRepository(store)
        val controller = OverlayController(OfferRecorder(dao<OfferDao>(), CostProfileRepository(dao<CostProfileDao>()), settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(dao<OfferPresentationDao>()), ReaderReadinessRepository(context))
        fun field(name: String) = OverlayController::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("overlayContext").set(controller, context)
        val rect = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
        val bounds = HostRect(rect.left, rect.top, rect.right, rect.bottom)
        val host = "com.ubercab.driver"
        HostGeometryEvidence.publish(HostWindowEvidence(host, System.currentTimeMillis(), bounds, 1f,
            bounds.copy(bottom = bounds.top + 24), emptyList()))
        try {
            assertNull(field("event").get(controller))
            OverlayController::class.java.getDeclaredMethod("attachBanner", String::class.java)
                .apply { isAccessible = true }.invoke(controller, host)
            assertNotNull(field("bannerView").get(controller))
            assertTrue((field("bannerGeometry").get(controller) as HostGeometry).isIdleHost)
            val view = field("bannerView").get(controller) as ComposeView
            val flags = (view.layoutParams as android.view.WindowManager.LayoutParams).flags
            assertTrue(flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        } finally {
            controller.stop()
            HostGeometryEvidence.publish(null)
        }
    }

    @Test @Config(qualifiers = S25)
    fun `after a stall the stopped tab attaches above the last offer's card`() {
        val g = tabGeometryAfterStall(offerAgeMs = 10_700)
        assertNotNull("The reader-down tab must reach the screen", g)
        assertFalse(g!!.isIdleHost)
        assertEquals(stalledOffer.offerCard, g.offerCard)
        assertShownAt(OverlayPosition(324, 315), g)
    }

    @Test @Config(qualifiers = S25)
    fun `past the parse ceiling the stopped tab uses the idle geometry`() {
        val g = tabGeometryAfterStall(offerAgeMs = 15_001)!!
        assertTrue(g.isIdleHost)
        assertShownAt(OverlayPosition(324, 1932), g)
    }

    @Test @Config(qualifiers = S25)
    fun `a stale idle observation attaches no tab`() {
        for (age in listOf(1_001L, 2_501L))
            assertNull("Idle observation aged $age ms", tabGeometryAfterStall(offerAgeMs = 10_700, idleAgeMs = age))
    }

    @Test fun `switching host removes its old tab before new geometry admission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                error("The window hand-off must not write settings")
        }
        val settings = SettingsRepository(store)
        val costs = CostProfileRepository(dao<CostProfileDao>())
        val controller = OverlayController(OfferRecorder(dao<OfferDao>(), costs, settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(dao<OfferPresentationDao>()),
            ReaderReadinessRepository(context))
        val oldView = ComposeView(context)
        val oldOwner = OverlayLifecycleOwner().apply { onCreate(); onResume() }
        fun field(name: String) = OverlayController::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("overlayContext").set(controller, context)
        field("bannerView").set(controller, oldView)
        field("bannerOwner").set(controller, oldOwner)
        field("bannerHost").set(controller, "com.ubercab.driver")
        val attach = OverlayController::class.java.getDeclaredMethod("attachBanner", String::class.java)
            .apply { isAccessible = true }

        val rect = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
        val bounds = HostRect(rect.left, rect.top, rect.right, rect.bottom)
        HostGeometryEvidence.publish(HostWindowEvidence("com.ubercab.driver", System.currentTimeMillis(), bounds, 1f,
            bounds.copy(bottom = bounds.top + 24), emptyList()))
        attach.invoke(controller, "com.ubercab.driver")
        assertSame("Repeated evidence for one host must not recreate its window", oldView, field("bannerView").get(controller))
        assertEquals(Lifecycle.State.RESUMED, oldOwner.lifecycle.currentState)

        // No DiDi geometry exists. The old Uber window must disappear even though no new tab fits.
        attach.invoke(controller, "com.didiglobal.driver")
        assertNull(field("bannerView").get(controller))
        assertNull(field("bannerHost").get(controller))
        assertNull(field("bannerOwner").get(controller))
        assertEquals(Lifecycle.State.DESTROYED, oldOwner.lifecycle.currentState)
        HostGeometryEvidence.publish(null)
    }

    // S25 numbers of StoppedTabAfterStallTest.
    private val s25Viewport = OverlayViewport(display, 3f, HostOrientation.PORTRAIT, OverlayInsets(0, 91, 0, 144))
    private val tabSize = ChipSize(720, 216)
    private val chrome = HostRect(0, 0, 1080, 1)
    private val controls = listOf(statusBar, navigationBar)
    private val stalledOffer = HostGeometry(UBER, "derived-ocr_text_bounds", 0, display, HostOrientation.PORTRAIT, chrome,
        HostRect(0, 579, 1080, 2340), controls, emptyList(), source = HostGeometrySource.OCR_TEXT_BOUNDS)

    private fun assertShownAt(position: OverlayPosition, g: HostGeometry) = assertEquals(StoppedTabWindowDecision.Show,
        stoppedTabWindowDecision(g, UBER, s25Viewport, tabSize, position, position, layoutPending = false))

    /** Runs the real attachBanner; returns the attached tab's geometry, or null when no tab attached. */
    @Suppress("UNCHECKED_CAST")
    private fun tabGeometryAfterStall(offerAgeMs: Long, idleAgeMs: Long = 0): HostGeometry? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No writes")
        }
        val settings = SettingsRepository(store)
        val controller = OverlayController(OfferRecorder(dao<OfferDao>(), CostProfileRepository(dao<CostProfileDao>()), settings, NetProfitEngine()),
            settings, OverlayPrefs(store), PresentationRecorder(dao<OfferPresentationDao>()), ReaderReadinessRepository(context))
        fun field(name: String) = OverlayController::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("overlayContext").set(controller, context)
        val rect = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
        assertEquals(display, HostRect(rect.left, rect.top, rect.right, rect.bottom))
        val now = System.currentTimeMillis()
        (field("geometry\$delegate").get(controller) as MutableState<HostGeometry?>).value =
            stalledOffer.copy(capturedAtMs = now - offerAgeMs)
        HostGeometryEvidence.publish(HostWindowEvidence(UBER, now - idleAgeMs, display, 3f, chrome, emptyList(),
            protectedControls = controls))
        try {
            OverlayController::class.java.getDeclaredMethod("attachBanner", String::class.java)
                .apply { isAccessible = true }.invoke(controller, UBER)
            assertEquals(field("bannerGeometry").get(controller) != null, field("bannerView").get(controller) != null)
            return field("bannerGeometry").get(controller) as HostGeometry?
        } finally {
            controller.stop()
            HostGeometryEvidence.publish(null)
        }
    }

    /** This window-only test fails if a hand-off starts doing capture, accounting or persistence. */
    private inline fun <reified T> dao(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ ->
        when (method.name) {
            "observe" -> flowOf(null)
            "observeVersions" -> flowOf(emptyList<mx.kompara.data.db.entity.CostVersionEntity>())
            else -> error("Unexpected DAO call: ${method.name}")
        }
    } as T
}
