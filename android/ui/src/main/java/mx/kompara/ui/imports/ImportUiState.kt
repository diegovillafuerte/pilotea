package mx.kompara.ui.imports

import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.api.ImportResponse

/**
 * The B-045 import flow's UI state machine (rendered by `ImportScreen`, driven by [ImportViewModel]).
 *
 * Flow: [SignedOut] (terminal until the driver makes an account) OR
 *   [Picking] → [Uploading] (dry-run) → [Review] → [Uploading] (confirm) → [Saved]
 * with [Error] reachable from any upload, carrying the exact Spanish backend message. The driver can
 * always restart the pick (back to [Picking]) or discard a review.
 */
sealed interface ImportUiState {

    /** Pre-load: session not yet read. */
    data object Loading : ImportUiState

    /**
     * No account → can't upload. Account UI is deferred (techdebt TD-008); this screen explains that
     * and offers to go back. Carries no data — the copy is static Spanish in strings.xml.
     */
    data object SignedOut : ImportUiState

    /**
     * Pick a platform + upload type, then a file (or two for DiDi). [platform] is null until the
     * driver chooses one; [pickedCount] tracks files chosen so far for the DiDi two-file flow.
     */
    data class Picking(
        val platform: ImportPlatform? = null,
        val pickedCount: Int = 0,
    ) : ImportUiState

    /**
     * A file shared into Kompara from another app (PR-D3) is pre-picked + pre-classified, awaiting the
     * driver's explicit confirmation before any upload. We do NOT auto-run the dry-run: the share
     * activity is exported, so auto-firing would let any app spend the driver's authenticated
     * backend/AI parse quota from an external intent. The driver taps "Continuar" → [confirmSharedImport].
     */
    data class SharedReady(
        val platform: ImportPlatform,
        val files: List<ImportFile>,
    ) : ImportUiState

    /**
     * An upload is in flight. [step] drives the ported 4-step animation; [confirming] distinguishes
     * the dry-run preview (false) from the real confirm (true) so the screen can label the wait.
     */
    data class Uploading(
        val step: ImportProgressStep,
        val confirming: Boolean,
    ) : ImportUiState

    /**
     * Dry-run succeeded: show the parsed week for confirmation before persisting. Holds the
     * [response] (metrics + completeness) plus the [platform]/[uploadType]/[files] needed to re-issue
     * the upload as a real import on "Guardar".
     */
    data class Review(
        val platform: ImportPlatform,
        val uploadType: String,
        val files: List<ImportFile>,
        val response: ImportResponse,
    ) : ImportUiState

    /** Confirmed import landed in local history. [weekStart] is shown in the success copy. */
    data class Saved(val weekStart: String) : ImportUiState

    /**
     * A failure the driver should see. [message] is the exact Spanish string (backend error or a
     * local validation/transport message). [retryable] gates the "Intentar de nuevo" button.
     */
    data class Error(val message: String, val retryable: Boolean) : ImportUiState
}

/** The four progress steps of the upload animation, ported from the web concept. */
enum class ImportProgressStep {
    READING_FILES,
    EXTRACTING,
    CALCULATING,
    COMPARING,
}

/**
 * A pickable platform + its upload contract, with the quality hints ported from the web upload copy
 * (Uber PDF ~95%, Uber screenshot limited ~40%, DiDi 2 capturas ~85%, inDrive 1 captura). The wire
 * platform string ([wire]) is what [ImportRepository] sends; [uploadType] is the matching
 * `upload_type`; [fileCount] is how many files the picker collects.
 */
enum class ImportPlatform(
    val wire: String,
    val uploadType: String,
    val fileCount: Int,
) {
    /** Uber weekly PDF — the recommended path (~95% of data). */
    UBER_PDF("uber", "pdf", 1),

    /** Uber pie-chart screenshot — limited data (~40%). */
    UBER_SCREENSHOT("uber", "screenshot", 1),

    /** DiDi — needs 2 captures: ganancias + tablero (~85%). */
    DIDI("didi", "screenshot", 2),

    /** inDrive — 1 captura del resumen de ganancias. */
    INDRIVE("indrive", "screenshot", 1),
}
