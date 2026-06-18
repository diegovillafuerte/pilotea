package mx.kompara.ui.imports

import mx.kompara.sync.api.ImportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [SharedImportBuffer] (PR-D3): exactly-once hand-off semantics between the share activity and the VM. */
class SharedImportBufferTest {

    private fun pending() = PendingSharedImport(
        ImportPlatform.UBER_PDF,
        listOf(ImportFile("report.pdf", "application/pdf", byteArrayOf(1))),
    )

    @Test
    fun `take returns the staged share then clears it`() {
        val buffer = SharedImportBuffer()
        val value = pending()
        buffer.set(value)

        assertEquals(value, buffer.take())
        assertNull(buffer.take()) // consumed exactly once
    }

    @Test
    fun `take on an empty buffer is null`() {
        assertNull(SharedImportBuffer().take())
    }

    @Test
    fun `set overwrites an unread share`() {
        val buffer = SharedImportBuffer()
        buffer.set(pending())
        val second = PendingSharedImport(
            ImportPlatform.DIDI,
            listOf(
                ImportFile("a.png", "image/png", byteArrayOf(1)),
                ImportFile("b.png", "image/png", byteArrayOf(2)),
            ),
        )
        buffer.set(second)
        assertEquals(second, buffer.take())
    }
}
