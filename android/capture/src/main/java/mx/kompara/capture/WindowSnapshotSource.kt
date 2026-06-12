package mx.kompara.capture

import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device [SnapshotReader] implementation.
 *
 * The service (constructed by the framework) registers a [RootProvider] when it connects; this
 * indirection lets Hilt build the [EventPipeline] as a singleton without holding a direct reference
 * to the framework-managed service. When the pipeline asks for a snapshot, this reads the current
 * `rootInActiveWindow` and hands it to [NodeFlattener].
 *
 * Read-only by construction: it only ever reads the tree and never calls `performAction`.
 */
@Singleton
class WindowSnapshotSource @Inject constructor(
    private val flattener: NodeFlattener,
) : SnapshotReader {

    /** Supplies the current active-window root; backed by the live service. */
    fun interface RootProvider {
        fun activeWindowRoot(): AccessibilityNodeInfo?
    }

    private val provider = AtomicReference<RootProvider?>(null)

    /** Called by the service in `onServiceConnected`. */
    fun attach(rootProvider: RootProvider) {
        provider.set(rootProvider)
    }

    /** Called by the service in `onDestroy` so we stop reading a dead window. */
    fun detach() {
        provider.set(null)
    }

    override fun read(packageName: String, timestampMs: Long): ScreenSnapshot? {
        val root = provider.get()?.activeWindowRoot() ?: return null
        return try {
            // The event's package and the active window can diverge: a target app firing a
            // background event while a DIFFERENT window — including Kompara's own UI — is active.
            // Trust the window's real package, and only snapshot when it's actually a target app.
            // Without this we'd read and mislabel our own simulator (which renders a mock offer
            // card) as a real DiDi/Uber offer. See the on-device fixture capture, 2026-06-10.
            val windowPackage = root.packageName?.toString()
            if (windowPackage == null || windowPackage !in KomparaAccessibilityService.TARGET_PACKAGES) {
                null
            } else {
                flattener.flattenInfo(windowPackage, timestampMs, root)
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }
}
