package mx.kompara.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The owner trio a [androidx.compose.ui.platform.ComposeView] needs to run outside an Activity.
 *
 * Compose attaches its composition to the host View's `ViewTree*Owner`s. An Activity/Fragment
 * supplies these for free; a `Service` does not, so when we host a ComposeView in a
 * `TYPE_ACCESSIBILITY_OVERLAY` window we must provide our own [LifecycleOwner],
 * [SavedStateRegistryOwner], and [ViewModelStoreOwner]. This is the standard, documented pattern.
 *
 * Drive the lifecycle explicitly:
 *  - [onCreate] before attaching the view (CREATED, restores saved state),
 *  - [onResume] once attached (RESUMED → composition runs),
 *  - [onDestroy] on detach (DESTROYED → composition disposes, ViewModelStore cleared).
 *
 * Re-usable across service restarts: build a fresh instance per window attach so a torn-down
 * lifecycle is never resurrected (a DESTROYED [LifecycleRegistry] cannot move back up).
 */
class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** Move to CREATED and restore (empty) saved state. Call before attaching the ComposeView. */
    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /** Move to RESUMED so the composition actively renders. Call after the view is attached. */
    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * Tear down: DESTROYED disposes the composition; clearing the [ViewModelStore] releases any
     * ViewModels Compose created. Idempotent enough that a double call is harmless.
     */
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
