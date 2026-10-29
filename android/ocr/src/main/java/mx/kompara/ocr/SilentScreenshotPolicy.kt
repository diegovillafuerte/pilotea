package mx.kompara.ocr

import android.accessibilityservice.AccessibilityServiceInfo
import kotlinx.coroutines.CancellationException

/** Missing grants are permanent for this lane session; other platform failures may be transient. */
internal enum class ScreenshotFailure { NO_CAPABILITY, HARD_ERROR }

internal fun classifyScreenshotFailure(error: RuntimeException): ScreenshotFailure = when (error) {
    is CancellationException -> throw error
    is SecurityException -> ScreenshotFailure.NO_CAPABILITY
    else -> ScreenshotFailure.HARD_ERROR
}

/** Uses the granted service capabilities, which can remain stale after an app update. */
internal fun canStartSilentLane(apiLevel: Int, grantedCapabilities: Int): Boolean =
    apiLevel >= 30 &&
        (grantedCapabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0
