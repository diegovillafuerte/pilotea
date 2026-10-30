package mx.kompara.data.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundHostProbeTest {
    private val owners = mutableListOf<Any>()
    private fun owner() = Any().also(owners::add)

    @After fun clear() {
        owners.forEach(ScreenReaderState::detachForegroundProbe)
        ScreenReaderState.setForegroundHost(null, 0)
    }

    @Test fun `live OS switch overrides a still fresh cached foreground package`() {
        ScreenReaderState.setForegroundHost("com.ubercab.driver", 1_000)
        var live: HostForegroundObservation = HostForegroundObservation.Target("com.didiglobal.driver")
        ScreenReaderState.attachForegroundProbe(owner()) { live }
        assertEquals("com.ubercab.driver", ScreenReaderState.freshForegroundHost(1_001, 1_500))
        assertEquals(HostForegroundObservation.Target("com.didiglobal.driver"), ScreenReaderState.liveForegroundObservation())
        live = HostForegroundObservation.NonHost
        assertEquals(HostForegroundObservation.NonHost, ScreenReaderState.liveForegroundObservation())
    }

    @Test fun `no probe or detached service never authorizes capture from cached state`() {
        ScreenReaderState.setForegroundHost("com.ubercab.driver", 1_000)
        val owner = owner()
        ScreenReaderState.attachForegroundProbe(owner) { HostForegroundObservation.Target("com.ubercab.driver") }
        ScreenReaderState.detachForegroundProbe(owner)
        assertEquals(HostForegroundObservation.Unknown, ScreenReaderState.liveForegroundObservation())
    }

    @Test fun `old service teardown cannot detach its successor live query`() {
        val old = owner()
        val current = owner()
        ScreenReaderState.attachForegroundProbe(old) { HostForegroundObservation.Target("com.ubercab.driver") }
        ScreenReaderState.attachForegroundProbe(current) { HostForegroundObservation.Target("com.didiglobal.driver") }
        ScreenReaderState.detachForegroundProbe(old)
        assertEquals(HostForegroundObservation.Target("com.didiglobal.driver"), ScreenReaderState.liveForegroundObservation())
    }

    @Test fun `old query completing after service replacement is unknown`() {
        val current = owner()
        ScreenReaderState.attachForegroundProbe(owner()) {
            ScreenReaderState.attachForegroundProbe(current) { HostForegroundObservation.Target("com.didiglobal.driver") }
            HostForegroundObservation.Target("com.ubercab.driver")
        }
        assertEquals(HostForegroundObservation.Unknown, ScreenReaderState.liveForegroundObservation())
        assertEquals(HostForegroundObservation.Target("com.didiglobal.driver"), ScreenReaderState.liveForegroundObservation())
    }

    @Test fun `query failure is uncertain and never cached host authorization`() {
        ScreenReaderState.setForegroundHost("com.ubercab.driver", 1_000)
        ScreenReaderState.attachForegroundProbe(owner()) { throw SecurityException() }
        assertEquals(HostForegroundObservation.Unknown, ScreenReaderState.liveForegroundObservation())
    }
}
