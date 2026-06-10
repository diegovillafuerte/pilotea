package mx.kompara.capture

import android.graphics.Rect

/**
 * A minimal, recyclable view over an accessibility node that hides `AccessibilityNodeInfo` behind
 * an interface.
 *
 * The real adapter (in [NodeFlattener]) wraps `AccessibilityNodeInfo`; unit tests supply in-memory
 * fakes. This is the seam that keeps [NodeFlattener]'s traversal logic unit-testable without an
 * emulator while still recycling framework handles correctly on device.
 */
interface AccessibilityNode {
    val text: String?
    val viewId: String?
    val className: String?

    /** Writes this node's screen bounds into [outRect]. */
    fun getBoundsInScreen(outRect: Rect)

    val childCount: Int

    /**
     * Returns the child at [index], or null if it can't be obtained. Callers own the returned
     * node and must [recycle] it (the real implementation obtains a fresh framework handle).
     */
    fun getChild(index: Int): AccessibilityNode?

    /** Releases any framework resources held by this node. No-op for fakes. */
    fun recycle()
}
