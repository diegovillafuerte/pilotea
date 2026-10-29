package mx.kompara.ui.imports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.kompara.data.imports.ImportDraftEntity
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.imports.*
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: Importer,
    private val sharedImportBuffer: SharedImportBuffer,
    private val source: ImportReviewSource,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var draft: ImportDraftEntity? = null
    private var initialRestore: Job? = null
    init { resolveSession(); initialRestore = job }

    fun resolveSession() = launch {
        val savedId = saved.get<String>("draftId")
        if (savedId != null) {
            draft = repository.open(savedId)
            if (draft?.previewJson != null) showReview(draft!!) else state.value = ImportUiState.Picking
            return@launch
        }
        val shared = sharedImportBuffer.take()
        draft = if (shared != null) repository.retain(shared.platform.wire, shared.platform.uploadType, shared.files) else repository.restore()
        showDraft(draft)
    }
    /** Android can recreate the caller while its document picker is in front. */
    fun preparePicker(adding: Boolean) { saved["addingFile"] = adding }
    /** Picker and Share both land on the same consent page; selection alone never uploads. */
    fun selected(files: List<ImportFile>, adding: Boolean = saved["addingFile"] ?: false) {
        // A restored ActivityResult can precede Room's initial draft read. Preserve that result
        // until its parent is loaded, instead of dropping it through the normal busy guard.
        viewModelScope.launch { initialRestore?.join(); retainSelection(files, adding) }
    }
    private fun retainSelection(files: List<ImportFile>, adding: Boolean) = launch {
        val parent = draft
        val combined = if (adding && parent != null) repository.files(parent.id).take(1) + files.take(1) else files
        val classified = if (adding) PendingSharedImport(ImportPlatform.DIDI, combined) else ShareTargetClassifier.classify(combined)
            ?: throw IllegalArgumentException("Elige un PDF de Uber o capturas de Ganancias y Tablero.")
        require(classified.files.size == combined.size) { "Elige un PDF o las dos capturas de DiDi." }
        draft = repository.retain(classified.platform.wire, classified.platform.uploadType, classified.files, if (adding) parent?.id else null)
        saved["addingFile"] = false
        state.value = ImportUiState.Ready(draft!!, repository.files(draft!!.id))
    }
    /** A single image cannot reliably identify its app. The driver can name DiDi before any parse. */
    fun singleImageIsDidi() = launch {
        val current = draft ?: return@launch
        draft = repository.retain("didi", "screenshot", repository.files(current.id))
        state.value = ImportUiState.Ready(draft!!, repository.files(draft!!.id))
    }
    fun upload() = launch {
        val current = draft ?: return@launch
        draft = repository.consent(current.id)
        if (!repository.isSignedIn()) { state.value = ImportUiState.SignedOut(draft); return@launch }
        readDraft()
    }
    fun returnedFromSignIn() = launch {
        if (state.value !is ImportUiState.SignedOut) return@launch
        if (repository.isSignedIn()) showDraft(draft) // explicit continue after auth, never auto-upload
    }
    private suspend fun readDraft() {
        val current = draft ?: return
        if (current.platform == "didi" && repository.files(current.id).size < 2) {
            state.value = ImportUiState.Missing(current, current.previewJson != null); return
        }
        state.value = ImportUiState.Reading()
        draft = repository.read(current.id)
        showReview(draft!!)
    }
    private suspend fun showDraft(current: ImportDraftEntity?) {
        when {
            current == null -> state.value = ImportUiState.Picking
            current.stage == "SAVED" -> state.value = ImportUiState.Saved(current.metrics()!!.weekStart)
            (current.previewJson != null || current.consentAt != null) && current.platform == "didi" && repository.files(current.id).size < 2 ->
                state.value = ImportUiState.Missing(current, parsed = current.previewJson != null)
            current.previewJson != null -> {
                showReview(current)
                if (saved.get<String>("editing") == current.id) edit()
            }
            current.stage == "ERROR" -> state.value = ImportUiState.Error(current, repository.files(current.id), current.error ?: "Sin conexión o el servidor no respondió")
            else -> state.value = ImportUiState.Ready(current, repository.files(current.id))
        }
    }
    private suspend fun showReview(current: ImportDraftEntity) {
        val partial = current.stage != "SAVED" && current.platform == "didi" && repository.files(current.id).size < 2
        state.value = ImportUiState.Review(current, source.overlap(current), partial)
    }
    fun reviewPartial() = launch { draft?.takeIf { it.previewJson != null }?.let { showReview(it) } }
    fun confirm() {
        val review = state.value as? ImportUiState.Review ?: return
        if (review.partial || review.draft.stage == "SAVED") return
        if (review.overlap != null) state.value = ImportUiState.Reconcile(review) else save(false)
    }
    fun useStatement() {
        val reconcile = state.value as? ImportUiState.Reconcile ?: return
        if (reconcile.review.overlap?.active == true) return
        save(true)
    }
    private fun save(reconcile: Boolean) = launch(saving = true) {
        val current = draft ?: return@launch
        // Refresh changing captures before asking for a replacement; no silent change from review.
        val overlap = source.overlap(current)
        val reviewed = when(val s = state.value) { is ImportUiState.Reconcile -> s.review.overlap; is ImportUiState.Review -> s.overlap; else -> null }
        if (overlap != null && (overlap != reviewed || !reconcile)) {
            state.value = ImportUiState.Reconcile(ImportUiState.Review(current, overlap)); return@launch
        }
        state.value = ImportUiState.Reading(saving = true)
        val statement = repository.save(current.id, reconcile)
        saved["editing"] = null
        state.value = ImportUiState.Saved(statement.start)
    }
    fun retry() {
        val error = state.value as? ImportUiState.Error ?: return
        if (error.saving) launch { showReview(draft!!) } else launch {
            if (!repository.isSignedIn()) state.value = ImportUiState.SignedOut(draft) else readDraft()
        }
    }
    fun restart() = launch {
        draft?.let { repository.discard(it.id) }
        draft = null; saved["editing"] = null; state.value = ImportUiState.Picking
    }
    fun edit() {
        val review = state.value as? ImportUiState.Review ?: return
        if (review.draft.stage == "SAVED") return
        val defaults = ImportCorrection.from(review.draft.metrics()!!)
        val restoring = saved.get<String>("editing") == review.draft.id
        val values = if (restoring) ImportCorrection(saved["payment"], saved["trips"], saved["hours"], saved["km"], saved["week"] ?: defaults.week) else defaults
        state.value = ImportUiState.Correcting(review, values)
        updateCorrection(values)
    }
    fun updateCorrection(values: ImportCorrection) {
        val edit = state.value as? ImportUiState.Correcting ?: return
        saved["editing"] = edit.review.draft.id
        saved["payment"] = values.payment; saved["trips"] = values.trips; saved["hours"] = values.hours; saved["km"] = values.km; saved["week"] = values.week
        state.value = edit.copy(values = values, error = null)
    }
    fun cancelCorrection() {
        val edit = state.value as? ImportUiState.Correcting ?: return
        saved["editing"] = null; state.value = edit.review
    }
    fun saveCorrection() = launch {
        val edit = state.value as? ImportUiState.Correcting ?: return@launch
        try {
            draft = repository.correct(edit.review.draft.id, edit.values.apply(edit.review.draft.metrics()!!))
            saved["editing"] = null; showReview(draft!!)
        } catch (e: IllegalArgumentException) { state.value = edit.copy(error = e.message ?: "Revisa este número") }
    }
    fun fileError() { state.value = ImportUiState.Error(draft, emptyList(), "No pudimos leer este archivo", retryable = false) }
    private fun launch(saving: Boolean = false, block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                state.value = if (e is ApiException && e.status == 401) ImportUiState.SignedOut(draft)
                    else ImportUiState.Error(draft, draft?.let { repository.files(it.id) }.orEmpty(),
                        if (e is ApiException || e is IllegalArgumentException) e.message ?: "Revisa el archivo" else "Sin conexión o el servidor no respondió",
                        retryable = e !is IllegalArgumentException && (e !is ApiException || e.status >= 500), saving = saving)
            }
        }
    }
}
