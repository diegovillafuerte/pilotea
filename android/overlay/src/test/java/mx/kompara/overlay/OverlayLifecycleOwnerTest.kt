package mx.kompara.overlay

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The manual owner trio must walk CREATED -> RESUMED -> DESTROYED so a ComposeView hosted in the
 * service composes and then disposes cleanly. Runs under Robolectric for the real Lifecycle/
 * SavedState machinery (they touch the main Looper).
 */
@RunWith(AndroidJUnit4::class)
class OverlayLifecycleOwnerTest {

    @Test
    fun `walks through the lifecycle states`() {
        val owner = OverlayLifecycleOwner()
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        owner.onCreate()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.onResume()
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        owner.onDestroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun `saved state registry is restored after onCreate`() {
        val owner = OverlayLifecycleOwner()
        owner.onCreate()
        // performRestore(null) must have run so the registry is usable (no IllegalStateException).
        owner.savedStateRegistry.consumeRestoredStateForKey("any")
    }

    @Test
    fun `provides a viewmodel store`() {
        val owner = OverlayLifecycleOwner()
        // Just exercising the accessor; clearing on destroy must not throw.
        owner.viewModelStore
        owner.onCreate()
        owner.onDestroy()
    }
}
