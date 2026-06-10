package mx.kompara.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.imports.Importer
import javax.inject.Inject

/**
 * Drives the B-045 import flow's state machine ([ImportUiState]).
 *
 * On creation it resolves the session: signed-out drivers land on [ImportUiState.SignedOut] (account
 * UI is deferred — techdebt TD-008), signed-in drivers start [ImportUiState.Picking]. The driver
 * picks a platform ([selectPlatform]) then files; [submitForReview] runs a dry-run preview (no
 * persistence) and, on success, shows [ImportUiState.Review]. [confirm] re-issues the upload as a
 * real import and backfills local history, ending on [ImportUiState.Saved]. Every upload failure maps
 * to [ImportUiState.Error] carrying the backend's exact Spanish string.
 *
 * The 4-step progress animation (Leyendo archivos → Extrayendo datos con IA → Calculando métricas →
 * Comparando) is driven by [animateProgress], a cosmetic timer that advances the visible step while
 * the real network call runs; the call completing flips state regardless of where the animation is.
 *
 * Testability: the animation cadence is injectable ([stepDelayMillis]) so a test can set it to 0, and
 * all transitions are plain state emissions over [uiState].
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: Importer,
) : ViewModel() {

    /** Visible step cadence for the upload animation. Overridable in tests (set to 0 for instant). */
    var stepDelayMillis: Long = DEFAULT_STEP_DELAY_MILLIS

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var animationJob: Job? = null

    init {
        resolveSession()
    }

    /** (Re)check the session and set the entry state. Called on init and from the signed-out retry. */
    fun resolveSession() {
        viewModelScope.launch {
            _uiState.value = if (repository.isSignedIn()) {
                ImportUiState.Picking()
            } else {
                ImportUiState.SignedOut
            }
        }
    }

    /** Choose the platform/upload-type to import. Resets any in-progress pick. */
    fun selectPlatform(platform: ImportPlatform) {
        _uiState.value = ImportUiState.Picking(platform = platform, pickedCount = 0)
    }

    /** Return to platform picking from anywhere (the screen's back / "elegir otra plataforma"). */
    fun restart() {
        cancelAnimation()
        _uiState.value = ImportUiState.Picking()
    }

    /**
     * Upload [files] for the [platform] as a DRY RUN; on success move to [ImportUiState.Review].
     * Validates the file count via the repository, so a wrong count surfaces as an [ImportUiState.
     * Error] without a network call. No-ops if the current state isn't [ImportUiState.Picking].
     */
    fun submitForReview(platform: ImportPlatform, files: List<ImportFile>) {
        runUpload(confirming = false) {
            val response = repository.preview(platform.wire, platform.uploadType, files)
            ImportUiState.Review(
                platform = platform,
                uploadType = platform.uploadType,
                files = files,
                response = response,
            )
        }
    }

    /**
     * Confirm the previewed import: re-upload for real and backfill local history. Only valid from
     * [ImportUiState.Review]; reads the platform/files off that state.
     */
    fun confirm() {
        val review = _uiState.value as? ImportUiState.Review ?: return
        runUpload(confirming = true) {
            val response = repository.confirm(review.platform.wire, review.uploadType, review.files)
            ImportUiState.Saved(weekStart = response.metrics.weekStart)
        }
    }

    /** Discard a review without saving — back to picking. */
    fun discard() {
        _uiState.value = ImportUiState.Picking()
    }

    /**
     * Run an upload [block] inside the [ImportUiState.Uploading] state with the progress animation,
     * mapping success to the block's result and any [ApiException] to [ImportUiState.Error] with the
     * backend's Spanish message. Non-API failures (transport) get a generic Spanish retryable error.
     */
    private fun runUpload(confirming: Boolean, block: suspend () -> ImportUiState) {
        cancelAnimation()
        _uiState.value = ImportUiState.Uploading(ImportProgressStep.READING_FILES, confirming)
        animateProgress(confirming)
        viewModelScope.launch {
            _uiState.value = try {
                block()
            } catch (e: ApiException) {
                // 4xx (bad input / parse failure) is not worth a blind retry; 5xx / transport is.
                ImportUiState.Error(message = e.message, retryable = e.status >= 500)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                ImportUiState.Error(
                    message = "No se pudo conectar. Revisa tu conexion e intentalo de nuevo.",
                    retryable = true,
                )
            } finally {
                cancelAnimation()
            }
        }
    }

    /** Advance the visible progress step on a timer while the real upload runs. Cosmetic only. */
    private fun animateProgress(confirming: Boolean) {
        val steps = ImportProgressStep.entries
        animationJob = viewModelScope.launch {
            for (step in steps.drop(1)) {
                delay(stepDelayMillis)
                if (!isActive) return@launch
                // Only advance if we're still uploading the same phase (not flipped to review/error).
                val current = _uiState.value
                if (current is ImportUiState.Uploading && current.confirming == confirming) {
                    _uiState.value = current.copy(step = step)
                } else {
                    return@launch
                }
            }
        }
    }

    private fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    override fun onCleared() {
        cancelAnimation()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_STEP_DELAY_MILLIS = 900L
    }
}
