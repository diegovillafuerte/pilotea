package mx.kompara.ui.imports

import mx.kompara.sync.api.ImportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [ShareTargetClassifier] (PR-D3): MIME/count → platform mapping, with file-count always consistent. */
class ShareTargetClassifierTest {

    private fun file(mime: String, name: String = "f") = ImportFile(name, mime, byteArrayOf(1))

    @Test
    fun `a shared PDF classifies as the Uber PDF path`() {
        val result = ShareTargetClassifier.classify(listOf(file("application/pdf")))
        assertEquals(ImportPlatform.UBER_PDF, result?.platform)
        assertEquals(1, result?.files?.size)
    }

    @Test
    fun `a PDF plus stray images still takes only the single PDF`() {
        val result = ShareTargetClassifier.classify(
            listOf(file("image/png"), file("application/pdf"), file("image/jpeg")),
        )
        assertEquals(ImportPlatform.UBER_PDF, result?.platform)
        assertEquals(1, result?.files?.size)
        assertEquals("application/pdf", result?.files?.single()?.mimeType)
    }

    @Test
    fun `two images classify as DiDi`() {
        val result = ShareTargetClassifier.classify(listOf(file("image/png"), file("image/jpeg")))
        assertEquals(ImportPlatform.DIDI, result?.platform)
        assertEquals(2, result?.files?.size)
    }

    @Test
    fun `three images are trimmed to DiDi's two slots`() {
        val result = ShareTargetClassifier.classify(
            listOf(file("image/png"), file("image/jpeg"), file("image/webp")),
        )
        assertEquals(ImportPlatform.DIDI, result?.platform)
        assertEquals(2, result?.files?.size)
    }

    @Test
    fun `a single image classifies as an Uber screenshot`() {
        val result = ShareTargetClassifier.classify(listOf(file("image/jpeg")))
        assertEquals(ImportPlatform.UBER_SCREENSHOT, result?.platform)
        assertEquals(1, result?.files?.size)
    }

    @Test
    fun `unsupported MIME types are dropped`() {
        assertNull(ShareTargetClassifier.classify(listOf(file("text/plain"), file("video/mp4"))))
    }

    @Test
    fun `an empty share is null`() {
        assertNull(ShareTargetClassifier.classify(emptyList()))
    }

    @Test
    fun `unsupported types mixed with one valid image still classify on the valid one`() {
        val result = ShareTargetClassifier.classify(listOf(file("text/plain"), file("image/png")))
        assertEquals(ImportPlatform.UBER_SCREENSHOT, result?.platform)
        assertEquals(1, result?.files?.size)
    }

    // --- plan(): the cheap MIME-only pass the share activity uses to read ONLY the needed files. ---

    @Test
    fun `plan picks only the PDF index out of a mixed share`() {
        val plan = ShareTargetClassifier.plan(listOf("image/png", "application/pdf", "image/jpeg"))
        assertEquals(ImportPlatform.UBER_PDF, plan?.platform)
        assertEquals(listOf(1), plan?.indices) // read only the PDF, not the stray images
    }

    @Test
    fun `plan reads exactly the first two of many images for DiDi`() {
        val plan = ShareTargetClassifier.plan(List(5) { "image/png" })
        assertEquals(ImportPlatform.DIDI, plan?.platform)
        assertEquals(listOf(0, 1), plan?.indices) // never drains the other three
    }

    @Test
    fun `plan reads the single image for the Uber screenshot path`() {
        val plan = ShareTargetClassifier.plan(listOf("image/jpeg"))
        assertEquals(ImportPlatform.UBER_SCREENSHOT, plan?.platform)
        assertEquals(listOf(0), plan?.indices)
    }

    @Test
    fun `plan is null for an empty MIME list`() {
        assertNull(ShareTargetClassifier.plan(emptyList()))
    }
}
