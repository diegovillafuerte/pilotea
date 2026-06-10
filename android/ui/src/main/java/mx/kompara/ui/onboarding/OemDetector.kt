package mx.kompara.ui.onboarding

import android.os.Build

/**
 * Maps a device's `Build.MANUFACTURER` to the matching [OemProfile] so the survival-kit screen can
 * show per-OEM autostart/battery steps only on devices that need them.
 *
 * The brand families follow dontkillmyapp.com groupings: Xiaomi's sub-brands (Redmi, Poco) share
 * MIUI/HyperOS settings; Oppo, Realme and OnePlus share ColorOS; Vivo and iQOO share Funtouch/OriginOS.
 * Matching is substring-based and case-insensitive because manufacturer strings are inconsistent
 * across firmware (e.g. "Xiaomi", "xiaomi", "Redmi"). Unknown brands fall back to [OemProfile.GENERIC].
 *
 * Pure function on a string so it can be unit-tested exhaustively without a device.
 */
object OemDetector {

    /** Detect the profile for the current device. */
    fun current(): OemProfile = detect(Build.MANUFACTURER)

    /** Detect the profile for an arbitrary manufacturer string (testable seam). */
    fun detect(manufacturer: String?): OemProfile {
        val m = manufacturer?.trim()?.lowercase().orEmpty()
        if (m.isEmpty()) return OemProfile.GENERIC
        return when {
            m.containsAny("xiaomi", "redmi", "poco") -> OemProfile.XIAOMI
            m.containsAny("oppo", "realme", "oneplus") -> OemProfile.OPPO
            m.containsAny("vivo", "iqoo") -> OemProfile.VIVO
            m.containsAny("samsung") -> OemProfile.SAMSUNG
            m.containsAny("huawei", "honor") -> OemProfile.HUAWEI
            else -> OemProfile.GENERIC
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }
}
