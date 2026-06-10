package mx.kompara.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncServiceTest {

    @Test
    fun `syncNow is a no-op until the backend lands`() = runTest {
        val service = SyncService(StandardTestDispatcher(testScheduler))
        assertEquals(0, service.syncNow())
    }
}
