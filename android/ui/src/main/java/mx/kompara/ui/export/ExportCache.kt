package mx.kompara.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One immutable file per export type, accessible for at most an hour. Never stores preview files.
 * Replaced/hidden exports are revoked immediately; expiry is also enforced by the provider if Android
 * delays cleanup work. Startup and the worker remove expired files from the private cache.
 */
@Singleton
class ExportCache @Inject constructor(@ApplicationContext private val context: Context, private val work: WorkManager) {
    @Synchronized fun write(directory: String, extension: String, encode: (OutputStream) -> Unit): Uri {
        require(directory in DIRECTORIES)
        clear(directory)
        val dir = File(context.cacheDir, directory).apply { check(mkdirs() || isDirectory) }
        val file = File(dir, "kompara_${UUID.randomUUID()}.$extension")
        try {
            file.outputStream().use(encode)
            check(file.length() > 0)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            work.enqueueUniqueWork("export-expiry-$directory", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ExportCleanupWorker>().setInitialDelay(MAX_AGE_MS, TimeUnit.MILLISECONDS).build())
            return uri
        } catch (failure: Throwable) { file.delete(); throw failure }
    }
    @Synchronized fun clear(directory: String) {
        require(directory in DIRECTORIES)
        File(context.cacheDir, directory).listFiles().orEmpty().forEach { remove(context, it) }
    }
    @Synchronized fun discard(uri: Uri) {
        val file = file(context, uri) ?: return
        remove(context, file)
    }
    companion object {
        const val MAX_AGE_MS = 60 * 60 * 1000L
        val DIRECTORIES = setOf("share_cards", "fiscal_reports")
        fun expired(file: File, now: Long) = file.lastModified() > now || now - file.lastModified() >= MAX_AGE_MS
        fun prune(context: Context, now: Long = System.currentTimeMillis()) {
            DIRECTORIES.forEach { directory -> File(context.cacheDir, directory).listFiles().orEmpty()
                .filter { expired(it, now) }.forEach { remove(context, it) } }
        }
        internal fun file(context: Context, uri: Uri): File? {
            val parts = uri.pathSegments
            if (parts.size != 2 || parts[0] !in DIRECTORIES || uri.authority != "${context.packageName}.fileprovider") return null
            val dir = File(context.cacheDir, parts[0]).canonicalFile
            return File(dir, parts[1]).canonicalFile.takeIf { it.parentFile == dir }
        }
        private fun remove(context: Context, file: File) {
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            check(file.delete()) { "Could not remove export" }
        }
    }
}

class ExportCleanupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try { ExportCache.prune(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
}

/** Reject writes and expired grants, even while WorkManager is deferred by the OS. */
class ExportFileProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
        val context = requireNotNull(context)
        val file = ExportCache.file(context, uri) ?: throw java.io.FileNotFoundException()
        if (mode != "r" || !file.exists()) throw java.io.FileNotFoundException()
        if (ExportCache.expired(file, System.currentTimeMillis())) {
            ExportCache.prune(context)
            throw java.io.FileNotFoundException("Export expired")
        }
        return requireNotNull(super.openFile(uri, mode))
    }
}
