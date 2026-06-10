package mx.kompara.sync.spec

import java.io.File

/**
 * On-device [SpecConfigStore] backed by files in the app's private `filesDir` (B-033).
 *
 * - The last-known-good bundle lives at `parser-config/last-known-good.json`. Writes go to a sibling
 *   temp file and are then atomically renamed over the target, so a crash mid-write can never leave
 *   a half-written cache that would fail verification on next launch (we'd silently fall back to
 *   bundled specs anyway, but atomic replace keeps a *valid* last-known-good intact).
 * - The dev override is read from `filesDir/spec-override.json` (note: at the `filesDir` root, where
 *   the task says a developer drops it — not inside the `parser-config/` subdir).
 */
class FileSpecConfigStore(
    private val filesDir: File,
) : SpecConfigStore {

    private val dir: File by lazy { File(filesDir, CONFIG_DIR).apply { mkdirs() } }
    private val cacheFile: File get() = File(dir, CACHE_FILE)
    private val overrideFile: File get() = File(filesDir, OVERRIDE_FILE)

    override fun readCachedBundle(): String? =
        cacheFile.takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    override fun writeCachedBundle(signedJson: String) {
        dir.mkdirs()
        val tmp = File(dir, "$CACHE_FILE.tmp")
        tmp.writeText(signedJson)
        // Atomic on the same filesystem; fall back to a plain copy if rename is refused.
        if (!tmp.renameTo(cacheFile)) {
            cacheFile.writeText(signedJson)
            tmp.delete()
        }
    }

    override fun readDevOverride(): String? =
        overrideFile.takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    private companion object {
        const val CONFIG_DIR = "parser-config"
        const val CACHE_FILE = "last-known-good.json"
        const val OVERRIDE_FILE = "spec-override.json"
    }
}
