package mx.kompara.capture

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Traversal order, depth, index and node recycling for [NodeFlattener], driven entirely by
 * in-memory [AccessibilityNode] fakes. Runs under Robolectric only because [SnapshotNode.bounds]
 * uses a real [android.graphics.Rect]; the flattener logic itself never touches a framework type.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NodeFlattenerTest {

    /** An in-memory accessibility node; records whether it was recycled. */
    private class FakeNode(
        override val text: String? = null,
        override val viewId: String? = null,
        override val className: String? = null,
        private val rect: Rect = Rect(0, 0, 0, 0),
        private val children: List<FakeNode> = emptyList(),
    ) : AccessibilityNode {
        var recycled = false
            private set

        override fun getBoundsInScreen(outRect: Rect) {
            outRect.set(rect)
        }

        override val childCount: Int get() = children.size
        override fun getChild(index: Int): AccessibilityNode? = children.getOrNull(index)
        override fun recycle() {
            recycled = true
        }
    }

    private val flattener = NodeFlattener()

    @Test
    fun `flattens in pre-order with correct depth and sibling index`() {
        // root
        //  ├─ a
        //  │   └─ a1
        //  └─ b
        val a1 = FakeNode(text = "a1")
        val a = FakeNode(text = "a", children = listOf(a1))
        val b = FakeNode(text = "b")
        val root = FakeNode(text = "root", viewId = "com.ubercab.driver:id/root", children = listOf(a, b))

        val snapshot = flattener.flatten("com.ubercab.driver", timestampMs = 42L, root = root)

        assertEquals("com.ubercab.driver", snapshot.packageName)
        assertEquals(42L, snapshot.timestampMs)

        // Pre-order: root, a, a1, b
        assertEquals(listOf("root", "a", "a1", "b"), snapshot.nodes.map { it.text })
        assertEquals(listOf(0, 1, 2, 1), snapshot.nodes.map { it.depth })
        // index = position among siblings: root=0, a=0, a1=0, b=1
        assertEquals(listOf(0, 0, 0, 1), snapshot.nodes.map { it.index })

        assertEquals("com.ubercab.driver:id/root", snapshot.nodes.first().viewId)
    }

    @Test
    fun `captures bounds per node`() {
        val child = FakeNode(text = "fare", rect = Rect(10, 20, 110, 70))
        val root = FakeNode(text = "card", rect = Rect(0, 0, 1080, 600), children = listOf(child))

        val nodes = flattener.flatten("com.sdu.didi.gsui", 0L, root).nodes

        assertEquals(Rect(0, 0, 1080, 600), nodes[0].bounds)
        assertEquals(Rect(10, 20, 110, 70), nodes[1].bounds)
    }

    @Test
    fun `recycles every obtained child but leaves the caller-owned root alone`() {
        val child = FakeNode(text = "c")
        val grandchild = FakeNode(text = "gc")
        val mid = FakeNode(text = "m", children = listOf(grandchild))
        val root = FakeNode(text = "r", children = listOf(child, mid))

        flattener.flatten("com.ubercab.driver", 0L, root)

        assertTrue("child recycled", child.recycled)
        assertTrue("mid recycled", mid.recycled)
        assertTrue("grandchild recycled", grandchild.recycled)
        assertTrue("root NOT recycled (owned by caller)", !root.recycled)
    }

    @Test
    fun `null root yields an empty snapshot`() {
        val snapshot = flattener.flatten("com.ubercab.driver", 7L, root = null)
        assertTrue(snapshot.nodes.isEmpty())
        assertEquals(7L, snapshot.timestampMs)
    }
}
