package mx.kompara.ui.stats

import androidx.annotation.StringRes
import mx.kompara.data.model.Platform
import mx.kompara.ui.R

/** Spanish chip label for a platform selection; null ⇒ "Todas". */
@StringRes
fun platformChipLabel(platform: Platform?): Int = when (platform) {
    null -> R.string.platform_chip_todas
    Platform.UBER -> R.string.platform_chip_uber
    Platform.DIDI -> R.string.platform_chip_didi
    Platform.INDRIVE -> R.string.platform_chip_indrive
    Platform.UNKNOWN -> R.string.platform_chip_desconocida
}
