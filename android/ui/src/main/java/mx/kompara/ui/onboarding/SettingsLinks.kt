package mx.kompara.ui.onboarding

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Deep-links into the system Settings screens the onboarding funnel needs. All are read-only
 * navigations — Kompara never requests a permission programmatically here, it only takes the driver
 * to the right screen and lets them flip the switch themselves (legal/Play posture, B-036).
 */
object AccessibilitySettings {

    /** Open the system Accessibility settings list (where the driver enables the Kompara reader). */
    fun open(context: Context) {
        launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** A [PendingIntent] that opens Accessibility settings — used by the watchdog notification. */
    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun launch(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Battery-optimization settings deep-link for the OEM survival kit. We open the *informational*
 * [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS] list and let the driver pick Kompara —
 * we deliberately do NOT fire `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which would request the
 * exemption directly and is a Play-policy-restricted action (B-036).
 */
object BatterySettings {

    /** Open the battery-optimization list (info only — never requests the exemption). */
    fun open(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Fall back to the generic app-details screen if the OEM doesn't expose the list activity.
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(fallback) }
    }
}
