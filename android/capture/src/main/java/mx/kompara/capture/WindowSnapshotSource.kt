package mx.kompara.capture

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-device [SnapshotReader] implementation.
 *
 * The service (constructed by the framework) registers a [RootProvider] when it connects; this
 * indirection lets Hilt build the [EventPipeline] as a singleton without holding a direct reference
 * to the framework-managed service.
 *
 * ## Why all windows, not just the active one
 * Ride-hailing offer cards (Uber/DiDi/inDrive) frequently render in a SEPARATE window that does not
 * hold input focus — `rootInActiveWindow` returns the map activity underneath, not the offer. So we
 * scan every window root the service exposes (`getWindows()`, enabled by
 * `flagRetrieveInteractiveWindows`), keep the ones whose package is a target app, and merge their
 * flattened nodes into one snapshot. The spec engine finds the offer anchors among them. Non-target
 * windows (including Kompara's own UI) are dropped, so we never read or mislabel our own screens.
 *
 * Read-only by construction: it only ever reads trees and never calls `performAction`.
 */
@Singleton
class WindowSnapshotSource @Inject constructor(
    private val flattener: NodeFlattener,
) : SnapshotReader {

    /** Supplies the current window roots; backed by the live service (`getWindows()` + fallback). */
    fun interface RootProvider {
        /** All currently-available window roots. Caller (this class) recycles them. */
        fun windowRoots(): List<AccessibilityNodeInfo>
    }

    private val provider = AtomicReference<RootProvider?>(null)

    /** Called by the service in `onServiceConnected`. */
    fun attach(rootProvider: RootProvider) {
        provider.set(rootProvider)
    }

    /** Called by the service in `onDestroy` so we stop reading dead windows. */
    fun detach() {
        provider.set(null)
    }

    override fun read(packageName: String, timestampMs: Long): ScreenSnapshot? {
        val roots = provider.get()?.windowRoots().orEmpty()
        if (roots.isEmpty()) return null
        val merged = ArrayList<SnapshotNode>()
        var targetPackage: String? = null
        try {
            for (root in roots) {
                val pkg = root.packageName?.toString() ?: continue
                if (pkg !in KomparaAccessibilityService.TARGET_PACKAGES) continue
                targetPackage = pkg
                merged.addAll(flattener.flattenInfo(pkg, timestampMs, root).nodes)
            }
        } finally {
            roots.forEach {
                @Suppress("DEPRECATION")
                it.recycle()
            }
        }
        // Diagnostic (debug only via logcat tag): what windows the service actually sees, and how
        // many nodes we kept from target apps. Remove once parser capture is validated on device.
        Log.d(
            TAG,
            "read pkgs=${roots.mapNotNull { it.packageName?.toString() }.distinct()} " +
                "target=$targetPackage nodes=${merged.size}",
        )
        if (targetPackage == null || merged.isEmpty()) return null
        return ScreenSnapshot(packageName = targetPackage, timestampMs = timestampMs, nodes = merged)
    }

    private companion object {
        const val TAG = "KomparaCapture"
    }
}
