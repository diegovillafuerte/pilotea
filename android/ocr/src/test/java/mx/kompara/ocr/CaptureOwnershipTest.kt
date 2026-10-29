package mx.kompara.ocr

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureOwnershipTest {
    private data class Publication(val state: CaptureOwnerState, val intentional: Boolean, val failed: Boolean)

    private class Harness {
        val publications = mutableListOf<Publication>()
        val ownership = CaptureOwnership { state, intentional, failed ->
            publications += Publication(state, intentional, failed)
        }
        val latest: Publication get() = publications.last()
    }

    @Test fun `silent takes ownership only after closing the projection pipeline`() {
        val events = mutableListOf<String>()
        val ownership = CaptureOwnership { state, _, _ -> events += "owner=${state.owner?.session?.lane}" }
        val projection = ownership.start(CaptureLane.PROJECTION) { events += "close projection" }
        val projectionPermit = requireNotNull(ownership.permit(projection))
        val silent = ownership.start(CaptureLane.SILENT) { events += "close silent" }
        val silentPermit = requireNotNull(ownership.permit(silent))

        assertEquals(listOf("owner=PROJECTION", "close projection", "owner=SILENT"), events)
        assertNull(ownership.permit(projection))
        assertFalse(ownership.withOwner(projectionPermit) { error("parked projection published") })
        assertTrue(ownership.withOwner(silentPermit) { events += "silent frame" })
    }

    @Test fun `projection arriving while silent is live stays parked`() {
        val harness = Harness()
        var silentCloses = 0
        val silent = harness.ownership.start(CaptureLane.SILENT) { silentCloses++ }
        val permit = requireNotNull(harness.ownership.permit(silent))
        val projection = harness.ownership.start(CaptureLane.PROJECTION) {}

        assertEquals(permit, harness.ownership.permit(silent))
        assertNull(harness.ownership.permit(projection))
        assertEquals(0, silentCloses)
        assertEquals(CaptureLane.SILENT, harness.latest.state.owner?.session?.lane)
        assertFalse(harness.latest.failed)
    }

    @Test fun `projection stopping while silent is live cannot clear running or close silent`() {
        for (failed in listOf(false, true)) {
            val harness = Harness()
            val projection = harness.ownership.start(CaptureLane.PROJECTION) {}
            var silentCloses = 0
            val silent = harness.ownership.start(CaptureLane.SILENT) { silentCloses++ }
            val permit = requireNotNull(harness.ownership.permit(silent))

            assertTrue(harness.ownership.stop(projection, failed = failed))

            assertEquals(permit, harness.latest.state.owner)
            assertEquals(permit, harness.ownership.permit(silent))
            assertEquals(0, silentCloses)
            assertFalse(harness.latest.failed)
            assertFalse(harness.latest.state.silentFailed)
            assertTrue(harness.publications.all { it.state.owner != null })
        }
    }

    @Test fun `silent stopping or standing down resumes the preserved projection without false running`() {
        for (failed in listOf(false, true)) {
            val harness = Harness()
            val projection = harness.ownership.start(CaptureLane.PROJECTION) {}
            val oldProjectionPermit = requireNotNull(harness.ownership.permit(projection))
            var silentCloses = 0
            val silent = harness.ownership.start(CaptureLane.SILENT) { silentCloses++ }
            val silentPermit = requireNotNull(harness.ownership.permit(silent))

            assertTrue(harness.ownership.stop(silent, failed = failed))

            val resumed = requireNotNull(harness.ownership.permit(projection))
            assertEquals(projection, resumed.session)
            assertNotEquals(oldProjectionPermit, resumed)
            assertEquals(resumed, harness.latest.state.owner)
            assertEquals(1, silentCloses)
            assertEquals(failed, harness.latest.state.silentFailed)
            assertFalse(harness.latest.failed)
            assertTrue(harness.publications.all { it.state.owner != null })
            assertFalse(harness.ownership.withOwner(silentPermit) { error("retired silent loop published") })
            assertFalse(harness.ownership.withOwner(oldProjectionPermit) { error("pre-handoff projection frame published") })
            assertTrue(harness.ownership.withOwner(resumed) {})
        }
    }

    @Test fun `NoCapability or DISABLE from a replaced silent generation cannot stand down its successor`() {
        for (reason in listOf("NoCapability", "DISABLE")) {
            val harness = Harness()
            var oldCloses = 0
            val old = harness.ownership.start(CaptureLane.SILENT) { oldCloses++ }
            val oldPermit = requireNotNull(harness.ownership.permit(old))
            var newCloses = 0
            val replacement = harness.ownership.start(CaptureLane.SILENT) { newCloses++ }
            val newPermit = requireNotNull(harness.ownership.permit(replacement))
            val publicationsBefore = harness.publications.toList()

            assertFalse(reason, harness.ownership.stop(old, failed = true))

            assertEquals(publicationsBefore, harness.publications)
            assertEquals(newPermit, harness.ownership.permit(replacement))
            assertEquals(1, oldCloses)
            assertEquals(0, newCloses)
            assertFalse(harness.latest.state.silentFailed)
            assertFalse(harness.ownership.withOwner(oldPermit) { error("stale generation published") })
            assertTrue(harness.ownership.withOwner(newPermit) {})
        }
    }

    @Test fun `stop then start is immune to the old loop finally or cancellation cleanup`() {
        val harness = Harness()
        val old = harness.ownership.start(CaptureLane.SILENT) {}
        assertTrue(harness.ownership.stop(old))
        val replacement = harness.ownership.start(CaptureLane.SILENT) {}
        val permit = requireNotNull(harness.ownership.permit(replacement))
        val count = harness.publications.size

        assertFalse(harness.ownership.stop(old, failed = true))
        assertFalse(harness.ownership.stop(old))

        assertEquals(count, harness.publications.size)
        assertEquals(permit, harness.latest.state.owner)
        assertFalse(harness.latest.state.silentFailed)
    }

    @Test fun `replaced parked projection cannot stop the projection resumed after silent failure`() {
        val harness = Harness()
        val oldProjection = harness.ownership.start(CaptureLane.PROJECTION) {}
        val silent = harness.ownership.start(CaptureLane.SILENT) {}
        val replacement = harness.ownership.start(CaptureLane.PROJECTION) {}
        harness.ownership.stop(silent, failed = true)
        val permit = requireNotNull(harness.ownership.permit(replacement))
        val count = harness.publications.size

        assertFalse(harness.ownership.stop(oldProjection, failed = true))

        assertEquals(count, harness.publications.size)
        assertEquals(permit, harness.latest.state.owner)
    }

    @Test fun `missing silent capability preserves a live projection and its readiness`() {
        val harness = Harness()
        val projection = harness.ownership.start(CaptureLane.PROJECTION) {}
        val permit = requireNotNull(harness.ownership.permit(projection))

        harness.ownership.silentUnavailable()

        assertEquals(permit, harness.latest.state.owner)
        assertTrue(harness.latest.state.silentFailed)
        assertFalse(harness.latest.failed)
    }

    @Test fun `startup refusal cannot mark an already live silent session failed`() {
        val harness = Harness()
        val silent = harness.ownership.start(CaptureLane.SILENT) {}
        val permit = requireNotNull(harness.ownership.permit(silent))
        val count = harness.publications.size

        harness.ownership.silentUnavailable()

        assertEquals(count, harness.publications.size)
        assertEquals(permit, harness.latest.state.owner)
        assertFalse(harness.latest.state.silentFailed)
    }

    @Test fun `unexpected owning cancellation clears active state and records failure only without fallback`() {
        val harness = Harness()
        var closes = 0
        val silent = harness.ownership.start(CaptureLane.SILENT) { closes++ }

        assertTrue(harness.ownership.stop(silent, failed = true))

        assertNull(harness.latest.state.owner)
        assertNull(harness.ownership.permit(silent))
        assertTrue(harness.latest.state.silentFailed)
        assertTrue(harness.latest.failed)
        assertEquals(1, closes)
    }

    @Test fun `intentional last-owner stop is published once without an incident`() {
        val harness = Harness()
        val projection = harness.ownership.start(CaptureLane.PROJECTION) {}

        assertTrue(harness.ownership.stop(projection, intentional = true))
        val count = harness.publications.size
        assertFalse(harness.ownership.stop(projection, failed = true))

        assertNull(harness.latest.state.owner)
        assertTrue(harness.latest.intentional)
        assertFalse(harness.latest.failed)
        assertEquals(count, harness.publications.size)
    }

    @Test fun `handoff waits for the current owner publication before closing and granting replacement`() {
        val events = mutableListOf<String>()
        val ownership = CaptureOwnership { state, _, _ -> events += "owner=${state.owner?.session?.lane}" }
        val projection = ownership.start(CaptureLane.PROJECTION) { events += "close projection" }
        val permit = requireNotNull(ownership.permit(projection))
        val publishing = CountDownLatch(1)
        val handoffAttempted = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val frame = workers.submit<Boolean> {
                ownership.withOwner(permit) {
                    events += "frame begin"
                    publishing.countDown()
                    assertTrue(handoffAttempted.await(5, TimeUnit.SECONDS))
                    events += "frame end"
                }
            }
            val handoff = workers.submit<CaptureSession> {
                assertTrue(publishing.await(5, TimeUnit.SECONDS))
                handoffAttempted.countDown()
                ownership.start(CaptureLane.SILENT) { events += "close silent" }
            }

            assertTrue(frame.get(5, TimeUnit.SECONDS))
            val silent = handoff.get(5, TimeUnit.SECONDS)
            assertNotNull(ownership.permit(silent))
            assertEquals(listOf("owner=PROJECTION", "frame begin", "frame end", "close projection", "owner=SILENT"), events)
            assertFalse(ownership.withOwner(permit) { error("stale frame published after handoff") })
        } finally {
            workers.shutdownNow()
        }
    }

    @Test fun `concurrent stale stand-down cannot interleave with serialized stop and replacement start`() {
        val harness = Harness()
        val old = harness.ownership.start(CaptureLane.SILENT) {}
        val replacementStarted = CountDownLatch(1)
        val staleAttempted = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val replacement = workers.submit<CaptureSession> {
                harness.ownership.serialized {
                    harness.ownership.stop(old)
                    val next = harness.ownership.start(CaptureLane.SILENT) {}
                    replacementStarted.countDown()
                    assertTrue(staleAttempted.await(5, TimeUnit.SECONDS))
                    next
                }
            }
            val stale = workers.submit<Boolean> {
                assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
                staleAttempted.countDown()
                harness.ownership.stop(old, failed = true)
            }

            val current = replacement.get(5, TimeUnit.SECONDS)
            assertFalse(stale.get(5, TimeUnit.SECONDS))
            assertEquals(current, harness.latest.state.owner?.session)
            assertFalse(harness.latest.state.silentFailed)
            assertFalse(harness.latest.failed)
        } finally {
            workers.shutdownNow()
        }
    }

    @Test fun `startup failure waits for concurrent registration before deciding outage`() {
        val harness = Harness()
        val registrationStarted = CountDownLatch(1)
        val failureAttempted = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val registration = workers.submit<CaptureSession> {
                harness.ownership.serialized {
                    registrationStarted.countDown()
                    assertTrue(failureAttempted.await(5, TimeUnit.SECONDS))
                    harness.ownership.start(CaptureLane.PROJECTION) {}
                }
            }
            val failure = workers.submit {
                assertTrue(registrationStarted.await(5, TimeUnit.SECONDS))
                failureAttempted.countDown()
                harness.ownership.startupFailed()
            }

            val projection = registration.get(5, TimeUnit.SECONDS)
            failure.get(5, TimeUnit.SECONDS)

            assertEquals(projection, harness.latest.state.owner?.session)
            assertEquals(2, harness.publications.size)
            assertTrue(harness.publications.all { it.state.owner?.session == projection && !it.failed })
        } finally {
            workers.shutdownNow()
        }
    }
}
