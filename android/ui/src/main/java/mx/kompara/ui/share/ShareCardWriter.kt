package mx.kompara.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a rendered share-card [Bitmap] to a private cache file and hands back a [FileProvider]
 * content [Uri] that can be granted to the share target (B-055). The PNG lives under a dedicated
 * `share_cards/` cache subdir (declared in `res/xml/file_paths.xml`) so the FileProvider only ever
 * exposes these images, never the rest of the cache.
 *
 * The file name is stable per variant so repeated shares overwrite rather than pile up; the cache is
 * the OS-evictable cache dir so the images are transient by design.
 */
@Singleton
class ShareCardWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Compress [bitmap] to a PNG in the share-cards cache and return a shareable content URI for it.
     * @param variant used only to name the file, so the STORY and LANDSCAPE PNGs don't clobber each
     *   other when a driver toggles between them.
     */
    fun write(bitmap: Bitmap, variant: ShareCardVariant): Uri {
        val dir = File(context.cacheDir, SUBDIR).apply { mkdirs() }
        val file = File(dir, fileNameFor(variant))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, authority(), file)
    }

    private fun authority(): String = "${context.packageName}$AUTHORITY_SUFFIX"

    companion object {
        /** Cache subdir declared in `file_paths.xml` — the only path the FileProvider exposes. */
        const val SUBDIR = "share_cards"

        /** Appended to the applicationId to form the FileProvider authority (manifest match). */
        const val AUTHORITY_SUFFIX = ".fileprovider"

        /** Stable, per-variant PNG file name. */
        fun fileNameFor(variant: ShareCardVariant): String =
            "tu_resumen_${variant.name.lowercase()}.png"
    }
}
