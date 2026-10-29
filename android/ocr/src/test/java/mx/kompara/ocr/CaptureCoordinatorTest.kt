package mx.kompara.ocr

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mx.kompara.data.service.ReaderIncidentCause
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderPhase
import mx.kompara.data.service.ReaderReadinessReducer
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenReaderState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptureCoordinatorTest {
    private class Store : ReaderMemoryStore {
        private var memory = ReaderMemory()
        override fun load() = memory
        override fun save(memory: ReaderMemory) { this.memory = memory }
    }

    private val clock = Clock.fixed(Instant.parse("2026-10-29T18:00:00Z"), ZoneId.of("America/Mexico_City"))
    private val readiness = ReaderReadinessRepository(Store(), clock).apply {
        bound()
        supportedApps(ReaderReadinessReducer.apps)
    }
    private val ownership = CaptureCoordinator(readiness).ownership

    @Before fun resetLiveState() {
        ScreenReaderState.setRunning(false)
        ScreenReaderState.setSilentLaneActive(false)
        ScreenReaderState.setSilentLaneFailed(false)
    }

    @After fun clearLiveState() = resetLiveState()

    private fun recordWorkingRead() {
        readiness.readOffer(ReaderReadinessReducer.UBER, "test-session", clock.millis(), readiness.captureToken())
        assertEquals(ReaderPhase.WORKING, readiness.state.value.phase)
    }

    @Test fun `projection-only reconnect republishes retained owner without another frame`() {
        var closes = 0
        val projection = ownership.start(CaptureLane.PROJECTION) { closes++ }
        val permit = requireNotNull(ownership.permit(projection))
        recordWorkingRead()
        val oldToken = readiness.captureToken()

        readiness.disconnected(permission = false)
        readiness.bound()
        assertFalse(readiness.state.value.captureRunning)
        assertTrue(ScreenReaderState.running.value)
        val reboundToken = readiness.captureToken()

        // The unconditional bind publication runs even on API 26–29, before lane availability.
        ownership.reaffirm()

        assertEquals(permit, ownership.permit(projection))
        assertEquals(0, closes)
        assertTrue(readiness.state.value.captureRunning)
        assertTrue(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertFalse(ScreenReaderState.silentLaneFailed.value)
        assertTrue(readiness.canRead(ReaderReadinessReducer.UBER))
        assertFalse(readiness.canRead(token = oldToken))
        assertEquals(reboundToken, readiness.captureToken())
        assertEquals(ReaderPhase.PREPARED, readiness.state.value.phase)
        assertFalse(readiness.state.value.apps.getValue(ReaderReadinessReducer.UBER).verifiedToday)
        assertNull(readiness.state.value.incident)
    }

    @Test fun `reconnect after silent stand-down republishes the retained fallback`() {
        val projection = ownership.start(CaptureLane.PROJECTION) {}
        val silent = ownership.start(CaptureLane.SILENT) {}
        ownership.stop(silent, failed = true)
        val permit = requireNotNull(ownership.permit(projection))

        readiness.disconnected(permission = false)
        readiness.bound()
        ownership.reaffirm()

        assertEquals(permit, ownership.permit(projection))
        assertTrue(readiness.state.value.captureRunning)
        assertTrue(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertTrue(ScreenReaderState.silentLaneFailed.value)
        assertNull(readiness.state.value.incident)
    }

    @Test fun `reconnect with missing screenshot capability retains projection readiness`() {
        val projection = ownership.start(CaptureLane.PROJECTION) {}
        val permit = requireNotNull(ownership.permit(projection))

        readiness.disconnected(permission = false)
        readiness.bound()
        ownership.reaffirm()
        ownership.silentUnavailable()

        assertEquals(permit, ownership.permit(projection))
        assertTrue(readiness.state.value.captureRunning)
        assertTrue(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertTrue(ScreenReaderState.silentLaneFailed.value)
        assertNull(readiness.state.value.incident)
    }

    @Test fun `bind without an owner clears stale live flags and keeps manual retry available`() {
        ScreenReaderState.setRunning(true)
        ScreenReaderState.setSilentLaneActive(true)
        readiness.bound()

        ownership.reaffirm()

        assertFalse(readiness.state.value.captureRunning)
        assertFalse(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertFalse(ScreenReaderState.silentLaneFailed.value)
        assertTrue(readiness.state.value.canRead)
        assertNull(readiness.state.value.incident)
    }

    @Test fun `failure before registration reports a stopped banner and watchdog incident`() {
        assertEquals(ReaderPhase.PREPARED, readiness.state.value.phase)

        ownership.startupFailed()

        assertFalse(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertFalse(ScreenReaderState.silentLaneFailed.value)
        assertFalse(readiness.state.value.captureRunning)
        assertEquals(ReaderPhase.OFF, readiness.state.value.phase)
        assertTrue(readiness.state.value.canShowStoppedTab)
        assertEquals(ReaderIncidentCause.CAPTURE_STOPPED, readiness.state.value.incident?.cause)
        assertEquals(readiness.state.value.incident, readiness.state.value.alertIncident)
        assertEquals(false, readiness.state.value.alertIncident?.notified)
    }

    @Test fun `failure before registration preserves a surviving silent owner`() {
        assertStartupFailurePreserves(CaptureLane.SILENT)
    }

    @Test fun `failure before registration preserves a surviving projection owner`() {
        assertStartupFailurePreserves(CaptureLane.PROJECTION)
    }

    private fun assertStartupFailurePreserves(lane: CaptureLane) {
        var closes = 0
        val session = ownership.start(lane) { closes++ }
        val permit = requireNotNull(ownership.permit(session))
        recordWorkingRead()
        val before = readiness.state.value
        val token = readiness.captureToken()

        ownership.startupFailed()

        assertEquals(permit, ownership.permit(session))
        assertEquals(0, closes)
        assertTrue(ScreenReaderState.running.value)
        assertEquals(lane == CaptureLane.SILENT, ScreenReaderState.silentLaneActive.value)
        assertFalse(ScreenReaderState.silentLaneFailed.value)
        assertEquals(before, readiness.state.value)
        assertEquals(token, readiness.captureToken())
        assertFalse(readiness.state.value.canShowStoppedTab)
        assertNull(readiness.state.value.alertIncident)
    }

    @Test fun `parked projection stop preserves silent running flags readiness and verified read`() {
        val projection = ownership.start(CaptureLane.PROJECTION) {}
        val silent = ownership.start(CaptureLane.SILENT) {}
        recordWorkingRead()
        val before = readiness.state.value
        val token = readiness.captureToken()

        assertTrue(ownership.stop(projection, failed = true))

        assertNotNull(ownership.permit(silent))
        assertTrue(ScreenReaderState.running.value)
        assertTrue(ScreenReaderState.silentLaneActive.value)
        assertFalse(ScreenReaderState.silentLaneFailed.value)
        assertTrue(readiness.state.value.captureRunning)
        assertEquals(before, readiness.state.value)
        assertEquals(token, readiness.captureToken())
        assertNull(readiness.state.value.incident)
    }

    @Test fun `silent failure resumes the retained projection without a false stopped incident`() {
        val projection = ownership.start(CaptureLane.PROJECTION) {}
        val silent = ownership.start(CaptureLane.SILENT) {}
        recordWorkingRead()
        val before = readiness.state.value
        val token = readiness.captureToken()

        assertTrue(ownership.stop(silent, failed = true))

        assertNotNull(ownership.permit(projection))
        assertTrue(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertTrue(ScreenReaderState.silentLaneFailed.value)
        assertTrue(readiness.state.value.captureRunning)
        assertEquals(before, readiness.state.value)
        assertEquals(token, readiness.captureToken())
        assertNull(readiness.state.value.incident)
    }

    @Test fun `stale generation failure cannot alter replacement active failure or readiness state`() {
        val old = ownership.start(CaptureLane.SILENT) {}
        ownership.stop(old)
        val replacement = ownership.start(CaptureLane.SILENT) {}
        recordWorkingRead()
        val before = readiness.state.value
        val token = readiness.captureToken()

        assertFalse(ownership.stop(old, failed = true))

        assertNotNull(ownership.permit(replacement))
        assertTrue(ScreenReaderState.running.value)
        assertTrue(ScreenReaderState.silentLaneActive.value)
        assertFalse(ScreenReaderState.silentLaneFailed.value)
        assertEquals(before, readiness.state.value)
        assertEquals(token, readiness.captureToken())
        assertTrue(readiness.state.value.captureRunning)
        assertNull(readiness.state.value.incident)
    }

    @Test fun `missing capability with a running projection preserves the actual readiness state`() {
        val projection = ownership.start(CaptureLane.PROJECTION) {}
        recordWorkingRead()
        val before = readiness.state.value
        val token = readiness.captureToken()

        ownership.silentUnavailable()

        assertNotNull(ownership.permit(projection))
        assertTrue(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertTrue(ScreenReaderState.silentLaneFailed.value)
        assertEquals(before, readiness.state.value)
        assertEquals(token, readiness.captureToken())
        assertTrue(readiness.state.value.captureRunning)
        assertNull(readiness.state.value.incident)
    }

    @Test fun `unexpected continuation cancellation while job remains active releases its owning state`() = runBlocking {
        var closes = 0
        val silent = ownership.start(CaptureLane.SILENT) { closes++ }
        var activeAtCancellation = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitScreenshotResult<Unit>(discard = {}, failureResult = { throw AssertionError() }) {
                    it.cancel(CancellationException())
                }
                throw AssertionError("cancelled screenshot must not be delivered")
            } catch (cancellation: CancellationException) {
                activeAtCancellation = currentCoroutineContext().isActive
                if (activeAtCancellation) ownership.stop(silent, failed = true)
                throw cancellation
            } finally {
                ownership.stop(silent)
            }
        }
        job.invokeOnCompletion { ownership.stop(silent) }
        job.join()

        assertTrue(activeAtCancellation)
        assertTrue(job.isCancelled)
        assertTrue(currentCoroutineContext().isActive)
        assertEquals(1, closes)
        assertNull(ownership.permit(silent))
        assertFalse(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertTrue(ScreenReaderState.silentLaneFailed.value)
        assertFalse(readiness.state.value.captureRunning)
        assertEquals(ReaderIncidentCause.CAPTURE_STOPPED, readiness.state.value.incident?.cause)
    }

    @Test fun `unexpected continuation cancellation preserves a live projection fallback`() = runBlocking {
        val projection = ownership.start(CaptureLane.PROJECTION) {}
        val silent = ownership.start(CaptureLane.SILENT) {}
        recordWorkingRead()
        val before = readiness.state.value
        val token = readiness.captureToken()
        var activeAtCancellation = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitScreenshotResult<Unit>(discard = {}, failureResult = { throw AssertionError() }) {
                    throw CancellationException()
                }
                throw AssertionError("cancelled screenshot must not be delivered")
            } catch (cancellation: CancellationException) {
                activeAtCancellation = currentCoroutineContext().isActive
                if (activeAtCancellation) ownership.stop(silent, failed = true)
                throw cancellation
            } finally {
                ownership.stop(silent)
            }
        }
        job.invokeOnCompletion { ownership.stop(silent) }
        job.join()

        assertTrue(activeAtCancellation)
        assertTrue(job.isCancelled)
        assertNotNull(ownership.permit(projection))
        assertTrue(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertTrue(ScreenReaderState.silentLaneFailed.value)
        assertEquals(before, readiness.state.value)
        assertEquals(token, readiness.captureToken())
        assertTrue(readiness.state.value.captureRunning)
        assertNull(readiness.state.value.incident)
    }

    @Test fun `scope cancelled before lazy body starts still releases ownership through completion`() = runBlocking {
        var closes = 0
        val silent = ownership.start(CaptureLane.SILENT) { closes++ }
        val parent = Job().apply { cancel() }
        val scope = CoroutineScope(coroutineContext + parent)
        var bodyRan = false
        val job = scope.launch(start = CoroutineStart.LAZY) { bodyRan = true }
        job.invokeOnCompletion { ownership.stop(silent) }

        assertFalse(job.start())
        job.join()

        assertFalse(bodyRan)
        assertTrue(job.isCancelled)
        assertEquals(1, closes)
        assertNull(ownership.permit(silent))
        assertFalse(ScreenReaderState.running.value)
        assertFalse(ScreenReaderState.silentLaneActive.value)
        assertFalse(ScreenReaderState.silentLaneFailed.value)
        assertFalse(readiness.state.value.captureRunning)
    }
}
