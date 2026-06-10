package mx.kompara.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Connected/disconnected state tracking for the watchdog. */
class ServiceStateRepositoryTest {

    @Test
    fun `starts disconnected`() {
        assertFalse(ServiceStateRepository().connected.value)
    }

    @Test
    fun `reflects connect then disconnect transitions`() {
        val repo = ServiceStateRepository()

        repo.setConnected(true)
        assertTrue(repo.connected.value)

        repo.setConnected(false)
        assertFalse(repo.connected.value)
    }
}
