package mx.kompara.ui.imports

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import mx.kompara.data.accounting.*
import mx.kompara.data.imports.*
import mx.kompara.sync.api.*
import mx.kompara.sync.imports.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private val pdf = ImportFile("semana.pdf", "application/pdf", byteArrayOf(1))
    private val m = ImportMetrics("2026-09-28", netEarnings = 2210.0, totalTrips = 20, hoursOnline = 26.0)
    private val source = ImportReviewSource { null }
    private fun vm(repo: Fake = Fake(), buffer: SharedImportBuffer = SharedImportBuffer(), source: ImportReviewSource = this.source, state: SavedStateHandle = SavedStateHandle()) = ImportViewModel(repo, buffer, source, state)
    private inner class Fake : Importer {
        var signed = true; var draft: ImportDraftEntity? = null; var selected = emptyList<ImportFile>()
        var parses = 0; var saves = 0; var uses = false; var fail = false; var saveFail = false; var wait: CompletableDeferred<Unit>? = null
        var restoreWait: CompletableDeferred<Unit>? = null
        override suspend fun isSignedIn() = signed
        override suspend fun open(id: String) = draft
        override suspend fun restore(): ImportDraftEntity? { restoreWait?.await(); return draft?.takeIf { it.stage != "SAVED" } }
        override suspend fun retain(platform: String, uploadType: String, files: List<ImportFile>, parentId: String?): ImportDraftEntity {
            selected = files
            return ImportDraftEntity(files.size.toString(), platform, uploadType, 1, parentId = parentId).also { draft = it }
        }
        override suspend fun files(id: String) = selected
        override suspend fun consent(id: String) = draft!!.copy(consentAt = 2).also { draft = it }
        override suspend fun read(id: String): ImportDraftEntity {
            check(draft!!.consentAt != null); parses++; wait?.await()
            if (fail) throw ApiException(503, "Sin conexión o el servidor no respondió")
            return draft!!.copy(previewJson = ImportResponse(metrics = m, dataCompleteness = .9, dryRun = true).retainedJson(), stage = "REVIEW").also { draft = it }
        }
        override suspend fun correct(id: String, metrics: ImportMetrics) = draft!!.copy(correctionJson = metrics.retainedJson()).also { draft = it }
        override suspend fun save(id: String, useStatement: Boolean): ImportStatementEntity {
            if (saveFail) error("disk")
            saves++; uses = useStatement
            draft = draft!!.copy(stage = "SAVED")
            return ImportStatementEntity(id, "UBER", m.weekStart, "2026-10-04", "2210", 20, null, "26", "PARSED_STATEMENT", "pdf", 3)
        }
        override suspend fun discard(id: String) { draft = null; selected = emptyList() }
    }
    private fun overlap() = ImportOverlap(listOf(IncomeRecord("captured", "UBER",
        Coverage(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-04")), MoneySource.CAPTURED_ESTIMATE,
        1480.toBigDecimal(), "150.6".toBigDecimal(), 14)), "427.955".toBigDecimal())
    @Test fun `picker and signed-out share both require consent and keep files across auth`() = runTest {
        val repo = Fake().apply { signed = false }
        val buffer = SharedImportBuffer().apply { set(PendingSharedImport(ImportPlatform.UBER_PDF, listOf(pdf))) }
        val vm = vm(repo, buffer)
        assertTrue(vm.uiState.value is ImportUiState.Ready); assertEquals(0, repo.parses)
        vm.upload(); assertTrue(vm.uiState.value is ImportUiState.SignedOut)
        repo.signed = true; vm.returnedFromSignIn()
        assertTrue(vm.uiState.value is ImportUiState.Ready); assertEquals(0, repo.parses)
        vm.upload(); assertTrue(vm.uiState.value is ImportUiState.Review)
        vm.confirm(); assertTrue(vm.uiState.value is ImportUiState.Saved); assertEquals(1, repo.parses)
    }
    @Test fun `picking never parses and closing a review never saves`() = runTest {
        val repo = Fake(); val vm = vm(repo)
        assertEquals(ImportUiState.Picking, vm.uiState.value)
        vm.selected(listOf(pdf)); assertEquals(0, repo.parses)
        vm.upload(); vm.restart(); assertEquals(ImportUiState.Picking, vm.uiState.value); assertEquals(0, repo.saves)
    }
    @Test fun `failed upload retry uses retained bytes and consent`() = runTest {
        val repo = Fake().apply { fail = true }; val vm = vm(repo)
        vm.selected(listOf(pdf)); vm.upload()
        assertEquals(pdf, (vm.uiState.value as ImportUiState.Error).files.single())
        repo.fail = false; vm.retry()
        assertTrue(vm.uiState.value is ImportUiState.Review); assertEquals(2, repo.parses)
    }
    @Test fun `duplicate upload and Save taps are guarded while a request is active`() = runTest {
        val wait = CompletableDeferred<Unit>(); val repo = Fake().apply { this.wait = wait }; val vm = vm(repo)
        vm.selected(listOf(pdf)); vm.upload(); vm.upload(); vm.confirm()
        assertEquals(1, repo.parses); wait.complete(Unit)
        vm.confirm(); vm.confirm(); assertEquals(1, repo.saves)
    }
    @Test fun `review corrections are local decimal fields and survive SavedState recreation`() = runTest {
        val repo = Fake(); val saved = SavedStateHandle(); val vm = vm(repo, state = saved)
        vm.selected(listOf(pdf)); vm.upload(); vm.edit()
        val correction = (vm.uiState.value as ImportUiState.Correcting).values.copy(payment = "$2210,50")
        vm.updateCorrection(correction)
        val restored = vm(repo, state = saved)
        assertEquals(correction, (restored.uiState.value as ImportUiState.Correcting).values)
        restored.saveCorrection(); assertEquals(2210.5, (restored.uiState.value as ImportUiState.Review).draft.metrics()!!.netEarnings!!, .0)
        assertEquals(1, repo.parses); restored.confirm(); assertEquals(1, repo.saves)
    }
    @Test fun `review period correction preserves read fields without reparsing and survives recreation`() = runTest {
        val repo = Fake(); val viewModel = vm(repo)
        viewModel.selected(listOf(pdf)); viewModel.upload()
        viewModel.correctWeek(LocalDate.parse("2026-09-16"))
        val corrected = (viewModel.uiState.value as ImportUiState.Review).draft.metrics()!!
        assertEquals("2026-09-14", corrected.weekStart)
        assertEquals(m.copy(weekStart = "2026-09-14"), corrected)
        assertEquals(1, repo.parses)
        val restored = vm(repo)
        assertEquals(corrected, (restored.uiState.value as ImportUiState.Review).draft.metrics())
        restored.edit()
        assertEquals("2026-09-14", (restored.uiState.value as ImportUiState.Correcting).values.week)
        restored.cancelCorrection()
        assertEquals(corrected, (restored.uiState.value as ImportUiState.Review).draft.metrics())
    }
    @Test fun `invalid and empty correction keep editor and original result`() = runTest {
        val repo = Fake(); val vm = vm(repo)
        vm.selected(listOf(pdf)); vm.upload(); vm.edit()
        val initial = (vm.uiState.value as ImportUiState.Correcting).values
        vm.updateCorrection(initial.copy(payment = "")); vm.saveCorrection()
        assertNotNull((vm.uiState.value as ImportUiState.Correcting).error)
        vm.cancelCorrection(); assertEquals(m, (vm.uiState.value as ImportUiState.Review).draft.metrics())
    }
    @Test fun `existing app data requires explicit Use PDF with partial km warning`() = runTest {
        val repo = Fake(); val vm = vm(repo, source = ImportReviewSource { overlap() })
        vm.selected(listOf(pdf)); vm.upload(); vm.confirm()
        val state = vm.uiState.value as ImportUiState.Reconcile
        assertEquals(14, state.review.overlap!!.trips); assertEquals(0, repo.saves)
        assertEquals("El PDF queda en la semana; octubre conserva lo leído del 1 al 4.", reconciliationConsequence(state.review))
        vm.useStatement(); assertTrue(repo.uses); assertEquals(1, repo.saves)
    }
    @Test fun `capture changing since review requires updated reconciliation before saving`() = runTest {
        val repo = Fake(); var overlap: ImportOverlap? = null
        val vm = vm(repo, source = ImportReviewSource { overlap })
        vm.selected(listOf(pdf)); vm.upload(); overlap = overlap(); vm.confirm()
        assertTrue(vm.uiState.value is ImportUiState.Reconcile); assertEquals(0, repo.saves)
        vm.useStatement(); assertEquals(1, repo.saves)
    }
    @Test fun `active trip cannot be reconciled or marked paid by an aggregate`() = runTest {
        val repo = Fake(); val o = overlap(); val active = o.copy(records = o.records.map { it.copy(inProgress = 1) })
        val vm = vm(repo, source = ImportReviewSource { active })
        vm.selected(listOf(pdf)); vm.upload(); vm.confirm(); vm.useStatement()
        assertTrue(vm.uiState.value is ImportUiState.Reconcile); assertEquals(0, repo.saves)
    }
    @Test fun `save failure returns retained review without parsing again`() = runTest {
        val repo = Fake().apply { saveFail = true }; val vm = vm(repo)
        vm.selected(listOf(pdf)); vm.upload(); vm.confirm()
        assertTrue((vm.uiState.value as ImportUiState.Error).saving)
        vm.retry(); repo.saveFail = false; vm.confirm()
        assertEquals(1, repo.parses); assertEquals(1, repo.saves)
    }
    @Test fun `DiDi missing input waits without a parse and a pair explicitly starts a new set`() = runTest {
        val repo = Fake(); val vm = vm(repo)
        vm.selected(listOf(pdf.copy(mimeType = "image/png"))); vm.singleImageIsDidi(); vm.upload()
        assertTrue(vm.uiState.value is ImportUiState.Missing); assertEquals(0, repo.parses)
        assertTrue(vm(repo).uiState.value is ImportUiState.Missing)
        vm.selected(listOf(pdf.copy(mimeType = "image/png", bytes = byteArrayOf(2))), adding = true)
        assertNotNull((vm.uiState.value as ImportUiState.Ready).draft.parentId); assertEquals(0, repo.parses)
        vm.upload(); assertEquals(1, repo.parses); assertEquals(0, repo.saves)
    }
    @Test fun `retained partial result can be reviewed but never saved before missing input`() = runTest {
        val repo = Fake()
        repo.retain("didi", "screenshot", listOf(pdf.copy(mimeType = "image/png")))
        repo.consent("1"); repo.read("1")
        val vm = vm(repo)
        assertTrue((vm.uiState.value as ImportUiState.Missing).parsed)
        vm.reviewPartial()
        assertTrue((vm.uiState.value as ImportUiState.Review).partial)
        vm.confirm(); assertEquals(0, repo.saves); assertEquals(1, repo.parses)
    }
    @Test fun `second capture picker result waits for recreated draft and preserves the first image and upload consent`() = runTest {
        val repo = Fake(); val saved = SavedStateHandle(); val first = vm(repo, state = saved)
        val firstImage = pdf.copy(fileName = "ganancias.png", mimeType = "image/png")
        val secondImage = pdf.copy(fileName = "tablero.png", mimeType = "image/png", bytes = byteArrayOf(2))
        first.selected(listOf(firstImage)); first.singleImageIsDidi(); first.upload()
        assertTrue(first.uiState.value is ImportUiState.Missing)
        first.preparePicker(adding = true)
        val restoredDraft = CompletableDeferred<Unit>()
        repo.restoreWait = restoredDraft
        val recreated = vm(repo, state = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        recreated.selected(listOf(secondImage))
        assertEquals(ImportUiState.Loading, recreated.uiState.value)
        assertEquals(listOf(firstImage), repo.selected)
        restoredDraft.complete(Unit); runCurrent()
        val ready = recreated.uiState.value as ImportUiState.Ready
        assertEquals(listOf(firstImage, secondImage), ready.files)
        assertEquals("didi", ready.draft.platform)
        assertNotNull(ready.draft.parentId)
        assertNull("The new pair still needs explicit upload consent", ready.draft.consentAt)
        assertEquals(0, repo.parses); assertEquals(0, repo.saves)
        recreated.preparePicker(adding = false)
        recreated.selected(listOf(pdf))
        assertEquals(listOf(pdf), (recreated.uiState.value as ImportUiState.Ready).files)
    }
    @Test fun `saved review opens without auth upload or another save`() = runTest {
        val repo = Fake(); val first = vm(repo)
        first.selected(listOf(pdf)); first.upload(); first.confirm(); repo.signed = false
        val review = vm(repo, state = SavedStateHandle(mapOf("draftId" to "1")))
        assertEquals("SAVED", (review.uiState.value as ImportUiState.Review).draft.stage)
        review.confirm(); review.edit()
        assertEquals(1, repo.saves); assertEquals(1, repo.parses)
    }

}
