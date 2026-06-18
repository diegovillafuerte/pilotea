package mx.kompara.ui.imports

import mx.kompara.sync.api.ImportFile

/**
 * Maps a set of shared files (PR-D3) to the [ImportPlatform] the import flow should preview them as,
 * purely from MIME type and file count — no Android dependencies, so it is unit-tested directly.
 *
 * Rules, in order:
 *  1. Any PDF → [ImportPlatform.UBER_PDF]. The Uber weekly statement is the only PDF artifact a driver
 *     can export (DiDi has none), so a shared PDF is unambiguously the Uber path.
 *  2. Two or more images → [ImportPlatform.DIDI] (the first two: ganancias + tablero). DiDi is the only
 *     platform that needs a pair of screenshots, so a multi-image share is the DiDi flow.
 *  3. A single image → [ImportPlatform.UBER_SCREENSHOT]. A lone earnings screenshot is most often the
 *     Uber fare-breakdown; if it is actually a DiDi/inDrive shot the dry-run preview rejects it with the
 *     backend's Spanish "wrong screen" string and the driver re-picks manually.
 *
 * The share activity classifies in two phases so it never drains a file it won't use (the activity is
 * exported and the streams are untrusted — see [plan]): a cheap MIME-only [plan] pass decides the
 * platform and exactly which file indices to read, then [classify] confirms once those (and only those)
 * bytes are in memory. Source-package / filename refinement is intentionally deferred — count already
 * separates the two dominant cases (TD-038).
 */
object ShareTargetClassifier {

    /** The MIME types the backend import endpoint accepts (mirrors `IMPORT_MIME_FILTER` in ImportScreen). */
    val ACCEPTED_MIME = setOf("application/pdf", "image/png", "image/jpeg", "image/webp")

    /** The platform a share maps to, plus which [acceptedMimes] indices the activity should actually read. */
    data class SharePlan(val platform: ImportPlatform, val indices: List<Int>)

    /**
     * Plan a share from the cheap MIME-only pass (no bytes read yet). [acceptedMimes] is the in-order
     * list of already-MIME-validated shares. Returns the target platform and the indices to read —
     * always at most [ImportPlatform.fileCount] of them — so the activity reads no unused files. Null
     * when there is nothing to import.
     */
    fun plan(acceptedMimes: List<String>): SharePlan? {
        if (acceptedMimes.isEmpty()) return null
        val pdfIndex = acceptedMimes.indexOfFirst { it == "application/pdf" }
        return when {
            pdfIndex >= 0 -> SharePlan(ImportPlatform.UBER_PDF, listOf(pdfIndex))
            acceptedMimes.size >= 2 -> SharePlan(ImportPlatform.DIDI, listOf(0, 1))
            else -> SharePlan(ImportPlatform.UBER_SCREENSHOT, listOf(0))
        }
    }

    /**
     * Build a [PendingSharedImport] from already-read [files]: drops non-accepted MIME types, [plan]s
     * the rest, and takes exactly the planned files. The result's file count always matches the chosen
     * platform's [ImportPlatform.fileCount] (or the result is null). Convenience over [plan] for callers
     * that already hold the bytes (e.g. the unit tests).
     */
    fun classify(files: List<ImportFile>): PendingSharedImport? {
        val accepted = files.filter { it.mimeType in ACCEPTED_MIME }
        val sharePlan = plan(accepted.map { it.mimeType }) ?: return null
        val chosen = sharePlan.indices.mapNotNull { accepted.getOrNull(it) }
        if (chosen.size != sharePlan.platform.fileCount) return null
        return PendingSharedImport(platform = sharePlan.platform, files = chosen)
    }
}
