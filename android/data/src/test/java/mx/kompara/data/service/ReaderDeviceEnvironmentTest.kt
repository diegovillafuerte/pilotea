package mx.kompara.data.service

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import java.time.*

/** When the battery step is needed: restricted on any maker, or a Samsung (freezer measured 2026-11-10, Galaxy S25) without the exemption. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderDeviceEnvironmentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun exempt(value: Boolean) =
        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, value)
    private fun restricted(value: Boolean) =
        shadowOf(context.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(value)

    @Test fun `a background-restricted app needs the step on any maker, exempt or not`() {
        restricted(true); exempt(true)
        listOf("Google", "samsung", "Xiaomi").forEach {
            ShadowBuild.setManufacturer(it)
            assertTrue(it, ReaderDeviceEnvironment.batteryRestricted(context))
        }
    }
    @Test fun `a samsung without the exemption needs the step`() {
        ShadowBuild.setManufacturer("samsung"); exempt(false)
        assertTrue(ReaderDeviceEnvironment.batteryRestricted(context))
    }
    @Test fun `a samsung with the exemption does not`() {
        ShadowBuild.setManufacturer("samsung"); exempt(true)
        assertFalse(ReaderDeviceEnvironment.batteryRestricted(context))
    }
    @Test fun `another maker with a profile keeps the restricted-only rule until it is measured`() {
        ShadowBuild.setManufacturer("Xiaomi"); exempt(false)
        assertFalse(ReaderDeviceEnvironment.batteryRestricted(context))
    }
    @Test fun `a maker without a profile does not, exemption or not`() {
        ShadowBuild.setManufacturer("Google"); exempt(false)
        assertFalse(ReaderDeviceEnvironment.batteryRestricted(context))
    }
    @Test fun `on a samsung without the exemption a working reader asks for attention and the exemption clears it`() {
        ShadowBuild.setManufacturer("samsung"); exempt(false)
        val clock = Clock.fixed(Instant.parse("2026-11-10T14:00:00Z"), ZoneId.of("America/Mexico_City"))
        val repo = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) {}
        }, clock)
        // What ReaderReadinessMonitor does every second.
        fun observe() { repo.observeEnvironment(true, ReaderDeviceEnvironment.batteryRestricted(context)); repo.refresh() }
        repo.supportedApps(setOf(ReaderReadinessReducer.UBER)); repo.bound(); repo.captureRunning(true); observe()
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase); assertTrue(repo.state.value.batteryRestricted)
        repo.readOffer(ReaderReadinessReducer.UBER, "offer", clock.millis(), repo.captureToken())
        assertEquals(ReaderPhase.ATTENTION, repo.state.value.phase)
        assertTrue(repo.state.value.canRead); assertNull(repo.state.value.incident)
        exempt(true); observe()
        assertEquals(ReaderPhase.WORKING, repo.state.value.phase); assertFalse(repo.state.value.batteryRestricted)
    }
}
