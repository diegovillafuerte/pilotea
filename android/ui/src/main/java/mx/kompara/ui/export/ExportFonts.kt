package mx.kompara.ui.export

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import mx.kompara.ui.R

/** Bundled faces shared with Compose; no platform substitution or synthetic condensing. */
class ExportFonts(context: Context) {
    val body = requireNotNull(ResourcesCompat.getFont(context, R.font.barlow_regular))
    val strong = requireNotNull(ResourcesCompat.getFont(context, R.font.barlow_semibold))
    val number = requireNotNull(ResourcesCompat.getFont(context, R.font.barlow_semi_condensed_semibold))
    val headline = requireNotNull(ResourcesCompat.getFont(context, R.font.barlow_semi_condensed_bold))
}
