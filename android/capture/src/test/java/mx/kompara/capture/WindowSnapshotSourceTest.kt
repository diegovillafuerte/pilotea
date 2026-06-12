package mx.kompara.capture

import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [WindowSnapshotSource] returns null when no live window is attached. The attached/read path is
 * exercised on device (instrumented, out of scope) since it needs a real `AccessibilityNodeInfo`.
 */
class WindowSnapshotSourceTest {

    @Test
    fun `read returns null when no root provider is attached`() {
        val source = WindowSnapshotSource(NodeFlattener())
        assertNull(source.read("com.ubercab.driver", 0L))
    }

    @Test
    fun `read returns null when no windows and after detach`() {
        val source = WindowSnapshotSource(NodeFlattener())
        // Provider that yields no window roots (e.g. service lost its windows).
        source.attach(WindowSnapshotSource.RootProvider { emptyList() })
        assertNull(source.read("com.ubercab.driver", 0L))

        source.detach()
        assertNull(source.read("com.ubercab.driver", 0L))
    }
}
