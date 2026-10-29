package mx.kompara.ui.share

import android.graphics.Bitmap
import android.net.Uri
import mx.kompara.ui.export.ExportCache
import javax.inject.Inject

class ShareCardWriter @Inject constructor(private val cache: ExportCache) {
    fun write(bitmap: Bitmap): Uri = cache.write(SUBDIR, "png") {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
    fun clear() = cache.clear(SUBDIR)
    fun discard(uri: Uri) = cache.discard(uri)
    companion object {
        const val SUBDIR = "share_cards"
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
