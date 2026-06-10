package mx.kompara.capture

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject

/**
 * Flattens an [AccessibilityNode] tree into an immutable [ScreenSnapshot] via pre-order traversal.
 *
 * The traversal is written against the [AccessibilityNode] abstraction (not `AccessibilityNodeInfo`
 * directly) so the ordering/depth/recycling logic is unit-testable with in-memory fakes. Use
 * [flattenInfo] on device to wrap a real root `AccessibilityNodeInfo`.
 *
 * Recycling: every child node obtained during traversal is recycled after its subtree is visited.
 * The caller-supplied [root] is NOT recycled here — ownership of the root stays with the caller
 * (the service typically gets it from `event.source`/`rootInActiveWindow`, which has its own
 * lifecycle).
 */
class NodeFlattener @Inject constructor() {

    /** Flatten an abstract [root] node captured at [timestampMs] for [packageName]. */
    fun flatten(
        packageName: String,
        timestampMs: Long,
        root: AccessibilityNode?,
    ): ScreenSnapshot {
        val nodes = ArrayList<SnapshotNode>()
        if (root != null) {
            visit(root, depth = 0, index = 0, out = nodes)
        }
        return ScreenSnapshot(packageName = packageName, timestampMs = timestampMs, nodes = nodes)
    }

    /**
     * Device entry point: wrap a real [AccessibilityNodeInfo] root and flatten it. The [root] is
     * adapted but not recycled (its handle belongs to the caller).
     */
    fun flattenInfo(
        packageName: String,
        timestampMs: Long,
        root: AccessibilityNodeInfo?,
    ): ScreenSnapshot =
        flatten(packageName, timestampMs, root?.let(::InfoAdapter))

    private fun visit(node: AccessibilityNode, depth: Int, index: Int, out: MutableList<SnapshotNode>) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        out.add(
            SnapshotNode(
                text = node.text,
                viewId = node.viewId,
                className = node.className,
                bounds = rect,
                depth = depth,
                index = index,
            ),
        )

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                visit(child, depth = depth + 1, index = i, out = out)
            } finally {
                // Recycle every obtained child once its subtree is fully visited so we never leak
                // framework handles. No-op for fakes.
                child.recycle()
            }
        }
    }

    /** Adapts a framework [AccessibilityNodeInfo] to [AccessibilityNode]. */
    private class InfoAdapter(private val info: AccessibilityNodeInfo) : AccessibilityNode {
        override val text: String?
            get() = info.text?.toString()

        override val viewId: String?
            get() = info.viewIdResourceName

        override val className: String?
            get() = info.className?.toString()

        override fun getBoundsInScreen(outRect: Rect) {
            info.getBoundsInScreen(outRect)
        }

        override val childCount: Int
            get() = info.childCount

        override fun getChild(index: Int): AccessibilityNode? =
            info.getChild(index)?.let(::InfoAdapter)

        @Suppress("DEPRECATION")
        override fun recycle() {
            // recycle() is deprecated/no-op on newer API levels but harmless and correct on the
            // minSdk=26 floor we still support.
            info.recycle()
        }
    }
}
