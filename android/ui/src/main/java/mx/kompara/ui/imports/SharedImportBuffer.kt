package mx.kompara.ui.imports

import mx.kompara.sync.api.ImportFile
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot hand-off from the inbound share-target ([mx.kompara.app.ShareImportActivity], PR-D3) to the
 * import flow's [ImportViewModel]. When the driver shares an Uber/DiDi earnings file into Kompara, the
 * (translucent) share activity drains the bytes into [ImportFile]s — the content-URI read grant is
 * transient and not guaranteed to survive the launch, so we copy out immediately — classifies the
 * platform ([ShareTargetClassifier]), drops the result here, and launches the shell at the import
 * route. The ViewModel [take]s it on init and jumps straight to the dry-run preview, so the file is
 * pre-picked (no document-picker round-trip).
 *
 * A Hilt [Singleton] so the share activity (which writes) and the import ViewModel (which reads) share
 * one instance. [take] is exactly-once: it clears the slot so a later, unrelated open of the import
 * flow never re-fires a stale share, and a configuration change can't double-submit. Holds the bytes in
 * memory only (no disk), so a never-consumed share is simply garbage-collected with the process.
 */
@Singleton
class SharedImportBuffer @Inject constructor() {

    private val pending = AtomicReference<PendingSharedImport?>(null)

    /** Stage a freshly-shared import for the next open of the import flow. Overwrites any prior unread share. */
    fun set(value: PendingSharedImport) {
        pending.set(value)
    }

    /** Consume the staged share exactly once (returns null if none / already taken). */
    fun take(): PendingSharedImport? = pending.getAndSet(null)
}

/**
 * A share handed off from the OS into the import flow: the already-read [files] plus the [platform] the
 * [ShareTargetClassifier] inferred from their MIME/count. The file count always matches
 * [ImportPlatform.fileCount] by construction (the classifier never produces a mismatch), so the
 * ViewModel can submit directly.
 */
data class PendingSharedImport(
    val platform: ImportPlatform,
    val files: List<ImportFile>,
)
