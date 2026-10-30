package mx.kompara.capture

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import mx.kompara.data.service.HostForegroundObservation
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenReaderState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock

/** Exercise the real accessibility callback with OS window roots, including background events. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForegroundHostProvenanceTest {
    private val uber = KomparaAccessibilityService.UBER_DRIVER_PACKAGE
    private val didi = KomparaAccessibilityService.DIDI_DRIVER_PACKAGE

    @After fun clearHost() { ScreenReaderState.setForegroundHost(null, SystemClock.elapsedRealtime()) }

    private fun event(rootPackage: String?, eventPackage: String, interactive: Boolean = true): String? {
        // Attach a context without connecting the service or starting capture/Hilt collaborators.
        val service = Robolectric.buildService(KomparaAccessibilityService::class.java).get()
        service.readiness = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) = Unit
        }, Clock.systemUTC())
        val root = rootPackage?.let { pkg -> AccessibilityNodeInfo.obtain().apply { packageName = pkg } }
        shadowOf(service).setRootInActiveWindow(root)
        shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(interactive)
        service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED).apply {
            packageName = eventPackage
        })
        return ScreenReaderState.freshForegroundHost(SystemClock.elapsedRealtime(), 1_500)
    }

    @Test fun `background DiDi event cannot override active Uber root`() {
        assertEquals(uber, event(uber, didi))
    }

    @Test fun `background Uber event cannot override active DiDi root`() {
        assertEquals(didi, event(didi, uber))
    }

    @Test fun `target event cannot authorize a nonhost foreground screen`() {
        assertNull(event("com.example.navigation", uber))
    }

    @Test fun `unavailable root is unknown even with a target event`() {
        assertNull(event(null, uber))
    }

    @Test fun `screen off cannot be revived by a target event`() {
        assertNull(event(uber, uber, interactive = false))
        assertEquals(HostForegroundObservation.NonHost,
            ScreenReaderState.freshForegroundObservation(SystemClock.elapsedRealtime(), 1_500))
    }

    @Test fun `unknown root and known other app remain distinct for capture lifetime`() {
        event(null, uber)
        assertEquals(HostForegroundObservation.Unknown,
            ScreenReaderState.freshForegroundObservation(SystemClock.elapsedRealtime(), 1_500))
        event("com.example.navigation", uber)
        assertEquals(HostForegroundObservation.NonHost,
            ScreenReaderState.freshForegroundObservation(SystemClock.elapsedRealtime(), 1_500))
    }
}
