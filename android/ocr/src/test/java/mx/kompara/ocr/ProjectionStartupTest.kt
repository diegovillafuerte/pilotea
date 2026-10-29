package mx.kompara.ocr

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionStartupTest {
    private class Fixture {
        val startup = ProjectionStartup()
        val events = mutableListOf<String>()
        var owner: CapturePermit? = null
        var incidents = 0
        val ownership = CaptureOwnership { state, _, failed ->
            owner = state.owner
            if (failed) incidents++
        }
        var session: CaptureSession? = null
        var foreground = false
        var projection = false
        var reader = false
        var display = false
        var wired = false
        var failures = 0

        fun start(nullDisplay: Boolean = false, failAt: String? = null): Boolean = startup.start(
            prepare = {
                events += "foreground"
                if (failAt == "foreground") throw IllegalStateException()
                foreground = true
                events += "projection"
                if (failAt == "projection") throw IllegalStateException()
                projection = true
                events += "reader"
                if (failAt == "reader") throw IllegalStateException()
                reader = true
            },
            createDisplay = {
                events += "display"
                if (failAt == "display") throw IllegalStateException()
                if (nullDisplay) null else Any().also { display = true }
            },
            wireReader = {
                assertTrue(reader && display)
                assertNull(session)
                assertEquals(ProjectionStartupState.DISPLAY_READY, startup.state)
                events += "wire"
                if (failAt == "wire") throw IllegalStateException()
                wired = true
            },
            register = {
                assertTrue(reader && display && wired)
                assertEquals(ProjectionStartupState.WIRED, startup.state)
                events += "register"
                // The service checks consumer liveness under the ownership monitor here.
                if (failAt == "consumer") throw CancellationException()
                session = ownership.start(CaptureLane.PROJECTION) {}
            },
            failure = {
                assertEquals(ProjectionStartupState.FAILED, startup.state)
                failures++
                ownership.startupFailed()
                if (display) events += "release display"
                display = false
                if (reader) events += "close reader"
                reader = false
                if (projection) events += "stop projection"
                projection = false
                if (foreground) events += "remove foreground"
                foreground = false
            },
        )

        fun assertReleased() {
            assertFalse(foreground)
            assertFalse(projection)
            assertFalse(reader)
            assertFalse(display)
            assertNull(session)
            assertEquals(1, failures)
            assertEquals(ProjectionStartupState.FAILED, startup.state)
        }
    }

    @Test fun `display and reader wiring precede owner registration`() {
        val fixture = Fixture()

        assertTrue(fixture.start())

        assertEquals(listOf("foreground", "projection", "reader", "display", "wire", "register"), fixture.events)
        assertEquals(ProjectionStartupState.AVAILABLE, fixture.startup.state)
        assertNotNull(fixture.owner)
        assertEquals(fixture.session, fixture.owner?.session)
        assertEquals(0, fixture.failures)
        assertEquals(0, fixture.incidents)
    }

    @Test fun `null display closes the reader projection and foreground without registering`() {
        val fixture = Fixture()

        assertFalse(fixture.start(nullDisplay = true))

        fixture.assertReleased()
        assertEquals(listOf("foreground", "projection", "reader", "display", "close reader", "stop projection", "remove foreground"), fixture.events)
        assertNull(fixture.owner)
        assertEquals(1, fixture.incidents)
    }

    @Test fun `null display behind silent capture can never become its fallback`() {
        val fixture = Fixture()
        val silent = fixture.ownership.start(CaptureLane.SILENT) {}

        assertFalse(fixture.start(nullDisplay = true))

        fixture.assertReleased()
        assertEquals(silent, fixture.owner?.session)
        assertEquals(0, fixture.incidents)
        assertTrue(fixture.ownership.stop(silent, failed = true))
        assertNull(fixture.owner)
        assertEquals(1, fixture.incidents)
    }

    @Test fun `working display stays parked then becomes fallback after silent stand down`() {
        val fixture = Fixture()
        val silent = fixture.ownership.start(CaptureLane.SILENT) {}

        assertTrue(fixture.start())

        assertEquals(silent, fixture.owner?.session)
        assertNull(fixture.ownership.permit(checkNotNull(fixture.session)))
        assertTrue(fixture.ownership.stop(silent, failed = true))
        assertEquals(fixture.session, fixture.owner?.session)
        assertTrue(fixture.foreground && fixture.projection && fixture.reader && fixture.display && fixture.wired)
        assertEquals(0, fixture.incidents)
    }

    @Test fun `foreground failure never acquires capture resources or registers`() {
        val fixture = Fixture()
        assertFalse(fixture.start(failAt = "foreground"))
        fixture.assertReleased()
        assertEquals(listOf("foreground"), fixture.events)
        assertEquals(1, fixture.incidents)
    }

    @Test fun `projection acquisition failure removes foreground and reports an outage`() {
        val fixture = Fixture()
        assertFalse(fixture.start(failAt = "projection"))
        fixture.assertReleased()
        assertEquals(listOf("foreground", "projection", "remove foreground"), fixture.events)
        assertEquals(1, fixture.incidents)
    }

    @Test fun `reader setup failure releases the projection and foreground`() {
        val fixture = Fixture()
        assertFalse(fixture.start(failAt = "reader"))
        fixture.assertReleased()
        assertEquals(listOf("foreground", "projection", "reader", "stop projection", "remove foreground"), fixture.events)
        assertEquals(1, fixture.incidents)
    }

    @Test fun `display creation exception releases all acquired startup resources`() {
        val fixture = Fixture()
        assertFalse(fixture.start(failAt = "display"))
        fixture.assertReleased()
        assertEquals(listOf("foreground", "projection", "reader", "display", "close reader", "stop projection", "remove foreground"), fixture.events)
        assertEquals(1, fixture.incidents)
    }

    @Test fun `listener wiring failure releases the display before registering any owner`() {
        val fixture = Fixture()
        assertFalse(fixture.start(failAt = "wire"))
        fixture.assertReleased()
        assertEquals(listOf("foreground", "projection", "reader", "display", "wire", "release display", "close reader", "stop projection", "remove foreground"), fixture.events)
        assertEquals(1, fixture.incidents)
    }

    @Test fun `consumer cancellation before registration cannot leave an available fallback`() {
        val fixture = Fixture()
        val silent = fixture.ownership.start(CaptureLane.SILENT) {}
        assertFalse(fixture.start(failAt = "consumer"))
        fixture.assertReleased()
        assertEquals(silent, fixture.owner?.session)
        assertEquals(0, fixture.incidents)
        fixture.ownership.stop(silent)
        assertNull(fixture.owner)
    }
}
