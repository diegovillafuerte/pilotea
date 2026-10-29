package mx.kompara.capture

import mx.kompara.data.service.*
import mx.kompara.parsers.spec.LoadedSpecs
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock

class ServiceStateRepositoryTest {
    private fun reader() = ReaderReadinessRepository(object : ReaderMemoryStore {
        override fun load() = ReaderMemory()
        override fun save(memory: ReaderMemory) = Unit
    }, Clock.systemUTC())
    @Test fun `starts disconnected`() { assertFalse(ServiceStateRepository(reader()).connected.value) }
    @Test fun `binding means prepared and disconnect retains observed permission cause`() {
        val repo = ServiceStateRepository(reader())
        repo.setConnected(true)
        assertTrue(repo.connected.value)
        assertEquals(ReaderPhase.PREPARED, repo.readiness.value.phase)
        repo.setConnected(false, false)
        assertFalse(repo.connected.value)
        assertEquals(ReaderIncidentCause.PERMISSION_LOST, repo.readiness.value.incident?.cause)
    }
    @Test fun `OCR support honors per app kill switch without enabling inDrive from node registry`() {
        val bundle = LoadedSpecs.bundled(emptyList())
        assertEquals(setOf(ReaderReadinessReducer.UBER, ReaderReadinessReducer.DIDI), ReaderSupportAdapter.supported(bundle))
        assertEquals(setOf(ReaderReadinessReducer.UBER), ReaderSupportAdapter.supported(bundle.copy(disabledPackages = setOf(ReaderReadinessReducer.DIDI))))
    }
}
