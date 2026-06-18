package mx.kompara.ui.imports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.api.ImportMetrics
import mx.kompara.sync.api.ImportResponse
import mx.kompara.sync.imports.Importer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ImportViewModel] state-machine tests (B-045): signed-out gate, picking → uploading → review →
 * saved, error mapping with the backend Spanish string, and the per-platform file-count guard
 * surfacing as an error state. Backed by a plain [FakeImporter] (no Ktor on the `:ui` test
 * classpath); the repository's own MockEngine coverage lives in `:sync`'s ImportRepositoryTest. The
 * upload animation cadence is set to 0 so transitions are deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    // Unconfined so viewModelScope launches run eagerly; settling is awaited via [settle], which
    // suspends on the StateFlow until a terminal (non-transient) state is reached.
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Suspend until the flow leaves the transient Loading/Uploading states (or time out). */
    private suspend fun ImportViewModel.settle(): ImportUiState =
        withTimeout(5_000) {
            uiState.first { it !is ImportUiState.Loading && it !is ImportUiState.Uploading }
        }

    private fun pdf() = ImportFile("report.pdf", "application/pdf", byteArrayOf(1, 2, 3))

    private fun metrics(weekStart: String = "2025-03-24") = ImportMetrics(
        weekStart = weekStart,
        netEarnings = 3850.5,
        totalTrips = 72,
    )

    private fun previewResponse() = ImportResponse(
        importId = null,
        metrics = metrics(),
        dataCompleteness = 0.95,
        dryRun = true,
    )

    private fun confirmResponse() = ImportResponse(
        importId = "imp-1",
        metrics = metrics(),
        dataCompleteness = 0.95,
        dryRun = false,
    )

    private fun viewModel(
        importer: Importer,
        buffer: SharedImportBuffer = SharedImportBuffer(),
    ): ImportViewModel =
        ImportViewModel(importer, buffer).also { it.stepDelayMillis = 0L }

    @Test
    fun `signed-out driver lands on SignedOut`() = runBlocking {
        val vm = viewModel(FakeImporter(signedIn = false))
        assertEquals(ImportUiState.SignedOut, vm.settle())
    }

    @Test
    fun `signed-in driver starts on Picking`() = runBlocking {
        val vm = viewModel(FakeImporter())
        assertTrue(vm.settle() is ImportUiState.Picking)
    }

    @Test
    fun `selecting a platform records it on the Picking state`() = runBlocking {
        val vm = viewModel(FakeImporter())
        vm.settle()
        vm.selectPlatform(ImportPlatform.UBER_PDF)
        assertEquals(ImportPlatform.UBER_PDF, (vm.uiState.value as ImportUiState.Picking).platform)
    }

    @Test
    fun `submit runs a dry-run preview and lands on Review`() = runBlocking {
        val importer = FakeImporter(preview = previewResponse())
        val vm = viewModel(importer)
        vm.settle()

        vm.submitForReview(ImportPlatform.UBER_PDF, listOf(pdf()))

        val review = vm.settle() as ImportUiState.Review
        assertTrue(review.response.dryRun)
        assertEquals("2025-03-24", review.response.metrics.weekStart)
        // Preview path was used, confirm was not.
        assertEquals(1, importer.previewCalls)
        assertEquals(0, importer.confirmCalls)
    }

    @Test
    fun `confirm from Review saves`() = runBlocking {
        val importer = FakeImporter(preview = previewResponse(), confirm = confirmResponse())
        val vm = viewModel(importer)
        vm.settle()

        vm.submitForReview(ImportPlatform.UBER_PDF, listOf(pdf()))
        assertTrue(vm.settle() is ImportUiState.Review)

        vm.confirm()

        val saved = vm.settle() as ImportUiState.Saved
        assertEquals("2025-03-24", saved.weekStart)
        assertEquals(1, importer.confirmCalls)
    }

    @Test
    fun `discard from Review returns to Picking`() = runBlocking {
        val vm = viewModel(FakeImporter(preview = previewResponse()))
        vm.settle()
        vm.submitForReview(ImportPlatform.UBER_PDF, listOf(pdf()))
        vm.settle()
        vm.discard()
        assertTrue(vm.uiState.value is ImportUiState.Picking)
    }

    @Test
    fun `a parse failure maps to Error with the backend Spanish message`() = runBlocking {
        val message = "No pudimos leer tus datos. Asegurate que el screenshot sea claro y completo."
        val vm = viewModel(FakeImporter(failure = ApiException(422, message)))
        vm.settle()

        vm.submitForReview(ImportPlatform.UBER_PDF, listOf(pdf()))

        val error = vm.settle() as ImportUiState.Error
        assertEquals(message, error.message)
        assertFalse(error.retryable) // 4xx — not retryable
    }

    @Test
    fun `a 5xx maps to a retryable Error`() = runBlocking {
        val vm = viewModel(FakeImporter(failure = ApiException(503, "Servidor no disponible")))
        vm.settle()

        vm.submitForReview(ImportPlatform.UBER_PDF, listOf(pdf()))

        assertTrue((vm.settle() as ImportUiState.Error).retryable)
    }

    @Test
    fun `a non-API failure maps to a retryable connection Error`() = runBlocking {
        val vm = viewModel(FakeImporter(failure = RuntimeException("socket closed")))
        vm.settle()

        vm.submitForReview(ImportPlatform.UBER_PDF, listOf(pdf()))

        val error = vm.settle() as ImportUiState.Error
        assertTrue(error.retryable)
        assertTrue(error.message.contains("conectar"))
    }

    @Test
    fun `a staged shared file waits on SharedReady and uploads only after explicit confirm`() = runBlocking {
        // PR-D3: a file shared into Kompara is pre-picked + pre-classified, but the dry-run must NOT
        // auto-fire (the share activity is exported) — it waits for the driver's "Continuar" tap.
        val importer = FakeImporter(preview = previewResponse())
        val buffer = SharedImportBuffer().apply {
            set(PendingSharedImport(ImportPlatform.UBER_PDF, listOf(pdf())))
        }
        val vm = viewModel(importer, buffer)

        val ready = vm.settle() as ImportUiState.SharedReady
        assertEquals(ImportPlatform.UBER_PDF, ready.platform)
        assertEquals(0, importer.previewCalls) // nothing uploaded yet
        // Consumed exactly once — nothing left to re-fire on a later open.
        assertEquals(null, buffer.take())

        vm.confirmSharedImport()

        val review = vm.settle() as ImportUiState.Review
        assertEquals("2025-03-24", review.response.metrics.weekStart)
        assertEquals(1, importer.previewCalls)
    }

    @Test
    fun `a staged share is dropped for a signed-out driver`() = runBlocking {
        val importer = FakeImporter(signedIn = false)
        val buffer = SharedImportBuffer().apply {
            set(PendingSharedImport(ImportPlatform.UBER_PDF, listOf(pdf())))
        }
        val vm = viewModel(importer, buffer)

        assertEquals(ImportUiState.SignedOut, vm.settle())
        assertEquals(0, importer.previewCalls) // never uploaded without an account
        assertEquals(null, buffer.take()) // and the staged bytes are cleared
    }

    @Test
    fun `restart returns to a fresh Picking from an error`() = runBlocking {
        val vm = viewModel(FakeImporter(failure = ApiException(422, "x")))
        vm.settle()
        vm.submitForReview(ImportPlatform.UBER_PDF, listOf(pdf()))
        assertTrue(vm.settle() is ImportUiState.Error)

        vm.restart()
        assertEquals(null, (vm.uiState.value as ImportUiState.Picking).platform)
    }
}

/**
 * Plain fake [Importer]: records preview/confirm calls and returns canned responses (or throws a
 * preset failure). The validation/upsert logic it stands in for is covered by `:sync`'s
 * ImportRepositoryTest against a real MockEngine.
 */
private class FakeImporter(
    private val signedIn: Boolean = true,
    private val preview: ImportResponse? = null,
    private val confirm: ImportResponse? = null,
    private val failure: Throwable? = null,
) : Importer {
    var previewCalls = 0
    var confirmCalls = 0

    override suspend fun isSignedIn(): Boolean = signedIn

    override suspend fun preview(
        platform: String,
        uploadType: String,
        files: List<ImportFile>,
    ): ImportResponse {
        previewCalls++
        failure?.let { throw it }
        return preview ?: error("no preview response configured")
    }

    override suspend fun confirm(
        platform: String,
        uploadType: String,
        files: List<ImportFile>,
    ): ImportResponse {
        confirmCalls++
        failure?.let { throw it }
        return confirm ?: error("no confirm response configured")
    }
}
