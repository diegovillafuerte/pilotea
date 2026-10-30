package mx.kompara.capture

import mx.kompara.parsers.model.HostRect
import org.junit.Assert.*
import org.junit.Test

class HostWindowOwnershipTest {
    private val host = "com.ubercab.driver"
    private val display = HostRect(0, 0, 1080, 2340)
    private val hostWindow = VisibleHostWindow(7, host, display, 1, true)
    private fun own(vararg windows: VisibleHostWindow) =
        HostWindowOwnership.fromWindows(host, 1_000, display, 7, windows.toList())

    @Test fun `OS selected lower split pane exclusively owns its complete contained pixels`() {
        val lower = hostWindow.copy(bounds = HostRect(0, 1170, 1080, 2340))
        val upper = VisibleHostWindow(8, "com.didiglobal.driver", HostRect(0, 0, 1080, 1170), 2, true)
        val scope = own(lower, upper)!!
        assertFalse(scope.owns(HostRect(83, 486, 543, 542)))
        assertTrue(scope.owns(HostRect(100, 1800, 900, 1900)))
        assertFalse("Cross-pane OCR blocks cannot be split into safe text", scope.owns(HostRect(10, 1150, 700, 1220)))
    }

    @Test fun `PiP notification and accessibility overlay pixels are excluded even without readable roots`() {
        val obstacles = listOf(
            VisibleHostWindow(8, "example.video", HostRect(700, 100, 1080, 500), 5, true, true),
            VisibleHostWindow(9, null, HostRect(0, 600, 1080, 800), 6, false),
            VisibleHostWindow(10, "mx.kompara", HostRect(300, 900, 1040, 1200), 7, false),
        )
        val scope = own(hostWindow, *obstacles.toTypedArray())!!
        obstacles.forEach { assertFalse(scope.owns(requireNotNull(it.bounds))) }
        assertTrue(scope.owns(HostRect(20, 1300, 600, 1400)))
        assertFalse(scope.owns(HostRect(20, 790, 600, 840)))
    }

    @Test fun `any visible window with unavailable bounds withholds attribution`() {
        assertNull(own(hostWindow.copy(bounds = null)))
        assertNull(own(hostWindow, VisibleHostWindow(8, null, null, 5, false)))
        assertNull(own(hostWindow, VisibleHostWindow(8, "other", HostRect(0, 0, 0, 0), 0, true)))
        assertNull(own())
    }

    @Test fun `ambiguous equal layer overlap cannot establish ownership`() {
        assertNull(own(hostWindow, VisibleHostWindow(8, "other", HostRect(0, 0, 600, 600), 1, true)))
        assertNull(own(hostWindow, hostWindow))
    }

    @Test fun `selected window must match OS host and be a normal application window`() {
        assertNull(own(hostWindow.copy(id = 8)))
        assertNull(own(hostWindow.copy(packageName = "other")))
        assertNull(own(hostWindow.copy(packageName = null)))
        assertNull(own(hostWindow.copy(application = false)))
        assertNull(own(hostWindow.copy(pictureInPicture = true)))
    }

    @Test fun `ownership is valid only for its capture host time and display`() {
        val scope = own(hostWindow)!!
        assertTrue(scope.validFor(host, 1_000, display))
        assertTrue(scope.validFor(host, 2_000, display))
        assertFalse(scope.validFor(host, 999, display))
        assertFalse(scope.validFor(host, 2_001, display))
        assertFalse(scope.validFor("other", 1_000, display))
        assertFalse(scope.validFor(host, 1_000, HostRect(0, 0, 2340, 1080)))
    }

    @Test fun `window movement or overlay arrival during recognition invalidates captured pixel ownership`() {
        val before = own(hostWindow)!!
        assertTrue(before.samePixels(before.copy(capturedAtMs = 1_900)))
        assertFalse(before.samePixels(own(hostWindow.copy(bounds = HostRect(0, 1170, 1080, 2340)))))
        assertFalse(before.samePixels(own(hostWindow, VisibleHostWindow(8, null, HostRect(0, 0, 1080, 400), 5, false))))
        assertFalse(before.samePixels(null))
        assertFalse(before.samePixels(before.copy(selectedWindowId = 9)))
    }

    @Test fun `window order does not invalidate an unchanged ownership observation`() {
        val a = VisibleHostWindow(8, null, HostRect(0, 0, 1080, 100), 5, false)
        val b = VisibleHostWindow(9, null, HostRect(0, 2200, 1080, 2340), 6, false)
        assertTrue(own(hostWindow, a, b)!!.samePixels(own(b, hostWindow, a)))
    }

    @Test fun `only our identified accessibility overlay is exempt from placement exclusion`() {
        val chip = HostRect(324, 221, 1044, 557)
        val own = VisibleHostWindow(8, "mx.kompara", chip, 5, false, accessibilityOverlay = true)
        fun scope(window: VisibleHostWindow) = HostWindowOwnership.fromWindows(host, 1_000,
            display, 7, listOf(hostWindow, window), ownOverlayPackage = "mx.kompara")!!
        assertFalse(scope(own).owns(chip))
        assertTrue(scope(own).placementExcludedBounds.isEmpty())
        for (foreign in listOf(own.copy(packageName = "other"), own.copy(packageName = null),
            own.copy(accessibilityOverlay = false))) {
            assertFalse(scope(foreign).owns(chip))
            assertEquals(listOf(chip), scope(foreign).placementExcludedBounds)
        }
    }
}
