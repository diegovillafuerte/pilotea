package mx.kompara.capture

import android.graphics.Rect

/**
 * An immutable, framework-free representation of a host app's window at a moment in time.
 *
 * Produced by [NodeFlattener] from an `AccessibilityNodeInfo` tree and consumed by the `:parsers`
 * layer. Decoupling the snapshot from the live accessibility tree is what lets parsing run off the
 * main thread without worrying about node recycling or stale framework handles.
 */
data class ScreenSnapshot(
    val packageName: String,
    val timestampMs: Long,
    val nodes: List<SnapshotNode>,
)

/**
 * A single node in a flattened, pre-order traversal of an accessibility tree.
 *
 * Field names here are a contract with the parallel `:parsers` task — do not rename them.
 *
 * @param text visible/content text of the node, if any
 * @param viewId the resource id name (e.g. `com.ubercab.driver:id/fare`), if any
 * @param className the node's `className` (e.g. `android.widget.TextView`), if any
 * @param bounds the node's bounds in screen coordinates
 * @param depth distance from the root (root is 0)
 * @param index position of this node among its siblings
 */
data class SnapshotNode(
    val text: String?,
    val viewId: String?,
    val className: String?,
    val bounds: Rect,
    val depth: Int,
    val index: Int,
)
