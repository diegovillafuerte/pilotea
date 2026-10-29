package mx.kompara.data.service

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReaderReadinessTest {
    private class TestClock(var time: Instant = Instant.parse("2026-10-20T02:10:00Z"), private val zone: ZoneId = ZoneId.of("America/Mexico_City")) : Clock() {
        override fun getZone() = zone
        override fun withZone(zone: ZoneId): Clock = TestClock(time, zone)
        override fun instant() = time
    }
    private class Store(var memory: ReaderMemory = ReaderMemory()) : ReaderMemoryStore {
        override fun load() = memory
        override fun save(memory: ReaderMemory) { this.memory = memory }
    }
    private val clock = TestClock()
    private val store = Store()
    private var repo = ReaderReadinessRepository(store, clock)
    private val uber = ReaderReadinessReducer.UBER
    private val didi = ReaderReadinessReducer.DIDI
    private fun bind() { repo.supportedApps(setOf(uber, didi)); repo.bound(); repo.captureRunning(true) }
    private fun read(app: String = uber, session: String = "offer") = repo.readOffer(app, session, clock.millis(), repo.captureToken())

    @Test fun `grant without binding and binding without proof are prepared`() {
        repo.observeEnvironment(true, false)
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        assertFalse(repo.canRead())
        bind()
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        read()
        assertEquals(ReaderPhase.WORKING, repo.state.value.phase)
    }
    @Test fun `capability refusal before first frame shows stopped tab until fallback starts`() {
        repo.bound()
        assertFalse(repo.state.value.captureRunning)
        repo.captureFailed()
        assertEquals(ReaderIncidentCause.CAPTURE_STOPPED, repo.state.value.incident?.cause)
        assertTrue(repo.state.value.canShowStoppedTab)
        repo.captureRunning(true)
        assertNull(repo.state.value.incident)
        assertFalse(repo.state.value.canShowStoppedTab)
    }
    @Test fun `capability refusal does not stop a running fallback`() {
        bind()
        val token = repo.captureToken()
        repo.captureFailed()
        assertTrue(repo.state.value.captureRunning)
        assertNull(repo.state.value.incident)
        assertEquals(token, repo.captureToken())
    }
    @Test fun `midnight decays proof but retains yesterday time`() {
        bind(); read()
        val at = clock.millis()
        clock.time = Instant.parse("2026-10-20T06:00:00Z"); repo.refresh()
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        assertEquals(at, repo.state.value.apps[uber]?.lastReadAt)
        read() // even the same long-lived offer can establish today’s actual read
        assertEquals(ReaderPhase.WORKING, repo.state.value.phase)
    }
    @Test fun `rebind and process restart require new offer even on same date`() {
        bind(); read(); repo.bound()
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        repo = ReaderReadinessRepository(store, clock); bind()
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        assertNotNull(repo.state.value.apps[uber]?.lastReadAt)
        read(); assertEquals(ReaderPhase.WORKING, repo.state.value.phase)
    }
    @Test fun `only latest binding accepts asynchronous results`() {
        bind(); val token = repo.captureToken(); repo.bound()
        repo.readOffer(uber, "stale", clock.millis(), token)
        assertNull(repo.state.value.apps[uber]?.lastReadAt)
    }
    @Test fun `future evidence is never proof`() {
        bind(); repo.readOffer(uber, "future", clock.millis() + 1000, repo.captureToken())
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
    }
    @Test fun `two app proof and support are independent`() {
        bind(); read()
        assertTrue(repo.state.value.apps[uber]!!.verifiedToday)
        assertFalse(repo.state.value.apps[didi]!!.verifiedToday)
        repo.supportedApps(setOf(uber)); read(didi)
        assertFalse(repo.state.value.apps[didi]!!.supported)
        assertEquals(ReaderPhase.WORKING, repo.state.value.phase)
        assertNull(repo.state.value.incident)
    }
    @Test fun `no parser is prepared and never a permission outage`() {
        bind(); read(); repo.supportedApps(emptySet())
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        assertNull(repo.state.value.incident)
    }
    @Test fun `new repeated offer failures override success only for that app`() {
        bind(); read(); read(didi)
        repeat(3) { repo.failedOffer(uber, repo.captureToken()) }
        assertEquals(ReaderPhase.WORKING, repo.state.value.phase)
        repeat(3) { repo.failedOffer(didi, repo.captureToken()) }
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        read(); assertEquals(ReaderPhase.WORKING, repo.state.value.phase)
    }
    @Test fun `battery attention requires a real read and never claims cause of outage`() {
        bind(); repo.observeEnvironment(true, true)
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        read(); assertEquals(ReaderPhase.ATTENTION, repo.state.value.phase)
        repo.observeEnvironment(false, true)
        assertEquals(ReaderIncidentCause.PERMISSION_LOST, repo.state.value.incident?.cause)
    }
    @Test fun `intentional capture replacement drops pending frames without creating an outage`() {
        bind(); read(); val old = repo.captureToken()
        repo.captureRunning(false, intentionalStop = true)
        assertNull(repo.state.value.alertIncident)
        assertFalse(repo.canRead(uber, old))
        repo.captureRunning(true)
        assertEquals(ReaderPhase.WORKING, repo.state.value.phase)
    }
    @Test fun `C6 stopped capture is distinct from C13 permission loss`() {
        bind(); read(); repo.captureRunning(false)
        assertEquals(ReaderIncidentCause.CAPTURE_STOPPED, repo.state.value.incident?.cause)
        assertTrue(repo.state.value.canShowStoppedTab)
        repo.observeEnvironment(false, false)
        assertFalse(repo.state.value.canShowStoppedTab)
        assertFalse(repo.canRead())
        assertEquals(ReaderIncidentCause.PERMISSION_LOST, repo.state.value.incident?.cause)
    }
    @Test fun `permission restored before service binds cannot revive old proof`() {
        bind(); read(); repo.observeEnvironment(false, false); repo.observeEnvironment(true, false)
        assertEquals(ReaderPhase.PREPARED, repo.state.value.phase)
        assertFalse(repo.canRead())
        assertNull(repo.state.value.incident)
    }
    @Test fun `missing permission on first run has no historical outage`() {
        repo.observeEnvironment(false, false)
        assertEquals(ReaderPhase.OFF, repo.state.value.phase)
        assertNull(repo.state.value.incident)
    }
    @Test fun `disconnect with permission still granted records only observed cause`() {
        bind(); repo.disconnected(true)
        assertEquals(ReaderIncidentCause.SERVICE_DISCONNECTED, repo.state.value.incident?.cause)
        assertFalse(repo.state.value.canShowStoppedTab)
    }
    @Test fun `incident keeps first seen and delivery across next morning and restart`() {
        bind(); repo.observeEnvironment(false, false)
        val original = repo.state.value.incident!!
        repo.markNotified(original)
        clock.time = clock.time.plusSeconds(43000)
        repo = ReaderReadinessRepository(store, clock)
        repo.observeEnvironment(false, true)
        assertEquals(original.copy(notified = true), repo.state.value.incident)
        repo.bound(); assertNull(repo.state.value.incident)
        repo.observeEnvironment(false, false)
        assertFalse(repo.state.value.incident!!.notified)
        assertNotEquals(original.firstSeenAt, repo.state.value.incident!!.firstSeenAt)
    }
    @Test fun `stale notification completion cannot mark a different incident`() {
        bind(); repo.captureRunning(false)
        val old = repo.state.value.incident!!
        repo.observeEnvironment(false, false); repo.markNotified(old)
        assertFalse(repo.state.value.incident!!.notified)
    }
    @Test fun `pause persists until next local date not twenty four hours`() {
        bind(); read(); val token = repo.captureToken(); repo.pauseUntilTomorrow()
        assertEquals(LocalDate.parse("2026-10-20"), store.memory.pauseUntil)
        assertEquals(ReaderPhase.PAUSED, repo.state.value.phase)
        assertFalse(repo.canRead(uber, token))
        repo = ReaderReadinessRepository(store, clock); bind()
        assertFalse(repo.canRead())
        clock.time = Instant.parse("2026-10-20T05:59:59Z"); repo.refresh(); assertTrue(repo.state.value.paused)
        clock.time = clock.time.plusSeconds(1); repo.refresh(); assertFalse(repo.state.value.paused)
        assertTrue(repo.canRead())
    }
    @Test fun `pause suppresses alerts reads and C6 and explicit resume restores unresolved incident`() {
        bind(); repo.captureRunning(false); val incident = repo.state.value.incident!!
        repo.pauseUntilTomorrow(); read()
        assertNull(repo.state.value.apps[uber]?.lastReadAt)
        assertNull(repo.state.value.alertIncident)
        assertFalse(repo.state.value.canShowStoppedTab)
        repo.markNotified(incident); assertFalse(repo.state.value.incident!!.notified)
        repo.resume(); assertEquals(incident, repo.state.value.alertIncident)
    }
    @Test fun `no room counts are dated positive hidden during pause and survive restart`() {
        bind(); repo.suppressionCount(repo.state.value.date, -1)
        assertEquals(0, repo.state.value.noRoomCount)
        repo.suppressionCount(repo.state.value.date, 2)
        repo = ReaderReadinessRepository(store, clock); assertEquals(2, repo.state.value.noRoomCount)
        repo.pauseUntilTomorrow(); assertEquals(0, repo.state.value.noRoomCount)
        repo.resume(); assertEquals(2, repo.state.value.noRoomCount)
        clock.time = clock.time.plusSeconds(86400); repo.refresh(); assertEquals(0, repo.state.value.noRoomCount)
    }
    @Test fun `pause follows local date across DST rather than elapsed duration`() {
        val ny = TestClock(Instant.parse("2026-11-01T04:30:00Z"), ZoneId.of("America/New_York"))
        val local = ReaderReadinessRepository(Store(), ny); local.pauseUntilTomorrow()
        ny.time = Instant.parse("2026-11-02T04:59:59Z"); local.refresh(); assertTrue(local.state.value.paused)
        ny.time = ny.time.plusSeconds(1); local.refresh(); assertFalse(local.state.value.paused)
    }
}
