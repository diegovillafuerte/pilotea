package mx.kompara.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.kompara.data.service.ReaderIncidentCause
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessReducer
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenReaderState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilentLaneStallTest {
    private class Store : ReaderMemoryStore {
        private var memory = ReaderMemory()
        override fun load() = memory
        override fun save(memory: ReaderMemory) { this.memory = memory }
    }

    /** The evidence from the live process: the host is in front and takeScreenshot never answers. */
    class Host : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
        override fun getRootInActiveWindow(): AccessibilityNodeInfo =
            AccessibilityNodeInfo().apply { packageName = ReaderReadinessReducer.UBER }
        override fun takeScreenshot(displayId: Int, executor: Executor, callback: TakeScreenshotCallback) = Unit
    }

    private val clock = Clock.fixed(Instant.parse("2026-11-09T18:50:45Z"), ZoneId.of("America/Mexico_City"))
    private val readiness = ReaderReadinessRepository(Store(), clock).apply {
        bound()
        supportedApps(ReaderReadinessReducer.apps)
    }
    private val coordinator = CaptureCoordinator(readiness)
    private val service = Robolectric.buildService(Host::class.java).create().get()
    private val frames = mutableListOf<Bitmap>()
    private fun frame(): Bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888).also(frames::add)

    @Before fun reset() { ScreenReaderState.setRunning(false); ScreenReaderState.setSilentLaneActive(false); ScreenReaderState.setSilentLaneFailed(false) }
    @After fun clear() = reset()

    private fun TestScope.run(recognize: suspend (Bitmap) -> List<OcrBlock>?,
        shoot: (suspend () -> SilentScreenshotLane.ShotResult)? = null) =
        run {
            val lane = SilentScreenshotLane(readiness, coordinator, recognize)
            val session = coordinator.ownership.start(CaptureLane.SILENT) {}
            val pipeline = OfferFramePipeline(readiness)
            backgroundScope.launch {
                if (shoot == null) lane.captureLoop(service, session, pipeline)
                else lane.captureLoop(service, session, pipeline, shoot)
            }
        }

    private fun assertDown() {
        assertEquals(ReaderIncidentCause.CAPTURE_STOPPED, readiness.state.value.incident?.cause)
        assertTrue(readiness.state.value.canShowStoppedTab)
        assertTrue(ScreenReaderState.silentLaneFailed.value)
        assertFalse(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertFalse(readiness.state.value.captureRunning)
    }

    private fun assertRunning() {
        assertNull(readiness.state.value.incident)
        assertTrue(readiness.state.value.captureRunning)
        assertTrue(ScreenReaderState.running.value)
    }

    @Test fun `a screenshot callback that never arrives reaches reader-down at ten seconds`() = runTest {
        val loop = run(recognize = { throw AssertionError("no frame exists") })
        advanceTimeBy(9_999); runCurrent()
        assertRunning()
        advanceTimeBy(1); runCurrent()
        assertDown()
        assertTrue(loop.isCompleted)
    }

    @Test fun `ok screenshots whose OCR times out reach reader-down and keep their frames`() = runTest {
        val loop = run(recognize = { null }, shoot = { SilentScreenshotLane.ShotResult.Ok(frame()) })
        advanceTimeBy(3_499); runCurrent()
        assertRunning()
        advanceTimeBy(1); runCurrent()
        assertDown()
        assertTrue(loop.isCompleted)
        assertEquals(5, frames.size)
        assertTrue(frames.none { it.isRecycled })
    }

    @Test fun `a recognized frame clears the run so four lost callbacks never reach reader-down`() = runTest {
        var attempt = 0
        val loop = run(recognize = { emptyList() }, shoot = {
            if (attempt++ % 5 == 4) SilentScreenshotLane.ShotResult.Ok(frame()) else SilentScreenshotLane.ShotResult.TimedOut
        })
        advanceTimeBy(7_000); runCurrent()
        assertRunning()
        assertTrue(loop.isActive)
        assertEquals(55, attempt)
        assertTrue(frames.all { it.isRecycled })
    }

    /** A new readiness generation while the screenshot is out: the token taken for this frame no longer matches. */
    private fun staleFrame(): SilentScreenshotLane.ShotResult {
        readiness.resume()
        return SilentScreenshotLane.ShotResult.Ok(frame())
    }

    @Test fun `frames skipped as stale never add to the failure run`() = runTest {
        var attempt = 0
        val loop = run(recognize = { throw AssertionError("a skipped frame never reaches OCR") },
            shoot = { attempt++; staleFrame() })
        advanceTimeBy(7_000); runCurrent()
        assertRunning()
        assertTrue(loop.isActive)
        assertEquals(11, attempt)
        assertTrue(frames.all { it.isRecycled })
    }

    @Test fun `a frame skipped as stale does not clear the failure run`() = runTest {
        var attempt = 0
        val loop = run(recognize = { throw AssertionError("a skipped frame never reaches OCR") }, shoot = {
            if (attempt++ == 4) staleFrame() else SilentScreenshotLane.ShotResult.TimedOut
        })
        advanceTimeBy(699); runCurrent()
        assertRunning()
        advanceTimeBy(1); runCurrent()
        assertDown()
        assertTrue(loop.isCompleted)
        assertEquals(6, attempt)
        assertTrue(frames.single().isRecycled)
    }

    @Test fun `repeated OCR errors free their frames and reach reader-down`() = runTest {
        run(recognize = { throw IllegalArgumentException("x") }, shoot = { SilentScreenshotLane.ShotResult.Ok(frame()) })
        advanceTimeBy(3_500); runCurrent()
        assertDown()
        assertEquals(5, frames.size)
        assertTrue(frames.all { it.isRecycled })
    }
}
