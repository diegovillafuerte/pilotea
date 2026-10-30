package mx.kompara.ocr

import java.io.Closeable
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

class ProjectionLifecycleTest {
    private class MutableClock : Clock() {
        var now: Instant = Instant.parse("2026-10-29T18:00:00Z")
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("America/Mexico_City")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }

    private class Store : ReaderMemoryStore {
        var memory = ReaderMemory()
        override fun load(): ReaderMemory = memory
        override fun save(memory: ReaderMemory) { this.memory = memory }
    }

    private class Fixture : Closeable {
        val clock = MutableClock()
        val store = Store()
        val readiness = ReaderReadinessRepository(store, clock).apply {
            bound()
            supportedApps(ReaderReadinessReducer.apps)
        }
        val ownership = CaptureCoordinator(readiness).ownership
        val lifecycle = ProjectionLifecycle()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val channel = Channel<Unit>(Channel.CONFLATED)
        var session: CaptureSession? = ownership.start(CaptureLane.PROJECTION) {}
        var failures = 0
        var completedWithActiveScope = false
        var deferFailure = false
        private var queuedFailure = false
        val consumer = scope.launch {
            for (frame in channel) Unit
        }.also { job ->
            job.invokeOnCompletion {
                completedWithActiveScope = scope.isActive
                if (scope.isActive) requestFailure()
            }
        }

        fun requestFailure() {
            if (!ownership.serialized { lifecycle.fail() }) return
            failures++
            scope.cancel()
            queuedFailure = true
            if (!deferFailure) publishFailure()
        }

        fun publishFailure() {
            check(queuedFailure)
            queuedFailure = false
            if (lifecycle.isIntentionalStop) return
            ownership.serialized {
                val current = session
                if (current == null) ownership.startupFailed()
                else ownership.stop(current, failed = true)
                session = null
            }
        }

        fun releaseIntentionally() {
            ownership.serialized {
                session?.let { ownership.stop(it, intentional = true) }
                session = null
            }
        }

        fun destroyWithCompletionRace(notificationStop: Boolean = false) {
            assertTrue(scope.isActive)
            ownership.serialized { lifecycle.stopIntentionally() }
            if (notificationStop) readiness.pauseUntilTomorrow()
            releaseIntentionally()
            // Force the old failure window deterministically: the consumer completes before its
            // supervisor is cancelled, after captureSession has already been cleared.
            channel.cancel()
            assertTrue(consumer.isCompleted)
            assertTrue(completedWithActiveScope)
            assertTrue(scope.isActive)
            scope.cancel()
        }

        fun assertNoIncident() {
            assertEquals(0, failures)
            assertNull(store.memory.incident)
            assertNull(readiness.state.value.incident)
            assertNull(readiness.state.value.alertIncident)
            assertFalse(readiness.state.value.canShowStoppedTab)
            assertFalse(readiness.state.value.captureRunning)
            assertFalse(ScreenReaderState.running.value)
        }

        fun assertFailure() {
            assertEquals(1, failures)
            assertEquals(ReaderIncidentCause.CAPTURE_STOPPED, store.memory.incident?.cause)
            assertEquals(store.memory.incident, readiness.state.value.incident)
            assertEquals(store.memory.incident, readiness.state.value.alertIncident)
            assertTrue(readiness.state.value.canShowStoppedTab)
            assertFalse(readiness.state.value.captureRunning)
            assertFalse(ScreenReaderState.running.value)
        }

        override fun close() {
            scope.cancel()
            channel.cancel()
        }
    }

    @Before fun resetLiveState() {
        ScreenReaderState.setRunning(false)
        ScreenReaderState.setSilentLaneActive(false)
        ScreenReaderState.setSilentLaneFailed(false)
    }

    @After fun clearLiveState() = resetLiveState()

    @Test fun `intentional destruction cannot publish a failure after the session is cleared`() {
        Fixture().use { fixture ->
            fixture.destroyWithCompletionRace()

            assertTrue(fixture.lifecycle.isIntentionalStop)
            assertFalse(fixture.lifecycle.isRunning)
            fixture.assertNoIncident()
            fixture.readiness.resume()
            fixture.assertNoIncident()
        }
    }

    @Test fun `resuming a notification stop cannot expose a latent stopped incident`() {
        Fixture().use { fixture ->
            fixture.destroyWithCompletionRace(notificationStop = true)
            assertTrue(fixture.readiness.state.value.paused)
            fixture.assertNoIncident()

            fixture.readiness.resume()

            assertFalse(fixture.readiness.state.value.paused)
            fixture.assertNoIncident()
        }
    }

    @Test fun `notification pause expiry cannot expose a latent stopped incident`() {
        Fixture().use { fixture ->
            fixture.destroyWithCompletionRace(notificationStop = true)
            assertTrue(fixture.readiness.state.value.paused)
            fixture.assertNoIncident()

            fixture.clock.now = fixture.clock.now.plusSeconds(86_400)
            fixture.readiness.refresh()

            assertFalse(fixture.readiness.state.value.paused)
            fixture.assertNoIncident()
        }
    }

    @Test fun `unexpected consumer cancellation with an active supervisor still reports once`() {
        Fixture().use { fixture ->
            fixture.channel.cancel()
            fixture.requestFailure()

            assertTrue(fixture.completedWithActiveScope)
            assertTrue(fixture.consumer.isCancelled)
            assertFalse(fixture.lifecycle.isIntentionalStop)
            fixture.assertFailure()
        }
    }

    @Test fun `unexpected normal consumer completion with an active supervisor still reports once`() {
        Fixture().use { fixture ->
            fixture.channel.close()
            fixture.requestFailure()

            assertTrue(fixture.completedWithActiveScope)
            assertTrue(fixture.consumer.isCompleted)
            assertFalse(fixture.consumer.isCancelled)
            fixture.assertFailure()
        }
    }

    @Test fun `intentional cleanup does not discard an already accepted queued real failure`() {
        Fixture().use { fixture ->
            fixture.deferFailure = true
            fixture.channel.cancel()
            assertEquals(1, fixture.failures)
            assertNull(fixture.store.memory.incident)

            fixture.ownership.serialized { fixture.lifecycle.stopIntentionally() }
            fixture.releaseIntentionally()
            assertNull(fixture.session)
            assertFalse(fixture.lifecycle.isIntentionalStop)
            fixture.publishFailure()
            fixture.requestFailure()

            fixture.assertFailure()
        }
    }
}
