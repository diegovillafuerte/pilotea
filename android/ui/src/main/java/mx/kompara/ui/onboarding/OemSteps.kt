package mx.kompara.ui.onboarding

import androidx.annotation.StringRes
import mx.kompara.ui.R

/**
 * The tailored survival-kit step copy for each [OemProfile], as ordered lists of string-resource
 * ids (Spanish, B-036). The mapping is pure (resource ids only) so the per-OEM step table is
 * unit-tested — we assert that every profile resolves to a non-empty, distinct step list.
 */
object OemSteps {

    @StringRes
    fun stepsFor(profile: OemProfile): List<Int> = when (profile) {
        OemProfile.XIAOMI -> listOf(
            R.string.onb_oem_xiaomi_1,
            R.string.onb_oem_xiaomi_2,
            R.string.onb_oem_xiaomi_3,
        )
        OemProfile.OPPO -> listOf(
            R.string.onb_oem_oppo_1,
            R.string.onb_oem_oppo_2,
            R.string.onb_oem_oppo_3,
        )
        OemProfile.VIVO -> listOf(
            R.string.onb_oem_vivo_1,
            R.string.onb_oem_vivo_2,
            R.string.onb_oem_vivo_3,
        )
        OemProfile.SAMSUNG -> listOf(
            R.string.onb_oem_samsung_1,
            R.string.onb_oem_samsung_2,
        )
        OemProfile.HUAWEI -> listOf(
            R.string.onb_oem_huawei_1,
            R.string.onb_oem_huawei_2,
        )
        OemProfile.GENERIC -> listOf(
            R.string.onb_oem_generic_1,
            R.string.onb_oem_generic_2,
        )
    }
}
