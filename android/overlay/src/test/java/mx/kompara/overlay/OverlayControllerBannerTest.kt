package mx.kompara.overlay

import android.content.Context
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.flowOf
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayControllerBannerTest {
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

        attach.invoke(controller, "com.ubercab.driver")
        assertSame("Repeated evidence for one host must not recreate its window", oldView, field("bannerView").get(controller))
        assertEquals(Lifecycle.State.RESUMED, oldOwner.lifecycle.currentState)

        // No DiDi geometry exists. The old Uber window must disappear even though no new tab fits.
        attach.invoke(controller, "com.didiglobal.driver")
        assertNull(field("bannerView").get(controller))
        assertNull(field("bannerHost").get(controller))
        assertNull(field("bannerOwner").get(controller))
        assertEquals(Lifecycle.State.DESTROYED, oldOwner.lifecycle.currentState)
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
